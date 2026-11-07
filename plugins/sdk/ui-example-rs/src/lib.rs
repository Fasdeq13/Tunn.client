use serde_json::Value;
use std::ffi::{c_char, c_float, c_int, CStr, CString};
use std::sync::Mutex;

const FONT: [(char, [u8; 7]); 40] = [
    ('A', [14, 17, 17, 31, 17, 17, 17]), ('B', [30, 17, 17, 30, 17, 17, 30]), ('C', [14, 17, 16, 16, 16, 17, 14]),
    ('D', [30, 17, 17, 17, 17, 17, 30]), ('E', [31, 16, 16, 30, 16, 16, 31]), ('F', [31, 16, 16, 30, 16, 16, 16]),
    ('G', [14, 17, 16, 23, 17, 17, 15]), ('H', [17, 17, 17, 31, 17, 17, 17]), ('I', [14, 4, 4, 4, 4, 4, 14]),
    ('J', [7, 2, 2, 2, 2, 18, 12]), ('K', [17, 18, 20, 24, 20, 18, 17]), ('L', [16, 16, 16, 16, 16, 16, 31]),
    ('M', [17, 27, 21, 21, 17, 17, 17]), ('N', [17, 25, 21, 19, 17, 17, 17]), ('O', [14, 17, 17, 17, 17, 17, 14]),
    ('P', [30, 17, 17, 30, 16, 16, 16]), ('Q', [14, 17, 17, 17, 21, 18, 13]), ('R', [30, 17, 17, 30, 20, 18, 17]),
    ('S', [15, 16, 16, 14, 1, 1, 30]), ('T', [31, 4, 4, 4, 4, 4, 4]), ('U', [17, 17, 17, 17, 17, 17, 14]),
    ('V', [17, 17, 17, 17, 17, 10, 4]), ('W', [17, 17, 17, 21, 21, 27, 17]), ('X', [17, 17, 10, 4, 10, 17, 17]),
    ('Y', [17, 17, 10, 4, 4, 4, 4]), ('Z', [31, 1, 2, 4, 8, 16, 31]), ('0', [14, 17, 19, 21, 25, 17, 14]),
    ('1', [4, 12, 4, 4, 4, 4, 14]), ('2', [14, 17, 1, 2, 4, 8, 31]), ('3', [30, 1, 1, 14, 1, 1, 30]),
    ('4', [2, 6, 10, 18, 31, 2, 2]), ('5', [31, 16, 30, 1, 1, 17, 14]), ('6', [6, 8, 16, 30, 17, 17, 14]),
    ('7', [31, 1, 2, 4, 8, 8, 8]), ('8', [14, 17, 17, 14, 17, 17, 14]), ('9', [14, 17, 17, 15, 1, 2, 12]),
    ('-', [0, 0, 0, 31, 0, 0, 0]), ('.', [0, 0, 0, 0, 0, 12, 12]), (' ', [0, 0, 0, 0, 0, 0, 0]), ('?', [14, 17, 1, 2, 4, 0, 4]),
];

struct Server {
    id: String,
    name: String,
}

struct Ui {
    w: i32,
    h: i32,
    d: f32,
    status: String,
    server: String,
    list: Vec<Server>,
    selected: String,
    actions: Vec<String>,
    dirty: bool,
    press: Option<(f32, f32)>,
    pending: Option<CString>,
}

static UI: Mutex<Option<Ui>> = Mutex::new(None);

fn glyph(c: char) -> [u8; 7] {
    let u = c.to_ascii_uppercase();
    FONT.iter().find(|(k, _)| *k == u).map(|(_, g)| *g).unwrap_or(FONT[FONT.len() - 1].1)
}

struct Canvas<'a> {
    px: &'a mut [u8],
    w: i32,
    h: i32,
    stride: i32,
}

impl Canvas<'_> {
    fn put(&mut self, x: i32, y: i32, c: [u8; 3]) {
        if x < 0 || y < 0 || x >= self.w || y >= self.h {
            return;
        }
        let i = (y * self.stride + x * 4) as usize;
        self.px[i] = c[0];
        self.px[i + 1] = c[1];
        self.px[i + 2] = c[2];
        self.px[i + 3] = 255;
    }
    fn rect(&mut self, x: i32, y: i32, w: i32, h: i32, c: [u8; 3]) {
        for yy in y.max(0)..(y + h).min(self.h) {
            for xx in x.max(0)..(x + w).min(self.w) {
                self.put(xx, yy, c);
            }
        }
    }
    fn round(&mut self, x: i32, y: i32, w: i32, h: i32, r: i32, c: [u8; 3]) {
        let r = r.min(w / 2).min(h / 2);
        for yy in y.max(0)..(y + h).min(self.h) {
            for xx in x.max(0)..(x + w).min(self.w) {
                let cx = if xx < x + r { x + r } else if xx >= x + w - r { x + w - r - 1 } else { xx };
                let cy = if yy < y + r { y + r } else if yy >= y + h - r { y + h - r - 1 } else { yy };
                let (dx, dy) = (xx - cx, yy - cy);
                if dx * dx + dy * dy <= r * r {
                    self.put(xx, yy, c);
                }
            }
        }
    }
    fn circle(&mut self, cx: i32, cy: i32, r: i32, c: [u8; 3]) {
        self.round(cx - r, cy - r, r * 2, r * 2, r, c);
    }
    fn text(&mut self, x: i32, y: i32, s: &str, scale: i32, c: [u8; 3]) -> i32 {
        let mut cx = x;
        for ch in s.chars() {
            let g = glyph(ch);
            for (row, bits) in g.iter().enumerate() {
                for col in 0..5 {
                    if bits >> (4 - col) & 1 == 1 {
                        self.rect(cx + col * scale, y + row as i32 * scale, scale, scale, c);
                    }
                }
            }
            cx += 6 * scale;
        }
        cx - x
    }
}

fn clip(s: &str, max: usize) -> String {
    if s.chars().count() <= max { s.to_string() } else { s.chars().take(max.saturating_sub(1)).collect::<String>() + "." }
}

impl Ui {
    fn button(&self) -> (i32, i32, i32) {
        (self.w / 2, (self.h as f32 * 0.30) as i32, (self.w.min(self.h) as f32 * 0.20) as i32)
    }
    fn row(&self, i: usize) -> (i32, i32, i32, i32) {
        let m = (16.0 * self.d) as i32;
        let h = (56.0 * self.d) as i32;
        let top = (self.h as f32 * 0.30) as i32 + self.button().2 + (40.0 * self.d) as i32;
        (m, top + i as i32 * (h + m / 2), self.w - 2 * m, h)
    }
    fn tap(&mut self, x: f32, y: f32) {
        let (bx, by, br) = self.button();
        let (dx, dy) = (x as i32 - bx, y as i32 - by);
        if dx * dx + dy * dy <= br * br {
            self.actions.push(r#"{"op":"toggle"}"#.to_string());
            return;
        }
        for (i, s) in self.list.iter().enumerate().take(6) {
            let (rx, ry, rw, rh) = self.row(i);
            if x as i32 >= rx && (x as i32) < rx + rw && y as i32 >= ry && (y as i32) < ry + rh {
                self.actions.push(serde_json::json!({"op": "select", "id": s.id}).to_string());
                return;
            }
        }
    }
}

#[no_mangle]
pub extern "C" fn fq_ui_abi() -> c_int {
    1
}

#[no_mangle]
pub extern "C" fn fq_ui_info() -> *const c_char {
    c"{\"name\":\"Example UI\",\"version\":\"1.0.0\"}".as_ptr()
}

#[no_mangle]
pub extern "C" fn fq_ui_init(w: c_int, h: c_int, density: c_float) -> c_int {
    *UI.lock().unwrap() = Some(Ui {
        w, h, d: density.max(0.5), status: "Off".into(), server: String::new(), list: vec![], selected: String::new(),
        actions: vec![], dirty: true, press: None, pending: None,
    });
    0
}

#[no_mangle]
pub extern "C" fn fq_ui_resize(w: c_int, h: c_int) {
    if let Some(u) = UI.lock().unwrap().as_mut() {
        u.w = w;
        u.h = h;
        u.dirty = true;
    }
}

#[no_mangle]
pub unsafe extern "C" fn fq_ui_state(json: *const c_char) {
    if json.is_null() {
        return;
    }
    let Ok(v) = serde_json::from_str::<Value>(&CStr::from_ptr(json).to_string_lossy()) else { return };
    if let Some(u) = UI.lock().unwrap().as_mut() {
        u.status = v["status"].as_str().unwrap_or("Off").to_string();
        u.server = v["server"]["name"].as_str().unwrap_or("").to_string();
        u.selected = v["server"]["id"].as_str().unwrap_or("").to_string();
        u.list = v["servers"].as_array().map(|a| a.iter().map(|s| Server {
            id: s["id"].as_str().unwrap_or("").to_string(),
            name: s["name"].as_str().unwrap_or("").to_string(),
        }).collect()).unwrap_or_default();
        u.dirty = true;
    }
}

#[no_mangle]
pub extern "C" fn fq_ui_touch(kind: c_int, x: c_float, y: c_float) {
    if let Some(u) = UI.lock().unwrap().as_mut() {
        match kind {
            0 => u.press = Some((x, y)),
            2 => {
                if let Some((px, py)) = u.press.take() {
                    if (px - x).abs() < 24.0 * u.d && (py - y).abs() < 24.0 * u.d {
                        u.tap(x, y);
                    }
                }
            }
            3 => u.press = None,
            _ => {}
        }
    }
}

#[no_mangle]
pub extern "C" fn fq_ui_back() {
    if let Some(u) = UI.lock().unwrap().as_mut() {
        u.actions.push(r#"{"op":"exit"}"#.to_string());
    }
}

#[no_mangle]
pub unsafe extern "C" fn fq_ui_render(rgba: *mut u8, w: c_int, h: c_int, stride: c_int, _t: u32) -> c_int {
    let mut g = UI.lock().unwrap();
    let Some(u) = g.as_mut() else { return -1 };
    if !u.dirty {
        return 0;
    }
    u.dirty = false;
    u.w = w;
    u.h = h;
    let px = std::slice::from_raw_parts_mut(rgba, (stride * h) as usize);
    let mut c = Canvas { px, w, h, stride };
    let on = u.status == "On";
    let busy = u.status == "Connecting" || u.status == "Stopping";
    c.rect(0, 0, w, h, [14, 16, 22]);
    let s = ((u.d * 3.0) as i32).max(2);
    let title_w = 6 * s * 4;
    c.text((w - title_w) / 2, (24.0 * u.d) as i32, "TUNN", s, [235, 238, 245]);
    let (bx, by, br) = u.button();
    let accent = if on { [64, 196, 130] } else if busy { [240, 180, 60] } else { [90, 110, 160] };
    c.circle(bx, by, br + (10.0 * u.d) as i32, [24, 28, 40]);
    c.circle(bx, by, br, accent);
    c.circle(bx, by, br - (14.0 * u.d) as i32, [20, 24, 34]);
    let label = if on { "ON" } else if busy { "..." } else { "OFF" };
    let ls = ((u.d * 4.0) as i32).max(2);
    let lw = label.chars().count() as i32 * 6 * ls;
    c.text(bx - lw / 2, by - 3 * ls, label, ls, accent);
    let sn = clip(&u.server, 22);
    let ss = ((u.d * 2.0) as i32).max(1);
    let sw = sn.chars().count() as i32 * 6 * ss;
    c.text((w - sw) / 2, by + br + (12.0 * u.d) as i32, &sn, ss, [170, 178, 196]);
    for i in 0..u.list.len().min(6) {
        let (rx, ry, rw, rh) = u.row(i);
        let sel = u.list[i].id == u.selected;
        c.round(rx, ry, rw, rh, (14.0 * u.d) as i32, if sel { [34, 52, 92] } else { [26, 30, 42] });
        let n = clip(&u.list[i].name, 24);
        c.text(rx + (16.0 * u.d) as i32, ry + rh / 2 - 7 * ss / 2 - ss, &n, ss, [226, 230, 240]);
    }
    1
}

#[no_mangle]
pub extern "C" fn fq_ui_poll() -> *const c_char {
    let mut g = UI.lock().unwrap();
    let Some(u) = g.as_mut() else { return std::ptr::null() };
    if u.actions.is_empty() {
        return std::ptr::null();
    }
    let a = u.actions.remove(0);
    u.pending = CString::new(a).ok();
    u.pending.as_ref().map(|c| c.as_ptr()).unwrap_or(std::ptr::null())
}

#[no_mangle]
pub extern "C" fn fq_ui_close() {
    *UI.lock().unwrap() = None;
}
