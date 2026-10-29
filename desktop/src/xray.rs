use crate::links;
use crate::model::{SERVICE_URLS};
use crate::store::data_dir;
use serde_json::Value;
use std::io::{Cursor, Read};
use std::net::{TcpListener, TcpStream};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::time::{Duration, Instant};

#[derive(Clone, Debug, PartialEq)]
pub struct CoreInfo {
    pub id: String,
    pub label: String,
    pub version: String,
    pub path: PathBuf,
}

#[derive(Clone, Debug)]
pub struct Release {
    pub tag: String,
    pub url: String,
    pub size: u64,
}

pub use fasdeq_core::proc::hide_window;

fn exe_name() -> &'static str {
    if cfg!(target_os = "windows") {
        "xray.exe"
    } else {
        "xray"
    }
}

fn cores_root() -> PathBuf {
    let dir = data_dir().join("cores");
    let _ = std::fs::create_dir_all(&dir);
    dir
}

pub fn list() -> Vec<CoreInfo> {
    let mut result = Vec::new();
    let Ok(entries) = std::fs::read_dir(cores_root()) else {
        return result;
    };
    for entry in entries.flatten() {
        let dir = entry.path();
        if !dir.is_dir() {
            continue;
        }
        let bin = dir.join(exe_name());
        let meta = dir.join("meta.json");
        if !bin.exists() || !meta.exists() {
            continue;
        }
        if let Ok(raw) = std::fs::read_to_string(&meta) {
            if let Ok(o) = serde_json::from_str::<Value>(&raw) {
                result.push(CoreInfo {
                    id: dir.file_name().unwrap_or_default().to_string_lossy().to_string(),
                    label: o["label"].as_str().unwrap_or("").to_string(),
                    version: o["version"].as_str().unwrap_or("").to_string(),
                    path: bin,
                });
            }
        }
    }
    result.sort_by(|a, b| a.label.cmp(&b.label));
    result
}

pub fn delete(id: &str) {
    let _ = std::fs::remove_dir_all(cores_root().join(id));
}

fn which(name: &str) -> Option<PathBuf> {
    let path = std::env::var_os("PATH")?;
    std::env::split_paths(&path)
        .map(|p| p.join(name))
        .find(|p| p.is_file())
}

pub fn find_binary(active: &str) -> Option<PathBuf> {
    if !active.is_empty() {
        let bin = cores_root().join(active).join(exe_name());
        if bin.exists() {
            return Some(bin);
        }
    }
    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            for candidate in [dir.join(exe_name()), dir.join("cores").join(exe_name())] {
                if candidate.is_file() {
                    return Some(candidate);
                }
            }
        }
    }
    which(exe_name())
}

pub fn version(path: &Path) -> Result<String, String> {
    let mut cmd = Command::new(path);
    cmd.arg("version")
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .stdin(Stdio::null());
    hide_window(&mut cmd);
    let child = cmd
        .spawn()
        .map_err(|e| format!("Ядро не запускается на этом устройстве: {}", e))?;
    let output = wait_output(child, Duration::from_secs(6));
    let text = output.trim().to_string();
    if let Some(i) = text.find("Xray") {
        let rest = text[i + 4..].trim_start();
        let ver: String = rest.chars().take_while(|c| !c.is_whitespace()).collect();
        if ver.chars().next().map(|c| c.is_ascii_digit()).unwrap_or(false) {
            return Ok(ver);
        }
    }
    let head: String = text.chars().take(120).collect();
    Err(format!("Это не ядро Xray или оно не подходит: {}", head))
}

fn wait_output(mut child: Child, limit: Duration) -> String {
    let mut stdout = child.stdout.take();
    let mut stderr = child.stderr.take();
    let a = std::thread::spawn(move || {
        let mut out = Vec::new();
        if let Some(s) = stdout.as_mut() {
            let _ = s.read_to_end(&mut out);
        }
        out
    });
    let b = std::thread::spawn(move || {
        let mut out = Vec::new();
        if let Some(s) = stderr.as_mut() {
            let _ = s.read_to_end(&mut out);
        }
        out
    });
    let end = Instant::now() + limit;
    loop {
        match child.try_wait() {
            Ok(Some(_)) => break,
            Ok(None) => {
                if Instant::now() > end {
                    let _ = child.kill();
                    let _ = child.wait();
                    break;
                }
                std::thread::sleep(Duration::from_millis(20));
            }
            Err(_) => break,
        }
    }
    let mut all = a.join().unwrap_or_default();
    all.extend(b.join().unwrap_or_default());
    String::from_utf8_lossy(&all).to_string()
}

fn random_id() -> String {
    format!("c{:x}", rand::random::<u32>() % 0xEFFFFF + 0x100000)
}

pub fn install(raw: &[u8], label: &str) -> Result<CoreInfo, String> {
    if raw.len() < 1024 {
        return Err("Файл слишком мал".into());
    }
    let id = random_id();
    let dir = cores_root().join(&id);
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    let result = (|| -> Result<CoreInfo, String> {
        let bin = dir.join(exe_name());
        if raw.starts_with(b"PK") {
            let mut archive = zip::ZipArchive::new(Cursor::new(raw)).map_err(|e| e.to_string())?;
            let mut found = false;
            for i in 0..archive.len() {
                let mut file = archive.by_index(i).map_err(|e| e.to_string())?;
                if file.is_dir() {
                    continue;
                }
                let name = file.name().rsplit('/').next().unwrap_or("").to_string();
                let target = match name.as_str() {
                    "xray" | "xray.exe" | "xray.bin" => {
                        found = true;
                        bin.clone()
                    }
                    "geoip.dat" | "geosite.dat" => dir.join(&name),
                    _ => continue,
                };
                let mut data = Vec::new();
                file.read_to_end(&mut data).map_err(|e| e.to_string())?;
                std::fs::write(&target, data).map_err(|e| e.to_string())?;
            }
            if !found {
                return Err("В архиве нет файла xray".into());
            }
        } else {
            std::fs::write(&bin, raw).map_err(|e| e.to_string())?;
        }
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let _ = std::fs::set_permissions(&bin, std::fs::Permissions::from_mode(0o755));
        }
        let ver = version(&bin)?;
        let title = if label.trim().is_empty() {
            format!("Xray {}", ver)
        } else {
            label.to_string()
        };
        std::fs::write(
            dir.join("meta.json"),
            serde_json::json!({"label": title, "version": ver}).to_string(),
        )
        .map_err(|e| e.to_string())?;
        Ok(CoreInfo {
            id: id.clone(),
            label: title,
            version: ver,
            path: bin,
        })
    })();
    if result.is_err() {
        let _ = std::fs::remove_dir_all(&dir);
    }
    result
}

pub fn install_file(path: &Path) -> Result<CoreInfo, String> {
    let raw = std::fs::read(path).map_err(|_| "Не удалось прочитать файл".to_string())?;
    let label = path
        .file_stem()
        .map(|s| s.to_string_lossy().to_string())
        .unwrap_or_default();
    install(&raw, &label)
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
        .header("User-Agent", "FasdeqVPN/1.4")
        .header("Accept", "application/vnd.github+json")
        .call()
        .map_err(|e| e.to_string())?;
    let code = resp.status().as_u16();
    if !(200..300).contains(&code) {
        return Err(format!("HTTP {}", code));
    }
    resp.body_mut()
        .with_config()
        .limit(limit)
        .read_to_vec()
        .map_err(|e| e.to_string())
}

fn asset_name() -> Result<&'static str, String> {
    match (std::env::consts::OS, std::env::consts::ARCH) {
        ("linux", "x86_64") => Ok("Xray-linux-64.zip"),
        ("linux", "aarch64") => Ok("Xray-linux-arm64-v8a.zip"),
        ("windows", "x86_64") => Ok("Xray-windows-64.zip"),
        ("windows", "aarch64") => Ok("Xray-windows-arm64-v8a.zip"),
        ("macos", "x86_64") => Ok("Xray-macos-64.zip"),
        ("macos", "aarch64") => Ok("Xray-macos-arm64-v8a.zip"),
        _ => Err("Для этой системы нет готовой сборки Xray".into()),
    }
}

pub fn releases() -> Result<Vec<Release>, String> {
    let asset = asset_name()?;
    let raw = http(
        "https://api.github.com/repos/XTLS/Xray-core/releases?per_page=20",
        8 * 1024 * 1024,
    )?;
    let arr: Value = serde_json::from_slice(&raw).map_err(|e| e.to_string())?;
    let mut result = Vec::new();
    for r in arr.as_array().cloned().unwrap_or_default() {
        for a in r["assets"].as_array().cloned().unwrap_or_default() {
            if a["name"].as_str() == Some(asset) {
                result.push(Release {
                    tag: r["tag_name"].as_str().unwrap_or("").to_string(),
                    url: a["browser_download_url"].as_str().unwrap_or("").to_string(),
                    size: a["size"].as_u64().unwrap_or(0),
                });
            }
        }
    }
    Ok(result)
}

pub fn download(url: &str) -> Result<Vec<u8>, String> {
    http(url, 200 * 1024 * 1024)
}

pub fn free_port() -> u16 {
    TcpListener::bind("127.0.0.1:0")
        .and_then(|l| l.local_addr())
        .map(|a| a.port())
        .unwrap_or(20000 + rand::random::<u16>() % 20000)
}

#[derive(Debug)]
pub struct Proc {
    child: Child,
    config: PathBuf,
}

impl Proc {
    pub fn alive(&mut self) -> bool {
        matches!(self.child.try_wait(), Ok(None))
    }

    pub fn stop(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
        let _ = std::fs::remove_file(&self.config);
    }
}

impl Drop for Proc {
    fn drop(&mut self) {
        self.stop();
    }
}

pub fn start(bin: &Path, config: &Value, wait_port: u16) -> Result<Proc, String> {
    let path = data_dir().join(format!("run-{}.json", wait_port));
    std::fs::write(&path, config.to_string()).map_err(|e| e.to_string())?;
    let log = data_dir().join(format!("run-{}.log", wait_port));
    let log_file = std::fs::File::create(&log).ok();
    let mut cmd = Command::new(bin);
    cmd.arg("run")
        .arg("-config")
        .arg(&path)
        .stdin(Stdio::null())
        .stdout(match log_file.as_ref().and_then(|f| f.try_clone().ok()) {
            Some(f) => Stdio::from(f),
            None => Stdio::null(),
        })
        .stderr(match log_file {
            Some(f) => Stdio::from(f),
            None => Stdio::null(),
        });
    if let Some(dir) = bin.parent() {
        cmd.env("XRAY_LOCATION_ASSET", dir);
    }
    hide_window(&mut cmd);
    let child = cmd
        .spawn()
        .map_err(|e| format!("Не удалось запустить ядро: {}", e))?;
    let mut proc = Proc {
        child,
        config: path,
    };
    let end = Instant::now() + Duration::from_secs(10);
    loop {
        if !proc.alive() {
            let text = std::fs::read_to_string(&log).unwrap_or_default();
            let _ = std::fs::remove_file(&log);
            let line = text
                .lines()
                .rev()
                .find(|l| !l.trim().is_empty())
                .unwrap_or("ядро завершилось сразу после запуска");
            return Err(format!("Ядро не запустилось: {}", line.trim()));
        }
        if TcpStream::connect_timeout(
            &([127, 0, 0, 1], wait_port).into(),
            Duration::from_millis(150),
        )
        .is_ok()
        {
            let _ = std::fs::remove_file(&log);
            return Ok(proc);
        }
        if Instant::now() > end {
            proc.stop();
            return Err("Ядро не ответило вовремя".into());
        }
        std::thread::sleep(Duration::from_millis(60));
    }
}

pub fn query_stats(bin: &Path, api_port: u16) -> Option<(u64, u64)> {
    let mut cmd = Command::new(bin);
    cmd.args(["api", "statsquery", &format!("--server=127.0.0.1:{}", api_port)])
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .stdin(Stdio::null());
    hide_window(&mut cmd);
    let child = cmd.spawn().ok()?;
    let text = wait_output(child, Duration::from_secs(3));
    let v: Value = serde_json::from_str(&text).ok()?;
    let mut down = 0u64;
    let mut up = 0u64;
    for s in v["stat"].as_array()? {
        let name = s["name"].as_str().unwrap_or("");
        let value = match &s["value"] {
            Value::String(t) => t.parse::<u64>().unwrap_or(0),
            Value::Number(n) => n.as_u64().unwrap_or(0),
            _ => 0,
        };
        if name == "outbound>>>proxy>>>traffic>>>downlink" {
            down = value;
        } else if name == "outbound>>>proxy>>>traffic>>>uplink" {
            up = value;
        }
    }
    Some((down, up))
}

fn probe(port: u16, url: &str, timeout: u64) -> i32 {
    let proxy = match ureq::Proxy::new(&format!("socks5h://127.0.0.1:{}", port)) {
        Ok(p) => p,
        Err(_) => return -1,
    };
    let agent: ureq::Agent = ureq::Agent::config_builder()
        .proxy(Some(proxy))
        .timeout_global(Some(Duration::from_secs(timeout)))
        .http_status_as_error(false)
        .max_redirects(0)
        .build()
        .into();
    let t0 = Instant::now();
    match agent.get(url).header("User-Agent", "FasdeqVPN/1.4").call() {
        Ok(_) => (t0.elapsed().as_millis() as i32).max(1),
        Err(_) => -1,
    }
}

pub fn check_server(bin: &Path, conf: &str, timeout: u64) -> Result<[i32; 3], String> {
    let mut parsed = links::parse(conf)?;
    links::resolve(&mut parsed.outbound);
    let port = free_port();
    let cfg = links::probe_config(parsed.outbound, port);
    let mut proc = start(bin, &cfg, port)?;
    let handles: Vec<_> = SERVICE_URLS
        .iter()
        .map(|url| {
            let url = url.to_string();
            std::thread::spawn(move || probe(port, &url, timeout))
        })
        .collect();
    let mut result = [-1i32; 3];
    for (i, h) in handles.into_iter().enumerate() {
        result[i] = h.join().unwrap_or(-1);
    }
    proc.stop();
    Ok(result)
}

fn run(program: &str, args: &[&str]) -> bool {
    let mut cmd = Command::new(program);
    cmd.args(args)
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .stdin(Stdio::null());
    hide_window(&mut cmd);
    cmd.status().map(|s| s.success()).unwrap_or(false)
}

fn output(program: &str, args: &[&str]) -> String {
    let mut cmd = Command::new(program);
    cmd.args(args)
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .stdin(Stdio::null());
    hide_window(&mut cmd);
    cmd.output()
        .map(|o| String::from_utf8_lossy(&o.stdout).to_string())
        .unwrap_or_default()
}

#[cfg(target_os = "windows")]
#[link(name = "wininet")]
extern "system" {
    fn InternetSetOptionW(
        handle: *mut std::ffi::c_void,
        option: u32,
        buffer: *mut std::ffi::c_void,
        length: u32,
    ) -> i32;
}

pub fn set_system_proxy(http_port: u16, socks_port: u16) {
    let server = format!("127.0.0.1:{}", http_port);
    #[cfg(target_os = "windows")]
    {
        let key = r"HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings";
        run("reg", &["add", key, "/v", "ProxyServer", "/t", "REG_SZ", "/d", &server, "/f"]);
        run(
            "reg",
            &["add", key, "/v", "ProxyOverride", "/t", "REG_SZ", "/d", "localhost;127.*;10.*;192.168.*;<local>", "/f"],
        );
        run("reg", &["add", key, "/v", "ProxyEnable", "/t", "REG_DWORD", "/d", "1", "/f"]);
        unsafe {
            InternetSetOptionW(std::ptr::null_mut(), 39, std::ptr::null_mut(), 0);
            InternetSetOptionW(std::ptr::null_mut(), 37, std::ptr::null_mut(), 0);
        }
        let _ = socks_port;
    }
    #[cfg(target_os = "macos")]
    {
        let services = output("networksetup", &["-listallnetworkservices"]);
        for svc in services.lines().skip(1).filter(|l| !l.starts_with('*') && !l.is_empty()) {
            run("networksetup", &["-setwebproxy", svc, "127.0.0.1", &http_port.to_string()]);
            run("networksetup", &["-setsecurewebproxy", svc, "127.0.0.1", &http_port.to_string()]);
            run("networksetup", &["-setsocksfirewallproxy", svc, "127.0.0.1", &socks_port.to_string()]);
        }
        let _ = server;
    }
    #[cfg(target_os = "linux")]
    {
        let http = http_port.to_string();
        let socks = socks_port.to_string();
        for scheme in ["http", "https"] {
            let schema = format!("org.gnome.system.proxy.{}", scheme);
            run("gsettings", &["set", &schema, "host", "127.0.0.1"]);
            run("gsettings", &["set", &schema, "port", &http]);
        }
        run("gsettings", &["set", "org.gnome.system.proxy.socks", "host", "127.0.0.1"]);
        run("gsettings", &["set", "org.gnome.system.proxy.socks", "port", &socks]);
        run("gsettings", &["set", "org.gnome.system.proxy", "mode", "manual"]);
        for tool in ["kwriteconfig6", "kwriteconfig5"] {
            if which(tool).is_some() {
                let value = format!("http://127.0.0.1 {}", http);
                run(tool, &["--file", "kioslaverc", "--group", "Proxy Settings", "--key", "ProxyType", "1"]);
                run(tool, &["--file", "kioslaverc", "--group", "Proxy Settings", "--key", "httpProxy", &value]);
                run(tool, &["--file", "kioslaverc", "--group", "Proxy Settings", "--key", "httpsProxy", &value]);
                break;
            }
        }
        let _ = server;
    }
}

pub fn clear_system_proxy() {
    #[cfg(target_os = "windows")]
    {
        let key = r"HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings";
        run("reg", &["add", key, "/v", "ProxyEnable", "/t", "REG_DWORD", "/d", "0", "/f"]);
        unsafe {
            InternetSetOptionW(std::ptr::null_mut(), 39, std::ptr::null_mut(), 0);
            InternetSetOptionW(std::ptr::null_mut(), 37, std::ptr::null_mut(), 0);
        }
    }
    #[cfg(target_os = "macos")]
    {
        let services = output("networksetup", &["-listallnetworkservices"]);
        for svc in services.lines().skip(1).filter(|l| !l.starts_with('*') && !l.is_empty()) {
            run("networksetup", &["-setwebproxystate", svc, "off"]);
            run("networksetup", &["-setsecurewebproxystate", svc, "off"]);
            run("networksetup", &["-setsocksfirewallproxystate", svc, "off"]);
        }
    }
    #[cfg(target_os = "linux")]
    {
        run("gsettings", &["set", "org.gnome.system.proxy", "mode", "none"]);
        for tool in ["kwriteconfig6", "kwriteconfig5"] {
            if which(tool).is_some() {
                run(tool, &["--file", "kioslaverc", "--group", "Proxy Settings", "--key", "ProxyType", "0"]);
                break;
            }
        }
    }
    let _ = output;
}
