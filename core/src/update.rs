use serde_json::{json, Value};

#[derive(Debug, PartialEq)]
pub struct PackName {
    pub kind: String,
    pub nums: Vec<u32>,
}

pub fn parse_name(file: &str) -> Option<PackName> {
    let base = file.rsplit('/').next()?;
    let rest = base.strip_prefix("Tunn-")?;
    let (stem, ext) = rest.rsplit_once('.')?;
    let kind = match ext.to_ascii_lowercase().as_str() {
        "update" => "update",
        "fix" => "fix",
        _ => return None,
    };
    let mut nums = Vec::new();
    for part in stem.split('.') {
        nums.push(part.parse::<u32>().ok()?);
    }
    if nums.len() < 2 || (kind == "fix" && nums.len() < 3) {
        return None;
    }
    Some(PackName { kind: kind.into(), nums })
}

pub fn numbers(v: &str) -> Vec<u32> {
    let mut out = Vec::new();
    let mut cur = String::new();
    for c in v.chars().chain(std::iter::once(' ')) {
        if c.is_ascii_digit() {
            cur.push(c);
        } else if !cur.is_empty() {
            out.push(cur.parse().unwrap_or(0));
            cur.clear();
        }
    }
    out
}

pub fn newer(a: &[u32], b: &[u32]) -> bool {
    for i in 0..a.len().max(b.len()) {
        let x = a.get(i).copied().unwrap_or(0);
        let y = b.get(i).copied().unwrap_or(0);
        if x != y {
            return x > y;
        }
    }
    false
}

fn ident(s: &str) -> bool {
    !s.is_empty() && s.chars().all(|c| c.is_ascii_alphanumeric() || c == '_' || c == '-')
}

pub fn safe_dest(dest: &str) -> bool {
    if dest.is_empty() || dest.starts_with('/') || dest.contains('\\') || dest.contains('\0') {
        return false;
    }
    if dest.split('/').any(|p| p.is_empty() || p == "." || p == "..") {
        return false;
    }
    let parts: Vec<&str> = dest.split('/').collect();
    match parts[0] {
        "dex" => parts.len() == 2 && parts[1].strip_suffix(".dex").map(ident).unwrap_or(false),
        "lib" => {
            parts.len() == 3
                && ["arm64-v8a", "armeabi-v7a", "x86_64", "x86"].contains(&parts[1])
                && parts[2].strip_prefix("lib").and_then(|r| r.strip_suffix(".so")).map(ident).unwrap_or(false)
        }
        "plugins" | "themes" => parts.len() >= 2,
        _ => false,
    }
}

fn err(msg: &str) -> Value {
    json!({"ok": false, "error": msg})
}

pub fn plan(file: &str, manifest: &Value, cur_version: &str, shell_level: i64, applied_fix: &str) -> Value {
    let Some(name) = parse_name(file) else { return err("Имя файла не похоже на Tunn-версия.Update или Tunn-версия.N.fix") };
    if manifest.get("format").and_then(|v| v.as_i64()) != Some(2) {
        return err("Неизвестный формат пакета");
    }
    let kind = manifest.get("kind").and_then(|v| v.as_str()).unwrap_or("");
    if kind != name.kind {
        return err("Тип в манифесте не совпадает с именем файла");
    }
    let need = manifest.get("shell").and_then(|v| v.as_i64()).unwrap_or(i64::MAX);
    if need > shell_level {
        return err("Пакету нужна более новая основа приложения, поставьте свежий APK");
    }
    let cur = numbers(cur_version);
    let serial_text;
    if kind == "update" {
        if !newer(&name.nums, &cur) {
            return err("Это не новее установленной версии");
        }
        if manifest.get("version").and_then(|v| v.as_str()).map(numbers).as_deref() != Some(&name.nums[..]) {
            return err("Версия в манифесте не совпадает с именем файла");
        }
        serial_text = String::new();
    } else {
        if cur.len() < 2 || name.nums[..2] != cur[..2] {
            return err("Исправление относится к другой версии приложения");
        }
        let serial = &name.nums[2..];
        if !newer(serial, &numbers(applied_fix)) {
            return err("Это исправление уже применено");
        }
        serial_text = serial.iter().map(|n| n.to_string()).collect::<Vec<_>>().join(".");
    }
    let Some(list) = manifest.get("files").and_then(|v| v.as_array()) else { return err("В пакете нет файлов") };
    let mut files = Vec::new();
    let mut dex = 0;
    for f in list {
        let Some(dest) = f.get("path").and_then(|v| v.as_str()) else { return err("Файл без пути") };
        if !safe_dest(dest) {
            return err("Недопустимый путь в пакете");
        }
        let hash = f.get("sha256").and_then(|v| v.as_str()).unwrap_or("");
        if hash.len() != 64 || !hash.chars().all(|c| c.is_ascii_hexdigit()) {
            return err("У файла нет SHA-256");
        }
        if dest.starts_with("dex/") {
            dex += 1;
        }
        files.push(json!({"path": dest, "sha256": hash.to_lowercase()}));
    }
    if files.is_empty() || (kind == "update" && dex == 0) {
        return err("В пакете нет кода");
    }
    json!({"ok": true, "kind": kind, "files": files, "serial": serial_text, "version": name.nums.iter().take(if kind == "update" { name.nums.len() } else { 2 }).map(|n| n.to_string()).collect::<Vec<_>>().join("."), "notes": manifest.get("notes").cloned().unwrap_or(Value::Null)})
}

#[cfg(test)]
mod tests {
    use super::*;

    fn files(paths: &[&str]) -> Value {
        let h = "a".repeat(64);
        Value::Array(paths.iter().map(|p| json!({"path": p, "sha256": h})).collect())
    }

    #[test]
    fn names() {
        assert_eq!(parse_name("Tunn-4.3.Update").unwrap().nums, vec![4, 3]);
        assert_eq!(parse_name("Tunn-4.3.update").unwrap().kind, "update");
        assert_eq!(parse_name("x/Tunn-4.3.1.2.fix").unwrap().nums, vec![4, 3, 1, 2]);
        assert!(parse_name("Tunn-4.3.fix").is_none());
        assert!(parse_name("Tunn-4.3.zip").is_none());
        assert!(parse_name("Other-4.3.Update").is_none());
    }

    #[test]
    fn dest() {
        assert!(safe_dest("dex/classes.dex"));
        assert!(safe_dest("dex/classes2.dex"));
        assert!(safe_dest("lib/arm64-v8a/libtunn_service.so"));
        assert!(safe_dest("plugins/a/plugin.json"));
        assert!(safe_dest("themes/x.json"));
        assert!(!safe_dest("dex/a/b.dex"));
        assert!(!safe_dest("dex/x.so"));
        assert!(!safe_dest("lib/mips/libx.so"));
        assert!(!safe_dest("lib/arm64-v8a/x.so"));
        assert!(!safe_dest("../x"));
        assert!(!safe_dest("plugins/../x"));
        assert!(!safe_dest("/plugins/x"));
        assert!(!safe_dest("cores/x"));
    }

    #[test]
    fn update_plan() {
        let m = json!({"format":2,"kind":"update","version":"4.3","shell":1,"files":files(&["dex/classes.dex","lib/arm64-v8a/libtunn_service.so"])});
        let p = plan("Tunn-4.3.Update", &m, "4.2", 1, "");
        assert_eq!(p["ok"], true);
        assert_eq!(p["version"], "4.3");
        assert_eq!(plan("Tunn-4.2.Update", &m, "4.2", 1, "")["ok"], false);
        assert_eq!(plan("Tunn-4.3.Update", &m, "4.2", 0, "")["ok"], false);
        let nodex = json!({"format":2,"kind":"update","version":"4.3","shell":1,"files":files(&["themes/a.json"])});
        assert_eq!(plan("Tunn-4.3.Update", &nodex, "4.2", 1, "")["ok"], false);
        let wrong = json!({"format":2,"kind":"update","version":"4.4","shell":1,"files":files(&["dex/classes.dex"])});
        assert_eq!(plan("Tunn-4.3.Update", &wrong, "4.2", 1, "")["ok"], false);
    }

    #[test]
    fn fix_plan() {
        let m = json!({"format":2,"kind":"fix","shell":1,"files":files(&["dex/fix1.dex"])});
        let p = plan("Tunn-4.3.1.fix", &m, "4.3", 1, "");
        assert_eq!(p["ok"], true);
        assert_eq!(p["serial"], "1");
        assert_eq!(p["version"], "4.3");
        assert_eq!(plan("Tunn-4.3.1.fix", &m, "4.3", 1, "1")["ok"], false);
        assert_eq!(plan("Tunn-4.3.2.fix", &m, "4.3", 1, "1")["ok"], true);
        assert_eq!(plan("Tunn-4.4.1.fix", &m, "4.3", 1, "")["ok"], false);
        let bad = json!({"format":2,"kind":"fix","shell":1,"files":files(&["../x"])});
        assert_eq!(plan("Tunn-4.3.1.fix", &bad, "4.3", 1, "")["ok"], false);
        let old = json!({"format":1,"kind":"fix","shell":1,"files":files(&["dex/a.dex"])});
        assert_eq!(plan("Tunn-4.3.1.fix", &old, "4.3", 1, "")["ok"], false);
    }
}
