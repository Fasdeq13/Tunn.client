use crate::conflict::{self, Found};
use crate::model::*;
use crate::store::{now_millis, Store};
use crate::xray::{self, Release};
use crate::{importer, links, ping};
use serde_json::{json, Map, Value};
use std::collections::{HashMap, HashSet};
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Condvar, Mutex};
use std::time::{Duration, Instant};

pub const VERSION: &str = "3.2";

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum UiCmd {
    Show,
    Toggle,
    Quit,
}

struct Conn {
    proc: xray::Proc,
    api: u16,
    bin: PathBuf,
}

struct Update {
    available: bool,
    checking: bool,
    tag: String,
    url: String,
    notes: String,
}

struct Inner {
    store: Store,
    status: Status,
    error: Option<String>,
    conn: Option<Conn>,
    active_id: Option<String>,
    pings: HashMap<String, PingResult>,
    checks: HashMap<String, ServiceCheck>,
    checking: bool,
    rx: u64,
    tx: u64,
    down: u64,
    up: u64,
    since: Option<Instant>,
    core_busy: Option<String>,
    releases: Option<Vec<Release>>,
    retries_left: i32,
    update: Update,
    ping_dirty: bool,
}

#[derive(Default)]
struct Events {
    seq: u64,
    latest: HashMap<&'static str, (u64, String)>,
}

pub struct Host {
    inner: Mutex<Inner>,
    ev: Mutex<Events>,
    cv: Condvar,
    ui: Mutex<Option<Box<dyn Fn(UiCmd) + Send>>>,
    import_lock: Mutex<()>,
    pub token: String,
    pub quitting: AtomicBool,
}

fn short_id() -> String {
    format!("{:08x}", rand::random::<u32>())
}

fn proto_of(conf: &str) -> String {
    if conf.to_lowercase().contains("[interface]") {
        return "WireGuard".into();
    }
    match conf.split("://").next().map(|s| s.to_lowercase()).unwrap_or_default().as_str() {
        "vless" => "VLESS".into(),
        "vmess" => "VMess".into(),
        "trojan" => "Trojan".into(),
        "ss" => "Shadowsocks".into(),
        "hysteria2" | "hy2" => "Hysteria2".into(),
        "hysteria" => "Hysteria".into(),
        "tuic" => "TUIC".into(),
        "socks" | "socks5" => "SOCKS".into(),
        _ if !conf.contains("://") => "Профиль".into(),
        other => other.to_uppercase(),
    }
}

fn ms_value(p: Option<&PingResult>) -> Value {
    let best = p.map(|p| p.best()).unwrap_or(i32::MAX);
    if best == i32::MAX || best <= 0 {
        Value::Null
    } else {
        json!(best)
    }
}

fn check_value(c: &ServiceCheck) -> Value {
    json!({"ms": c.ms.iter().map(|v| match v { Some(x) => json!(x), None => Value::Null }).collect::<Vec<_>>(), "busy": c.busy})
}

impl Inner {
    fn best_for(&self, index: usize) -> Option<String> {
        let mut best_id = None;
        let mut best = i32::MAX;
        for (id, check) in &self.checks {
            if let Some(v) = check.ms[index] {
                if v > 0 && v < best {
                    best = v;
                    best_id = Some(id.clone());
                }
            }
        }
        best_id
    }

    fn uptime(&self) -> u64 {
        self.since.map(|s| s.elapsed().as_secs()).unwrap_or(0)
    }

    fn selected_server(&self) -> Option<Server> {
        let id = self.store.selected.as_ref()?;
        self.store.servers.iter().find(|s| &s.id == id).cloned()
    }

    fn ensure_group(&mut self, name: &str, url: &str) {
        match self.store.groups.iter().position(|g| g.name == name) {
            None => self.store.groups.push(Group { name: name.to_string(), url: url.to_string() }),
            Some(i) => {
                if !url.is_empty() && self.store.groups[i].url != url {
                    self.store.groups[i].url = url.to_string();
                }
            }
        }
    }

    fn fix_selection(&mut self) {
        let ok = self
            .store
            .selected
            .as_ref()
            .map(|id| self.store.servers.iter().any(|s| &s.id == id))
            .unwrap_or(false);
        if !ok {
            self.store.selected = self.store.servers.first().map(|s| s.id.clone());
        }
    }
}

struct Item {
    link: String,
    group: String,
    url: String,
    meta: Option<[u64; 3]>,
}

fn stop_conn(conn: Option<Conn>) {
    if let Some(mut c) = conn {
        c.proc.stop();
    }
    xray::clear_system_proxy();
}

impl Host {
    pub fn new() -> Arc<Host> {
        let store = Store::load();
        let mut checks = HashMap::new();
        for (id, v) in &store.checks {
            checks.insert(
                id.clone(),
                ServiceCheck { ms: [Some(v[0]).filter(|x| *x != 0), Some(v[1]).filter(|x| *x != 0), Some(v[2]).filter(|x| *x != 0)], busy: false },
            );
        }
        let token: String = (0..4).map(|_| format!("{:016x}", rand::random::<u64>())).collect();
        let h = Arc::new(Host {
            inner: Mutex::new(Inner {
                store,
                status: Status::Off,
                error: None,
                conn: None,
                active_id: None,
                pings: HashMap::new(),
                checks,
                checking: false,
                rx: 0,
                tx: 0,
                down: 0,
                up: 0,
                since: None,
                core_busy: None,
                releases: None,
                retries_left: 0,
                update: Update { available: false, checking: false, tag: String::new(), url: String::new(), notes: String::new() },
                ping_dirty: false,
            }),
            ev: Mutex::new(Events::default()),
            cv: Condvar::new(),
            ui: Mutex::new(None),
            import_lock: Mutex::new(()),
            token,
            quitting: AtomicBool::new(false),
        });
        h
    }

    pub fn set_ui(&self, f: Box<dyn Fn(UiCmd) + Send>) {
        *self.ui.lock().unwrap() = Some(f);
    }

    pub fn ui(&self, cmd: UiCmd) {
        if let Some(f) = self.ui.lock().unwrap().as_ref() {
            f(cmd);
        }
    }

    pub fn close_to_tray(&self) -> bool {
        self.inner.lock().unwrap().store.settings.close_to_tray
    }

    fn emit(&self, kind: &'static str, json: String) {
        let mut ev = self.ev.lock().unwrap();
        ev.seq += 1;
        let seq = ev.seq;
        ev.latest.insert(kind, (seq, json));
        self.cv.notify_all();
    }

    pub fn poll(&self, since: u64, wait: Duration) -> (u64, Vec<(&'static str, String)>) {
        let end = Instant::now() + wait;
        let mut ev = self.ev.lock().unwrap();
        loop {
            if ev.seq > since {
                let mut out: Vec<(u64, &'static str, String)> =
                    ev.latest.iter().filter(|(_, (s, _))| *s > since).map(|(k, (s, j))| (*s, *k, j.clone())).collect();
                out.sort_by_key(|x| x.0);
                return (ev.seq, out.into_iter().map(|(_, k, j)| (k, j)).collect());
            }
            let now = Instant::now();
            if now >= end {
                return (ev.seq, Vec::new());
            }
            let (g, _) = self.cv.wait_timeout(ev, end - now).unwrap();
            ev = g;
        }
    }

    pub fn push_state(&self) {
        let s = self.snapshot().to_string();
        self.emit("state", s);
    }

    fn push_pings(&self) {
        let o = {
            let mut g = self.inner.lock().unwrap();
            g.ping_dirty = false;
            self.pings_value(&g)
        };
        self.emit("pings", o.to_string());
    }

    fn pings_value(&self, g: &Inner) -> Value {
        let mut pings = Map::new();
        let mut checks = Map::new();
        for s in &g.store.servers {
            let p = g.pings.get(&s.id);
            pings.insert(s.id.clone(), json!({"ms": ms_value(p), "busy": p.map(|p| p.busy).unwrap_or(false)}));
            if let Some(c) = g.checks.get(&s.id) {
                checks.insert(s.id.clone(), check_value(c));
            }
        }
        let best: Vec<Value> = (0..3).map(|i| g.best_for(i).map(Value::from).unwrap_or(Value::Null)).collect();
        json!({"pings": pings, "checks": checks, "best": best, "checking": g.checking})
    }

    fn push_stats(&self) {
        let o = {
            let g = self.inner.lock().unwrap();
            json!({"rx": g.rx, "tx": g.tx, "down": g.down, "up": g.up, "uptime": g.uptime()})
        };
        self.emit("stats", o.to_string());
    }

    fn save(&self) {
        let g = self.inner.lock().unwrap();
        self.save_locked(&g);
    }

    fn save_locked(&self, g: &Inner) {
        let mut checks = HashMap::new();
        for (id, c) in &g.checks {
            checks.insert(id.clone(), [c.ms[0].unwrap_or(0), c.ms[1].unwrap_or(0), c.ms[2].unwrap_or(0)]);
        }
        let s = &g.store;
        Store {
            servers: s.servers.clone(),
            groups: s.groups.clone(),
            selected: s.selected.clone(),
            settings: s.settings.clone(),
            checks,
            checked_at: s.checked_at,
            meta: s.meta.clone(),
            web: s.web.clone(),
            collapsed: s.collapsed.clone(),
            notice: s.notice,
            hwid: s.hwid.clone(),
            skip_update: s.skip_update.clone(),
        }
        .save();
    }

    pub fn snapshot(&self) -> Value {
        let g = self.inner.lock().unwrap();
        let s = &g.store;
        let servers: Vec<Value> = s
            .servers
            .iter()
            .map(|x| json!({"id": x.id, "name": x.name, "proto": proto_of(&x.conf), "endpoint": x.endpoint, "group": x.group}))
            .collect();
        let groups: Vec<Value> = s
            .groups
            .iter()
            .map(|x| {
                let m = s.meta.get(&x.name).copied().unwrap_or([0, 0, 0]);
                json!({"name": x.name, "url": x.url, "used": m[0], "total": m[1], "expire": m[2]})
            })
            .collect();
        let pv = self.pings_value(&g);
        let mut settings = match &s.web {
            Value::Object(m) => m.clone(),
            _ => Map::new(),
        };
        let st = &s.settings;
        settings.insert("theme".into(), json!(st.theme));
        settings.insert("dynamic".into(), json!(st.dynamic));
        settings.insert("amoled".into(), json!(st.amoled));
        settings.insert("autoConnect".into(), json!(st.auto_connect));
        settings.insert("dns".into(), json!(st.dns_preset));
        settings.insert("dnsCustom".into(), json!(st.dns_custom));
        settings.insert("mtu".into(), json!(st.mtu));
        settings.insert("pingMode".into(), json!(st.ping_mode));
        settings.insert("pingTimeout".into(), json!(st.ping_timeout));
        settings.insert("autoPing".into(), json!(st.auto_ping));
        settings.insert("sortPing".into(), json!(st.sort_ping));
        settings.insert("autoCheck".into(), json!(st.auto_check));
        settings.insert("checkInterval".into(), json!(st.check_interval));
        settings.insert("tuning".into(), serde_json::to_value(&st.tuning).unwrap_or_default());
        settings.insert("mask".into(), json!(0));
        settings.insert("closeToTray".into(), json!(st.close_to_tray));
        settings.insert("autostart".into(), json!(st.autostart));
        let core_list: Vec<Value> =
            xray::list().into_iter().map(|c| json!({"id": c.id, "label": c.label, "version": c.version})).collect();
        let rel = g.releases.as_ref().map(|r| {
            Value::Array(r.iter().take(12).map(|x| json!({"tag": x.tag, "size": x.size})).collect())
        });
        json!({
            "platform": "desktop",
            "appVersion": VERSION,
            "status": match g.status { Status::Off => "Off", Status::Connecting => "Connecting", Status::On => "On", Status::Stopping => "Stopping" },
            "selected": s.selected,
            "rx": g.rx, "tx": g.tx, "down": g.down, "up": g.up, "uptime": g.uptime(),
            "error": g.error,
            "colors": [],
            "servers": servers,
            "groups": groups,
            "collapsed": s.collapsed,
            "pings": pv["pings"],
            "settings": settings,
            "plugins": {"list": [], "engine": "", "busy": null, "ui": [], "catalog": null, "families": []},
            "notice": !s.notice,
            "services": {"names": SERVICE_NAMES, "checking": g.checking, "checks": pv["checks"], "best": pv["best"]},
            "cores": {
                "active": s.settings.active_core,
                "builtin": "",
                "busy": g.core_busy,
                "list": core_list,
                "releases": rel.unwrap_or(Value::Null),
            },
            "update": {"available": g.update.available, "checking": g.update.checking, "tag": g.update.tag, "url": g.update.url, "notes": g.update.notes, "current": VERSION},
        })
    }

    pub fn start_background(self: &Arc<Self>) {
        let h = self.clone();
        std::thread::spawn(move || {
            let (auto_connect, auto_ping) = {
                let g = h.inner.lock().unwrap();
                (g.store.settings.auto_connect, g.store.settings.auto_ping)
            };
            if auto_ping {
                h.ping_list(None);
            }
            if auto_connect {
                std::thread::sleep(Duration::from_millis(600));
                let _ = h.connect(false);
            }
            let mut tick = 0u64;
            let mut last_ping_push = Instant::now();
            loop {
                std::thread::sleep(Duration::from_millis(200));
                if h.quitting.load(Ordering::Relaxed) {
                    break;
                }
                tick += 1;
                if last_ping_push.elapsed() >= Duration::from_millis(250) {
                    let dirty = h.inner.lock().unwrap().ping_dirty;
                    if dirty {
                        h.push_pings();
                        last_ping_push = Instant::now();
                    }
                }
                if tick % 5 == 0 {
                    h.second();
                }
            }
        });
    }

    fn second(self: &Arc<Self>) {
        let (status, probe, due) = {
            let g = self.inner.lock().unwrap();
            let probe = match (&g.conn, g.status) {
                (Some(_), Status::On) => true,
                _ => false,
            };
            let due = g.store.settings.auto_check
                && !g.checking
                && !g.store.servers.is_empty()
                && now_millis() >= g.store.checked_at + (g.store.settings.check_interval * 60_000.0) as u64;
            (g.status, probe, due)
        };
        if probe && status == Status::On {
            let alive_stats = {
                let mut g = self.inner.lock().unwrap();
                match g.conn.as_mut() {
                    Some(c) => {
                        let alive = c.proc.alive();
                        Some((alive, c.bin.clone(), c.api))
                    }
                    None => None,
                }
            };
            if let Some((alive, bin, api)) = alive_stats {
                if !alive {
                    self.lost();
                    return;
                }
                let stats = xray::query_stats(&bin, api);
                {
                    let mut g = self.inner.lock().unwrap();
                    if let Some((d, u)) = stats {
                        g.down = d.saturating_sub(g.rx);
                        g.up = u.saturating_sub(g.tx);
                        g.rx = d;
                        g.tx = u;
                    }
                }
                self.push_stats();
            }
        }
        if due {
            let h = self.clone();
            std::thread::spawn(move || {
                let _ = h.check_services();
            });
        }
    }

    fn lost(self: &Arc<Self>) {
        let (conn, retry, delay) = {
            let mut g = self.inner.lock().unwrap();
            let conn = g.conn.take();
            let t = g.store.settings.tuning.clone();
            let retry = t.reconnect && g.retries_left > 0;
            if retry {
                g.retries_left -= 1;
            }
            g.status = if retry { Status::Connecting } else { Status::Off };
            g.down = 0;
            g.up = 0;
            if !retry {
                g.error = Some("Ядро Xray остановилось".into());
                g.since = None;
                g.active_id = None;
            }
            (conn, retry, t.retry_delay.max(1) as u64)
        };
        stop_conn(conn);
        self.push_state();
        if retry {
            let h = self.clone();
            std::thread::spawn(move || {
                std::thread::sleep(Duration::from_secs(delay));
                {
                    let mut g = h.inner.lock().unwrap();
                    if g.status != Status::Connecting {
                        return;
                    }
                    g.status = Status::Off;
                }
                let _ = h.connect_inner(true, true);
            });
        }
    }

    fn ensure_core(&self) -> Result<PathBuf, String> {
        let active = self.inner.lock().unwrap().store.settings.active_core.clone();
        if let Some(b) = xray::find_binary(&active) {
            return Ok(b);
        }
        self.set_core_busy(Some("Скачиваю ядро Xray…".into()));
        let r = (|| -> Result<(), String> {
            let rel = xray::releases()?;
            let first = rel.first().ok_or("Нет доступных версий Xray")?;
            let raw = xray::download(&first.url)?;
            let info = xray::install(&raw, "")?;
            let mut g = self.inner.lock().unwrap();
            g.store.settings.active_core = info.id;
            Ok(())
        })();
        self.set_core_busy(None);
        r.map_err(|e| format!("Не удалось скачать ядро Xray: {}", e))?;
        self.save();
        let active = self.inner.lock().unwrap().store.settings.active_core.clone();
        xray::find_binary(&active).ok_or_else(|| "Ядро Xray не найдено".to_string())
    }

    fn set_core_busy(&self, v: Option<String>) {
        self.inner.lock().unwrap().core_busy = v;
        self.push_state();
    }

    pub fn conflicts(&self) -> Vec<Found> {
        conflict::running()
    }

    pub fn connect(self: &Arc<Self>, skip_conflicts: bool) -> Value {
        self.connect_inner(skip_conflicts, false)
    }

    fn connect_inner(self: &Arc<Self>, skip_conflicts: bool, retry: bool) -> Value {
        {
            let g = self.inner.lock().unwrap();
            if g.status != Status::Off {
                return json!({});
            }
            if g.selected_server().is_none() {
                drop(g);
                self.inner.lock().unwrap().error = Some("Сначала добавьте сервер".into());
                self.push_state();
                return json!({"error": "Сначала добавьте сервер"});
            }
        }
        if !skip_conflicts {
            let found = self.conflicts();
            if !found.is_empty() {
                return json!({"conflict": conflict::labels(&found)});
            }
        }
        let server = {
            let mut g = self.inner.lock().unwrap();
            g.error = None;
            g.status = Status::Connecting;
            if !retry {
                g.retries_left = g.store.settings.tuning.retries.max(0);
            }
            let s = g.selected_server().unwrap();
            g.active_id = Some(s.id.clone());
            s
        };
        self.push_state();
        let h = self.clone();
        std::thread::spawn(move || {
            let result = h.start_conn(&server);
            let mut stale = None;
            {
                let mut g = h.inner.lock().unwrap();
                if g.status != Status::Connecting {
                    stale = result.ok();
                } else {
                    match result {
                        Ok(c) => {
                            g.conn = Some(c);
                            g.status = Status::On;
                            g.since = Some(Instant::now());
                            g.rx = 0;
                            g.tx = 0;
                            g.down = 0;
                            g.up = 0;
                        }
                        Err(e) => {
                            g.status = Status::Off;
                            g.active_id = None;
                            g.error = Some(e);
                        }
                    }
                }
            }
            if let Some(c) = stale {
                stop_conn(Some(c));
            }
            h.push_state();
        });
        json!({})
    }

    fn start_conn(&self, server: &Server) -> Result<Conn, String> {
        let bin = self.ensure_core()?;
        let dns = self.inner.lock().unwrap().store.settings.dns_list();
        let mut parsed = links::parse(&server.conf)?;
        links::resolve(&mut parsed.outbound);
        let socks = xray::free_port();
        let http = xray::free_port();
        let api = xray::free_port();
        let cfg = links::inner_config(parsed.outbound, socks, http, Some(api), &dns);
        let proc = xray::start(&bin, &cfg, socks)?;
        xray::set_system_proxy(http, socks);
        Ok(Conn { proc, api, bin })
    }

    pub fn disconnect(self: &Arc<Self>) {
        let conn = {
            let mut g = self.inner.lock().unwrap();
            if g.status != Status::On && g.status != Status::Connecting {
                return;
            }
            let was_on = g.status == Status::On;
            g.status = Status::Stopping;
            g.retries_left = 0;
            if was_on {
                g.conn.take()
            } else {
                None
            }
        };
        self.push_state();
        let h = self.clone();
        std::thread::spawn(move || {
            stop_conn(conn);
            {
                let mut g = h.inner.lock().unwrap();
                g.status = Status::Off;
                g.conn = None;
                g.active_id = None;
                g.down = 0;
                g.up = 0;
                g.rx = 0;
                g.tx = 0;
                g.since = None;
            }
            h.push_state();
        });
    }

    pub fn shutdown(&self) {
        self.quitting.store(true, Ordering::Relaxed);
        let conn = {
            let mut g = self.inner.lock().unwrap();
            self.save_locked(&g);
            g.conn.take()
        };
        stop_conn(conn);
    }

    fn ping_list(self: &Arc<Self>, only: Option<Vec<String>>) {
        let (list, mode, timeout) = {
            let mut g = self.inner.lock().unwrap();
            let list: Vec<Server> = g
                .store
                .servers
                .iter()
                .filter(|s| only.as_ref().map(|o| o.contains(&s.id)).unwrap_or(true))
                .cloned()
                .collect();
            for s in &list {
                g.pings.insert(s.id.clone(), PingResult { busy: true, ..Default::default() });
            }
            g.ping_dirty = true;
            (list, g.store.settings.ping_mode, PING_TIMEOUTS[g.store.settings.ping_timeout.min(PING_TIMEOUTS.len() - 1)])
        };
        if list.is_empty() {
            return;
        }
        self.push_pings();
        let queue = Arc::new(Mutex::new(list));
        let workers = 24;
        let mut handles = Vec::new();
        for _ in 0..workers {
            let q = queue.clone();
            let h = self.clone();
            handles.push(std::thread::spawn(move || loop {
                let next = q.lock().unwrap().pop();
                let Some(server) = next else { break };
                let r = ping::measure(&server, mode, timeout);
                let mut g = h.inner.lock().unwrap();
                if g.store.servers.iter().any(|s| s.id == server.id) {
                    g.pings.insert(server.id.clone(), r);
                    g.ping_dirty = true;
                }
            }));
        }
        for t in handles {
            let _ = t.join();
        }
        {
            let mut g = self.inner.lock().unwrap();
            if g.store.settings.sort_ping {
                let pings = g.pings.clone();
                let best = |id: &String| pings.get(id).map(|p| p.best()).unwrap_or(i32::MAX);
                g.store.servers.sort_by_key(|s| best(&s.id));
            }
        }
        self.push_pings();
        self.push_state();
    }

    fn check_services(self: &Arc<Self>) -> Result<(), String> {
        {
            let g = self.inner.lock().unwrap();
            if g.checking || g.store.servers.is_empty() {
                return Ok(());
            }
        }
        let bin = self.ensure_core()?;
        let list = {
            let mut g = self.inner.lock().unwrap();
            g.checking = true;
            g.checks.clear();
            let list = g.store.servers.clone();
            for s in &list {
                g.checks.insert(s.id.clone(), ServiceCheck { busy: true, ..Default::default() });
            }
            g.ping_dirty = true;
            list
        };
        self.push_pings();
        let queue = Arc::new(Mutex::new(list));
        let mut handles = Vec::new();
        for _ in 0..8 {
            let q = queue.clone();
            let h = self.clone();
            let bin = bin.clone();
            handles.push(std::thread::spawn(move || loop {
                let next = q.lock().unwrap().pop();
                let Some(server) = next else { break };
                let r = xray::check_server(&bin, &server.conf, 12);
                let mut g = h.inner.lock().unwrap();
                let c = match r {
                    Ok(v) => {
                        let mut ms = [None; 3];
                        for i in 0..3 {
                            ms[i] = Some(if v[i] > 0 { v[i] } else { -1 });
                        }
                        ServiceCheck { ms, busy: false }
                    }
                    Err(_) => ServiceCheck { ms: [Some(-1); 3], busy: false },
                };
                g.checks.insert(server.id.clone(), c);
                g.ping_dirty = true;
            }));
        }
        for t in handles {
            let _ = t.join();
        }
        {
            let mut g = self.inner.lock().unwrap();
            g.checking = false;
            g.store.checked_at = now_millis();
            self.save_locked(&g);
        }
        self.push_pings();
        self.push_state();
        Ok(())
    }

    fn gather(&self, text: &str, force_group: Option<String>, force_url: &str) -> Result<Vec<Item>, String> {
        let input = text.trim().to_string();
        if input.is_empty() {
            return Err("Вставьте ссылку или конфигурацию".into());
        }
        let prepared = importer::prepare(&input);
        if let Some(e) = prepared.error {
            return Err(e);
        }
        let mut items = Vec::new();
        let mut direct = Vec::new();
        for source in prepared.items {
            if source.is_url {
                let fetched = links::fetch_meta(&source.value).map_err(|e| format!("Не удалось загрузить список: {}", e))?;
                let group_name = match &force_group {
                    Some(g) => g.clone(),
                    None => {
                        if source.name.is_empty() {
                            fetched.group.clone()
                        } else {
                            source.name.clone()
                        }
                    }
                };
                let body = links::decode_subscription(&fetched.body);
                for link in importer::prepare(&body).items {
                    if link.is_url {
                        continue;
                    }
                    items.push(Item {
                        link: link.value,
                        group: group_name.clone(),
                        url: if force_url.is_empty() { source.value.clone() } else { force_url.to_string() },
                        meta: Some([fetched.used, fetched.total, fetched.expire]),
                    });
                }
            } else {
                direct.push(source.value);
            }
        }
        let direct_group = match &force_group {
            Some(g) => g.clone(),
            None => String::new(),
        };
        for link in direct {
            items.push(Item { link, group: direct_group.clone(), url: force_url.to_string(), meta: None });
        }
        Ok(items)
    }

    fn apply(&self, items: Vec<Item>, replace_group: Option<&str>) -> (usize, Option<String>) {
        let mut g = self.inner.lock().unwrap();
        let mut added = 0;
        let mut failure: Option<String> = None;
        let mut keep: HashSet<String> = HashSet::new();
        for item in items {
            match links::parse(&item.link) {
                Ok(parsed) => {
                    let group = item.group.trim().to_string();
                    let existing = g.store.servers.iter().position(|s| s.conf == item.link);
                    match existing {
                        None => {
                            let mut title = parsed.name.clone();
                            if title.is_empty() {
                                title = format!("Сервер {}", g.store.servers.len() + 1);
                            }
                            let id = short_id();
                            g.store.servers.push(Server { id: id.clone(), name: title, conf: item.link.clone(), endpoint: parsed.endpoint.clone(), group: group.clone() });
                            keep.insert(id.clone());
                            if g.store.selected.is_none() {
                                g.store.selected = Some(id);
                            }
                            added += 1;
                        }
                        Some(i) => {
                            keep.insert(g.store.servers[i].id.clone());
                            if !group.is_empty() && g.store.servers[i].group != group {
                                g.store.servers[i].group = group.clone();
                            }
                        }
                    }
                    if !group.is_empty() {
                        g.ensure_group(&group, &item.url);
                        if let Some(m) = item.meta {
                            g.store.meta.insert(group, m);
                        }
                    }
                }
                Err(e) => {
                    if failure.is_none() {
                        failure = Some(e);
                    }
                }
            }
        }
        if let Some(name) = replace_group {
            if !keep.is_empty() {
                let drop: Vec<String> = g.store.servers.iter().filter(|s| s.group == name && !keep.contains(&s.id)).map(|s| s.id.clone()).collect();
                g.store.servers.retain(|s| !drop.contains(&s.id));
                for id in &drop {
                    g.pings.remove(id);
                    g.checks.remove(id);
                }
                g.fix_selection();
            }
        }
        g.fix_selection();
        self.save_locked(&g);
        (added, if added == 0 && keep.is_empty() { Some(failure.unwrap_or_else(|| "Не удалось распознать".into())) } else { None })
    }

    fn add(&self, text: &str) -> Value {
        let _lock = self.import_lock.lock().unwrap();
        let items = match self.gather(text, None, "") {
            Ok(v) => v,
            Err(e) => return json!({"added": 0, "error": e}),
        };
        let (added, error) = self.apply(items, None);
        let mut o = json!({"added": added});
        if let Some(e) = error {
            o["error"] = json!(e);
        }
        o
    }

    fn refresh_group(&self, name: &str) -> Option<String> {
        let _lock = self.import_lock.lock().unwrap();
        let url = {
            let g = self.inner.lock().unwrap();
            let Some(group) = g.store.groups.iter().find(|x| x.name == name) else {
                return Some("Группа не найдена".into());
            };
            group.url.clone()
        };
        if url.is_empty() {
            return Some("У группы нет ссылки для обновления".into());
        }
        match self.gather(&url, Some(name.to_string()), &url) {
            Ok(items) => self.apply(items, Some(name)).1,
            Err(e) => Some(e),
        }
    }

    fn remove_ids(self: &Arc<Self>, ids: &HashSet<String>) {
        let need_disconnect = {
            let mut g = self.inner.lock().unwrap();
            let active = g.active_id.clone();
            g.store.servers.retain(|s| !ids.contains(&s.id));
            for id in ids {
                g.pings.remove(id);
                g.checks.remove(id);
            }
            g.fix_selection();
            self.save_locked(&g);
            active.map(|a| ids.contains(&a)).unwrap_or(false) && (g.status == Status::On || g.status == Status::Connecting)
        };
        if need_disconnect {
            self.disconnect();
        }
    }

    fn set_setting(&self, path: &str, v: &Value) {
        let mut g = self.inner.lock().unwrap();
        let int = || v.as_i64().unwrap_or(0);
        let flag = || v.as_bool().unwrap_or(false);
        let st = &mut g.store.settings;
        match path {
            "theme" => st.theme = int().clamp(0, 2) as usize,
            "dynamic" => st.dynamic = flag(),
            "amoled" => st.amoled = flag(),
            "autoConnect" => st.auto_connect = flag(),
            "dns" => st.dns_preset = int().clamp(0, 4) as usize,
            "dnsCustom" => st.dns_custom = v.as_str().unwrap_or("").to_string(),
            "mtu" => st.mtu = int() as i32,
            "pingMode" => st.ping_mode = int().clamp(0, 4) as usize,
            "pingTimeout" => st.ping_timeout = int().clamp(0, 2) as usize,
            "autoPing" => st.auto_ping = flag(),
            "sortPing" => st.sort_ping = flag(),
            "autoCheck" => st.auto_check = flag(),
            "checkInterval" => st.check_interval = v.as_f64().unwrap_or(60.0) as f32,
            "closeToTray" => st.close_to_tray = flag(),
            "autostart" => {
                st.autostart = flag();
                set_autostart(st.autostart);
            }
            "mask" => {}
            _ => {
                if let Some(key) = path.strip_prefix("tuning.") {
                    let mut t = serde_json::to_value(&st.tuning).unwrap_or_default();
                    t[key] = v.clone();
                    if let Ok(parsed) = serde_json::from_value(t) {
                        st.tuning = parsed;
                    }
                } else {
                    if !g.store.web.is_object() {
                        g.store.web = json!({});
                    }
                    let mut node = &mut g.store.web;
                    let keys: Vec<&str> = path.split('.').collect();
                    for k in &keys[..keys.len() - 1] {
                        if !node[*k].is_object() {
                            node[*k] = json!({});
                        }
                        node = &mut node[*k];
                    }
                    node[keys[keys.len() - 1]] = v.clone();
                }
            }
        }
        self.save_locked(&g);
    }

    fn check_update(&self) -> Value {
        self.inner.lock().unwrap().update.checking = true;
        let result = (|| -> Result<(String, String, String), String> {
            let agent: ureq::Agent = ureq::Agent::config_builder()
                .timeout_global(Some(Duration::from_secs(15)))
                .http_status_as_error(false)
                .build()
                .into();
            let mut resp = agent
                .get("https://api.github.com/repos/Fasdeq13/FasdeqVPN/releases/latest")
                .header("User-Agent", "FasdeqVPN")
                .call()
                .map_err(|e| e.to_string())?;
            if resp.status().as_u16() != 200 {
                return Err(format!("HTTP {}", resp.status().as_u16()));
            }
            let body = resp.body_mut().read_to_string().map_err(|e| e.to_string())?;
            let v: Value = serde_json::from_str(&body).map_err(|e| e.to_string())?;
            Ok((
                v["tag_name"].as_str().unwrap_or("").to_string(),
                v["html_url"].as_str().unwrap_or("").to_string(),
                v["body"].as_str().unwrap_or("").to_string(),
            ))
        })();
        let mut g = self.inner.lock().unwrap();
        g.update.checking = false;
        match result {
            Ok((tag, url, notes)) => {
                let newer = newer(&tag, VERSION) && g.store.skip_update != tag;
                g.update = Update { available: newer, checking: false, tag: tag.clone(), url, notes };
                json!({"available": newer, "tag": tag, "error": null})
            }
            Err(e) => json!({"available": false, "tag": "", "error": e}),
        }
    }

    pub fn act(self: &Arc<Self>, name: &str, payload: &Value) -> Value {
        let text = payload.as_str().unwrap_or("").to_string();
        let mut push = true;
        let reply: Value = match name {
            "connect" => {
                push = false;
                self.connect(false)
            }
            "connectForce" => {
                push = false;
                self.connect(true)
            }
            "killConflicts" => {
                let found = self.conflicts();
                let left = if found.is_empty() { Vec::new() } else { conflict::kill(&found) };
                if left.is_empty() {
                    json!({"ok": true})
                } else {
                    json!({"left": conflict::labels(&left)})
                }
            }
            "disconnect" => {
                self.disconnect();
                push = false;
                json!({})
            }
            "select" => {
                let mut g = self.inner.lock().unwrap();
                if g.store.servers.iter().any(|s| s.id == text) {
                    g.store.selected = Some(text.clone());
                    self.save_locked(&g);
                }
                json!({})
            }
            "remove" => {
                let mut set = HashSet::new();
                set.insert(text.clone());
                self.remove_ids(&set);
                json!({})
            }
            "removeGroup" => {
                let ids: HashSet<String> = {
                    let mut g = self.inner.lock().unwrap();
                    g.store.groups.retain(|x| x.name != text);
                    g.store.meta.remove(&text);
                    g.store.servers.iter().filter(|s| s.group == text).map(|s| s.id.clone()).collect()
                };
                self.remove_ids(&ids);
                self.save();
                json!({})
            }
            "clearGroup" => {
                let ids: HashSet<String> = {
                    let g = self.inner.lock().unwrap();
                    g.store.servers.iter().filter(|s| s.group == text).map(|s| s.id.clone()).collect()
                };
                self.remove_ids(&ids);
                json!({})
            }
            "ping" => {
                self.ping_list(None);
                push = false;
                json!({})
            }
            "pingOne" => {
                self.ping_list(Some(vec![text.clone()]));
                push = false;
                json!({})
            }
            "pingGroup" => {
                let ids: Vec<String> = {
                    let g = self.inner.lock().unwrap();
                    g.store.servers.iter().filter(|s| s.group == text).map(|s| s.id.clone()).collect()
                };
                self.ping_list(Some(ids));
                push = false;
                json!({})
            }
            "checkServices" => {
                let h = self.clone();
                std::thread::spawn(move || {
                    if let Err(e) = h.check_services() {
                        h.inner.lock().unwrap().error = Some(e);
                        h.push_state();
                    }
                });
                std::thread::sleep(Duration::from_millis(60));
                json!({})
            }
            "selectCore" => {
                self.inner.lock().unwrap().store.settings.active_core = text.clone();
                self.save();
                json!({})
            }
            "deleteCore" => {
                xray::delete(&text);
                let mut g = self.inner.lock().unwrap();
                if g.store.settings.active_core == text {
                    g.store.settings.active_core = String::new();
                }
                self.save_locked(&g);
                json!({})
            }
            "pickCore" => match rfd::FileDialog::new().set_title("Файл ядра Xray").pick_file() {
                None => json!({"cancel": true}),
                Some(path) => {
                    self.set_core_busy(Some("Устанавливаю ядро…".into()));
                    let r = xray::install_file(&path);
                    self.set_core_busy(None);
                    match r {
                        Ok(info) => {
                            self.inner.lock().unwrap().store.settings.active_core = info.id;
                            self.save();
                            json!({})
                        }
                        Err(e) => json!({"error": e}),
                    }
                }
            },
            "loadReleases" => {
                self.set_core_busy(Some("Загружаю список версий…".into()));
                let r = xray::releases();
                let mut g = self.inner.lock().unwrap();
                g.core_busy = None;
                match r {
                    Ok(v) => {
                        g.releases = Some(v);
                        json!({})
                    }
                    Err(e) => json!({"error": e}),
                }
            }
            "downloadCore" => {
                let rel = {
                    let g = self.inner.lock().unwrap();
                    g.releases.as_ref().and_then(|r| r.iter().find(|x| x.tag == text).cloned())
                };
                match rel {
                    None => json!({"error": "Версия не найдена"}),
                    Some(rel) => {
                        self.set_core_busy(Some(format!("Скачиваю {}…", rel.tag)));
                        let r = xray::download(&rel.url).and_then(|raw| xray::install(&raw, ""));
                        self.set_core_busy(None);
                        match r {
                            Ok(info) => {
                                self.inner.lock().unwrap().store.settings.active_core = info.id;
                                self.save();
                                json!({})
                            }
                            Err(e) => json!({"error": e}),
                        }
                    }
                }
            }
            "renameServer" => {
                let id = payload["id"].as_str().unwrap_or("");
                let name = payload["name"].as_str().unwrap_or("").trim().to_string();
                let mut g = self.inner.lock().unwrap();
                if !name.is_empty() {
                    if let Some(s) = g.store.servers.iter_mut().find(|s| s.id == id) {
                        s.name = name;
                    }
                }
                self.save_locked(&g);
                json!({})
            }
            "renameGroup" => {
                let old = payload["old"].as_str().unwrap_or("").to_string();
                let new = payload["name"].as_str().unwrap_or("").trim().to_string();
                let mut g = self.inner.lock().unwrap();
                if new.is_empty() {
                    json!({"error": "Пустое имя"})
                } else if new != old && g.store.groups.iter().any(|x| x.name == new) {
                    json!({"error": "Группа с таким именем уже есть"})
                } else {
                    for x in g.store.groups.iter_mut().filter(|x| x.name == old) {
                        x.name = new.clone();
                    }
                    for s in g.store.servers.iter_mut().filter(|s| s.group == old) {
                        s.group = new.clone();
                    }
                    if let Some(m) = g.store.meta.remove(&old) {
                        g.store.meta.insert(new.clone(), m);
                    }
                    self.save_locked(&g);
                    json!({})
                }
            }
            "moveServer" => {
                let id = payload["id"].as_str().unwrap_or("").to_string();
                let group = payload["group"].as_str().unwrap_or("").to_string();
                let before = payload["before"].as_str().map(|s| s.to_string());
                let mut g = self.inner.lock().unwrap();
                if let Some(i) = g.store.servers.iter().position(|s| s.id == id) {
                    let mut s = g.store.servers.remove(i);
                    s.group = group.clone();
                    let at = match before.and_then(|b| g.store.servers.iter().position(|x| x.id == b)) {
                        Some(p) => p,
                        None => match g.store.servers.iter().rposition(|x| x.group == group) {
                            Some(p) => p + 1,
                            None => g.store.servers.len(),
                        },
                    };
                    g.store.servers.insert(at, s);
                    self.save_locked(&g);
                }
                json!({})
            }
            "refreshInfo" => {
                let names: Vec<String> = {
                    let g = self.inner.lock().unwrap();
                    g.store.groups.iter().filter(|x| !x.url.is_empty()).map(|x| x.name.clone()).collect()
                };
                for n in names {
                    self.refresh_group(&n);
                }
                json!({})
            }
            "setCollapsed" => {
                let list: Vec<String> = payload.as_array().map(|a| a.iter().filter_map(|v| v.as_str().map(|s| s.to_string())).collect()).unwrap_or_default();
                let mut g = self.inner.lock().unwrap();
                g.store.collapsed = list;
                self.save_locked(&g);
                push = false;
                json!({})
            }
            "add" => self.add(&text),
            "refreshGroup" => match self.refresh_group(&text) {
                Some(e) => json!({"error": e}),
                None => json!({}),
            },
            "clipboard" => {
                let t = arboard::Clipboard::new().and_then(|mut c| c.get_text()).unwrap_or_default();
                push = false;
                json!({"text": t})
            }
            "copy" => {
                if let Ok(mut c) = arboard::Clipboard::new() {
                    let _ = c.set_text(text.clone());
                }
                push = false;
                json!({})
            }
            "hwid" | "hwidReset" => {
                let mut g = self.inner.lock().unwrap();
                if g.store.hwid.is_empty() || name == "hwidReset" {
                    g.store.hwid = format!("{:016x}", rand::random::<u64>());
                    self.save_locked(&g);
                }
                push = false;
                json!({"hwid": g.store.hwid})
            }
            "noticeSeen" => {
                let mut g = self.inner.lock().unwrap();
                g.store.notice = true;
                self.save_locked(&g);
                json!({})
            }
            "open" => {
                if text.starts_with("http://") || text.starts_with("https://") {
                    open_url(&text);
                }
                push = false;
                json!({})
            }
            "set" => {
                let path = payload["path"].as_str().unwrap_or("").to_string();
                self.set_setting(&path, &payload["value"]);
                json!({})
            }
            "checkUpdate" => self.check_update(),
            "dismissUpdate" => {
                let mut g = self.inner.lock().unwrap();
                g.store.skip_update = text.clone();
                g.update.available = false;
                self.save_locked(&g);
                json!({})
            }
            "hide" => {
                push = false;
                self.ui(UiCmd::Toggle);
                json!({})
            }
            "quit" => {
                push = false;
                self.ui(UiCmd::Quit);
                json!({})
            }
            "haptic" => {
                push = false;
                json!({})
            }
            _ => {
                push = false;
                json!({})
            }
        };
        if push {
            self.push_state();
        }
        reply
    }
}

fn newer(tag: &str, current: &str) -> bool {
    let nums = |s: &str| -> Vec<u32> {
        s.trim_start_matches(|c: char| !c.is_ascii_digit())
            .split(|c: char| !c.is_ascii_digit())
            .filter(|p| !p.is_empty())
            .map(|p| p.parse().unwrap_or(0))
            .collect()
    };
    let (a, b) = (nums(tag), nums(current));
    for i in 0..a.len().max(b.len()) {
        let (x, y) = (a.get(i).copied().unwrap_or(0), b.get(i).copied().unwrap_or(0));
        if x != y {
            return x > y;
        }
    }
    false
}

pub fn open_url(url: &str) {
    #[cfg(target_os = "windows")]
    {
        let mut c = std::process::Command::new("rundll32");
        c.args(["url.dll,FileProtocolHandler", url]);
        fasdeq_core::proc::hide_window(&mut c);
        let _ = c.spawn();
    }
    #[cfg(target_os = "macos")]
    {
        let _ = std::process::Command::new("open").arg(url).spawn();
    }
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        let _ = std::process::Command::new("xdg-open").arg(url).spawn();
    }
}

pub fn set_autostart(enable: bool) {
    let Ok(exe) = std::env::current_exe() else { return };
    let Some(path) = exe.to_str() else { return };
    if let Ok(auto) = auto_launch::AutoLaunchBuilder::new().set_app_name("FasdeqVPN").set_app_path(path).build() {
        if enable {
            let _ = auto.enable();
        } else {
            let _ = auto.disable();
        }
    }
}
