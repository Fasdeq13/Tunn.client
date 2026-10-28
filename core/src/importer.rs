use crate::links;
use base64::Engine;
use serde_json::Value;
use std::collections::HashMap;

#[derive(Clone, Debug)]
pub struct Source {
    pub value: String,
    pub is_url: bool,
    pub name: String,
}

impl Source {
    fn new(value: &str, is_url: bool) -> Source {
        Source {
            value: value.to_string(),
            is_url,
            name: String::new(),
        }
    }

    fn named(value: &str, is_url: bool, name: String) -> Source {
        Source {
            value: value.to_string(),
            is_url,
            name,
        }
    }
}

pub struct Prepared {
    pub items: Vec<Source>,
    pub error: Option<String>,
}

const DIRECT: [&str; 5] = ["vless://", "vmess://", "trojan://", "ss://", "tuic://"];
const DEEP: [&str; 13] = [
    "happ",
    "v2rayng",
    "v2rayn",
    "clash",
    "clashmeta",
    "sing-box",
    "hiddify",
    "v2box",
    "streisand",
    "nekobox",
    "v2raytun",
    "flclash",
    "koala-clash",
];
const URL_KEYS: [&str; 8] = [
    "url",
    "link",
    "sublink",
    "subscription",
    "suburl",
    "uri",
    "href",
    "source",
];
const NAME_KEYS: [&str; 5] = ["remarks", "name", "title", "ps", "alias"];

fn fail(msg: &str) -> Prepared {
    Prepared {
        items: Vec::new(),
        error: Some(msg.to_string()),
    }
}

fn ok(items: Vec<Source>) -> Prepared {
    Prepared { items, error: None }
}

fn distinct(items: Vec<Source>) -> Vec<Source> {
    let mut seen = std::collections::HashSet::new();
    items
        .into_iter()
        .filter(|s| seen.insert(s.value.clone()))
        .collect()
}

fn is_direct(lower: &str) -> bool {
    DIRECT.iter().any(|p| lower.starts_with(p))
}

fn is_http(lower: &str) -> bool {
    lower.starts_with("http://") || lower.starts_with("https://")
}

pub fn prepare(text: &str) -> Prepared {
    let t = text.trim().trim_start_matches('\u{feff}');
    if t.is_empty() {
        return fail("Пусто");
    }
    if t.starts_with("PK") {
        return fail("Резервная копия в zip не поддерживается. В v2rayNG выберите «Экспорт конфигураций в буфер обмена» и вставьте текст сюда");
    }
    if t.to_lowercase().contains("[interface]") {
        return ok(vec![Source::new(t, false)]);
    }
    let mut items: Vec<Source> = Vec::new();
    let mut errors: Vec<String> = Vec::new();
    if t.starts_with('{') || t.starts_with('[') {
        match serde_json::from_str::<Value>(t) {
            Ok(root) => walk(&root, "", None, &mut items, &mut errors),
            Err(_) => errors.push("Файл не удалось прочитать как JSON".into()),
        }
        if items.is_empty() {
            return fail(
                errors
                    .first()
                    .map(|s| s.as_str())
                    .unwrap_or("В файле не найдено серверов или подписок"),
            );
        }
        return ok(distinct(items));
    }
    let mut lines: Vec<String> = t
        .lines()
        .map(|l| l.trim().to_string())
        .filter(|l| !l.is_empty())
        .collect();
    if lines.len() == 1 && !lines[0].contains("://") {
        if let Some(decoded) = decode_base64(&lines[0]) {
            if decoded.contains("://") {
                lines = decoded
                    .lines()
                    .map(|l| l.trim().to_string())
                    .filter(|l| !l.is_empty())
                    .collect();
            }
        }
    }
    for line in &lines {
        if let Some(src) = parse_line(line, &mut errors) {
            items.push(src);
        }
    }
    if items.is_empty() {
        let fallback = links::extract(t);
        if !fallback.is_empty() {
            return ok(fallback
                .iter()
                .map(|s| Source::new(s, s.to_lowercase().starts_with("http")))
                .collect());
        }
        return fail(
            errors
                .first()
                .map(|s| s.as_str())
                .unwrap_or("Не удалось распознать"),
        );
    }
    ok(distinct(items))
}

fn parse_line(line: &str, errors: &mut Vec<String>) -> Option<Source> {
    let lower = line.to_lowercase();
    if is_direct(&lower) {
        return Some(Source::new(line, false));
    }
    if is_http(&lower) {
        return Some(Source::new(line, true));
    }
    if lower.starts_with("sub://") {
        let body = line[6..].split('#').next().unwrap_or("");
        let decoded = decode_base64(body)?;
        if decoded.to_lowercase().starts_with("http") {
            let name = line.split_once('#').map(|(_, n)| n).unwrap_or("");
            return Some(Source::named(decoded.trim(), true, decode_name(name)));
        }
        return None;
    }
    deep(line, errors)
}

fn decode_name(raw: &str) -> String {
    percent_encoding::percent_decode_str(raw)
        .decode_utf8()
        .map(|c| c.trim().to_string())
        .unwrap_or_else(|_| raw.trim().to_string())
}

fn query_params(line: &str) -> HashMap<String, String> {
    let mut map = HashMap::new();
    let without_fragment = line.split('#').next().unwrap_or("");
    if let Some((_, q)) = without_fragment.split_once('?') {
        for pair in q.split('&') {
            if let Some((k, v)) = pair.split_once('=') {
                map.insert(k.to_string(), decode_name(v));
            }
        }
    }
    map
}

fn deep(line: &str, errors: &mut Vec<String>) -> Option<Source> {
    let i = line.find("://").filter(|i| *i > 0)?;
    let scheme = line[..i].to_lowercase();
    if !DEEP.contains(&scheme.as_str()) {
        return None;
    }
    let rest = &line[i + 3..];
    if rest.to_lowercase().starts_with("crypt") {
        errors.push("Зашифрованная ссылка Happ не поддерживается. Попросите обычную ссылку подписки (https://…)".into());
        return None;
    }
    let params = query_params(line);
    let fragment = line.split_once('#').map(|(_, f)| f).unwrap_or("");
    let name = decode_name(
        params
            .get("name")
            .map(|s| s.as_str())
            .filter(|s| !s.is_empty())
            .unwrap_or(fragment),
    );
    let target = match params.get("url") {
        Some(u) => u.clone(),
        None => {
            let mut r = rest.split('#').next().unwrap_or("").to_string();
            if r.contains('/') {
                r = r.splitn(2, '/').nth(1).unwrap_or("").to_string();
            }
            let rl = r.to_lowercase();
            if ["http%", "vless%", "vmess%", "trojan%", "ss%", "tuic%"]
                .iter()
                .any(|p| rl.starts_with(p))
            {
                r = percent_encoding::percent_decode_str(&r)
                    .decode_utf8()
                    .map(|c| c.to_string())
                    .unwrap_or(r);
            }
            r
        }
    };
    let target = target.trim().to_string();
    let lower = target.to_lowercase();
    if is_direct(&lower) {
        Some(Source::new(&target, false))
    } else if is_http(&lower) {
        Some(Source::named(&target, true, name))
    } else {
        None
    }
}

fn decode_base64(raw: &str) -> Option<String> {
    let cleaned: String = raw
        .trim()
        .chars()
        .filter(|c| !c.is_whitespace())
        .map(|c| match c {
            '-' => '+',
            '_' => '/',
            c => c,
        })
        .collect();
    let cleaned = cleaned.trim_end_matches('=');
    base64::engine::general_purpose::STANDARD_NO_PAD
        .decode(cleaned)
        .ok()
        .map(|b| String::from_utf8_lossy(&b).to_string())
}

fn walk(
    node: &Value,
    key: &str,
    sibling: Option<&serde_json::Map<String, Value>>,
    out: &mut Vec<Source>,
    errors: &mut Vec<String>,
) {
    match node {
        Value::Object(map) => {
            if let Some(link) = profile_link(map) {
                out.push(Source::new(&link, false));
                return;
            }
            for (k, v) in map {
                walk(v, k, Some(map), out, errors);
            }
        }
        Value::Array(arr) => {
            for v in arr {
                walk(v, key, sibling, out, errors);
            }
        }
        Value::String(s) => {
            let v = s.trim();
            let lower = v.to_lowercase();
            if is_direct(&lower) {
                out.push(Source::new(v, false));
            } else if is_http(&lower) && URL_KEYS.contains(&key.to_lowercase().as_str()) {
                let mut name = String::new();
                if let Some(sib) = sibling {
                    for nk in NAME_KEYS {
                        if let Some((_, found)) = sib.iter().find(|(k, _)| k.eq_ignore_ascii_case(nk)) {
                            let candidate = found.as_str().unwrap_or("").trim().to_string();
                            if !candidate.is_empty() {
                                name = candidate;
                                break;
                            }
                        }
                    }
                }
                out.push(Source::named(v, true, name));
            } else if let Some(src) = deep(v, errors) {
                out.push(src);
            }
        }
        _ => {}
    }
}

fn enc(v: &str) -> String {
    percent_encoding::utf8_percent_encode(v, percent_encoding::NON_ALPHANUMERIC)
        .to_string()
        .replace("%20", "+")
}

fn profile_link(o: &serde_json::Map<String, Value>) -> Option<String> {
    let mut m: HashMap<String, String> = HashMap::new();
    for (k, v) in o {
        match v {
            Value::String(s) => {
                m.insert(k.to_lowercase(), s.clone());
            }
            Value::Number(n) => {
                m.insert(k.to_lowercase(), n.to_string());
            }
            Value::Bool(b) => {
                m.insert(k.to_lowercase(), b.to_string());
            }
            _ => {}
        }
    }
    let addr = m.get("address")?.clone();
    let port: u32 = m.get("port")?.parse().ok()?;
    let id = m.get("id").or_else(|| m.get("password"))?.clone();
    if addr.trim().is_empty() || id.trim().is_empty() {
        return None;
    }
    let kind: i64 = m
        .get("configtype")
        .and_then(|v| v.parse().ok())
        .unwrap_or(if m.contains_key("alterid") { 1 } else { 0 });
    let blank = |s: &str, d: &str| -> String {
        if s.trim().is_empty() {
            d.to_string()
        } else {
            s.to_string()
        }
    };
    let get = |k: &str| m.get(k).cloned().unwrap_or_default();
    let remarks = blank(
        &m.get("remarks").or_else(|| m.get("ps")).cloned().unwrap_or_else(|| addr.clone()),
        &addr,
    );
    let net = blank(&m.get("network").cloned().unwrap_or_else(|| "tcp".into()), "tcp");
    let tls = m
        .get("streamsecurity")
        .or_else(|| m.get("tls"))
        .cloned()
        .unwrap_or_default();
    let sni = get("sni");
    let host = m
        .get("requesthost")
        .or_else(|| m.get("host"))
        .cloned()
        .unwrap_or_default();
    let path = get("path");
    let header = blank(
        &m.get("headertype")
            .or_else(|| m.get("type"))
            .cloned()
            .unwrap_or_else(|| "none".into()),
        "none",
    );
    let std_b64 = base64::engine::general_purpose::STANDARD;
    match kind {
        1 => {
            let json = serde_json::json!({
                "v": "2",
                "ps": remarks,
                "add": addr,
                "port": port.to_string(),
                "id": id,
                "aid": blank(&m.get("alterid").cloned().unwrap_or_else(|| "0".into()), "0"),
                "scy": blank(&m.get("security").cloned().unwrap_or_else(|| "auto".into()), "auto"),
                "net": net,
                "type": header,
                "host": host,
                "path": path,
                "tls": if tls == "tls" { "tls" } else { "" },
                "sni": sni
            });
            Some(format!("vmess://{}", std_b64.encode(json.to_string())))
        }
        5 => {
            let mut q = vec![
                "encryption=none".to_string(),
                format!("type={}", enc(&net)),
                format!("security={}", enc(&blank(&tls, "none"))),
            ];
            if !sni.trim().is_empty() {
                q.push(format!("sni={}", enc(&sni)));
            }
            if !host.trim().is_empty() {
                q.push(format!("host={}", enc(&host)));
            }
            if !path.trim().is_empty() {
                q.push(format!("path={}", enc(&path)));
            }
            if header != "none" {
                q.push(format!("headerType={}", enc(&header)));
            }
            for (key, param) in [
                ("flow", "flow"),
                ("fingerprint", "fp"),
                ("publickey", "pbk"),
                ("shortid", "sid"),
                ("spiderx", "spx"),
            ] {
                let v = get(key);
                if !v.trim().is_empty() {
                    q.push(format!("{}={}", param, enc(&v)));
                }
            }
            Some(format!(
                "vless://{}@{}:{}?{}#{}",
                id,
                addr,
                port,
                q.join("&"),
                enc(&remarks)
            ))
        }
        6 => {
            let mut q = vec![
                format!("type={}", enc(&net)),
                format!("security={}", enc(&blank(&tls, "tls"))),
            ];
            if !sni.trim().is_empty() {
                q.push(format!("sni={}", enc(&sni)));
            }
            if !host.trim().is_empty() {
                q.push(format!("host={}", enc(&host)));
            }
            if !path.trim().is_empty() {
                q.push(format!("path={}", enc(&path)));
            }
            Some(format!(
                "trojan://{}@{}:{}?{}#{}",
                enc(&id),
                addr,
                port,
                q.join("&"),
                enc(&remarks)
            ))
        }
        3 => {
            let method = blank(
                &m.get("security")
                    .or_else(|| m.get("method"))
                    .cloned()
                    .unwrap_or_else(|| "aes-256-gcm".into()),
                "aes-256-gcm",
            );
            let user = base64::engine::general_purpose::URL_SAFE_NO_PAD
                .encode(format!("{}:{}", method, id));
            Some(format!("ss://{}@{}:{}#{}", user, addr, port, enc(&remarks)))
        }
        _ => None,
    }
}
