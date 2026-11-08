use tunn_backend::model::Server;
use tunn_backend::{importer, links, ping};
use serde_json::{json, Value};

pub fn parse_link(conf: &str) -> Value {
    match links::parse(conf) {
        Ok(p) => json!({"ok": true, "name": p.name, "endpoint": p.endpoint, "outbound": p.outbound}),
        Err(e) => json!({"ok": false, "error": e}),
    }
}

pub fn prepare_import(text: &str) -> Value {
    let p = importer::prepare(text);
    match p.error {
        Some(e) => json!({"ok": false, "error": e}),
        None => json!({
            "ok": true,
            "items": p.items.iter().map(|s| json!({"value": s.value, "url": s.is_url, "name": s.name})).collect::<Vec<_>>()
        }),
    }
}

pub fn fetch_subscription(url: &str) -> Value {
    match links::fetch_meta(url) {
        Ok(f) => {
            let body = links::decode_subscription(&f.body);
            json!({"ok": true, "group": f.group, "body": body})
        }
        Err(e) => json!({"ok": false, "error": e}),
    }
}

pub fn measure_ping(conf: &str, endpoint: &str, mode: i32, timeout_ms: u64) -> Value {
    let server = Server {
        id: String::new(),
        name: String::new(),
        conf: conf.to_string(),
        endpoint: endpoint.to_string(),
        group: String::new(),
    };
    let r = ping::measure(&server, mode.max(0) as usize, timeout_ms);
    json!({"tcp": r.tcp, "udp": r.udp, "icmp": r.icmp, "best": r.best()})
}

pub fn inner_config(outbound: &str, socks: u16, http: u16, dns: &[String]) -> Value {
    match serde_json::from_str::<Value>(outbound) {
        Ok(o) => json!({"ok": true, "config": links::inner_config(o, socks, http, None, dns)}),
        Err(e) => json!({"ok": false, "error": e.to_string()}),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_vless() {
        let v = parse_link("vless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls&type=tcp#Test");
        assert_eq!(v["ok"], true);
        assert_eq!(v["endpoint"], "example.com:443");
        assert_eq!(v["name"], "Test");
    }

    #[test]
    fn rejects_garbage() {
        assert_eq!(parse_link("hello")["ok"], false);
    }

    #[test]
    fn imports_direct_links() {
        let v = prepare_import("vless://11111111-1111-1111-1111-111111111111@example.com:443#A");
        assert_eq!(v["ok"], true);
        assert_eq!(v["items"].as_array().map(|a| a.len()), Some(1));
    }

    #[test]
    fn builds_inner_config() {
        let o = parse_link("vless://11111111-1111-1111-1111-111111111111@example.com:443#A");
        let c = inner_config(&o["outbound"].to_string(), 10808, 10809, &["1.1.1.1".to_string()]);
        assert_eq!(c["ok"], true);
        assert!(c["config"]["inbounds"].is_array());
    }
}
