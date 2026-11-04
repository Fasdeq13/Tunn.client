#![cfg_attr(all(not(debug_assertions), target_os = "windows"), windows_subsystem = "windows")]

mod conflict;
#[cfg(unix)]
mod elevate;
mod host;
mod server;
mod singbox;
mod store;
mod palette;
mod tray;
mod ui;
#[cfg(target_os = "linux")]
mod wg_linux;
mod xray;

pub use fasdeq_core::{importer, links, model, ping};

use host::Host;
use std::io::{Read, Write};
use std::time::Duration;

#[cfg(target_os = "linux")]
fn install_desktop_entry() {
    let Some(home) = std::env::var_os("HOME").map(std::path::PathBuf::from) else { return };
    let exec = std::env::var("APPIMAGE").ok().or_else(|| std::env::current_exe().ok().map(|p| p.display().to_string()));
    let Some(exec) = exec else { return };
    let icons = home.join(".local/share/icons/hicolor/256x256/apps");
    let apps = home.join(".local/share/applications");
    let _ = std::fs::create_dir_all(&icons);
    let _ = std::fs::create_dir_all(&apps);
    let icon = icons.join("Tunn.png");
    let png = tray::png(256);
    if std::fs::read(&icon).map(|c| c != png).unwrap_or(true) {
        let _ = std::fs::write(&icon, &png);
    }
    let entry = format!(
        "[Desktop Entry]\nType=Application\nName=Tunn\nExec=\"{}\"\nIcon={}\nCategories=Network;\nTerminal=false\nStartupWMClass=tunn\n",
        exec,
        icon.display()
    );
    let path = apps.join("Tunn.desktop");
    if std::fs::read_to_string(&path).map(|c| c != entry).unwrap_or(true) {
        let _ = std::fs::write(&path, entry);
        let _ = std::process::Command::new("update-desktop-database").arg(&apps).status();
    }
}

fn instance_file() -> std::path::PathBuf {
    store::data_dir().join("instance.json")
}

fn ping_existing() -> bool {
    for _ in 0..50 {
        if let Ok(raw) = std::fs::read_to_string(instance_file()) {
            if let Ok(v) = serde_json::from_str::<serde_json::Value>(&raw) {
                let port = v["port"].as_u64().unwrap_or(0) as u16;
                let token = v["token"].as_str().unwrap_or("").to_string();
                if port != 0 {
                    if let Ok(mut s) = std::net::TcpStream::connect_timeout(&std::net::SocketAddr::from(([127, 0, 0, 1], port)), Duration::from_millis(800)) {
                        let _ = s.set_read_timeout(Some(Duration::from_secs(2)));
                        let req = format!("GET /__show?k={} HTTP/1.1\r\nHost: 127.0.0.1:{}\r\nConnection: close\r\n\r\n", token, port);
                        let _ = s.write_all(req.as_bytes());
                        let mut buf = Vec::new();
                        let _ = s.read_to_end(&mut buf);
                        return true;
                    }
                }
            }
        }
        std::thread::sleep(Duration::from_millis(100));
    }
    false
}

fn lock_instance() -> Option<std::fs::File> {
    let file = std::fs::OpenOptions::new().create(true).write(true).truncate(false).open(store::data_dir().join("instance.lock")).ok()?;
    match file.try_lock() {
        Ok(()) => Some(file),
        Err(_) => None,
    }
}

fn main() {
    if let Some(i) = std::env::args().position(|a| a == "--icon-png") {
        if let Some(path) = std::env::args().nth(i + 1) {
            let _ = std::fs::write(path, tray::png(256));
            return;
        }
    }
    if let Some(i) = std::env::args().position(|a| a == "--screenshot") {
        let a: Vec<String> = std::env::args().collect();
        let get = |n: usize, d: &str| a.get(i + n).cloned().unwrap_or_else(|| d.to_string());
        let host = Host::new();
        for link in std::env::var("TUNN_DEMO").unwrap_or_default().split_whitespace() {
            host.act("add", &serde_json::json!(link));
        }
        let r = ui::screenshot(host, &get(1, "shot.png"), &get(2, "home"), &get(3, ""), get(4, "460").parse().unwrap_or(460), get(5, "800").parse().unwrap_or(800));
        if let Err(e) = r {
            eprintln!("screenshot: {}", e);
        }
        return;
    }
    let headless = std::env::args().any(|a| a == "--headless");
    let _lock = match lock_instance() {
        Some(f) => f,
        None => {
            ping_existing();
            return;
        }
    };
    #[cfg(target_os = "linux")]
    {
        install_desktop_entry();
    }

    let host = Host::new();
    let port = match server::start(host.clone()) {
        Ok(p) => p,
        Err(e) => {
            eprintln!("server: {}", e);
            return;
        }
    };
    let _ = std::fs::write(instance_file(), serde_json::json!({"port": port, "token": host.token, "pid": std::process::id()}).to_string());
    host.start_background();

    if headless {
        println!("http://127.0.0.1:{}/api/state (cookie fq={})", port, host.token);
        loop {
            std::thread::sleep(Duration::from_secs(3600));
        }
    }

    tray::set_sender({
        let host = host.clone();
        move |c| host.ui(c)
    });
    let tray_ready = tray::start();
    if let Err(e) = ui::run(host.clone(), tray_ready) {
        eprintln!("ui: {}", e);
    }
    host.shutdown();
    let _ = std::fs::remove_file(instance_file());
}
