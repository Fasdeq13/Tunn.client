
use base64::Engine as _;
use serde_json::{json, Map, Value};
use std::collections::HashSet;
use std::fs::OpenOptions;
use std::io::Write;
use std::sync::Mutex;

pub fn retry_delay(attempt: u32) -> Option<u64> {
    const LADDER: [u64; 5] = [0, 300, 900, 1800, 3500];
    LADDER.get(attempt as usize).copied()
}

pub fn start_timeout_ms(kind: &str, attempt: u32) -> u64 {
    let base = match kind {
        "awg" | "wg" => 9000,
        "dpi" => 20000,
        "plugin" => 12000,
        _ => 11000,
    };
    base + (attempt.min(3) as u64) * 1500
}

pub const FAILOVER_AFTER: u32 = 2;

pub fn settle_ms(prev: &str) -> u64 {
    match prev {
        "awg" | "wg" => 900, // the Go runtime and the tun fd need a moment
        "plugin" => 700,
        _ => 400,
    }
}

pub fn pick_next(candidates: &Value, failed: &[String], current: &str) -> Option<String> {
    let bad: HashSet<&str> = failed.iter().map(|s| s.as_str()).collect();
    let mut best: Option<(i64, String)> = None;
    let mut unknown: Option<String> = None;
    for c in candidates.as_array()? {
        let id = c.get("id")?.as_str()?;
        if id == current || bad.contains(id) {
            continue;
        }
        let p = c.get("ping").and_then(|v| v.as_i64()).unwrap_or(0);
        if p > 0 {
            if best.as_ref().map_or(true, |(b, _)| p < *b) {
                best = Some((p, id.to_string()));
            }
        } else if p == 0 && unknown.is_none() {
            unknown = Some(id.to_string());
        }
    }
    best.map(|b| b.1).or(unknown)
}

#[derive(Default)]
pub struct Health {
    bad: u32,
    good: u32,
}

impl Health {
    pub const LIMIT: u32 = 3;
    pub fn record(&mut self, ok: bool) -> bool {
        if ok {
            self.good += 1;
            self.bad = 0;
            false
        } else {
            self.bad += 1;
            self.good = 0;
            self.bad >= Self::LIMIT
        }
    }
    pub fn reset(&mut self) {
        *self = Health::default();
    }
}


static PATH: Mutex<Option<String>> = Mutex::new(None);
const MAX_BYTES: u64 = 192 * 1024;

pub fn journal_init(path: &str) {
    if let Ok(mut p) = PATH.lock() {
        *p = Some(path.to_string());
    }
}

fn now_ms() -> u128 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis())
        .unwrap_or(0)
}

fn clock(ms: u128) -> String {
    let s = (ms / 1000) % 86400;
    format!("{:02}:{:02}:{:02}.{:03}", s / 3600, (s / 60) % 60, s % 60, ms % 1000)
}

pub fn format_line(ms: u128, level: &str, tag: &str, msg: &str) -> String {
    let msg = msg.replace('\n', " ⏎ ");
    format!("{} {} [{}] {}\n", clock(ms), level, tag, msg)
}

pub fn journal_add(level: &str, tag: &str, msg: &str) {
    let path = match PATH.lock().ok().and_then(|p| p.clone()) {
        Some(p) => p,
        None => return,
    };
    if let Ok(meta) = std::fs::metadata(&path) {
        if meta.len() > MAX_BYTES {
            rotate(&path);
        }
    }
    if let Ok(mut f) = OpenOptions::new().create(true).append(true).open(&path) {
        let _ = f.write_all(format_line(now_ms(), level, tag, msg).as_bytes());
    }
}

fn rotate(path: &str) {
    if let Ok(text) = std::fs::read_to_string(path) {
        let keep: Vec<&str> = text.lines().collect();
        let from = keep.len() / 2;
        let tail = keep[from..].join("\n") + "\n";
        let _ = std::fs::write(path, tail);
    }
}

pub fn journal_read(max_lines: usize) -> String {
    let path = match PATH.lock().ok().and_then(|p| p.clone()) {
        Some(p) => p,
        None => return String::new(),
    };
    let text = std::fs::read_to_string(&path).unwrap_or_default();
    let lines: Vec<&str> = text.lines().collect();
    let from = lines.len().saturating_sub(max_lines);
    lines[from..].join("\n")
}

pub fn journal_clear() {
    if let Some(p) = PATH.lock().ok().and_then(|p| p.clone()) {
        let _ = std::fs::write(p, "");
    }
}


fn decode_b64_text(v: &str) -> String {
    let t = v.trim();
    let Some(rest) = t.strip_prefix("base64:").or_else(|| t.strip_prefix("BASE64:")) else {
        return t.to_string();
    };
    let clean: String = rest.chars().filter(|c| !c.is_whitespace()).collect();
    for eng in [
        &base64::engine::general_purpose::STANDARD,
        &base64::engine::general_purpose::STANDARD_NO_PAD,
        &base64::engine::general_purpose::URL_SAFE,
        &base64::engine::general_purpose::URL_SAFE_NO_PAD,
    ] {
        if let Ok(b) = eng.decode(clean.as_bytes()) {
            if let Ok(s) = String::from_utf8(b) {
                return s;
            }
        }
    }
    t.to_string()
}

fn truthy(v: &str) -> bool {
    matches!(v.trim().to_ascii_lowercase().as_str(), "1" | "true" | "yes" | "on")
}

pub fn parse_sub_headers(headers: &Value) -> Value {
    let mut h: Map<String, Value> = Map::new();
    if let Some(o) = headers.as_object() {
        for (k, v) in o {
            if let Some(s) = v.as_str() {
                h.insert(k.to_ascii_lowercase(), Value::String(s.to_string()));
            }
        }
    }
    let get = |k: &str| h.get(k).and_then(|v| v.as_str()).map(|s| s.to_string());
    let mut out = Map::new();
    if let Some(v) = get("profile-title") {
        out.insert("title".into(), json!(decode_b64_text(&v)));
    }
    if let Some(v) = get("announce") {
        out.insert("announce".into(), json!(decode_b64_text(&v)));
    }
    if let Some(v) = get("announce-url") {
        out.insert("announceUrl".into(), json!(v.trim()));
    }
    if let Some(v) = get("support-url") {
        out.insert("support".into(), json!(v.trim()));
    }
    if let Some(v) = get("profile-web-page-url") {
        out.insert("web".into(), json!(v.trim()));
    }
    if let Some(v) = get("profile-update-interval") {
        if let Ok(n) = v.trim().parse::<f64>() {
            if n > 0.0 {
                out.insert("updateHours".into(), json!(n.clamp(1.0, 24.0 * 30.0)));
            }
        }
    }
    if let Some(v) = get("hide-settings") {
        out.insert("hideSettings".into(), json!(truthy(&v)));
    }
    if let Some(v) = get("check-url-via-proxy") {
        out.insert("checkUrl".into(), json!(v.trim()));
    }
    if let Some(v) = get("ping-type") {
        out.insert("pingType".into(), json!(v.trim().to_ascii_lowercase()));
    }
    if let Some(v) = get("fallback-url") {
        out.insert("fallback".into(), json!(v.trim()));
    }
    Value::Object(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ladder_is_bounded_and_fast() {
        assert_eq!(retry_delay(0), Some(0));
        assert!(retry_delay(4).is_some());
        assert_eq!(retry_delay(5), None);
        let total: u64 = (0..5).filter_map(retry_delay).sum();
        assert!(total < 8000);
    }

    #[test]
    fn timeouts_grow_but_cap() {
        assert!(start_timeout_ms("xray", 1) > start_timeout_ms("xray", 0));
        assert_eq!(start_timeout_ms("xray", 3), start_timeout_ms("xray", 9));
        assert!(start_timeout_ms("awg", 0) < start_timeout_ms("dpi", 0));
    }

    #[test]
    fn picks_lowest_ping_skipping_failed_and_current() {
        let c = json!([
            {"id": "a", "ping": 30}, {"id": "b", "ping": 80}, {"id": "c", "ping": 20},
            {"id": "d", "ping": -1}, {"id": "e", "ping": null}
        ]);
        assert_eq!(pick_next(&c, &[], "x").as_deref(), Some("c"));
        assert_eq!(pick_next(&c, &["c".into()], "x").as_deref(), Some("a"));
        assert_eq!(pick_next(&c, &["c".into()], "a").as_deref(), Some("b"));
        assert_eq!(pick_next(&c, &["c".into(), "a".into(), "b".into()], "x").as_deref(), Some("e"));
        assert_eq!(pick_next(&c, &["c".into(), "a".into(), "b".into(), "e".into()], "x"), None);
        assert_eq!(pick_next(&json!([]), &[], "x"), None);
        assert_eq!(pick_next(&json!("junk"), &[], "x"), None);
    }

    #[test]
    fn health_needs_consecutive_failures() {
        let mut h = Health::default();
        assert!(!h.record(false));
        assert!(!h.record(false));
        assert!(!h.record(true));
        assert!(!h.record(false));
        assert!(!h.record(false));
        assert!(h.record(false));
        h.reset();
        assert!(!h.record(false));
    }

    #[test]
    fn journal_roundtrip_and_rotation() {
        let dir = std::env::temp_dir().join(format!("fq-journal-{}", std::process::id()));
        let _ = std::fs::create_dir_all(&dir);
        let f = dir.join("j.log");
        let _ = std::fs::remove_file(&f);
        journal_init(f.to_str().unwrap());
        journal_add("I", "test", "hello\nworld");
        journal_add("E", "test", "boom");
        let r = journal_read(10);
        assert!(r.contains("[test] hello ⏎ world"));
        assert_eq!(r.lines().count(), 2);
        assert_eq!(journal_read(1).lines().count(), 1);
        for i in 0..4000 {
            journal_add("I", "bulk", &format!("line {i} {}", "x".repeat(60)));
        }
        let size = std::fs::metadata(&f).unwrap().len();
        assert!(size < MAX_BYTES + 4096, "size {size}");
        assert!(journal_read(5).contains("line 3999"));
        journal_clear();
        assert_eq!(journal_read(10), "");
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn headers_happ_style() {
        let h = json!({
            "Profile-Title": "base64:0JTQvtCx0YDRi9C5IFZQTg==",
            "ANNOUNCE": "base64:0J/RgNC40LLQtdGC",
            "Support-Url": " https://t.me/support ",
            "profile-web-page-url": "https://example.com",
            "profile-update-interval": "6",
            "hide-settings": "1",
            "ping-type": "TCP",
            "check-url-via-proxy": "https://www.gstatic.com/generate_204"
        });
        let o = parse_sub_headers(&h);
        assert_eq!(o["title"], "Добрый VPN");
        assert_eq!(o["announce"], "Привет");
        assert_eq!(o["support"], "https://t.me/support");
        assert_eq!(o["web"], "https://example.com");
        assert_eq!(o["updateHours"], 6.0);
        assert_eq!(o["hideSettings"], true);
        assert_eq!(o["pingType"], "tcp");
        assert!(o.get("fallback").is_none());
    }

    #[test]
    fn headers_plain_and_junk() {
        let o = parse_sub_headers(&json!({"announce": "просто текст", "profile-update-interval": "abc", "profile-title": "base64:!!!"}));
        assert_eq!(o["announce"], "просто текст");
        assert!(o.get("updateHours").is_none());
        assert_eq!(o["title"], "base64:!!!");
        assert_eq!(parse_sub_headers(&json!(null)), json!({}));
    }
}
