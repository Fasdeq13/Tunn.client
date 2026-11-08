import argparse
import glob
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

KEY = os.path.expanduser("~/.tunn/pack.pem")
PUB = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "android", "app", "src", "main", "assets", "tunn_pack.pub")
ABIS = ("arm64-v8a", "armeabi-v7a", "x86_64", "x86")


def run(*a, **k):
    return subprocess.run(a, check=True, **k)


def keygen(key=KEY, pub=PUB):
    os.makedirs(os.path.dirname(key), exist_ok=True)
    if os.path.exists(key):
        sys.exit("ключ уже есть: " + key)
    run("openssl", "ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", key)
    os.chmod(key, 0o600)
    os.makedirs(os.path.dirname(pub), exist_ok=True)
    run("openssl", "ec", "-in", key, "-pubout", "-outform", "DER", "-out", pub, stderr=subprocess.DEVNULL)
    print("приватный ключ:", key)
    print("публичный ключ:", os.path.abspath(pub), "(пересоберите APK, чтобы приложение принимало пакеты)")


def sign(data, key=KEY):
    with tempfile.TemporaryDirectory() as t:
        src = os.path.join(t, "m")
        out = os.path.join(t, "s")
        open(src, "wb").write(data)
        run("openssl", "dgst", "-sha256", "-sign", key, "-out", out, src)
        return open(out, "rb").read()


def finish(kind, version, serial, files, shell, notes, out, key=KEY):
    if kind == "update":
        name = "Tunn-%s.Update" % version
    else:
        name = "Tunn-%s.%s.fix" % (version, serial)
    entries = [{"path": p, "sha256": hashlib.sha256(d).hexdigest()} for p, d in sorted(files.items())]
    manifest = {"format": 2, "kind": kind, "version": version, "shell": shell, "notes": notes, "files": entries}
    mb = json.dumps(manifest, ensure_ascii=False, indent=1).encode("utf-8")
    os.makedirs(out, exist_ok=True)
    path = os.path.join(out, name)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("manifest.json", mb)
        z.writestr("manifest.sig", sign(mb, key))
        for p, d in sorted(files.items()):
            z.writestr(p, d)
    return path


def from_apk(apk, libs):
    files = {}
    with zipfile.ZipFile(apk) as z:
        for n in z.namelist():
            base = os.path.basename(n)
            if n.startswith("classes") and n.endswith(".dex") and "/" not in n:
                files["dex/" + n] = z.read(n)
            parts = n.split("/")
            if len(parts) == 3 and parts[0] == "lib" and parts[1] in ABIS and base in libs:
                files[n] = z.read(n)
    return files


def d8_tools():
    home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
    d8s = sorted(glob.glob(os.path.join(home, "build-tools", "*", "d8")))
    jars = sorted(glob.glob(os.path.join(home, "platforms", "android-*", "android.jar")))
    if not d8s or not jars:
        sys.exit("не найден d8 или android.jar, задайте ANDROID_HOME")
    return d8s[-1], jars[-1]


FROZEN = ("tunn/client/Layer", "tunn/client/TunnApp")


def changed_classes(old, new):
    out = []
    for d, _, fs in os.walk(new):
        for f in fs:
            if not f.endswith(".class"):
                continue
            p = os.path.join(d, f)
            rel = os.path.relpath(p, new)
            if rel.startswith(FROZEN):
                continue
            o = os.path.join(old, rel)
            if not os.path.isfile(o) or open(o, "rb").read() != open(p, "rb").read():
                out.append(p)
    return out


def fix_dex(old, new, name="fix"):
    classes = changed_classes(old, new)
    if not classes:
        sys.exit("изменённых классов нет")
    d8, jar = d8_tools()
    with tempfile.TemporaryDirectory() as t:
        run(d8, "--release", "--min-api", "24", "--lib", jar, "--output", t, *classes)
        dex = os.path.join(t, "classes.dex")
        print("классов в исправлении:", len(classes))
        return {"dex/%s.dex" % name: open(dex, "rb").read()}


def add_extra(files, extra):
    for e in extra or []:
        dest, src = e.split("=", 1)
        if os.path.isdir(src):
            for d, _, fs in os.walk(src):
                for f in fs:
                    p = os.path.join(d, f)
                    files[dest.rstrip("/") + "/" + os.path.relpath(p, src).replace(os.sep, "/")] = open(p, "rb").read()
        else:
            files[dest] = open(src, "rb").read()


def main(argv=None):
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("keygen")
    u = sub.add_parser("update")
    u.add_argument("--apk", required=True)
    u.add_argument("--version", required=True)
    u.add_argument("--shell", type=int, default=1)
    u.add_argument("--libs", default="libtunn_service.so")
    u.add_argument("--notes", default="")
    u.add_argument("--extra", action="append")
    u.add_argument("--out", default="dist")
    u.add_argument("--key", default=KEY)
    f = sub.add_parser("fix")
    f.add_argument("--old-classes", required=True)
    f.add_argument("--new-classes", required=True)
    f.add_argument("--version", required=True)
    f.add_argument("--serial", required=True)
    f.add_argument("--shell", type=int, default=1)
    f.add_argument("--notes", default="")
    f.add_argument("--extra", action="append")
    f.add_argument("--out", default="dist")
    f.add_argument("--key", default=KEY)
    a = ap.parse_args(argv)
    if a.cmd == "keygen":
        return keygen()
    if a.cmd == "update":
        files = from_apk(a.apk, set(a.libs.split(",")))
        add_extra(files, a.extra)
        print(finish("update", a.version, "", files, a.shell, a.notes, a.out, a.key))
    else:
        files = fix_dex(a.old_classes, a.new_classes, "fix" + a.serial.replace(".", "_"))
        add_extra(files, a.extra)
        print(finish("fix", a.version, a.serial, files, a.shell, a.notes, a.out, a.key))


if __name__ == "__main__":
    main()
