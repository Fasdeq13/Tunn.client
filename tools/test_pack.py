import json
import os
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pack

with tempfile.TemporaryDirectory() as t:
    key = os.path.join(t, "k.pem")
    pub = os.path.join(t, "k.pub")
    pack.keygen(key, pub)
    apk = os.path.join(t, "a.apk")
    with zipfile.ZipFile(apk, "w") as z:
        z.writestr("classes.dex", b"dex\n035" + b"1" * 50)
        z.writestr("classes2.dex", b"dex\n035" + b"2" * 50)
        z.writestr("lib/arm64-v8a/libtunn_service.so", b"so1")
        z.writestr("lib/arm64-v8a/libother.so", b"so2")
    files = pack.from_apk(apk, {"libtunn_service.so"})
    assert sorted(files) == ["dex/classes.dex", "dex/classes2.dex", "lib/arm64-v8a/libtunn_service.so"], sorted(files)
    path = pack.finish("update", "4.3", "", files, 1, "x", t, key)
    assert os.path.basename(path) == "Tunn-4.3.Update"
    with zipfile.ZipFile(path) as z:
        m = z.read("manifest.json")
        s = z.read("manifest.sig")
        open(os.path.join(t, "m"), "wb").write(m)
        open(os.path.join(t, "s"), "wb").write(s)
        subprocess.run("openssl ec -pubin -inform DER -in %s -out %s/pub.pem 2>/dev/null" % (pub, t), shell=True, check=True)
        r = subprocess.run(["openssl", "dgst", "-sha256", "-verify", os.path.join(t, "pub.pem"), "-signature", os.path.join(t, "s"), os.path.join(t, "m")], capture_output=True, text=True)
        assert "Verified OK" in r.stdout, r
        open(os.path.join(t, "m"), "ab").write(b" ")
        r = subprocess.run(["openssl", "dgst", "-sha256", "-verify", os.path.join(t, "pub.pem"), "-signature", os.path.join(t, "s"), os.path.join(t, "m")], capture_output=True, text=True)
        assert "Verified OK" not in r.stdout
        man = json.loads(m)
        assert man["format"] == 2 and man["kind"] == "update" and man["shell"] == 1
        for e in man["files"]:
            assert len(e["sha256"]) == 64
            assert z.read(e["path"])
    old = os.path.join(t, "old")
    new = os.path.join(t, "new")
    for d in (old, new):
        os.makedirs(os.path.join(d, "tunn/client"))
    open(os.path.join(old, "tunn/client/A.class"), "wb").write(b"1")
    open(os.path.join(new, "tunn/client/A.class"), "wb").write(b"2")
    open(os.path.join(old, "tunn/client/B.class"), "wb").write(b"same")
    open(os.path.join(new, "tunn/client/B.class"), "wb").write(b"same")
    open(os.path.join(new, "tunn/client/C.class"), "wb").write(b"new")
    ch = sorted(os.path.basename(x) for x in pack.changed_classes(old, new))
    assert ch == ["A.class", "C.class"], ch
    fp = pack.finish("fix", "4.3", "1", {"dex/fix1.dex": b"dex"}, 1, "", t, key)
    assert os.path.basename(fp) == "Tunn-4.3.1.fix"
print("pack ok")
