use crate::host::UiCmd;
use std::sync::OnceLock;

static SEND: OnceLock<Box<dyn Fn(UiCmd) + Send + Sync>> = OnceLock::new();

pub fn set_sender(f: impl Fn(UiCmd) + Send + Sync + 'static) {
    let _ = SEND.set(Box::new(f));
}

fn send(c: UiCmd) {
    if let Some(f) = SEND.get() {
        f(c);
    }
}

fn render(size: u32) -> resvg::tiny_skia::Pixmap {
    let data = include_bytes!("../assets/app.svg");
    let tree = resvg::usvg::Tree::from_data(data, &resvg::usvg::Options::default()).expect("app icon");
    let mut pixmap = resvg::tiny_skia::Pixmap::new(size, size).expect("pixmap");
    let scale = size as f32 / tree.size().width();
    resvg::render(&tree, resvg::tiny_skia::Transform::from_scale(scale, scale), &mut pixmap.as_mut());
    pixmap
}

pub fn raster(size: u32) -> (Vec<u8>, u32, u32) {
    let pixmap = render(size);
    let mut out = Vec::with_capacity((size * size * 4) as usize);
    for p in pixmap.pixels() {
        let c = p.demultiply();
        out.extend_from_slice(&[c.red(), c.green(), c.blue(), c.alpha()]);
    }
    (out, size, size)
}

pub fn png(size: u32) -> Vec<u8> {
    render(size).encode_png().unwrap_or_default()
}

pub fn build() -> Result<tray_icon::TrayIcon, String> {
    use tray_icon::menu::{Menu, MenuEvent, MenuItem, PredefinedMenuItem};
    use tray_icon::{Icon, MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
    let (rgba, w, h) = raster(64);
    let icon = Icon::from_rgba(rgba, w, h).map_err(|e| e.to_string())?;
    let menu = Menu::new();
    let show = MenuItem::with_id("show", "Открыть Tunn", true, None);
    let quit = MenuItem::with_id("quit", "Выход", true, None);
    menu.append(&show).map_err(|e| e.to_string())?;
    menu.append(&PredefinedMenuItem::separator()).map_err(|e| e.to_string())?;
    menu.append(&quit).map_err(|e| e.to_string())?;
    MenuEvent::set_event_handler(Some(|ev: MenuEvent| match ev.id.0.as_str() {
        "show" => send(UiCmd::Show),
        "quit" => send(UiCmd::Quit),
        _ => {}
    }));
    TrayIconEvent::set_event_handler(Some(|ev: TrayIconEvent| {
        if let TrayIconEvent::Click { button: MouseButton::Left, button_state: MouseButtonState::Up, .. } = ev {
            send(UiCmd::Show);
        }
    }));
    TrayIconBuilder::new().with_menu(Box::new(menu)).with_tooltip("Tunn").with_icon(icon).build().map_err(|e| e.to_string())
}
