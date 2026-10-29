use std::process::Command;

#[derive(Clone, Debug, PartialEq)]
pub struct Found {
    pub pid: u32,
    pub label: String,
}

const KNOWN: &[(&str, &str)] = &[
    ("happ", "Happ"),
    ("happ-desktop", "Happ"),
    ("v2raytun", "v2RayTun"),
    ("winws", "zapret"),
    ("nfqws", "zapret"),
    ("tpws", "zapret"),
    ("dvtws", "zapret"),
    ("zapret", "zapret"),
    ("goodbyedpi", "GoodbyeDPI"),
    ("byedpi", "ByeDPI"),
    ("ciadpi", "ByeDPI"),
    ("spoofdpi", "SpoofDPI"),
    ("hiddify", "Hiddify"),
    ("hiddifycli", "Hiddify"),
    ("v2rayn", "v2rayN"),
    ("clash", "Clash"),
    ("clash-verge", "Clash Verge"),
    ("mihomo", "Mihomo"),
    ("verge-mihomo", "Clash Verge"),
    ("flclash", "FlClash"),
    ("flclashcore", "FlClash"),
    ("nekoray", "NekoRay"),
    ("nekobox", "NekoBox"),
    ("throne", "Throne"),
    ("sing-box", "sing-box"),
    ("amneziavpn", "AmneziaVPN"),
    ("amneziawg", "AmneziaWG"),
    ("wireguard", "WireGuard"),
    ("openvpn", "OpenVPN"),
    ("openvpn-gui", "OpenVPN"),
    ("psiphon3", "Psiphon"),
    ("cloudflare warp", "Cloudflare WARP"),
    ("warp-svc", "Cloudflare WARP"),
];

fn label_of(name: &str) -> Option<&'static str> {
    let mut n = name.trim().to_lowercase();
    if let Some(i) = n.rfind(['/', '\\']) {
        n = n[i + 1..].to_string();
    }
    if let Some(s) = n.strip_suffix(".exe") {
        n = s.to_string();
    }
    KNOWN.iter().find(|(k, _)| *k == n).map(|(_, l)| *l)
}

fn command(program: &str) -> Command {
    let mut c = Command::new(program);
    fasdeq_core::proc::hide_window(&mut c);
    c
}

fn listing() -> Vec<(u32, String)> {
    let mut out = Vec::new();
    if cfg!(target_os = "windows") {
        let Ok(o) = command("tasklist").args(["/FO", "CSV", "/NH"]).output() else {
            return out;
        };
        for line in String::from_utf8_lossy(&o.stdout).lines() {
            let parts: Vec<&str> = line.split("\",\"").collect();
            if parts.len() < 2 {
                continue;
            }
            let name = parts[0].trim_matches('"').to_string();
            if let Ok(pid) = parts[1].trim_matches('"').parse::<u32>() {
                out.push((pid, name));
            }
        }
    } else {
        let Ok(o) = command("ps").args(["-axo", "pid=,stat=,comm="]).output() else {
            return out;
        };
        for line in String::from_utf8_lossy(&o.stdout).lines() {
            let l = line.trim_start();
            let mut it = l.splitn(3, char::is_whitespace);
            let (Some(pid), Some(stat)) = (it.next(), it.next()) else {
                continue;
            };
            let name = it.next().unwrap_or("").trim().to_string();
            if stat.starts_with('Z') {
                continue;
            }
            if let Ok(pid) = pid.parse::<u32>() {
                out.push((pid, name));
            }
        }
    }
    out
}

pub fn running() -> Vec<Found> {
    let me = std::process::id();
    let mut out: Vec<Found> = listing()
        .into_iter()
        .filter(|(pid, _)| *pid != me)
        .filter_map(|(pid, name)| label_of(&name).map(|l| Found { pid, label: l.to_string() }))
        .collect();
    out.sort_by(|a, b| a.label.cmp(&b.label).then(a.pid.cmp(&b.pid)));
    out
}

pub fn labels(list: &[Found]) -> Vec<String> {
    let mut v: Vec<String> = Vec::new();
    for f in list {
        if !v.contains(&f.label) {
            v.push(f.label.clone());
        }
    }
    v
}

pub fn kill(list: &[Found]) -> Vec<Found> {
    for f in list {
        if cfg!(target_os = "windows") {
            let _ = command("taskkill").args(["/F", "/T", "/PID", &f.pid.to_string()]).output();
        } else {
            let _ = command("kill").args(["-TERM", &f.pid.to_string()]).output();
        }
    }
    std::thread::sleep(std::time::Duration::from_millis(700));
    let mut left = running();
    left.retain(|r| list.iter().any(|f| f.pid == r.pid));
    if !cfg!(target_os = "windows") && !left.is_empty() {
        for f in &left {
            let _ = command("kill").args(["-KILL", &f.pid.to_string()]).output();
        }
        std::thread::sleep(std::time::Duration::from_millis(400));
        left = running();
        left.retain(|r| list.iter().any(|f| f.pid == r.pid));
    }
    left
}
