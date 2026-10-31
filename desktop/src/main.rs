#![cfg_attr(all(not(debug_assertions), target_os = "windows"), windows_subsystem = "windows")]

mod conflict;
mod host;
mod server;
mod singbox;
mod store;
mod tray;
mod xray;

pub use fasdeq_core::{importer, links, model, ping};

use host::{Host, UiCmd};
use std::io::{Read, Write};
use std::time::Duration;
use tao::event::{Event, StartCause, WindowEvent};
use tao::event_loop::{ControlFlow, EventLoopBuilder};
use tao::window::{Icon, WindowBuilder};

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

fn open_in_browser(url: &str) {
    #[cfg(target_os = "windows")]
    {
        let mut done = false;
        for exe in ["msedge", "chrome"] {
            let mut c = std::process::Command::new("cmd");
            c.args(["/C", "start", "", exe, &format!("--app={}", url)]);
            fasdeq_core::proc::hide_window(&mut c);
            if c.status().map(|s| s.success()).unwrap_or(false) {
                done = true;
                break;
            }
        }
        if done {
            return;
        }
    }
    host::open_url(url);
}

fn window_icon() -> Option<Icon> {
    let (rgba, w, h) = tray::raster(128);
    Icon::from_rgba(rgba, w, h).ok()
}

fn main() {
    if let Some(i) = std::env::args().position(|a| a == "--icon-png") {
        if let Some(path) = std::env::args().nth(i + 1) {
            let _ = std::fs::write(path, tray::png(256));
            return;
        }
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
        if std::env::var_os("WEBKIT_DISABLE_DMABUF_RENDERER").is_none() {
            std::env::set_var("WEBKIT_DISABLE_DMABUF_RENDERER", "1");
        }
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
    let url = format!("http://127.0.0.1:{}/?k={}", port, host.token);

    if headless {
        println!("{}", url);
        loop {
            std::thread::sleep(Duration::from_secs(3600));
        }
    }

    let event_loop = EventLoopBuilder::<UiCmd>::with_user_event().build();
    let proxy = event_loop.create_proxy();
    host.set_ui(Box::new(move |c| {
        let _ = proxy.send_event(c);
    }));
    tray::set_sender({
        let proxy = event_loop.create_proxy();
        move |c| {
            let _ = proxy.send_event(c);
        }
    });

    let window = WindowBuilder::new()
        .with_title("Tunn")
        .with_inner_size(tao::dpi::LogicalSize::new(1100.0, 760.0))
        .with_min_inner_size(tao::dpi::LogicalSize::new(360.0, 560.0))
        .with_window_icon(window_icon())
        .build(&event_loop);
    let window = match window {
        Ok(w) => Some(w),
        Err(_) => None,
    };

    let ctx_dir = store::data_dir().join("webview");
    let mut ctx = wry::WebContext::new(Some(ctx_dir));
    let mut webview = None;
    if let Some(w) = &window {
        let builder = wry::WebViewBuilder::new_with_web_context(&mut ctx).with_url(&url);
        #[cfg(target_os = "linux")]
        let built = {
            use tao::platform::unix::WindowExtUnix;
            use wry::WebViewBuilderExtUnix;
            builder.build_gtk(w.default_vbox().unwrap())
        };
        #[cfg(not(target_os = "linux"))]
        let built = builder.build(w);
        match built {
            Ok(v) => webview = Some(v),
            Err(e) => eprintln!("webview: {}", e),
        }
    }
    if webview.is_none() {
        open_in_browser(&url);
        if let Some(w) = &window {
            w.set_visible(false);
        }
    }

    let host2 = host.clone();
    let mut tray_icon = None;
    event_loop.run(move |event, _, flow| {
        *flow = ControlFlow::Wait;
        let _keep = (&webview, &ctx);
        match event {
            Event::NewEvents(StartCause::Init) => {
                tray_icon = tray::build().ok();
            }
            Event::WindowEvent { event: WindowEvent::CloseRequested, .. } => {
                if tray_icon.is_some() && host2.close_to_tray() {
                    if let Some(w) = &window {
                        w.set_visible(false);
                    }
                } else {
                    host2.shutdown();
                    let _ = std::fs::remove_file(instance_file());
                    *flow = ControlFlow::Exit;
                }
            }
            Event::UserEvent(cmd) => match cmd {
                UiCmd::Show => match &window {
                    Some(w) if webview.is_some() => {
                        w.set_visible(true);
                        w.set_minimized(false);
                        w.set_focus();
                    }
                    _ => open_in_browser(&url),
                },
                UiCmd::Toggle => {
                    if let Some(w) = &window {
                        if w.is_visible() {
                            w.set_visible(false);
                        } else {
                            w.set_visible(true);
                            w.set_focus();
                        }
                    }
                }
                UiCmd::Quit => {
                    host2.shutdown();
                    let _ = std::fs::remove_file(instance_file());
                    *flow = ControlFlow::Exit;
                }
            },
            Event::LoopDestroyed => {
                host2.shutdown();
                drop(tray_icon.take());
            }
            _ => {}
        }
    });
}
