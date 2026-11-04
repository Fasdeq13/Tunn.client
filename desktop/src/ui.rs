
use crate::host::{Host, UiCmd};
use crate::palette::{self, Rgb};
use serde_json::{json, Value};
use slint::{Color, ComponentHandle, Model, ModelRc, SharedString, Timer, TimerMode, VecModel};
use std::cell::RefCell;
use std::rc::Rc;
use std::sync::Arc;
use std::time::{Duration, Instant};

slint::include_modules!();

const SWATCHES: [&str; 12] = [
    "#4FD8C8", "#7CB7FF", "#B69CFF", "#FF8FB1", "#FF8A65", "#FFC857", "#9BD36A", "#4CAF7A", "#00B8D4", "#E91E63", "#8D6E63", "#9E9E9E",
];

fn col(c: Rgb) -> Color {
    Color::from_rgb_u8(c.0, c.1, c.2)
}

fn cookie(size: f32, amp: f32, lobes: f32, phase: f32, scale: f32) -> String {
    let c = size / 2.0;
    let r = size / 2.0 * 0.94 * scale;
    let steps = 120;
    let mut out = String::with_capacity(steps * 14);
    for i in 0..=steps {
        let t = i as f32 / steps as f32 * std::f32::consts::TAU;
        let rr = r * (1.0 + amp * (lobes * (t - phase)).cos());
        let (x, y) = (c + rr * t.cos(), c + rr * t.sin());
        out.push_str(if i == 0 { "M " } else { "L " });
        out.push_str(&format!("{:.1} {:.1} ", x, y));
    }
    out.push('Z');
    out
}

fn ms_color(sch: &palette::Scheme, ms: Option<i64>, busy: bool) -> Color {
    if busy {
        return Color::from_rgb_u8(0xE0, 0xA5, 0x2E);
    }
    match ms {
        None | Some(-3) | Some(-4) => Color::from_rgb_u8(0x8A, 0x93, 0x90),
        Some(-2) => Color::from_rgb_u8(0xE0, 0xA5, 0x2E),
        Some(v) if v > 0 => Color::from_rgb_u8(0x4C, 0xAF, 0x7A),
        _ => col(sch.error),
    }
}

fn fmt_bytes(n: u64) -> String {
    let f = n as f64;
    if f >= 1073741824.0 {
        format!("{:.2} ГБ", f / 1073741824.0)
    } else if f >= 1048576.0 {
        format!("{:.1} МБ", f / 1048576.0)
    } else {
        format!("{:.0} КБ", f / 1024.0)
    }
}

fn fmt_speed(n: u64) -> String {
    format!("{}/с", fmt_bytes(n))
}

fn fmt_time(sec: u64) -> String {
    format!("{:02}:{:02}:{:02}", sec / 3600, sec / 60 % 60, sec % 60)
}

struct State {
    snap: Value,
    phase: f32,
    last: Instant,
    speed: f32,
    amp: f32,
    toast_until: Option<Instant>,
    sch_key: String,
}

fn scheme_for(snap: &Value) -> (palette::Scheme, String) {
    let st = &snap["settings"];
    let theme = st["theme"].as_i64().unwrap_or(0);
    let sys = palette::system();
    let dynamic = st["dynamic"].as_bool().unwrap_or(false);
    let dark = match theme {
        1 => false,
        2 => true,
        _ => sys.dark.unwrap_or(true),
    };
    let own = st["accentHex"].as_str().and_then(palette::parse_hex).unwrap_or(Rgb(0x4F, 0xD8, 0xC8));
    let accent = if dynamic { sys.accent.unwrap_or(own) } else { own };
    let bg = if st["bgCustom"].as_bool().unwrap_or(false) { st["bgHex"].as_str().and_then(palette::parse_hex) } else { None };
    let amoled = st["amoled"].as_bool().unwrap_or(false);
    let key = format!("{}{}{}{:?}{}{}", theme, dynamic, dark, (accent.0, accent.1, accent.2), bg.map(|b| (b.0, b.1, b.2)).is_some(), amoled);
    (palette::scheme(accent, dark, bg, amoled), key)
}

fn apply_palette(app: &App, s: &palette::Scheme) {
    let p = app.global::<Pal>();
    p.set_bg(col(s.bg));
    p.set_container(col(s.container));
    p.set_container_high(col(s.container_high));
    p.set_container_highest(col(s.container_highest));
    p.set_primary(col(s.primary));
    p.set_on_primary(col(s.on_primary));
    p.set_primary_container(col(s.primary_container));
    p.set_on_primary_container(col(s.on_primary_container));
    p.set_secondary_container(col(s.secondary_container));
    p.set_on_secondary_container(col(s.on_secondary_container));
    p.set_on_surface(col(s.on_surface));
    p.set_on_variant(col(s.on_variant));
    p.set_outline(col(s.outline));
    p.set_error(col(s.error));
}

fn build_rows(snap: &Value, sch: &palette::Scheme) -> Vec<Row> {
    let servers = snap["servers"].as_array().cloned().unwrap_or_default();
    let selected = snap["selected"].as_str().unwrap_or("").to_string();
    let collapsed: Vec<String> = snap["collapsed"].as_array().map(|a| a.iter().filter_map(|v| v.as_str().map(String::from)).collect()).unwrap_or_default();
    let local = snap["settings"]["localName"].as_str().filter(|s| !s.is_empty()).unwrap_or("Мои серверы").to_string();
    let mut order: Vec<String> = vec![String::new()];
    for g in snap["groups"].as_array().cloned().unwrap_or_default() {
        if let Some(n) = g["name"].as_str() {
            order.push(n.to_string());
        }
    }
    let mut rows = Vec::new();
    for g in order {
        let members: Vec<&Value> = servers.iter().filter(|s| s["group"].as_str().unwrap_or("") == g).collect();
        if members.is_empty() {
            continue;
        }
        let folded = collapsed.contains(&g);
        rows.push(Row {
            kind: 0,
            id: SharedString::from(g.as_str()),
            title: SharedString::from(format!("{} · {}", if g.is_empty() { local.as_str() } else { g.as_str() }, members.len())),
            sub: SharedString::default(),
            ping: SharedString::default(),
            ping_color: Color::default(),
            c0: Color::default(),
            c1: Color::default(),
            c2: Color::default(),
            has_checks: false,
            picked: false,
            folded,
            blocked: false,
            letter: SharedString::default(),
        });
        if folded {
            continue;
        }
        for s in members {
            let id = s["id"].as_str().unwrap_or("");
            let p = &snap["pings"][id];
            let busy = p["busy"].as_bool().unwrap_or(false);
            let ms = p["ms"].as_i64();
            let (ping, pc) = if busy {
                ("…".to_string(), col(sch.on_variant))
            } else if let Some(v) = ms {
                (format!("{} мс", v), Color::from_rgb_u8(0x4C, 0xAF, 0x7A))
            } else if snap["pings"][id].is_null() {
                (String::new(), col(sch.on_variant))
            } else {
                ("—".to_string(), col(sch.on_variant))
            };
            let chk = &snap["services"]["checks"][id];
            let cbusy = chk["busy"].as_bool().unwrap_or(false);
            let has = chk.is_object();
            let at = |i: usize| -> Color { ms_color(sch, chk["ms"][i].as_i64(), cbusy) };
            let blocked = s["blocked"].is_string();
            let name = s["name"].as_str().unwrap_or("");
            rows.push(Row {
                kind: 1,
                id: SharedString::from(id),
                title: SharedString::from(name),
                sub: SharedString::from(if blocked { s["blocked"].as_str().unwrap_or("").to_string() } else { format!("{} · {}", s["proto"].as_str().unwrap_or(""), s["endpoint"].as_str().unwrap_or("")) }),
                ping: SharedString::from(ping),
                ping_color: pc,
                c0: at(0),
                c1: at(1),
                c2: at(2),
                has_checks: has,
                picked: id == selected,
                folded: false,
                blocked,
                letter: SharedString::from(name.chars().find(|c| c.is_alphanumeric()).map(|c| c.to_uppercase().to_string()).unwrap_or_else(|| "•".into())),
            });
        }
    }
    rows
}

fn toast(weak: &slint::Weak<App>, text: &str) {
    let text = text.to_string();
    let w = weak.clone();
    let _ = slint::invoke_from_event_loop(move || {
        if let Some(a) = w.upgrade() {
            a.global::<Ui>().set_toast(text.into());
        }
    });
}

fn load_device(host: &Arc<Host>, weak: &slint::Weak<App>) {
    let h = host.clone();
    let weak = weak.clone();
    std::thread::spawn(move || {
        let r = h.act("hwidInfo", &Value::Null);
        let get = |k: &str| r[k].as_str().unwrap_or("").to_string();
        let (id, os, ver, model) = (get("hwid"), get("os"), get("ver"), get("model"));
        let _ = slint::invoke_from_event_loop(move || {
            if let Some(a) = weak.upgrade() {
                let ui = a.global::<Ui>();
                ui.set_hwid(id.into());
                ui.set_hw_os(os.into());
                ui.set_hw_ver(ver.into());
                ui.set_hw_model(model.into());
            }
        });
    });
}

fn load_comps(host: &Arc<Host>, weak: &slint::Weak<App>) {
    if let Some(a) = weak.upgrade() {
        a.global::<Ui>().set_setup_loading(true);
    }
    let h = host.clone();
    let weak = weak.clone();
    std::thread::spawn(move || {
        let r = h.act("componentsInfo", &Value::Null);
        let items = r["items"].as_array().cloned().unwrap_or_default();
        let _ = slint::invoke_from_event_loop(move || {
            if let Some(a) = weak.upgrade() {
                let ui = a.global::<Ui>();
                let rows: Vec<Comp> = items
                    .iter()
                    .map(|c| {
                        let versions: Vec<SharedString> = c["versions"].as_array().map(|v| v.iter().filter_map(|x| x.as_str().map(SharedString::from)).collect()).unwrap_or_default();
                        let installed = c["installed"].as_str().unwrap_or("").to_string();
                        let sel = if installed.is_empty() && !versions.is_empty() { 0 } else { -1 };
                        Comp {
                            key: c["key"].as_str().unwrap_or("").into(),
                            name: c["name"].as_str().unwrap_or("").into(),
                            installed: installed.into(),
                            versions: ModelRc::new(VecModel::from(versions)),
                            sel,
                            note: c["note"].as_str().unwrap_or("").into(),
                        }
                    })
                    .collect();
                ui.set_comps(ModelRc::new(VecModel::from(rows)));
                ui.set_setup_loading(false);
            }
        });
    });
}

fn load_egg() -> Option<slint::Image> {
    const EMBED: &[u8] = include_bytes!(concat!(env!("OUT_DIR"), "/icon.bin"));
    let mut data: Vec<u8> = EMBED.to_vec();
    if data.is_empty() {
        if let Some(dir) = std::env::current_exe().ok().and_then(|e| e.parent().map(|p| p.to_path_buf())) {
            for name in ["icon.png", "icon.jpg", "icon.jpeg"] {
                if let Ok(b) = std::fs::read(dir.join(name)) {
                    data = b;
                    break;
                }
            }
        }
    }
    if data.is_empty() {
        return None;
    }
    let img = image::load_from_memory(&data).ok()?.to_rgba8();
    let (w, h) = img.dimensions();
    let buf = slint::SharedPixelBuffer::<slint::Rgba8Pixel>::clone_from_slice(img.as_raw(), w, h);
    Some(slint::Image::from_rgba8(buf))
}

fn refresh(app: &App, st: &Rc<RefCell<State>>, host: &Arc<Host>) {
    let snap = host.snapshot();
    let (sch, key) = scheme_for(&snap);
    {
        let mut s = st.borrow_mut();
        if s.sch_key != key {
            apply_palette(app, &sch);
            s.sch_key = key;
        }
    }
    let ui = app.global::<Ui>();
    let status = snap["status"].as_str().unwrap_or("Off");
    ui.set_status(status.into());
    ui.set_status_text(
        match status {
            "On" => "Подключено",
            "Connecting" => "Подключение…",
            "Stopping" => "Отключение…",
            _ => "Отключено",
        }
        .into(),
    );
    let selected = snap["selected"].as_str().unwrap_or("");
    let name = snap["servers"].as_array().and_then(|a| a.iter().find(|s| s["id"].as_str() == Some(selected))).and_then(|s| s["name"].as_str()).unwrap_or("");
    ui.set_server_name(name.into());
    let detail = if status == "On" {
        format!("{} · {} · ↓ {} ↑ {}", name, fmt_time(snap["uptime"].as_u64().unwrap_or(0)), fmt_speed(snap["down"].as_u64().unwrap_or(0)), fmt_speed(snap["up"].as_u64().unwrap_or(0)))
    } else if name.is_empty() {
        "Добавьте сервер".to_string()
    } else {
        name.to_string()
    };
    ui.set_detail_text(detail.into());
    ui.set_error_text(snap["error"].as_str().unwrap_or("").into());
    ui.set_setup_busy(snap["cores"]["busy"].as_str().unwrap_or("").into());
    ui.set_ping_busy(snap["pings"].as_object().map(|m| m.values().any(|p| p["busy"].as_bool().unwrap_or(false))).unwrap_or(false));
    ui.set_check_busy(snap["services"]["checking"].as_bool().unwrap_or(false));
    ui.set_rows(ModelRc::new(VecModel::from(build_rows(&snap, &sch))));
    let set = &snap["settings"];
    ui.set_theme(set["theme"].as_i64().unwrap_or(0) as i32);
    ui.set_dynamic(set["dynamic"].as_bool().unwrap_or(false));
    ui.set_amoled(set["amoled"].as_bool().unwrap_or(false));
    ui.set_auto_connect(set["autoConnect"].as_bool().unwrap_or(false));
    ui.set_dns(set["dns"].as_i64().unwrap_or(0) as i32);
    ui.set_ping_mode(set["pingMode"].as_i64().unwrap_or(0) as i32);
    ui.set_auto_ping(set["autoPing"].as_bool().unwrap_or(false));
    ui.set_sort_ping(set["sortPing"].as_bool().unwrap_or(false));
    ui.set_auto_check(set["autoCheck"].as_bool().unwrap_or(false));
    ui.set_close_to_tray(set["closeToTray"].as_bool().unwrap_or(false));
    ui.set_autostart(set["autostart"].as_bool().unwrap_or(false));
    ui.set_tun_ok(snap["tun"].as_bool().unwrap_or(false));
    ui.set_tun_mode(set["tunMode"].as_i64().unwrap_or(0) as i32);
    ui.set_xray_fallback(set["xrayFallback"].as_bool().unwrap_or(true));
    ui.set_accent(set["accentHex"].as_str().unwrap_or("#4FD8C8").into());
    st.borrow_mut().snap = snap;
}

fn frame(app: &App, st: &Rc<RefCell<State>>) {
    let mut s = st.borrow_mut();
    let now = Instant::now();
    let dt = now.duration_since(s.last).as_secs_f32().min(0.2);
    s.last = now;
    let status = s.snap["status"].as_str().unwrap_or("Off").to_string();
    let (speed_t, amp_t) = match status.as_str() {
        "Connecting" | "Stopping" => (3.2, 0.085),
        "On" => (0.45, 0.05),
        _ => (0.2, 0.062),
    };
    s.speed += (speed_t - s.speed) * (dt * 2.5).min(1.0);
    s.amp += (amp_t - s.amp) * (dt * 3.0).min(1.0);
    s.phase += dt * s.speed;
    let ph = s.phase;
    let amp = s.amp + if status == "On" { 0.006 * (ph * 2.0).sin() } else { 0.012 * (ph * 3.0).sin() };
    let ui = app.global::<Ui>();
    ui.set_btn_path(cookie(1000.0, amp, 9.0, ph, 1.0).into());
    ui.set_halo_path(if status == "On" { cookie(1000.0, 0.075, 9.0, -ph * 0.8, 1.17 * 196.0 / 240.0) } else { String::new() }.into());
    if let Some(t) = s.toast_until {
        if now >= t {
            s.toast_until = None;
            ui.set_toast("".into());
        }
    }
}

pub fn run(host: Arc<Host>, tray_ready: bool) -> Result<(), String> {
    let app = App::new().map_err(|e| e.to_string())?;
    let st = Rc::new(RefCell::new(State { snap: json!({}), phase: 0.0, last: Instant::now(), speed: 0.2, amp: 0.062, toast_until: None, sch_key: String::new() }));
    let ui = app.global::<Ui>();
    ui.set_version(crate::host::VERSION.into());
    ui.set_swatches(ModelRc::new(VecModel::from(SWATCHES.iter().map(|h| col(palette::parse_hex(h).unwrap())).collect::<Vec<_>>())));
    ui.set_swatch_hex(ModelRc::new(VecModel::from(SWATCHES.iter().map(|h| SharedString::from(*h)).collect::<Vec<_>>())));
    refresh(&app, &st, &host);

    let weak = app.as_weak();

    ui.on_toggle({
        let h = host.clone();
        let weak = weak.clone();
        let st = st.clone();
        move || {
            let status = st.borrow().snap["status"].as_str().unwrap_or("Off").to_string();
            let h = h.clone();
            let weak = weak.clone();
            std::thread::spawn(move || {
                if status == "On" || status == "Connecting" {
                    h.act("disconnect", &Value::Null);
                } else if status == "Off" {
                    let r = h.act("connect", &Value::Null);
                    if let Some(list) = r["conflict"].as_array() {
                        let names: Vec<String> = list.iter().filter_map(|v| v.as_str().map(String::from)).collect();
                        let msg = format!("Закрываю {} и подключаюсь…", names.join(", "));
                        let w = weak.clone();
                        let _ = slint::invoke_from_event_loop(move || {
                            if let Some(a) = w.upgrade() {
                                a.global::<Ui>().set_toast(msg.into());
                            }
                        });
                        h.act("killConflicts", &Value::Null);
                        h.act("connectForce", &Value::Null);
                    }
                }
            });
        }
    });
    ui.on_select({
        let h = host.clone();
        move |id| {
            let h = h.clone();
            let id = id.to_string();
            std::thread::spawn(move || {
                h.act("select", &json!(id));
                h.push_state();
            });
        }
    });
    ui.on_remove({
        let h = host.clone();
        move |id| {
            let h = h.clone();
            let id = id.to_string();
            std::thread::spawn(move || {
                h.act("remove", &json!(id));
            });
        }
    });
    ui.on_toggle_group({
        let h = host.clone();
        let st = st.clone();
        move |id| {
            let mut list: Vec<String> = st.borrow().snap["collapsed"].as_array().map(|a| a.iter().filter_map(|v| v.as_str().map(String::from)).collect()).unwrap_or_default();
            let id = id.to_string();
            if let Some(i) = list.iter().position(|x| *x == id) {
                list.remove(i);
            } else {
                list.push(id);
            }
            let h = h.clone();
            std::thread::spawn(move || {
                h.act("setCollapsed", &json!(list));
                h.push_state();
            });
        }
    });
    ui.on_group_menu({
        let h = host.clone();
        move |id| {
            let h = h.clone();
            let id = id.to_string();
            std::thread::spawn(move || {
                if id.is_empty() {
                    h.act("ping", &Value::Null);
                } else {
                    h.act("pingGroup", &json!(id));
                }
            });
        }
    });
    ui.on_ping({
        let h = host.clone();
        move || {
            let h = h.clone();
            std::thread::spawn(move || {
                h.act("ping", &Value::Null);
            });
        }
    });
    ui.on_services({
        let h = host.clone();
        move || {
            let h = h.clone();
            std::thread::spawn(move || {
                h.act("checkServices", &Value::Null);
            });
        }
    });
    ui.on_ping_one({
        let h = host.clone();
        move |id| {
            let h = h.clone();
            let id = id.to_string();
            std::thread::spawn(move || {
                h.act("pingOne", &json!(id));
            });
        }
    });
    ui.on_submit_add({
        let h = host.clone();
        let weak = weak.clone();
        move |text| {
            let h = h.clone();
            let weak = weak.clone();
            let text = text.to_string();
            std::thread::spawn(move || {
                let r = h.act("add", &json!(text));
                let msg = r["error"].as_str().map(String::from).unwrap_or_else(|| "Добавлено".into());
                let _ = slint::invoke_from_event_loop(move || {
                    if let Some(a) = weak.upgrade() {
                        a.global::<Ui>().set_toast(msg.into());
                    }
                });
            });
        }
    });
    ui.on_paste(|| arboard::Clipboard::new().and_then(|mut c| c.get_text()).unwrap_or_default().into());
    ui.on_rename({
        let h = host.clone();
        move |id, name| {
            let h = h.clone();
            let (id, name) = (id.to_string(), name.to_string());
            std::thread::spawn(move || {
                h.act("renameServer", &json!({"id": id, "name": name}));
            });
        }
    });
    ui.on_set_bool({
        let h = host.clone();
        move |path, v| {
            let h = h.clone();
            let path = path.to_string();
            std::thread::spawn(move || {
                h.act("set", &json!({"path": path, "value": v}));
            });
        }
    });
    ui.on_set_int({
        let h = host.clone();
        move |path, v| {
            let h = h.clone();
            let path = path.to_string();
            std::thread::spawn(move || {
                h.act("set", &json!({"path": path, "value": v}));
            });
        }
    });
    ui.on_set_str({
        let h = host.clone();
        move |path, v| {
            let h = h.clone();
            let (path, v) = (path.to_string(), v.to_string());
            std::thread::spawn(move || {
                h.act("set", &json!({"path": path, "value": v}));
            });
        }
    });
    ui.on_hwid_reset({
        let h = host.clone();
        let weak = weak.clone();
        move || {
            let h = h.clone();
            let weak = weak.clone();
            std::thread::spawn(move || {
                let r = h.act("hwidReset", &Value::Null);
                let id = r["hwid"].as_str().unwrap_or("").to_string();
                let _ = slint::invoke_from_event_loop(move || {
                    if let Some(a) = weak.upgrade() {
                        a.global::<Ui>().set_hwid(id.into());
                    }
                });
            });
        }
    });
    load_device(&host, &weak);
    ui.on_hwid_defaults({
        let h = host.clone();
        let weak = weak.clone();
        move || {
            let h = h.clone();
            let weak = weak.clone();
            std::thread::spawn(move || {
                h.act("hwidDefaults", &Value::Null);
                load_device(&h, &weak);
                toast(&weak, "Значения устройства сброшены");
            });
        }
    });
    ui.on_hwid_save({
        let h = host.clone();
        let weak = weak.clone();
        move |os, ver, model| {
            let h = h.clone();
            let weak = weak.clone();
            let payload = json!({"os": os.to_string(), "ver": ver.to_string(), "model": model.to_string()});
            std::thread::spawn(move || {
                h.act("hwidSet", &payload);
                toast(&weak, "Сохранено");
            });
        }
    });
    ui.on_copy({
        let h = host.clone();
        let weak = weak.clone();
        move |t| {
            let h = h.clone();
            let weak = weak.clone();
            let t = t.to_string();
            std::thread::spawn(move || {
                h.act("copy", &json!(t));
                toast(&weak, "Скопировано");
            });
        }
    });
    ui.on_version_tap({
        let weak = weak.clone();
        let taps: Rc<RefCell<Vec<Instant>>> = Rc::new(RefCell::new(Vec::new()));
        move || {
            let now = Instant::now();
            let mut t = taps.borrow_mut();
            t.push(now);
            t.retain(|x| now.duration_since(*x) <= Duration::from_secs(5));
            if t.len() >= 3 {
                t.clear();
                if let Some(a) = weak.upgrade() {
                    let ui = a.global::<Ui>();
                    match load_egg() {
                        Some(img) => {
                            ui.set_egg_image(img);
                            ui.set_egg_has(true);
                        }
                        None => ui.set_egg_has(false),
                    }
                    ui.set_egg(true);
                }
            }
        }
    });
    ui.on_pass_submit(|pw| {
        #[cfg(unix)]
        crate::elevate::answer(Some(pw.to_string()));
        #[cfg(not(unix))]
        let _ = pw;
    });
    ui.on_pass_cancel(|| {
        #[cfg(unix)]
        crate::elevate::answer(None);
    });
    ui.on_comps_load({
        let h = host.clone();
        let weak = weak.clone();
        move || load_comps(&h, &weak)
    });
    ui.on_comp_pick({
        let weak = weak.clone();
        move |i, j| {
            if let Some(a) = weak.upgrade() {
                let ui = a.global::<Ui>();
                let model = ui.get_comps();
                if let Some(mut row) = model.row_data(i as usize) {
                    let n = row.versions.row_count() as i32;
                    row.sel = if n == 0 { -1 } else if j < -1 { n - 1 } else if j >= n { -1 } else { j };
                    model.set_row_data(i as usize, row);
                }
            }
        }
    });
    ui.on_comps_install({
        let h = host.clone();
        let weak = weak.clone();
        move || {
            let Some(a) = weak.upgrade() else { return };
            let model = a.global::<Ui>().get_comps();
            let mut picks = serde_json::Map::new();
            for i in 0..model.row_count() {
                if let Some(c) = model.row_data(i) {
                    if c.sel >= 0 {
                        if let Some(v) = c.versions.row_data(c.sel as usize) {
                            picks.insert(c.key.to_string(), json!(v.to_string()));
                        }
                    }
                }
            }
            if picks.is_empty() {
                toast(&weak, "Ничего не выбрано");
                return;
            }
            a.global::<Ui>().set_setup_busy("Подготовка…".into());
            let h = h.clone();
            let weak = weak.clone();
            std::thread::spawn(move || {
                let r = h.act("componentsInstall", &Value::Object(picks));
                let errs: Vec<String> = r["errors"].as_array().map(|a| a.iter().filter_map(|v| v.as_str().map(String::from)).collect()).unwrap_or_default();
                let done = errs.is_empty();
                let w = weak.clone();
                let _ = slint::invoke_from_event_loop(move || {
                    if let Some(a) = w.upgrade() {
                        let ui = a.global::<Ui>();
                        ui.set_setup_busy("".into());
                        if done && ui.get_setup_first() {
                            ui.set_page("home".into());
                        }
                    }
                });
                toast(&weak, &if done { "Готово".to_string() } else { errs.join("; ") });
                load_comps(&h, &weak);
            });
        }
    });
    ui.on_setup_skip({
        let h = host.clone();
        let weak = weak.clone();
        move || {
            let h = h.clone();
            std::thread::spawn(move || {
                h.act("setupDone", &Value::Null);
            });
            if let Some(a) = weak.upgrade() {
                a.global::<Ui>().set_page("home".into());
            }
        }
    });
    ui.on_open_link({
        let h = host.clone();
        move |u| {
            h.act("open", &json!(u.to_string()));
        }
    });
    ui.on_quit({
        let h = host.clone();
        move || {
            h.shutdown();
            let _ = slint::quit_event_loop();
        }
    });

    {
        let h = host.clone();
        let weak = weak.clone();
        std::thread::spawn(move || {
            let mut since = 0u64;
            loop {
                let (seq, ev) = h.poll(since, Duration::from_secs(20));
                since = seq;
                if ev.is_empty() {
                    continue;
                }
                let w = weak.clone();
                if slint::invoke_from_event_loop(move || {
                    if let Some(a) = w.upgrade() {
                        UI_REFRESH.with(|f| {
                            if let Some(f) = f.borrow().as_ref() {
                                f(&a);
                            }
                        });
                    }
                })
                .is_err()
                {
                    break;
                }
            }
        });
    }
    let st2 = st.clone();
    let h2 = host.clone();
    UI_REFRESH.with(|f| {
        *f.borrow_mut() = Some(Box::new(move |a: &App| refresh(a, &st2, &h2)));
    });

    let anim = Timer::default();
    {
        let weak = weak.clone();
        let st = st.clone();
        anim.start(TimerMode::Repeated, Duration::from_millis(33), move || {
            if let Some(a) = weak.upgrade() {
                if a.window().is_visible() {
                    frame(&a, &st);
                }
            }
        });
    }
    let clock = Timer::default();
    {
        let weak = weak.clone();
        let st = st.clone();
        let host = host.clone();
        clock.start(TimerMode::Repeated, Duration::from_secs(1), move || {
            if let Some(a) = weak.upgrade() {
                if a.window().is_visible() && st.borrow().snap["status"].as_str() == Some("On") {
                    refresh(&a, &st, &host);
                }
            }
        });
    }

    let watch = Timer::default();
    {
        let weak = weak.clone();
        let st = st.clone();
        let host = host.clone();
        let mut ticks = 0u32;
        watch.start(TimerMode::Repeated, Duration::from_millis(250), move || {
            ticks += 1;
            if let Some(a) = weak.upgrade() {
                #[cfg(unix)]
                {
                    let ask = crate::elevate::pending();
                    let ui = a.global::<Ui>();
                    if ui.get_ask_pass() != ask.is_some() {
                        ui.set_ask_pass(ask.is_some());
                        if ask.is_some() && !a.window().is_visible() {
                            let _ = a.show();
                        }
                    }
                    ui.set_ask_wrong(ask.unwrap_or(false));
                }
                if ticks % 16 == 0 && a.window().is_visible() {
                    refresh(&a, &st, &host);
                }
            }
        });
    }
    if st.borrow().snap["setup"]["needed"].as_bool().unwrap_or(false) {
        let ui = app.global::<Ui>();
        ui.set_setup_first(true);
        ui.set_page("setup".into());
        load_comps(&host, &weak);
    }

    app.window().on_close_requested({
        let host = host.clone();
        move || {
            if tray_ready && host.close_to_tray() {
                slint::CloseRequestResponse::HideWindow
            } else {
                host.shutdown();
                let _ = slint::quit_event_loop();
                slint::CloseRequestResponse::HideWindow
            }
        }
    });
    host.set_ui({
        let weak = weak.clone();
        let h = host.clone();
        Box::new(move |c: UiCmd| {
            let w = weak.clone();
            let h = h.clone();
            let _ = slint::invoke_from_event_loop(move || match c {
                UiCmd::Show => {
                    if let Some(a) = w.upgrade() {
                        let _ = a.show();
                    }
                }
                UiCmd::Toggle => {
                    if let Some(a) = w.upgrade() {
                        if a.window().is_visible() {
                            let _ = a.hide();
                        } else {
                            let _ = a.show();
                        }
                    }
                }
                UiCmd::Quit => {
                    h.shutdown();
                    let _ = slint::quit_event_loop();
                }
            });
        })
    });

    app.show().map_err(|e| e.to_string())?;
    slint::run_event_loop_until_quit().map_err(|e| e.to_string())?;
    anim.stop();
    clock.stop();
    watch.stop();
    Ok(())
}

thread_local! {
    static UI_REFRESH: RefCell<Option<Box<dyn Fn(&App)>>> = RefCell::new(None);
}

struct ShotPlatform {
    window: Rc<slint::platform::software_renderer::MinimalSoftwareWindow>,
    start: Instant,
}

impl slint::platform::Platform for ShotPlatform {
    fn create_window_adapter(&self) -> Result<Rc<dyn slint::platform::WindowAdapter>, slint::PlatformError> {
        Ok(self.window.clone())
    }
    fn duration_since_start(&self) -> Duration {
        self.start.elapsed()
    }
}

pub fn screenshot(host: Arc<Host>, path: &str, page: &str, sheet: &str, w: u32, h: u32) -> Result<(), String> {
    use slint::platform::software_renderer::{MinimalSoftwareWindow, RepaintBufferType};
    let window = MinimalSoftwareWindow::new(RepaintBufferType::NewBuffer);
    slint::platform::set_platform(Box::new(ShotPlatform { window: window.clone(), start: Instant::now() })).map_err(|e| e.to_string())?;
    let app = App::new().map_err(|e| e.to_string())?;
    let st = Rc::new(RefCell::new(State { snap: json!({}), phase: 0.0, last: Instant::now(), speed: 0.2, amp: 0.062, toast_until: None, sch_key: String::new() }));
    app.global::<Ui>().set_swatches(ModelRc::new(VecModel::from(SWATCHES.iter().map(|h| col(palette::parse_hex(h).unwrap())).collect::<Vec<_>>())));
    app.global::<Ui>().set_swatch_hex(ModelRc::new(VecModel::from(SWATCHES.iter().map(|h| SharedString::from(*h)).collect::<Vec<_>>())));
    app.global::<Ui>().set_version(crate::host::VERSION.into());
    app.global::<Ui>().set_hwid("0123456789abcdef".into());
    refresh(&app, &st, &host);
    frame(&app, &st);
    if page == "setup" {
        let mk = |key: &str, name: &str, inst: &str, v: &[&str], sel: i32| Comp {
            key: key.into(),
            name: name.into(),
            installed: inst.into(),
            versions: ModelRc::new(VecModel::from(v.iter().map(|x| SharedString::from(*x)).collect::<Vec<_>>())),
            sel,
            note: "".into(),
        };
        app.global::<Ui>().set_comps(ModelRc::new(VecModel::from(vec![
            mk("xray", "Xray", "", &["v26.10.31", "v26.9.20"], 0),
            mk("singbox", "sing-box", "1.12.4", &["1.13.0", "1.12.4"], -1),
            mk("awg", "AmneziaWG", "", &["последняя"], 0),
        ])));
        app.global::<Ui>().set_setup_first(true);
    }
    app.global::<Ui>().set_page(page.into());
    app.global::<Ui>().set_sheet(sheet.into());
    app.show().map_err(|e| e.to_string())?;
    window.set_size(slint::PhysicalSize::new(w, h));
    for _ in 0..30 {
        slint::platform::update_timers_and_animations();
        std::thread::sleep(Duration::from_millis(30));
    }
    let mut buf = vec![slint::Rgb8Pixel::default(); (w * h) as usize];
    window.draw_if_needed(|r| {
        r.render(&mut buf, w as usize);
    });
    let mut pix = resvg::tiny_skia::Pixmap::new(w, h).ok_or("pixmap")?;
    for (i, p) in pix.pixels_mut().iter_mut().enumerate() {
        let b = buf[i];
        *p = resvg::tiny_skia::PremultipliedColorU8::from_rgba(b.r, b.g, b.b, 255).ok_or("px")?;
    }
    pix.save_png(path).map_err(|e| e.to_string())
}
