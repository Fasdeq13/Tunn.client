use crate::store::data_dir;
use crate::xray::{free_port, Proc};
use serde_json::{json, Value};
use std::io::{Cursor, Read};
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::time::{Duration, Instant};

pub fn supports(o: &Value) -> bool {
    let p = o["protocol"].as_str().unwrap_or("");
    if !["vless", "vmess", "trojan", "shadowsocks", "socks", "tuic"].contains(&p) {
        return false;
    }
    if p == "tuic" {
        return true;
    }
    let net = o["streamSettings"]["network"].as_str().unwrap_or("tcp");
    ["tcp", "ws", "grpc", "httpupgrade", "h2"].contains(&net)
}

fn server(o: &Value) -> Value {
    let s = &o["settings"];
    if let Some(v) = s["vnext"].get(0) {
        return v.clone();
    }
    s["servers"].get(0).cloned().unwrap_or(json!({}))
}

fn tls(stream: &Value) -> Option<Value> {
    match stream["security"].as_str().unwrap_or("none") {
        "tls" => {
            let t = &stream["tlsSettings"];
            let mut o = json!({"enabled": true});
            if let Some(sn) = t["serverName"].as_str().filter(|x| !x.is_empty()) {
                o["server_name"] = json!(sn);
            }
            if t["allowInsecure"].as_bool().unwrap_or(false) {
                o["insecure"] = json!(true);
            }
            if t["alpn"].as_array().map(|a| !a.is_empty()).unwrap_or(false) {
                o["alpn"] = t["alpn"].clone();
            }
            if let Some(fp) = t["fingerprint"].as_str().filter(|x| !x.is_empty()) {
                o["utls"] = json!({"enabled": true, "fingerprint": fp});
            }
            Some(o)
        }
        "reality" => {
            let r = &stream["realitySettings"];
            let fp = r["fingerprint"].as_str().filter(|x| !x.is_empty()).unwrap_or("chrome");
            let key = r["publicKey"].as_str().or(r["password"].as_str()).unwrap_or("");
            Some(json!({
                "enabled": true,
                "server_name": r["serverName"].as_str().unwrap_or(""),
                "utls": {"enabled": true, "fingerprint": fp},
                "reality": {"enabled": true, "public_key": key, "short_id": r["shortId"].as_str().unwrap_or("")}
            }))
        }
        _ => None,
    }
}

fn transport(stream: &Value) -> Option<Value> {
    match stream["network"].as_str().unwrap_or("tcp") {
        "ws" => {
            let w = &stream["wsSettings"];
            let mut o = json!({"type": "ws", "path": w["path"].as_str().unwrap_or("/")});
            if let Some(h) = w["headers"]["Host"].as_str().filter(|x| !x.is_empty()) {
                o["headers"] = json!({"Host": h});
            }
            Some(o)
        }
        "grpc" => Some(json!({"type": "grpc", "service_name": stream["grpcSettings"]["serviceName"].as_str().unwrap_or("")})),
        "httpupgrade" => {
            let h = &stream["httpupgradeSettings"];
            Some(json!({"type": "httpupgrade", "path": h["path"].as_str().unwrap_or("/"), "host": h["host"].as_str().unwrap_or("")}))
        }
        "h2" => Some(json!({"type": "http"})),
        _ => None,
    }
}

pub const TUN_NAME: &str = "tunn0";

pub fn build_config(outbound: &Value, port: u16, tun: bool) -> Result<Value, String> {
    let protocol = outbound["protocol"].as_str().unwrap_or("");
    let s = server(outbound);
    let stream = &outbound["streamSettings"];
    let user = s["users"].get(0).cloned().unwrap_or(json!({}));
    let mut o = json!({
        "tag": "proxy",
        "server": s["address"].as_str().unwrap_or(""),
        "server_port": s["port"].as_u64().unwrap_or(0)
    });
    match protocol {
        "vless" => {
            o["type"] = json!("vless");
            o["uuid"] = user["id"].clone();
            if let Some(f) = user["flow"].as_str().filter(|x| !x.is_empty()) {
                o["flow"] = json!(f);
            }
        }
        "vmess" => {
            o["type"] = json!("vmess");
            o["uuid"] = user["id"].clone();
            o["security"] = json!(user["security"].as_str().unwrap_or("auto"));
            o["alter_id"] = json!(user["alterId"].as_u64().unwrap_or(0));
        }
        "trojan" => {
            o["type"] = json!("trojan");
            o["password"] = s["password"].clone();
        }
        "shadowsocks" => {
            o["type"] = json!("shadowsocks");
            o["method"] = s["method"].clone();
            o["password"] = s["password"].clone();
        }
        "socks" => {
            o["type"] = json!("socks");
            o["version"] = json!("5");
        }
        "tuic" => {
            let t = &outbound["tuic"];
            o["type"] = json!("tuic");
            o["uuid"] = s["uuid"].clone();
            o["password"] = s["password"].clone();
            o["congestion_control"] = json!(t["congestion_control"].as_str().unwrap_or("bbr"));
            o["udp_relay_mode"] = json!(t["udp_relay_mode"].as_str().unwrap_or("native"));
            if t["zero_rtt"].as_bool().unwrap_or(false) {
                o["zero_rtt_handshake"] = json!(true);
            }
            let ts = &stream["tlsSettings"];
            let mut tl = json!({
                "enabled": true,
                "server_name": ts["serverName"].as_str().unwrap_or(s["address"].as_str().unwrap_or("")),
                "alpn": if ts["alpn"].is_array() { ts["alpn"].clone() } else { json!(["h3"]) }
            });
            if ts["allowInsecure"].as_bool().unwrap_or(false) {
                tl["insecure"] = json!(true);
            }
            o["tls"] = tl;
        }
        other => return Err(format!("sing-box не поддерживает протокол {}", other)),
    }
    if !["socks", "shadowsocks", "tuic"].contains(&protocol) {
        if let Some(t) = tls(stream) {
            o["tls"] = t;
        }
        if let Some(t) = transport(stream) {
            o["transport"] = t;
        }
    }
    let mut inbounds = vec![json!({"type": "mixed", "tag": "in", "listen": "127.0.0.1", "listen_port": port})];
    let mut route = json!({"final": "proxy"});
    if tun {
        inbounds.push(json!({
            "type": "tun", "tag": "tun-in", "interface_name": TUN_NAME,
            "address": ["172.19.0.1/30", "fdfe:dcba:9876::1/126"],
            "mtu": 1400, "auto_route": true, "strict_route": false, "stack": "mixed"
        }));
        route["auto_detect_interface"] = json!(true);
    }
    Ok(json!({
        "log": {"level": "warn"},
        "inbounds": inbounds,
        "outbounds": [o, {"type": "direct", "tag": "direct"}],
        "route": route
    }))
}

fn exe() -> &'static str {
    if cfg!(windows) { "sing-box.exe" } else { "sing-box" }
}

fn core_dir() -> PathBuf {
    let d = data_dir().join("sbcore");
    let _ = std::fs::create_dir_all(&d);
    d
}

pub fn find_binary() -> Option<PathBuf> {
    let own = core_dir().join(exe());
    if own.is_file() {
        return Some(own);
    }
    let path = std::env::var_os("PATH")?;
    std::env::split_paths(&path).map(|p| p.join(exe())).find(|p| p.is_file())
}

fn http(url: &str, limit: u64) -> Result<Vec<u8>, String> {
    let agent: ureq::Agent = ureq::Agent::config_builder()
        .timeout_global(Some(Duration::from_secs(300)))
        .timeout_connect(Some(Duration::from_secs(15)))
        .http_status_as_error(false)
        .build()
        .into();
    let mut resp = agent
        .get(url)
        .header("User-Agent", "Tunn/1.4")
        .header("Accept", "application/vnd.github+json")
        .call()
        .map_err(|e| e.to_string())?;
    let code = resp.status().as_u16();
    if !(200..300).contains(&code) {
        return Err(format!("HTTP {}", code));
    }
    resp.body_mut().with_config().limit(limit).read_to_vec().map_err(|e| e.to_string())
}

fn platform() -> Result<(&'static str, &'static str, bool), String> {
    match (std::env::consts::OS, std::env::consts::ARCH) {
        ("linux", "x86_64") => Ok(("linux", "amd64", false)),
        ("linux", "aarch64") => Ok(("linux", "arm64", false)),
        ("windows", "x86_64") => Ok(("windows", "amd64", true)),
        ("windows", "aarch64") => Ok(("windows", "arm64", true)),
        ("macos", "x86_64") => Ok(("darwin", "amd64", false)),
        ("macos", "aarch64") => Ok(("darwin", "arm64", false)),
        _ => Err("Для этой системы нет готовой сборки sing-box".into()),
    }
}

fn unpack(raw: &[u8], is_zip: bool, dir: &Path) -> Result<(), String> {
    let mut found = false;
    if is_zip {
        let mut z = zip::ZipArchive::new(Cursor::new(raw)).map_err(|e| e.to_string())?;
        for i in 0..z.len() {
            let mut f = z.by_index(i).map_err(|e| e.to_string())?;
            if f.is_dir() {
                continue;
            }
            let name = f.name().rsplit('/').next().unwrap_or("").to_string();
            if name.is_empty() {
                continue;
            }
            let mut buf = Vec::new();
            f.read_to_end(&mut buf).map_err(|e| e.to_string())?;
            std::fs::write(dir.join(&name), buf).map_err(|e| e.to_string())?;
            if name == exe() {
                found = true;
            }
        }
    } else {
        let gz = flate2::read::GzDecoder::new(Cursor::new(raw));
        let mut ar = tar::Archive::new(gz);
        for e in ar.entries().map_err(|e| e.to_string())? {
            let mut e = e.map_err(|e| e.to_string())?;
            let path = e.path().map_err(|e| e.to_string())?.to_path_buf();
            let name = path.file_name().map(|s| s.to_string_lossy().to_string()).unwrap_or_default();
            if name == exe() {
                let mut buf = Vec::new();
                e.read_to_end(&mut buf).map_err(|e| e.to_string())?;
                std::fs::write(dir.join(exe()), buf).map_err(|e| e.to_string())?;
                found = true;
            }
        }
    }
    if !found {
        return Err("В архиве нет sing-box".into());
    }
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let _ = std::fs::set_permissions(dir.join(exe()), std::fs::Permissions::from_mode(0o755));
    }
    Ok(())
}

pub fn download_latest() -> Result<PathBuf, String> {
    let (os, arch, zip) = platform()?;
    let raw = http("https://api.github.com/repos/SagerNet/sing-box/releases/latest", 8 * 1024 * 1024)?;
    let rel: Value = serde_json::from_slice(&raw).map_err(|e| e.to_string())?;
    let suffix = format!("-{}-{}.{}", os, arch, if zip { "zip" } else { "tar.gz" });
    let url = rel["assets"]
        .as_array()
        .and_then(|a| a.iter().find(|x| x["name"].as_str().map(|n| n.ends_with(&suffix)).unwrap_or(false)))
        .and_then(|x| x["browser_download_url"].as_str())
        .ok_or("Не нашёл сборку sing-box для этой системы")?
        .to_string();
    let data = http(&url, 200 * 1024 * 1024)?;
    unpack(&data, zip, &core_dir())?;
    find_binary().ok_or_else(|| "sing-box не найден после загрузки".to_string())
}

#[cfg(target_os = "linux")]
const TUN_SCRIPT: &str = r#"
SB="$1"; CFG="$2"; STOP="$3"; PARENT="$4"
alive() { [ -r "/proc/$1/stat" ] && ! grep -q ') Z' "/proc/$1/stat"; }
D=$(mktemp -d /run/tunn.XXXXXX 2>/dev/null || mktemp -d) || exit 5
chmod 700 "$D"
cp "$SB" "$D/sing-box" && cp "$CFG" "$D/c.json" || exit 5
chmod 700 "$D/sing-box"
SP=""
fin() { [ -n "$SP" ] && kill "$SP" 2>/dev/null; wait 2>/dev/null; rm -rf "$D"; }
trap fin EXIT
trap 'exit 0' INT TERM HUP
"$D/sing-box" run -c "$D/c.json" &
SP=$!
while alive "$SP" && alive "$PARENT" && [ ! -e "$STOP" ]; do sleep 1; done
exit 0
"#;

pub fn start(bin: &Path, config: &Value, port: u16) -> Result<Proc, String> {
    let path = data_dir().join(format!("run-{}.json", port));
    std::fs::write(&path, config.to_string()).map_err(|e| e.to_string())?;
    let log = data_dir().join(format!("run-{}.log", port));
    let lf = std::fs::File::create(&log).ok();
    let mut cmd = Command::new(bin);
    cmd.arg("run").arg("-c").arg(&path).stdin(Stdio::null())
        .stdout(match lf.as_ref().and_then(|f| f.try_clone().ok()) { Some(f) => Stdio::from(f), None => Stdio::null() })
        .stderr(match lf { Some(f) => Stdio::from(f), None => Stdio::null() });
    crate::xray::hide_window(&mut cmd);
    let child = cmd.spawn().map_err(|e| format!("Не удалось запустить sing-box: {}", e))?;
    let mut proc = Proc::new(child, path, None);
    wait_ready(&mut proc, &log, port, 10, false)?;
    Ok(proc)
}

fn wait_ready(proc: &mut Proc, log: &Path, port: u16, secs: u64, need_tun: bool) -> Result<(), String> {
    let end = Instant::now() + Duration::from_secs(secs);
    loop {
        if !proc.alive() {
            let text = std::fs::read_to_string(log).unwrap_or_default();
            let last = text.lines().rev().find(|l| !l.trim().is_empty()).unwrap_or("sing-box завершился сразу после запуска").trim().to_string();
            return Err(format!("sing-box не запустился: {}", last));
        }
        let port_ok = std::net::TcpStream::connect_timeout(&([127, 0, 0, 1], port).into(), Duration::from_millis(150)).is_ok();
        let tun_ok = !need_tun || std::path::Path::new(&format!("/sys/class/net/{}", TUN_NAME)).exists();
        if port_ok && tun_ok {
            let _ = std::fs::remove_file(log);
            return Ok(());
        }
        if Instant::now() > end {
            proc.stop();
            return Err("sing-box не ответил вовремя".into());
        }
        std::thread::sleep(Duration::from_millis(100));
    }
}

#[cfg(target_os = "linux")]
pub fn start_tun(bin: &Path, config: &Value, port: u16) -> Result<Proc, String> {
    let Some(pkexec) = crate::xray::which("pkexec") else {
        return Err("Для TUN нужен pkexec (polkit). Установите polkit или выберите режим «Системный прокси»".into());
    };
    if !Path::new("/dev/net/tun").exists() {
        return Err("Нет /dev/net/tun. Загрузите модуль: sudo modprobe tun".into());
    }
    let path = data_dir().join(format!("run-{}.json", port));
    std::fs::write(&path, config.to_string()).map_err(|e| e.to_string())?;
    let stop = data_dir().join("tun.stop");
    let _ = std::fs::remove_file(&stop);
    let log = data_dir().join("tun.log");
    let lf = std::fs::File::create(&log).ok();
    let mut cmd = Command::new(pkexec);
    cmd.arg("/bin/sh").arg("-c").arg(TUN_SCRIPT).arg("tunn").arg(bin).arg(&path).arg(&stop)
        .arg(std::process::id().to_string())
        .stdin(Stdio::null())
        .stdout(match lf.as_ref().and_then(|f| f.try_clone().ok()) { Some(f) => Stdio::from(f), None => Stdio::null() })
        .stderr(match lf { Some(f) => Stdio::from(f), None => Stdio::null() });
    let child = cmd.spawn().map_err(|e| format!("Не удалось запустить pkexec: {}", e))?;
    let mut proc = Proc::new(child, path, Some(stop));
    let end = Instant::now() + Duration::from_secs(120);
    loop {
        if let Ok(Some(status)) = proc.try_wait() {
            let text = std::fs::read_to_string(&log).unwrap_or_default();
            let last = text.lines().rev().find(|l| !l.trim().is_empty()).unwrap_or("").trim().to_string();
            proc.forget_stop();
            let msg = match status.code() {
                Some(126) => "Доступ отклонён или пароль не введён".to_string(),
                Some(127) => "Не найден polkit-агент для запроса пароля. Запустите hyprpolkitagent или polkit-gnome, либо выберите режим «Системный прокси»".to_string(),
                _ => if last.is_empty() { "TUN не запустился".to_string() } else { last },
            };
            return Err(msg);
        }
        let port_ok = std::net::TcpStream::connect_timeout(&([127, 0, 0, 1], port).into(), Duration::from_millis(150)).is_ok();
        if port_ok && Path::new(&format!("/sys/class/net/{}", TUN_NAME)).exists() {
            let _ = std::fs::remove_file(&log);
            return Ok(proc);
        }
        if Instant::now() > end {
            proc.stop();
            return Err("TUN не запустился вовремя".into());
        }
        std::thread::sleep(Duration::from_millis(150));
    }
}

#[allow(dead_code)]
pub fn port() -> u16 {
    free_port()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reality_and_xhttp() {
        let p = fasdeq_core::links::parse("vless://11111111-1111-1111-1111-111111111111@1.2.3.4:443?type=tcp&security=reality&pbk=KEY&sid=ab&sni=example.com&fp=chrome&flow=xtls-rprx-vision#r").unwrap();
        assert!(supports(&p.outbound));
        let c = build_config(&p.outbound, 1080, true).unwrap();
        let o = &c["outbounds"][0];
        assert_eq!(o["type"], "vless");
        assert_eq!(o["tls"]["reality"]["public_key"], "KEY");
        assert_eq!(c["inbounds"][1]["type"], "tun");
        let x = fasdeq_core::links::parse("vless://11111111-1111-1111-1111-111111111111@1.2.3.4:443?type=xhttp&security=tls&sni=a.com&path=%2Fx#x").unwrap();
        assert!(!supports(&x.outbound));
    }
}
