use base64::engine::general_purpose::{GeneralPurpose, GeneralPurposeConfig};
use base64::engine::DecodePaddingMode;
use base64::{alphabet, Engine};
use serde_json::{json, Map, Value};
use std::collections::HashMap;
use std::net::ToSocketAddrs;
use std::time::Duration;

pub struct Parsed {
    pub name: String,
    pub endpoint: String,
    pub outbound: Value,
}

pub struct Fetched {
    pub body: String,
    pub group: String,
    pub used: u64,
    pub total: u64,
    pub expire: u64,
}

type Res<T> = Result<T, String>;

const LENIENT: GeneralPurpose = GeneralPurpose::new(
    &alphabet::STANDARD,
    GeneralPurposeConfig::new()
        .with_decode_padding_mode(DecodePaddingMode::Indifferent)
        .with_decode_allow_trailing_bits(true),
);

pub fn extract(text: &str) -> Vec<String> {
    let t = text.trim().trim_start_matches('\u{feff}');
    if t.to_lowercase().contains("[interface]") {
        return vec![t.to_string()];
    }
    t.lines()
        .map(|l| l.trim().to_string())
        .filter(|l| !l.is_empty())
        .collect()
}

pub fn parse(raw: &str) -> Res<Parsed> {
    let text = raw.trim();
    if text.to_lowercase().contains("[interface]") {
        return parse_interface(text);
    }
    let end = text.find("://").filter(|i| *i > 0).ok_or("Не удалось распознать ссылку")?;
    match text[..end].to_lowercase().as_str() {
        "vless" => parse_vless(text),
        "vmess" => parse_vmess(text),
        "trojan" => parse_trojan(text),
        "ss" => parse_ss(text),
        "tuic" => parse_tuic(text),
        _ => Err("Неподдерживаемый формат ссылки".into()),
    }
}

pub fn protocol(raw: &str) -> String {
    parse(raw)
        .ok()
        .and_then(|p| p.outbound.get("protocol").and_then(|v| v.as_str()).map(|s| s.to_string()))
        .unwrap_or_default()
}

pub fn inner_config(
    mut outbound: Value,
    socks: u16,
    http: u16,
    api: Option<u16>,
    dns: &[String],
) -> Value {
    outbound["tag"] = json!("proxy");
    let mut inbounds = vec![
        json!({
            "tag": "in-socks",
            "listen": "127.0.0.1",
            "port": socks,
            "protocol": "socks",
            "settings": {"auth": "noauth", "udp": true, "ip": "127.0.0.1"},
            "sniffing": {"enabled": true, "destOverride": ["http", "tls", "quic"]}
        }),
        json!({
            "tag": "in-http",
            "listen": "127.0.0.1",
            "port": http,
            "protocol": "http",
            "settings": {}
        }),
    ];
    let mut root = json!({
        "log": {"loglevel": "warning"},
        "outbounds": [
            outbound,
            {"tag": "direct", "protocol": "freedom"},
            {"tag": "block", "protocol": "blackhole"}
        ]
    });
    if !dns.is_empty() {
        root["dns"] = json!({"servers": dns});
    }
    if let Some(port) = api {
        inbounds.push(json!({
            "tag": "api",
            "listen": "127.0.0.1",
            "port": port,
            "protocol": "dokodemo-door",
            "settings": {"address": "127.0.0.1"}
        }));
        root["stats"] = json!({});
        root["api"] = json!({"tag": "api", "services": ["StatsService"]});
        root["policy"] = json!({"system": {
            "statsOutboundUplink": true,
            "statsOutboundDownlink": true
        }});
        root["routing"] = json!({"rules": [
            {"type": "field", "inboundTag": ["api"], "outboundTag": "api"}
        ]});
    }
    root["inbounds"] = Value::Array(inbounds);
    root
}

pub fn probe_config(mut outbound: Value, socks: u16) -> Value {
    outbound["tag"] = json!("proxy");
    json!({
        "log": {"loglevel": "none"},
        "inbounds": [{
            "tag": "in",
            "listen": "127.0.0.1",
            "port": socks,
            "protocol": "socks",
            "settings": {"auth": "noauth", "udp": true, "ip": "127.0.0.1"}
        }],
        "outbounds": [outbound, {"tag": "direct", "protocol": "freedom"}]
    })
}

fn ip(host: &str) -> String {
    (host, 0u16)
        .to_socket_addrs()
        .ok()
        .and_then(|mut it| it.next())
        .map(|a| a.ip().to_string())
        .unwrap_or_else(|| host.to_string())
}

pub fn resolve(outbound: &mut Value) {
    let Some(settings) = outbound.get_mut("settings") else {
        return;
    };
    for key in ["vnext", "servers"] {
        if let Some(arr) = settings.get_mut(key).and_then(|v| v.as_array_mut()) {
            for o in arr {
                let host = o.get("address").and_then(|v| v.as_str()).unwrap_or("").to_string();
                if !host.is_empty() {
                    o["address"] = json!(ip(&host));
                }
            }
        }
    }
    if let Some(peers) = settings.get_mut("peers").and_then(|v| v.as_array_mut()) {
        for p in peers {
            let ep = p.get("endpoint").and_then(|v| v.as_str()).unwrap_or("").to_string();
            if ep.is_empty() {
                continue;
            }
            if let Ok((host, port)) = host_port(&ep) {
                let addr = ip(&host);
                p["endpoint"] = if addr.contains(':') {
                    json!(format!("[{}]:{}", addr, port))
                } else {
                    json!(format!("{}:{}", addr, port))
                };
            }
        }
    }
}

pub fn fetch_meta(url: &str) -> Res<Fetched> {
    let agent: ureq::Agent = ureq::Agent::config_builder()
        .timeout_global(Some(Duration::from_secs(20)))
        .timeout_connect(Some(Duration::from_secs(10)))
        .http_status_as_error(false)
        .build()
        .into();
    let mut resp = agent
        .get(url)
        .header("User-Agent", "FasdeqVPN/1.0")
        .call()
        .map_err(|e| e.to_string())?;
    let code = resp.status().as_u16();
    if !(200..300).contains(&code) {
        return Err(format!("HTTP {}", code));
    }
    let header = |name: &str| -> String {
        resp.headers()
            .get(name)
            .and_then(|v| v.to_str().ok())
            .unwrap_or("")
            .to_string()
    };
    let mut title = header("profile-title");
    let content_disposition = header("content-disposition");
    let (used, total, expire) = userinfo(&header("subscription-userinfo"));
    let body = resp.body_mut().read_to_string().map_err(|e| e.to_string())?;
    if title.to_lowercase().starts_with("base64:") {
        let rest = &title[7..];
        title = match b64(rest).map(|b| String::from_utf8_lossy(&b).to_string()) {
            Ok(v) => v,
            Err(_) => rest.to_string(),
        };
    }
    if title.trim().is_empty() {
        if let Some(name) = filename(&content_disposition) {
            let decoded = decode(&name);
            title = match decoded.rfind('.') {
                Some(i) => decoded[..i].to_string(),
                None => decoded,
            };
        }
    }
    if title.trim().is_empty() {
        for line in decode_subscription(&body).lines() {
            let l = line.trim();
            if let Some(rest) = l.strip_prefix('#') {
                let rest = rest.trim();
                if rest.to_lowercase().starts_with("profile-title") {
                    if let Some(i) = rest.find(':') {
                        title = rest[i + 1..].trim().to_string();
                        break;
                    }
                }
            }
        }
    }
    if title.trim().is_empty() {
        title = url_host(url);
    }
    Ok(Fetched {
        body,
        group: decode(&title).trim().to_string(),
        used,
        total,
        expire,
    })
}

fn userinfo(raw: &str) -> (u64, u64, u64) {
    let (mut up, mut down, mut total, mut expire) = (0u64, 0u64, 0u64, 0u64);
    for part in raw.split(';') {
        let Some((k, v)) = part.split_once('=') else { continue };
        let v = v.trim().parse::<f64>().unwrap_or(0.0).max(0.0) as u64;
        match k.trim().to_lowercase().as_str() {
            "upload" => up = v,
            "download" => down = v,
            "total" => total = v,
            "expire" => expire = v,
            _ => {}
        }
    }
    (up + down, total, expire)
}

fn filename(cd: &str) -> Option<String> {
    let lower = cd.to_lowercase();
    let i = lower.find("filename")?;
    let rest = &cd[i + 8..];
    let rest = rest.trim_start_matches('*').trim_start();
    let rest = rest.strip_prefix('=')?.trim_start();
    let rest = rest
        .strip_prefix("UTF-8''")
        .or_else(|| rest.strip_prefix("utf-8''"))
        .unwrap_or(rest);
    let rest = rest.trim_start_matches('"');
    let end = rest.find(|c| c == '"' || c == ';').unwrap_or(rest.len());
    let value = &rest[..end];
    if value.is_empty() {
        None
    } else {
        Some(value.to_string())
    }
}

pub fn url_host(url: &str) -> String {
    let rest = url.split("://").nth(1).unwrap_or(url);
    let end = rest.find(|c| c == '/' || c == '?' || c == '#').unwrap_or(rest.len());
    let hp = &rest[..end];
    let hp = hp.rsplit('@').next().unwrap_or(hp);
    if hp.starts_with('[') {
        return hp.to_string();
    }
    hp.split(':').next().unwrap_or(hp).to_string()
}

pub fn decode_subscription(body: &str) -> String {
    let t = body.trim();
    if t.contains("://") {
        return t.to_string();
    }
    match b64(t) {
        Ok(bytes) => String::from_utf8_lossy(&bytes).to_string(),
        Err(_) => t.to_string(),
    }
}

struct Link {
    user_info: String,
    host: String,
    port: u16,
    params: HashMap<String, String>,
    fragment: String,
}

pub fn decode(s: &str) -> String {
    let replaced = s.replace('+', "%2B");
    percent_encoding::percent_decode_str(&replaced)
        .decode_utf8()
        .map(|c| c.to_string())
        .unwrap_or_else(|_| s.to_string())
}

pub fn b64(s: &str) -> Res<Vec<u8>> {
    let n: String = s
        .chars()
        .filter(|c| !c.is_whitespace())
        .map(|c| match c {
            '-' => '+',
            '_' => '/',
            c => c,
        })
        .collect();
    let n = n.trim_end_matches('=');
    LENIENT.decode(n).map_err(|_| "Неверный формат ссылки".to_string())
}

pub fn host_port(value: &str) -> Res<(String, u16)> {
    let hp = value.trim_end_matches('/');
    let host;
    let port: u32;
    if hp.starts_with('[') {
        let e = hp.find(']').filter(|e| *e > 0).ok_or("Неверный адрес сервера")?;
        host = hp[1..e].to_string();
        port = hp[e + 1..]
            .trim_start_matches(':')
            .parse::<u32>()
            .unwrap_or(443);
    } else {
        let c = hp.rfind(':').filter(|c| *c > 0).ok_or("В ссылке нет порта")?;
        host = hp[..c].to_string();
        port = hp[c + 1..].parse::<u32>().map_err(|_| "Неверный порт".to_string())?;
    }
    if host.is_empty() || !(1..=65535).contains(&port) {
        return Err("Неверный адрес сервера".into());
    }
    Ok((host, port as u16))
}

fn split(text: &str) -> Res<Link> {
    let mut rest = text[text.find("://").unwrap_or(0) + 3..].to_string();
    let mut fragment = String::new();
    if let Some(hash) = rest.find('#') {
        fragment = decode(&rest[hash + 1..]);
        rest.truncate(hash);
    }
    let mut query = String::new();
    if let Some(q) = rest.find('?') {
        query = rest[q + 1..].to_string();
        rest.truncate(q);
    }
    let at = rest.rfind('@');
    let user_info = at.map(|a| rest[..a].to_string()).unwrap_or_default();
    let hp = host_port(&rest[at.map(|a| a + 1).unwrap_or(0)..])?;
    let mut params = HashMap::new();
    for pair in query.split('&') {
        if let Some(i) = pair.find('=') {
            if i > 0 {
                params.insert(pair[..i].to_string(), decode(&pair[i + 1..]));
            }
        }
    }
    Ok(Link {
        user_info,
        host: hp.0,
        port: hp.1,
        params,
        fragment,
    })
}

fn list(value: &str) -> Vec<String> {
    value
        .split(',')
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
        .collect()
}

fn get<'a>(p: &'a HashMap<String, String>, k: &str) -> &'a str {
    p.get(k).map(|s| s.as_str()).unwrap_or("")
}

fn stream(p: &HashMap<String, String>, host: &str, force_tls: bool) -> Res<Value> {
    let mut net = {
        let t = get(p, "type").to_lowercase();
        if t.is_empty() {
            "tcp".to_string()
        } else {
            t
        }
    };
    if net == "h2" {
        net = "http".into();
    }
    if net == "splithttp" {
        net = "xhttp".into();
    }
    let sec = {
        let t = p
            .get("security")
            .cloned()
            .unwrap_or_else(|| if force_tls { "tls".into() } else { "none".into() })
            .to_lowercase();
        if t.is_empty() {
            "none".to_string()
        } else {
            t
        }
    };
    let mut s = json!({"network": net, "security": sec});
    let path = {
        let t = get(p, "path");
        if t.is_empty() {
            "/".to_string()
        } else {
            t.to_string()
        }
    };
    let host_header = get(p, "host").to_string();
    match net.as_str() {
        "ws" => {
            let mut headers = Map::new();
            if !host_header.is_empty() {
                headers.insert("Host".into(), json!(host_header));
            }
            s["wsSettings"] = json!({"path": path, "headers": headers});
        }
        "grpc" => {
            let name = p
                .get("serviceName")
                .cloned()
                .unwrap_or_else(|| get(p, "path").to_string());
            s["grpcSettings"] = json!({"serviceName": name, "multiMode": get(p, "mode") == "multi"});
        }
        "http" => {
            s["httpSettings"] = json!({"path": path, "host": list(&host_header)});
        }
        "httpupgrade" => {
            s["httpupgradeSettings"] = json!({"path": path, "host": host_header});
        }
        "xhttp" => {
            let mut x = json!({"path": path, "host": host_header});
            let mode = get(p, "mode");
            if !mode.is_empty() {
                x["mode"] = json!(mode);
            }
            let extra = get(p, "extra");
            if !extra.is_empty() {
                if let Ok(v) = serde_json::from_str::<Value>(extra) {
                    x["extra"] = v;
                }
            }
            s["xhttpSettings"] = x;
        }
        "kcp" => {
            let header_type = p.get("headerType").cloned().unwrap_or_else(|| "none".into());
            let mut k = json!({"header": {"type": header_type}});
            let seed = get(p, "seed");
            if !seed.is_empty() {
                k["seed"] = json!(seed);
            }
            s["kcpSettings"] = k;
        }
        "tcp" => {
            if get(p, "headerType") == "http" {
                let request = json!({
                    "path": [path],
                    "headers": {"Host": list(&host_header)}
                });
                s["tcpSettings"] = json!({"header": {"type": "http", "request": request}});
            }
        }
        _ => {}
    }
    let sni = [get(p, "sni"), get(p, "peer"), host_header.as_str(), host]
        .into_iter()
        .find(|v| !v.is_empty())
        .unwrap_or("")
        .to_string();
    let fp = get(p, "fp");
    if sec == "tls" {
        let mut t = json!({"serverName": sni});
        if !fp.is_empty() {
            t["fingerprint"] = json!(fp);
        }
        let alpn = list(get(p, "alpn"));
        if !alpn.is_empty() {
            t["alpn"] = json!(alpn);
        }
        let insecure = p
            .get("allowInsecure")
            .or_else(|| p.get("insecure"))
            .map(|s| s.as_str())
            .unwrap_or("0");
        if insecure == "1" || insecure == "true" {
            t["allowInsecure"] = json!(true);
        }
        s["tlsSettings"] = t;
    } else if sec == "reality" {
        let key = get(p, "pbk");
        if key.is_empty() {
            return Err("В ссылке нет публичного ключа".into());
        }
        s["realitySettings"] = json!({
            "serverName": sni,
            "fingerprint": if fp.is_empty() { "chrome" } else { fp },
            "publicKey": key,
            "shortId": get(p, "sid"),
            "spiderX": get(p, "spx")
        });
    }
    Ok(s)
}

fn nonempty(s: String, fallback: &str) -> String {
    if s.is_empty() {
        fallback.to_string()
    } else {
        s
    }
}

fn parse_vless(text: &str) -> Res<Parsed> {
    let l = split(text)?;
    let id = decode(&l.user_info);
    if id.is_empty() {
        return Err("В ссылке нет идентификатора".into());
    }
    let encryption = nonempty(get(&l.params, "encryption").to_string(), "none");
    let mut user = json!({"id": id, "encryption": encryption, "level": 0});
    let flow = get(&l.params, "flow");
    if !flow.is_empty() {
        user["flow"] = json!(flow);
    }
    let server = json!({"address": l.host, "port": l.port, "users": [user]});
    let out = json!({
        "protocol": "vless",
        "settings": {"vnext": [server]},
        "streamSettings": stream(&l.params, &l.host, false)?
    });
    Ok(Parsed {
        name: nonempty(l.fragment.clone(), &l.host),
        endpoint: format!("{}:{}", l.host, l.port),
        outbound: out,
    })
}

fn parse_tuic(text: &str) -> Res<Parsed> {
    let l = split(text)?;
    let info = decode(&l.user_info);
    let (uuid, password) = match info.split_once(':') {
        Some((u, p)) => (u.to_string(), p.to_string()),
        None => (info.clone(), String::new()),
    };
    if uuid.is_empty() {
        return Err("В ссылке нет идентификатора".into());
    }
    let sni = nonempty(get(&l.params, "sni").to_string(), &l.host);
    let mut alpn: Vec<String> = get(&l.params, "alpn")
        .split(',')
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
        .collect();
    if alpn.is_empty() {
        alpn.push("h3".into());
    }
    let flag = |k: &str| matches!(get(&l.params, k), "1" | "true");
    let insecure = flag("allow_insecure") || flag("insecure") || flag("allowInsecure");
    let server = json!({"address": l.host, "port": l.port, "uuid": uuid, "password": password});
    let out = json!({
        "protocol": "tuic",
        "settings": {"servers": [server]},
        "streamSettings": {
            "network": "udp",
            "security": "tls",
            "tlsSettings": {"serverName": sni, "alpn": alpn, "allowInsecure": insecure}
        },
        "tuic": {
            "congestion_control": nonempty(get(&l.params, "congestion_control").to_string(), "bbr"),
            "udp_relay_mode": nonempty(get(&l.params, "udp_relay_mode").to_string(), "native"),
            "zero_rtt": flag("zero_rtt") || flag("reduce_rtt")
        }
    });
    Ok(Parsed {
        name: nonempty(l.fragment.clone(), &l.host),
        endpoint: format!("{}:{}", l.host, l.port),
        outbound: out,
    })
}

fn parse_trojan(text: &str) -> Res<Parsed> {
    let l = split(text)?;
    let password = decode(&l.user_info);
    if password.is_empty() {
        return Err("В ссылке нет пароля".into());
    }
    let server = json!({"address": l.host, "port": l.port, "password": password, "level": 0});
    let out = json!({
        "protocol": "trojan",
        "settings": {"servers": [server]},
        "streamSettings": stream(&l.params, &l.host, true)?
    });
    Ok(Parsed {
        name: nonempty(l.fragment.clone(), &l.host),
        endpoint: format!("{}:{}", l.host, l.port),
        outbound: out,
    })
}

fn js(v: &Value, k: &str) -> String {
    match v.get(k) {
        Some(Value::String(s)) => s.clone(),
        Some(Value::Number(n)) => n.to_string(),
        Some(Value::Bool(b)) => b.to_string(),
        _ => String::new(),
    }
}

fn parse_vmess(text: &str) -> Res<Parsed> {
    let body = text[8..].split('#').next().unwrap_or("").trim();
    let json: Value = b64(body)
        .ok()
        .and_then(|b| serde_json::from_slice(&b).ok())
        .ok_or("Неверная ссылка")?;
    let host = js(&json, "add");
    let port: u32 = js(&json, "port").parse().unwrap_or(0);
    let id = js(&json, "id");
    if host.is_empty() || !(1..=65535).contains(&port) || id.is_empty() {
        return Err("В ссылке не хватает данных".into());
    }
    let net = nonempty(js(&json, "net"), "tcp");
    let mut p: HashMap<String, String> = HashMap::new();
    p.insert("type".into(), net.clone());
    p.insert("headerType".into(), js(&json, "type"));
    p.insert("host".into(), js(&json, "host"));
    p.insert("path".into(), js(&json, "path"));
    if net == "grpc" {
        p.insert("serviceName".into(), js(&json, "path"));
        p.insert("mode".into(), js(&json, "type"));
    }
    p.insert(
        "security".into(),
        if js(&json, "tls").to_lowercase() == "tls" {
            "tls".into()
        } else {
            "none".into()
        },
    );
    p.insert("sni".into(), js(&json, "sni"));
    p.insert("fp".into(), js(&json, "fp"));
    p.insert("alpn".into(), js(&json, "alpn"));
    let user = json!({
        "id": id,
        "alterId": js(&json, "aid").parse::<i64>().unwrap_or(0),
        "security": nonempty(js(&json, "scy"), "auto"),
        "level": 0
    });
    let server = json!({"address": host, "port": port, "users": [user]});
    let out = json!({
        "protocol": "vmess",
        "settings": {"vnext": [server]},
        "streamSettings": stream(&p, &host, false)?
    });
    Ok(Parsed {
        name: nonempty(js(&json, "ps"), &host),
        endpoint: format!("{}:{}", host, port),
        outbound: out,
    })
}

fn parse_ss(text: &str) -> Res<Parsed> {
    let mut rest = text[5..].to_string();
    let mut name = String::new();
    if let Some(hash) = rest.find('#') {
        name = decode(&rest[hash + 1..]);
        rest.truncate(hash);
    }
    if let Some(q) = rest.find('?') {
        if rest[q + 1..].contains("plugin=") {
            return Err("Плагины в ссылках не поддерживаются".into());
        }
        rest.truncate(q);
    }
    let rest = rest.trim_end_matches('/').to_string();
    let cred;
    let target;
    if let Some(at) = rest.rfind('@') {
        let ui = &rest[..at];
        cred = if ui.contains(':') {
            decode(ui)
        } else {
            String::from_utf8_lossy(&b64(&decode(ui))?).to_string()
        };
        target = rest[at + 1..].to_string();
    } else {
        let plain = String::from_utf8_lossy(&b64(&rest)?).to_string();
        let i = plain.rfind('@').filter(|i| *i > 0).ok_or("Неверная ссылка")?;
        cred = plain[..i].to_string();
        target = plain[i + 1..].to_string();
    }
    let c = cred.find(':').filter(|c| *c > 0).ok_or("Неверная ссылка")?;
    let hp = host_port(&target)?;
    let server = json!({
        "address": hp.0,
        "port": hp.1,
        "method": &cred[..c],
        "password": &cred[c + 1..],
        "level": 0
    });
    let out = json!({"protocol": "shadowsocks", "settings": {"servers": [server]}});
    Ok(Parsed {
        name: nonempty(name, &hp.0),
        endpoint: format!("{}:{}", hp.0, hp.1),
        outbound: out,
    })
}

fn parse_interface(text: &str) -> Res<Parsed> {
    let mut section = String::new();
    let mut iface: HashMap<String, String> = HashMap::new();
    let mut peers: Vec<HashMap<String, String>> = Vec::new();
    for line in text.lines() {
        let t = line.split('#').next().unwrap_or("").trim();
        if t.is_empty() {
            continue;
        }
        if t.starts_with('[') {
            section = t.to_lowercase();
            if section == "[peer]" {
                peers.push(HashMap::new());
            }
            continue;
        }
        let Some(i) = t.find('=').filter(|i| *i > 0) else {
            continue;
        };
        let k = t[..i].trim().to_lowercase();
        let v = t[i + 1..].trim().to_string();
        if section == "[interface]" {
            iface.insert(k, v);
        } else if section == "[peer]" {
            if let Some(last) = peers.last_mut() {
                last.insert(k, v);
            }
        }
    }
    let key = iface.get("privatekey").cloned().unwrap_or_default();
    if key.is_empty() {
        return Err("В конфигурации нет приватного ключа".into());
    }
    let addresses = list(iface.get("address").map(|s| s.as_str()).unwrap_or(""));
    if addresses.is_empty() {
        return Err("В конфигурации нет адреса".into());
    }
    if peers.is_empty() {
        return Err("В конфигурации нет сервера".into());
    }
    let mut peer_array = Vec::new();
    let mut endpoint = String::new();
    for p in &peers {
        let pub_key = get(p, "publickey");
        let ep = get(p, "endpoint");
        if pub_key.is_empty() || ep.is_empty() {
            return Err("В конфигурации не хватает данных сервера".into());
        }
        if endpoint.is_empty() {
            endpoint = ep.to_string();
        }
        let mut o = json!({"publicKey": pub_key, "endpoint": ep});
        let psk = get(p, "presharedkey");
        if !psk.is_empty() {
            o["preSharedKey"] = json!(psk);
        }
        let ka = get(p, "persistentkeepalive").parse::<i64>().unwrap_or(0);
        o["keepAlive"] = json!(if (1..=60).contains(&ka) { ka } else { 25 });
        let mut allowed = list(get(p, "allowedips"));
        if allowed.is_empty() {
            allowed = vec!["0.0.0.0/0".into(), "::/0".into()];
        }
        o["allowedIPs"] = json!(allowed);
        peer_array.push(o);
    }
    let settings = json!({
        "secretKey": key,
        "address": addresses,
        "peers": peer_array,
        "mtu": iface.get("mtu").and_then(|v| v.parse::<i64>().ok()).unwrap_or(1280).clamp(1200, 1420),
        "noKernelTun": true
    });
    Ok(Parsed {
        name: String::new(),
        endpoint,
        outbound: json!({"protocol": "wireguard", "settings": settings}),
    })
}

#[cfg(test)]
mod tuic_tests {
    use super::*;

    #[test]
    fn parses_tuic() {
        let p = parse("tuic://11111111-1111-1111-1111-111111111111:pa%40ss@example.com:443?congestion_control=bbr&alpn=h3&sni=cdn.example.com&allow_insecure=1#My%20TUIC").unwrap();
        assert_eq!(p.name, "My TUIC");
        assert_eq!(p.endpoint, "example.com:443");
        assert_eq!(p.outbound["protocol"], "tuic");
        assert_eq!(p.outbound["settings"]["servers"][0]["password"], "pa@ss");
        assert_eq!(p.outbound["streamSettings"]["tlsSettings"]["serverName"], "cdn.example.com");
        assert_eq!(p.outbound["streamSettings"]["tlsSettings"]["allowInsecure"], true);
        assert_eq!(protocol("tuic://u:p@h:1"), "tuic");
    }
}
