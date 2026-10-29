use crate::host::{Host, UiCmd};
use serde_json::Value;
use std::sync::Arc;
use std::time::Duration;
use tiny_http::{Header, Method, Request, Response, Server};

struct Asset {
    path: &'static str,
    mime: &'static str,
    data: &'static [u8],
}

const ASSETS: &[Asset] = &[
    Asset { path: "index.html", mime: "text/html; charset=utf-8", data: include_bytes!("../../web/index.html") },
    Asset { path: "app.js", mime: "text/javascript; charset=utf-8", data: include_bytes!("../../web/app.js") },
    Asset { path: "bridge.js", mime: "text/javascript; charset=utf-8", data: include_bytes!("../../web/bridge.js") },
    Asset { path: "style.css", mime: "text/css; charset=utf-8", data: include_bytes!("../../web/style.css") },
    Asset { path: "fonts/Roboto-Regular.ttf", mime: "font/ttf", data: include_bytes!("../../web/fonts/Roboto-Regular.ttf") },
    Asset { path: "fonts/Roboto-Medium.ttf", mime: "font/ttf", data: include_bytes!("../../web/fonts/Roboto-Medium.ttf") },
    Asset { path: "fonts/Roboto-Bold.ttf", mime: "font/ttf", data: include_bytes!("../../web/fonts/Roboto-Bold.ttf") },
];

fn header(name: &str, value: &str) -> Header {
    Header::from_bytes(name.as_bytes(), value.as_bytes()).unwrap()
}

fn asset(path: &str) -> Option<(Vec<u8>, &'static str)> {
    let path = if path.is_empty() { "index.html" } else { path };
    if let Ok(dir) = std::env::var("FASDEQ_WEB") {
        if !path.contains("..") {
            if let Ok(data) = std::fs::read(std::path::Path::new(&dir).join(path)) {
                let mime = ASSETS.iter().find(|a| a.path == path).map(|a| a.mime).unwrap_or("application/octet-stream");
                return Some((data, mime));
            }
        }
    }
    ASSETS.iter().find(|a| a.path == path).map(|a| (a.data.to_vec(), a.mime))
}

fn query(url: &str, key: &str) -> Option<String> {
    let q = url.split_once('?')?.1;
    for part in q.split('&') {
        if let Some((k, v)) = part.split_once('=') {
            if k == key {
                return Some(v.to_string());
            }
        }
    }
    None
}

fn cookie_ok(rq: &Request, token: &str) -> bool {
    for h in rq.headers() {
        if h.field.equiv("Cookie") {
            for part in h.value.as_str().split(';') {
                if part.trim() == format!("fq={}", token) {
                    return true;
                }
            }
        }
    }
    false
}

fn host_ok(rq: &Request, port: u16) -> bool {
    for h in rq.headers() {
        if h.field.equiv("Host") {
            let v = h.value.as_str();
            return v == format!("127.0.0.1:{}", port) || v == format!("localhost:{}", port);
        }
    }
    false
}

fn json_response(v: String) -> Response<std::io::Cursor<Vec<u8>>> {
    Response::from_string(v).with_header(header("Content-Type", "application/json; charset=utf-8")).with_header(header("Cache-Control", "no-store"))
}

fn handle(host: Arc<Host>, port: u16, mut rq: Request) {
    if !host_ok(&rq, port) {
        let _ = rq.respond(Response::from_string("bad host").with_status_code(403));
        return;
    }
    let url = rq.url().to_string();
    let path = url.split('?').next().unwrap_or("/").to_string();
    let method = rq.method().clone();
    if path == "/__show" {
        if query(&url, "k").as_deref() == Some(host.token.as_str()) {
            host.ui(UiCmd::Show);
            let _ = rq.respond(Response::from_string("ok"));
        } else {
            let _ = rq.respond(Response::from_string("denied").with_status_code(403));
        }
        return;
    }
    if let Some(k) = query(&url, "k") {
        if k == host.token {
            let resp = Response::from_string("")
                .with_status_code(302)
                .with_header(header("Location", "/"))
                .with_header(header("Set-Cookie", &format!("fq={}; Path=/; HttpOnly; SameSite=Strict", host.token)));
            let _ = rq.respond(resp);
            return;
        }
    }
    if !cookie_ok(&rq, &host.token) {
        let _ = rq.respond(Response::from_string("FasdeqVPN").with_status_code(403));
        return;
    }
    match (&method, path.as_str()) {
        (Method::Get, "/api/state") => {
            let _ = rq.respond(json_response(host.snapshot().to_string()));
        }
        (Method::Get, "/api/poll") => {
            let since: u64 = query(&url, "since").and_then(|s| s.parse().ok()).unwrap_or(0);
            let (seq, events) = host.poll(since, Duration::from_secs(20));
            let mut out = String::from("{\"seq\":");
            out.push_str(&seq.to_string());
            out.push_str(",\"ev\":[");
            for (i, (k, d)) in events.iter().enumerate() {
                if i > 0 {
                    out.push(',');
                }
                out.push_str(&format!("{{\"k\":\"{}\",\"d\":{}}}", k, d));
            }
            out.push_str("]}");
            let _ = rq.respond(json_response(out));
        }
        (Method::Post, "/api/act") => {
            let mut body = String::new();
            let _ = rq.as_reader().read_to_string(&mut body);
            let v: Value = serde_json::from_str(&body).unwrap_or(Value::Null);
            let name = v["name"].as_str().unwrap_or("").to_string();
            let payload = v.get("payload").cloned().unwrap_or(Value::Null);
            let r = host.act(&name, &payload);
            let _ = rq.respond(json_response(r.to_string()));
        }
        (Method::Get, p) => {
            let rel = p.trim_start_matches('/');
            match asset(rel) {
                Some((mut data, mime)) => {
                    if rel.is_empty() || rel == "index.html" {
                        let s = String::from_utf8_lossy(&data).replace("<script src=\"bridge.js\"></script>", "<script>window.__FQHTTP=1;</script>\n<script src=\"bridge.js\"></script>");
                        data = s.into_bytes();
                    }
                    let cache = if mime.starts_with("font") { "max-age=86400" } else { "no-store" };
                    let _ = rq.respond(Response::from_data(data).with_header(header("Content-Type", mime)).with_header(header("Cache-Control", cache)));
                }
                None => {
                    let _ = rq.respond(Response::from_string("not found").with_status_code(404));
                }
            }
        }
        _ => {
            let _ = rq.respond(Response::from_string("no").with_status_code(405));
        }
    }
}

pub fn start(host: Arc<Host>) -> Result<u16, String> {
    let server = Server::http("127.0.0.1:0").map_err(|e| e.to_string())?;
    let port = server.server_addr().to_ip().map(|a| a.port()).ok_or("no port")?;
    std::thread::spawn(move || {
        for rq in server.incoming_requests() {
            let h = host.clone();
            std::thread::spawn(move || handle(h, port, rq));
        }
    });
    Ok(port)
}
