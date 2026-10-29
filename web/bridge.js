(function () {
  const listeners = [];
  const native = window.FasdeqNative;

  const pingListeners = [];
  let current = null;
  function emit(state) {
    current = state;
    for (const cb of listeners) cb(state);
  }

  if (native) {
    window.__fasdeqPush = function (json) {
      try { emit(JSON.parse(json)); } catch (e) {}
    };
    window.__fasdeqPings = function (json) {
      try {
        const p = JSON.parse(json);
        for (const cb of pingListeners) cb(p);
      } catch (e) {}
    };
    window.__fasdeqStats = function (json) {
      try {
        if (!current) return;
        const p = JSON.parse(json);
        current.rx = p.rx; current.tx = p.tx; current.down = p.down; current.up = p.up; current.uptime = p.uptime;
        for (const cb of listeners) cb(current, true);
      } catch (e) {}
    };
    const pending = {};
    let seq = 0;
    window.__fasdeqResult = function (id, json) {
      const f = pending[id];
      delete pending[id];
      if (!f) return;
      try { f(json ? JSON.parse(json) : {}); } catch (e) { f({}); }
    };
    window.Bridge = {
      native: true,
      onState(cb) { listeners.push(cb); },
      onPings(cb) { pingListeners.push(cb); },
      state() { return Promise.resolve(JSON.parse(native.state())); },
      act(name, payload) {
        return new Promise((resolve) => {
          const id = ++seq;
          pending[id] = resolve;
          try { native.act(name, payload === undefined ? "" : JSON.stringify(payload), id); } catch (e) { delete pending[id]; resolve({}); }
        });
      },
      ready() { try { native.ready(); } catch (e) {} },
      bars(dark) { try { native.bars(dark); } catch (e) {} },
    };
    return;
  }

  if (window.__FQHTTP) {
    const mq = window.matchMedia ? window.matchMedia("(prefers-color-scheme: dark)") : null;
    const wrap = (st) => { st.dark = mq ? mq.matches : true; return st; };
    const applyStats = (p) => {
      if (!current) return;
      current.rx = p.rx; current.tx = p.tx; current.down = p.down; current.up = p.up; current.uptime = p.uptime;
      for (const cb of listeners) cb(current, true);
    };
    async function call(path, body) {
      const r = await fetch(path, body === undefined ? { cache: "no-store" } : { method: "POST", headers: { "Content-Type": "application/json", "X-Fasdeq": "1" }, body: JSON.stringify(body) });
      return r.json();
    }
    let seq = 0;
    (async function loop() {
      let fails = 0;
      for (;;) {
        try {
          const r = await call("/api/poll?since=" + seq);
          fails = 0;
          seq = r.seq;
          for (const e of r.ev) {
            if (e.k === "state") emit(wrap(e.d));
            else if (e.k === "pings") pingListeners.forEach((cb) => cb(e.d));
            else if (e.k === "stats") applyStats(e.d);
          }
        } catch (err) {
          fails++;
          await new Promise((res) => setTimeout(res, Math.min(3000, 300 * fails)));
        }
      }
    })();
    if (mq && mq.addEventListener) mq.addEventListener("change", () => { if (current) emit(wrap(current)); });
    window.Bridge = {
      native: false,
      desktop: true,
      onState(cb) { listeners.push(cb); },
      onPings(cb) { pingListeners.push(cb); },
      state() { return call("/api/state").then(wrap); },
      act(name, payload) { return call("/api/act", { name, payload: payload === undefined ? null : payload }).catch(() => ({})); },
      ready() {},
      bars() {},
    };
    return;
  }

  const S = {
    status: "Off",
    selected: "s2",
    rx: 0, tx: 0, down: 0, up: 0, uptime: 0,
    error: null,
    dark: true,
    colors: [],
    servers: [
      { id: "s1", name: "Хельсинки 1", proto: "VLESS Reality", endpoint: "hel1.example.net:443", group: "Основная" },
      { id: "s2", name: "Нидерланды", proto: "VLESS Reality", endpoint: "ams.example.net:443", group: "Основная" },
      { id: "s3", name: "Германия, Франкфурт", proto: "Trojan", endpoint: "fra.example.net:443", group: "Основная" },
      { id: "s4", name: "Казахстан", proto: "Shadowsocks", endpoint: "kz.example.net:8388", group: "Запасные" },
      { id: "s5", name: "Домашний WireGuard", proto: "WireGuard", endpoint: "home.example.org:51820", group: "" },
    ],
    pings: { s1: { ms: 38 }, s2: { ms: 61 }, s3: { ms: 112 }, s4: { ms: 284 }, s5: { ms: null } },
    groups: [{ name: "Основная", url: "https://example.com/sub", used: 13555990528, total: 107374182400, expire: Math.floor(Date.now() / 1000) + 86400 * 142 }, { name: "Запасные", url: "https://example.com/sub2", used: 5368709120, total: 0, expire: 0 }],
    settings: {},
    services: { names: ["Discord", "Telegram", "YouTube"], checking: false, checks: {}, best: [] },
    cores: { active: "", builtin: "25.9.11", busy: null, list: [{ id: "c1", label: "Xray 25.8.3", version: "25.8.3" }], releases: null },
    plugins: {
      list: [{ key: "ru-bypass@1.0", id: "ru-bypass", name: "Обход для приложений", version: "1.0", family: "ru-bypass", author: "Fasdeq13", description: "Напрямую: банки и госуслуги", kinds: ["rules"], enabled: true, wg: false, hasUi: false,
        schema: [{ key: "apps", type: "text", title: "Пакеты через запятую", placeholder: "ru.sberbankmobile" }, { key: "strict", type: "switch", title: "Строгий режим", sub: "Блокировать остальное" }, { key: "level", type: "select", title: "Уровень", options: ["Мало", "Средне", "Много"] }, { key: "n", type: "number", title: "Лимит", min: 0, max: 20, step: 1 }],
        values: { apps: "", strict: false, level: 1, n: 5 } }],
      engine: "", busy: null, ui: [], catalog: null,
      families: [{ family: "wireguard-go", name: "WireGuard" }, { family: "amneziawg", name: "AmneziaWG" }],
    },
    update: { available: true, checking: false, tag: "v2.0", url: "https://github.com/Fasdeq13/FasdeqVPN/releases/", notes: "Compose удалён, быстрее работа с большими списками", current: "1.9" },
  };
  let timer = null;
  let ticker = null;

  function clone() { return JSON.parse(JSON.stringify(S)); }
  function push() { emit(clone()); }

  function startTicker() {
    clearInterval(ticker);
    ticker = setInterval(() => {
      if (S.status !== "On") return;
      S.uptime += 1;
      S.down = 180000 + Math.random() * 900000;
      S.up = 20000 + Math.random() * 160000;
      S.rx += S.down;
      S.tx += S.up;
      push();
    }, 1000);
  }

  window.Bridge = {
    native: false,
    ready() {},
    bars() {},
    onState(cb) { listeners.push(cb); },
    state() { return Promise.resolve(clone()); },
    act(name, payload) {
      switch (name) {
        case "connect":
          if (S.status === "Off") {
            S.status = "Connecting"; S.error = null; push();
            clearTimeout(timer);
            timer = setTimeout(() => { S.status = "On"; S.uptime = 0; S.rx = 0; S.tx = 0; startTicker(); push(); }, 1100);
          }
          break;
        case "disconnect":
          if (S.status === "On" || S.status === "Connecting") {
            clearTimeout(timer);
            S.status = "Stopping"; push();
            timer = setTimeout(() => { S.status = "Off"; S.down = 0; S.up = 0; push(); }, 450);
          }
          break;
        case "select":
          S.selected = payload; push();
          break;
        case "remove":
          S.servers = S.servers.filter((s) => s.id !== payload);
          if (S.selected === payload) S.selected = S.servers[0] ? S.servers[0].id : null;
          push();
          break;
        case "add": {
          const lines = String(payload || "").split(/\s+/).filter((l) => /^[a-z0-9+.-]+:\/\//i.test(l));
          lines.forEach((l, i) => {
            const id = "n" + Date.now() + i;
            S.servers.push({ id, name: "Новый сервер " + (S.servers.length + 1), proto: l.split(":")[0].toUpperCase(), endpoint: l.replace(/^[^:]+:\/\//, "").split(/[?#]/)[0], group: "" });
            if (!S.selected) S.selected = id;
          });
          push();
          return Promise.resolve({ added: lines.length });
        }
        case "ping":
          S.servers.forEach((s) => { S.pings[s.id] = { ms: Math.round(30 + Math.random() * 320) }; });
          return new Promise((r) => setTimeout(() => { push(); r({}); }, 900));
        case "set": {
          const path = payload.path.split(".");
          let o = S.settings;
          for (let i = 0; i < path.length - 1; i++) { o[path[i]] = o[path[i]] || {}; o = o[path[i]]; }
          o[path[path.length - 1]] = payload.value;
          push();
          break;
        }
        case "pingGroup":
          S.servers.filter((s) => s.group === payload).forEach((s) => { S.pings[s.id] = { ms: Math.round(30 + Math.random() * 320) }; });
          push();
          break;
        case "refreshGroup":
          return Promise.resolve({});
        case "moveServer": {
          const i = S.servers.findIndex((x) => x.id === payload.id);
          if (i < 0) break;
          const sv = S.servers.splice(i, 1)[0];
          sv.group = payload.group || "";
          let at = payload.before ? S.servers.findIndex((x) => x.id === payload.before) : -1;
          if (at < 0) {
            let last = -1;
            S.servers.forEach((x, k) => { if (x.group === sv.group) last = k; });
            at = last >= 0 ? last + 1 : S.servers.length;
          }
          S.servers.splice(at, 0, sv);
          push();
          break;
        }
        case "setCollapsed":
          S.collapsed = payload;
          return Promise.resolve({});
        case "refreshInfo":
          return Promise.resolve({});
        case "removeGroup":
          S.servers = S.servers.filter((s) => s.group !== payload);
          S.groups = S.groups.filter((g) => g.name !== payload);
          if (!S.servers.some((s) => s.id === S.selected)) S.selected = S.servers[0] ? S.servers[0].id : null;
          push();
          break;
        case "checkServices":
          S.services.checking = true;
          S.servers.forEach((x) => { S.services.checks[x.id] = { ms: [null, null, null], busy: true }; });
          push();
          setTimeout(() => {
            S.servers.forEach((x) => { S.services.checks[x.id] = { ms: [0, 1, 2].map(() => (Math.random() < 0.2 ? -1 : Math.round(80 + Math.random() * 500))), busy: false }; });
            S.services.checking = false;
            S.services.best = [0, 1, 2].map((i) => { let b = null, m = 1e9; S.servers.forEach((x) => { const v = S.services.checks[x.id].ms[i]; if (v > 0 && v < m) { m = v; b = x.id; } }); return b; });
            push();
          }, 1200);
          break;
        case "selectCore": S.cores.active = payload; push(); break;
        case "deleteCore": S.cores.list = S.cores.list.filter((c) => c.id !== payload); if (S.cores.active === payload) S.cores.active = ""; push(); break;
        case "pickCore": return Promise.resolve({});
        case "loadReleases":
          S.cores.busy = "Загружаю список версий…"; push();
          return new Promise((r) => setTimeout(() => { S.cores.busy = null; S.cores.releases = [{ tag: "v25.9.11", size: 14000000 }, { tag: "v25.8.3", size: 13800000 }, { tag: "v25.7.26", size: 13700000 }]; push(); r({}); }, 600));
        case "downloadCore": return Promise.resolve({});
        case "checkUpdate": return Promise.resolve({ available: S.update.available, tag: S.update.tag });
        case "dismissUpdate": S.update.available = false; push(); break;
        case "renameServer": { const x = S.servers.find((q) => q.id === payload.id); if (x) x.name = payload.name; push(); break; }
        case "renameGroup": S.servers.forEach((x) => { if (x.group === payload.old) x.group = payload.name; }); S.groups.forEach((g) => { if (g.name === payload.old) g.name = payload.name; }); push(); break;
        case "clearGroup": break;
        case "open": return Promise.resolve({});
        case "wgEngine": S.plugins.engine = payload; push(); break;
        case "loadCatalog":
          return new Promise((r) => setTimeout(() => { S.plugins.catalog = [{ family: "amneziawg", name: "AmneziaWG", versions: [{ version: "1.0.20250601", url: "https://x/awg1.zip", size: 6200000, notes: "AWG 1.5" }, { version: "1.0.20250301", url: "https://x/awg0.zip", size: 6100000, notes: "" }] }, { family: "wireguard-go", name: "WireGuard", versions: [{ version: "0.0.20250522", url: "https://x/wg.zip", size: 3000000, notes: "" }] }]; push(); r({}); }, 500));
        case "installPluginUrl": {
          const v = String(payload.url).includes("awg") ? "1.0.20250601" : "0.0.20250522";
          const fam = String(payload.url).includes("awg") ? "amneziawg" : "wireguard-go";
          const key = fam + "@" + v;
          if (!S.plugins.list.some((x) => x.key === key)) S.plugins.list.push({ key, id: fam, name: fam === "amneziawg" ? "AmneziaWG" : "WireGuard-Go", version: v, family: fam, author: "", description: "", kinds: ["protocol"], enabled: true, wg: true, hasUi: false, schema: [], values: {} });
          if (payload.select) S.plugins.engine = key;
          push(); return Promise.resolve({});
        }
        case "pickPlugin": return Promise.resolve({ cancel: true });
        case "pluginEnable": { const x = S.plugins.list.find((q) => q.key === payload.key); if (x) x.enabled = payload.value; push(); break; }
        case "pluginDelete": S.plugins.list = S.plugins.list.filter((q) => q.key !== payload); if (S.plugins.engine === payload) S.plugins.engine = ""; push(); break;
        case "pluginSet": { const x = S.plugins.list.find((q) => q.key === payload.key); if (x) x.values[payload.name] = payload.value; push(); break; }
        case "clipboard":
          return Promise.resolve({ text: "vless://uuid@demo.example.net:443?security=reality#Демо" });
        case "haptic":
        default:
      }
      return Promise.resolve({});
    },
  };
})();
