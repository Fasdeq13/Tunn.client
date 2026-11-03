
use crate::store::data_dir;
use crate::xray::{which, Proc};
use sha2::{Digest, Sha256};
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::time::{Duration, Instant};

pub const IFACE: &str = "tunnwg";
const HELPER: &[u8] = include_bytes!(concat!(env!("OUT_DIR"), "/tunn-helper"));
const GO_URL: &str = "https://github.com/Fasdeq13/FasdeqVPN/releases/download/tools/";

pub fn is_conf(conf: &str) -> bool {
    let l = conf.to_ascii_lowercase();
    l.contains("[interface]") && l.contains("[peer]")
}

fn bin_dir() -> PathBuf {
    let d = data_dir().join("bin");
    let _ = std::fs::create_dir_all(&d);
    d
}

fn arch() -> Result<&'static str, String> {
    match std::env::consts::ARCH {
        "x86_64" => Ok("amd64"),
        "aarch64" => Ok("arm64"),
        a => Err(format!("Для архитектуры {} нет готовой сборки amneziawg-go", a)),
    }
}

fn make_exec(p: &Path) {
    let _ = std::fs::set_permissions(p, std::fs::Permissions::from_mode(0o755));
}

fn works(p: &Path) -> bool {
    p.is_file()
}

pub fn ensure_go(progress: &dyn Fn(&str)) -> Result<PathBuf, String> {
    let own = bin_dir().join("amneziawg-go");
    if works(&own) {
        return Ok(own);
    }
    if let Some(p) = which("amneziawg-go") {
        return Ok(p);
    }
    progress("Скачиваю amneziawg-go…");
    let name = format!("amneziawg-go-linux-{}", arch()?);
    let download: Result<(), String> = (|| {
        let data = crate::singbox::http(&format!("{}{}", GO_URL, name), 64 * 1024 * 1024)?;
        if let Ok(sum) = crate::singbox::http(&format!("{}{}.sha256", GO_URL, name), 4096) {
            let want = String::from_utf8_lossy(&sum).split_whitespace().next().unwrap_or("").to_lowercase();
            let got: String = Sha256::digest(&data).iter().map(|b| format!("{:02x}", b)).collect();
            if want.len() == 64 && want != got {
                return Err("Контрольная сумма amneziawg-go не совпала".into());
            }
        }
        let tmp = own.with_extension("part");
        std::fs::write(&tmp, &data).map_err(|e| e.to_string())?;
        make_exec(&tmp);
        std::fs::rename(&tmp, &own).map_err(|e| e.to_string())
    })();
    if download.is_ok() && works(&own) {
        return Ok(own);
    }
    if let Some(go) = which("go") {
        progress("Собираю amneziawg-go через go install…");
        let ok = Command::new(go)
            .args(["install", "github.com/amnezia-vpn/amneziawg-go@latest"])
            .env("GOBIN", bin_dir())
            .env("CGO_ENABLED", "0")
            .stdin(Stdio::null())
            .status()
            .map(|s| s.success())
            .unwrap_or(false);
        if ok && works(&own) {
            return Ok(own);
        }
    }
    Err(format!(
        "Не удалось получить amneziawg-go ({}). Положите бинарник в {} или установите пакет amneziawg-go",
        download.err().unwrap_or_default(),
        own.display()
    ))
}

fn ensure_helper() -> Result<PathBuf, String> {
    let p = bin_dir().join("tunn-helper");
    let same = std::fs::read(&p).map(|c| c == HELPER).unwrap_or(false);
    if !same {
        if HELPER.is_empty() {
            return Err("Помощник tunn-helper не был собран (нужен gcc при сборке)".into());
        }
        let tmp = p.with_extension("part");
        std::fs::write(&tmp, HELPER).map_err(|e| e.to_string())?;
        make_exec(&tmp);
        std::fs::rename(&tmp, &p).map_err(|e| e.to_string())?;
    }
    Ok(p)
}

fn find_tool(name: &str, extra: &[&str]) -> Option<PathBuf> {
    which(name).or_else(|| extra.iter().map(PathBuf::from).find(|p| p.is_file()))
}

pub fn start(conf: &str, progress: &dyn Fn(&str)) -> Result<Proc, String> {
    let Some(pkexec) = which("pkexec") else {
        return Err("Для WireGuard нужен pkexec (polkit)".into());
    };
    if !Path::new("/dev/net/tun").exists() {
        return Err("Нет /dev/net/tun. Загрузите модуль: sudo modprobe tun".into());
    }
    let ip = find_tool("ip", &["/run/current-system/sw/bin/ip", "/usr/sbin/ip", "/sbin/ip", "/usr/bin/ip"]).ok_or("Не найдена утилита ip (iproute2)")?;
    let resolvectl = find_tool("resolvectl", &["/run/current-system/sw/bin/resolvectl", "/usr/bin/resolvectl"]);
    let go = ensure_go(progress)?;
    let helper = ensure_helper()?;

    let cfg = data_dir().join("wg-run.conf");
    {
        use std::io::Write;
        use std::os::unix::fs::OpenOptionsExt;
        let _ = std::fs::remove_file(&cfg);
        let mut f = std::fs::OpenOptions::new().write(true).create_new(true).mode(0o600).open(&cfg).map_err(|e| e.to_string())?;
        f.write_all(conf.as_bytes()).map_err(|e| e.to_string())?;
    }
    let stop = data_dir().join("wg.stop");
    let _ = std::fs::remove_file(&stop);
    let log = data_dir().join("core.log");
    let _ = std::fs::write(&log, b"");
    let lf = std::fs::OpenOptions::new().append(true).open(&log).ok();
    let mut cmd = Command::new(pkexec);
    cmd.arg(&helper)
        .arg("run")
        .arg(&go)
        .arg(&cfg)
        .arg(&stop)
        .arg(std::process::id().to_string())
        .arg(IFACE)
        .arg(&ip)
        .arg(resolvectl.as_deref().map(|p| p.as_os_str()).unwrap_or_else(|| std::ffi::OsStr::new("-")))
        .stdin(Stdio::null())
        .stdout(match lf.as_ref().and_then(|f| f.try_clone().ok()) {
            Some(f) => Stdio::from(f),
            None => Stdio::null(),
        })
        .stderr(match lf {
            Some(f) => Stdio::from(f),
            None => Stdio::null(),
        });
    progress("Запрашиваю доступ для WireGuard…");
    let child = cmd.spawn().map_err(|e| format!("Не удалось запустить pkexec: {}", e))?;
    let mut proc = Proc::new(child, cfg, Some(stop));
    let end = Instant::now() + Duration::from_secs(120);
    loop {
        let text = std::fs::read_to_string(&log).unwrap_or_default();
        if text.lines().any(|l| l.trim() == "READY") {
            return Ok(proc);
        }
        if let Ok(Some(status)) = proc.try_wait() {
            proc.forget_stop();
            let last = text.lines().rev().find(|l| !l.trim().is_empty()).unwrap_or("").trim().to_string();
            return Err(match status.code() {
                Some(126) => "Доступ отклонён или пароль не введён".to_string(),
                Some(127) => "Не найден polkit-агент для запроса пароля".to_string(),
                _ => if last.is_empty() { "WireGuard не запустился".to_string() } else { last },
            });
        }
        if Instant::now() > end {
            proc.stop();
            return Err("WireGuard не запустился вовремя".into());
        }
        std::thread::sleep(Duration::from_millis(150));
    }
}

pub fn counters() -> Option<(u64, u64)> {
    let base = format!("/sys/class/net/{}/statistics/", IFACE);
    let rx = std::fs::read_to_string(format!("{}rx_bytes", base)).ok()?.trim().parse().ok()?;
    let tx = std::fs::read_to_string(format!("{}tx_bytes", base)).ok()?.trim().parse().ok()?;
    Some((rx, tx))
}
