(function () {
  const ICONS = {
    power: "M13 3h-2v10h2V3zm4.83 2.17l-1.42 1.42C17.99 7.86 19 9.81 19 12c0 3.87-3.13 7-7 7s-7-3.13-7-7c0-2.19 1.01-4.14 2.58-5.42L6.17 5.17C4.23 6.82 3 9.26 3 12c0 4.97 4.03 9 9 9s9-4.03 9-9c0-2.74-1.23-5.18-3.17-6.83z",
    home: "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z",
    servers: "M20 13H4c-.55 0-1 .45-1 1v6c0 .55.45 1 1 1h16c.55 0 1-.45 1-1v-6c0-.55-.45-1-1-1zM7 19c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zM20 3H4c-.55 0-1 .45-1 1v6c0 .55.45 1 1 1h16c.55 0 1-.45 1-1V4c0-.55-.45-1-1-1zM7 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z",
    settings: "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 00.12-.61l-1.92-3.32a.488.488 0 00-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 00-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 00-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z",
    add: "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z",
    down: "M20 12l-1.41-1.41L13 16.17V4h-2v12.17l-5.58-5.59L4 12l8 8 8-8z",
    up: "M4 12l1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8-8 8z",
    clock: "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z",
    del: "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z",
    paste: "M19 2h-4.18C14.4.84 13.3 0 12 0c-1.3 0-2.4.84-2.82 2H5c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-7 0c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm7 18H5V4h2v3h10V4h2v16z",
    bolt: "M11 21h-1l1-7H7.5c-.58 0-.57-.32-.38-.66.19-.34.05-.08.07-.12C8.48 10.94 10.42 7.54 13 3h1l-1 7h3.5c.49 0 .56.33.47.51l-.07.15C12.96 17.55 11 21 11 21z",
    chevron: "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z",
    refresh: "M17.65 6.35A7.958 7.958 0 0012 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0112 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z",
    edit: "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a.996.996 0 000-1.41l-2.34-2.34a.996.996 0 00-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z",
    cpu: "M15 9H9v6h6V9zm-2 4h-2v-2h2v2zm8-2V9h-2V7c0-1.1-.9-2-2-2h-2V3h-2v2h-2V3H9v2H7c-1.1 0-2 .9-2 2v2H3v2h2v2H3v2h2v2c0 1.1.9 2 2 2h2v2h2v-2h2v2h2v-2h2c1.1 0 2-.9 2-2v-2h2v-2h-2v-2h2zm-4 6H7V7h10v10z",
    download: "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z",
    check: "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z",
    info: "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z",
    plug: "M20.5 11H19V7c0-1.1-.9-2-2-2h-4V3.5C13 2.12 11.88 1 10.5 1S8 2.12 8 3.5V5H4c-1.1 0-1.99.9-1.99 2v3.8H3.5c1.49 0 2.7 1.21 2.7 2.7s-1.21 2.7-2.7 2.7H2V20c0 1.1.9 2 2 2h3.8v-1.5c0-1.49 1.21-2.7 2.7-2.7 1.49 0 2.7 1.21 2.7 2.7V22H17c1.1 0 2-.9 2-2v-4h1.5c1.38 0 2.5-1.12 2.5-2.5S21.88 11 20.5 11z",
    gear: "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 00.12-.61l-1.92-3.32a.488.488 0 00-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 00-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 00-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z",
    close: "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z",
  };

  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));
  const clamp = (v, a, b) => Math.min(b, Math.max(a, v));

  function h(tag, attrs, ...kids) {
    const el = document.createElement(tag);
    for (const k in attrs || {}) {
      const v = attrs[k];
      if (v === false || v == null) continue;
      if (k === "class") el.className = v;
      else if (k === "html") el.innerHTML = v;
      else if (k.startsWith("on")) el.addEventListener(k.slice(2), v);
      else el.setAttribute(k, v === true ? "" : v);
    }
    for (const kid of kids.flat(Infinity)) {
      if (kid == null || kid === false) continue;
      el.append(kid.nodeType ? kid : document.createTextNode(kid));
    }
    return el;
  }

  function icon(name, cls) {
    const s = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    s.setAttribute("viewBox", "0 0 24 24");
    s.setAttribute("class", cls || "i");
    const p = document.createElementNS("http://www.w3.org/2000/svg", "path");
    p.setAttribute("d", ICONS[name]);
    s.append(p);
    return s;
  }

  let S = null;
  const refreshers = [];

  function fmtBytes(n) {
    n = Number(n) || 0;
    if (n < 1024) return Math.round(n) + " Б";
    const u = ["КБ", "МБ", "ГБ", "ТБ"];
    let i = -1;
    do { n /= 1024; i++; } while (n >= 1024 && i < u.length - 1);
    return (n >= 100 ? n.toFixed(0) : n >= 10 ? n.toFixed(1) : n.toFixed(2)) + " " + u[i];
  }
  function fmtTime(s) {
    s = Math.max(0, Math.floor(s));
    const hh = Math.floor(s / 3600), mm = Math.floor((s % 3600) / 60), ss = s % 60;
    const p = (x) => String(x).padStart(2, "0");
    return (hh ? hh + ":" : "") + p(mm) + ":" + p(ss);
  }
  function selectedServer() {
    return S.servers.find((x) => x.id === S.selected) || null;
  }

  const DEFAULTS = {
    theme: 0, dynamic: false, amoled: false, tint: true,
    accent: { r: 64, g: 216, b: 208 },
    anim: { all: true, pages: true, controls: true, sheets: true, enabled: true, speed: 1, intensity: 1, tempo: 1, burst: true, waves: 3, idle: true, iconFx: true, press: true, haptics: true, ripple: true, list: true, fold: true, cards: true, pingFx: true, select: true, glow: 1 },
    look: { style: 0, contrast: 0, tintLevel: 1, secHue: 0, terHue: 60, radius: 1, density: 1 },
    button: { size: 224, lobes: 10, shape: 0 },
    textScale: 1, autoCheck: false, mask: 1, checkInterval: 60, updates: { auto: true },
    autoConnect: false, dns: 0, dnsCustom: "", mtu: 1500,
    pingMode: 0, pingTimeout: 1, autoPing: false, sortPing: true,
    tuning: { ipv6: true, log: 2, sniff: false, lan: false, quic: false, ds: 0, am: 0, apps: "", unm: false, idle: 300, buf: 0, mux: false, muxc: 8 },
  };
  const PRESETS = [
    ["Бирюза", 64, 216, 208], ["Синий", 92, 150, 255], ["Фиолетовый", 178, 140, 255], ["Розовый", 255, 125, 170],
    ["Красный", 255, 100, 90], ["Оранжевый", 255, 160, 60], ["Зелёный", 110, 220, 120], ["Белый", 255, 255, 255],
  ];

  function merge(base, over) {
    if (over === undefined || over === null) return base;
    if (typeof base !== "object" || base === null || Array.isArray(base)) return over;
    const out = {};
    for (const k in base) out[k] = merge(base[k], over[k]);
    for (const k in over) if (!(k in out)) out[k] = over[k];
    return out;
  }

  function toLin(c) { c /= 255; return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }
  function rgbToOklch(r, g, b) {
    const R = toLin(r), G = toLin(g), B = toLin(b);
    const l = Math.cbrt(0.4122214708 * R + 0.5363325363 * G + 0.0514459929 * B);
    const m = Math.cbrt(0.2119034982 * R + 0.6806995451 * G + 0.1073969566 * B);
    const s = Math.cbrt(0.0883024619 * R + 0.2817188376 * G + 0.6299787005 * B);
    const L = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s;
    const A = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s;
    const bb = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s;
    return [L, Math.hypot(A, bb), (Math.atan2(bb, A) * 180 / Math.PI + 360) % 360];
  }
  function hex2(n) { return clamp(Math.round(n), 0, 255).toString(16).padStart(2, "0"); }
  function toHex(c) { return "#" + hex2(c.r) + hex2(c.g) + hex2(c.b); }
  const COLOR_NAMES = {
    red: "ff0000", green: "00a000", blue: "0000ff", white: "ffffff", black: "000000", orange: "ff8000", purple: "8000ff", violet: "8f00ff", pink: "ff69b4",
    yellow: "ffd600", cyan: "00e5ff", teal: "009688", gray: "808080", grey: "808080", brown: "8d5524", lime: "76ff03", indigo: "3f51b5", magenta: "ff00ff",
    "красный": "ff0000", "зелёный": "00a000", "зеленый": "00a000", "синий": "0000ff", "белый": "ffffff", "чёрный": "000000", "черный": "000000", "оранжевый": "ff8000",
    "фиолетовый": "8f00ff", "розовый": "ff69b4", "жёлтый": "ffd600", "желтый": "ffd600", "голубой": "00b0ff", "бирюзовый": "009688", "серый": "808080",
  };
  function parseColor(t) {
    let s = String(t == null ? "" : t).trim().toLowerCase().replace(/[\u200b\ufeff]/g, "");
    if (!s) return null;
    const chan = (a, b, c) => {
      const v = [a, b, c].map(Number);
      if (v.some((x) => !isFinite(x) || x < 0 || x > 255)) return null;
      return { r: v[0], g: v[1], b: v[2] };
    };
    let m = /^rgba?\(\s*(\d{1,3})\s*[,\s]\s*(\d{1,3})\s*[,\s]\s*(\d{1,3})/.exec(s);
    if (m) return chan(m[1], m[2], m[3]);
    m = /^(\d{1,3})[\s,;.]+(\d{1,3})[\s,;.]+(\d{1,3})$/.exec(s);
    if (m) return chan(m[1], m[2], m[3]);
    if (COLOR_NAMES[s]) s = COLOR_NAMES[s];
    s = s.replace(/^(#|0x)/, "").replace(/\s+/g, "");
    if (/^[0-9a-f]{3,4}$/.test(s)) s = s.slice(0, 3).split("").map((x) => x + x).join("");
    else if (/^[0-9a-f]{8}$/.test(s)) s = s.slice(0, 6);
    if (!/^[0-9a-f]{6}$/.test(s)) return null;
    const n = parseInt(s, 16);
    return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 };
  }
  function parseHex(t) { return parseColor(t); }

  const THEME_KEYS = ["--primary", "--on-primary", "--primary-container", "--on-primary-container", "--secondary-container", "--on-secondary-container", "--tertiary",
    "--surface", "--sc-low", "--sc", "--sc-high", "--sc-highest", "--on-surface", "--on-surface-variant", "--outline", "--outline-variant", "--accent-text"];

  let themeKey = "", barsDark = null;
  function applyTheme() {
    const st = S.settings;
    const lk = Object.assign({}, DEFAULTS.look, st.look || {});
    const key = JSON.stringify([st.theme, st.dynamic, st.amoled, st.tint, st.accent, S.dark, S.colors, lk]);
    if (key === themeKey) return;
    themeKey = key;
    document.documentElement.style.setProperty("--rs", String(clamp(Number(lk.radius) || 1, 0.3, 2)));
    document.documentElement.style.setProperty("--dn", String(clamp(Number(lk.density) || 1, 0.7, 1.5)));
    srv.rowH = 68;
    srv.sig = "";
    const sysDark = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
    const dark = st.theme === 1 ? false : st.theme === 2 ? true : (S.dark !== undefined ? S.dark : sysDark);
    document.body.classList.toggle("light", !dark);
    if (barsDark !== dark) { barsDark = dark; Bridge.bars(dark); }
    document.body.classList.toggle("amoled", dark && !!st.amoled);
    const root = document.documentElement.style;
    const set = (k, v) => root.setProperty(k, v);
    if (st.dynamic && S.colors && S.colors.length === 8) {
      THEME_KEYS.forEach((k) => root.removeProperty(k));
      const c = S.colors.map((x) => "#" + (x & 0xffffff).toString(16).padStart(6, "0"));
      set("--primary", c[0]); set("--on-primary", c[1]); set("--primary-container", c[2]); set("--on-primary-container", c[3]);
      set("--secondary-container", c[4]); set("--on-secondary-container", c[5]); set("--tertiary", c[6]);
      return;
    }
    const acc = st.accent;
    const hex = toHex(acc);
    const [L, C, H] = rgbToOklch(acc.r, acc.g, acc.b);
    const styleI = clamp(Math.round(lk.style) || 0, 0, 4);
    const grey = C < 0.025 || styleI === 4;
    const ct = clamp(Math.round(lk.contrast) || 0, 0, 2);
    const tintMul = [1, 1.8, 1.4, 0.35, 0][styleI] * clamp(Number(lk.tintLevel), 0, 2);
    const tc = grey ? 0 : (st.tint ? clamp(C * 0.1, 0.004, 0.022) : 0.004) * tintMul;
    const pc = [30, 42, 36, 18, 12][styleI];
    const sc = [16, 22, 20, 8, 6][styleI];
    const secH = (H + (Number(lk.secHue) || 0) + (styleI === 2 ? 40 : 0) + 360) % 360;
    const useSecHue = !!Number(lk.secHue) || styleI === 2;
    const o = (l, c) => "oklch(" + l + " " + c + " " + H.toFixed(1) + ")";
    const o2 = (l, c) => "oklch(" + l + " " + c + " " + secH.toFixed(1) + ")";
    const sp = 1 + 0.4 * ct;
    const lv = (base, x) => (base + (x - base) * sp).toFixed(3);
    set("--primary", hex);
    set("--on-primary", L > 0.62 ? "oklch(.2 0 0)" : "oklch(.99 0 0)");
    if (dark) {
      set("--surface", o(0.17, tc)); set("--sc-low", o(lv(0.17, 0.2), tc)); set("--sc", o(lv(0.17, 0.225), tc * 1.1)); set("--sc-high", o(lv(0.17, 0.255), tc * 1.2)); set("--sc-highest", o(lv(0.17, 0.285), tc * 1.3));
      set("--on-surface", o(0.92 + 0.03 * ct, tc)); set("--on-surface-variant", o(0.77 + 0.07 * ct, tc * 1.5)); set("--outline", o(0.58 + 0.08 * ct, tc * 1.5)); set("--outline-variant", o(0.38 + 0.1 * ct, tc * 1.5));
      set("--primary-container", "color-mix(in oklab, " + hex + " " + (pc + 8 * ct) + "%, " + o(0.2, tc) + ")");
      set("--on-primary-container", grey ? "oklch(.94 0 0)" : o(0.92, Math.min(C, 0.05)));
      set("--secondary-container", useSecHue && !grey ? o2(0.28 + 0.02 * ct, Math.min(C * 0.5, 0.075)) : "color-mix(in oklab, " + hex + " " + (sc + 5 * ct) + "%, " + o(0.27, tc) + ")");
      set("--on-secondary-container", useSecHue && !grey ? o2(0.93, 0.03) : o(0.92, tc));
      set("--accent-text", "oklch(" + Math.max(L, 0.74 + 0.05 * ct).toFixed(3) + " " + (grey ? 0 : C.toFixed(3)) + " " + H.toFixed(1) + ")");
    } else {
      set("--surface", o(0.985, tc)); set("--sc-low", o(lv(0.985, 0.968), tc)); set("--sc", o(lv(0.985, 0.955), tc * 1.1)); set("--sc-high", o(lv(0.985, 0.94), tc * 1.2)); set("--sc-highest", o(lv(0.985, 0.92), tc * 1.3));
      set("--on-surface", o(0.22 - 0.07 * ct, tc)); set("--on-surface-variant", o(0.42 - 0.09 * ct, tc * 1.5)); set("--outline", o(0.55 - 0.1 * ct, tc * 1.5)); set("--outline-variant", o(0.8 - 0.12 * ct, tc * 1.5));
      set("--primary-container", "color-mix(in oklab, " + hex + " " + (pc - 2 + 8 * ct) + "%, white)");
      set("--on-primary-container", o(0.25, Math.min(C, 0.08)));
      set("--secondary-container", useSecHue && !grey ? o2(0.91 - 0.02 * ct, Math.min(C * 0.4, 0.06)) : "color-mix(in oklab, " + hex + " " + (sc + 4 * ct) + "%, white)");
      set("--on-secondary-container", useSecHue && !grey ? o2(0.25, 0.05) : o(0.25, tc));
      set("--accent-text", "oklch(" + Math.min(L, 0.5 - 0.06 * ct).toFixed(3) + " " + (grey ? 0 : C.toFixed(3)) + " " + H.toFixed(1) + ")");
    }
    const terC = grey ? 0 : [0.1, 0.15, 0.13, 0.07, 0][styleI];
    set("--tertiary", "oklch(" + (dark ? 0.8 : 0.5) + " " + terC + " " + ((H + (Number(lk.terHue) || 0) + 360) % 360).toFixed(1) + ")");
  }

  let motionKey = "";
  function applyMotionFlags() {
    const a = S.settings.anim;
    const mk = JSON.stringify([a, S.settings.button, S.settings.textScale]);
    if (mk === motionKey) return;
    motionKey = mk;
    const b = document.body.classList;
    b.toggle("no-motion", !a.all);
    b.toggle("no-pages", !a.pages);
    b.toggle("no-controls", !a.controls);
    b.toggle("no-sheets", !a.sheets);
    b.toggle("no-burst", !a.burst || !a.enabled || !a.all);
    b.toggle("no-ripple", !a.ripple);
    b.toggle("no-list", !a.list);
    b.toggle("no-fold", !a.fold);
    b.toggle("no-cards", !a.cards);
    b.toggle("no-pingfx", !a.pingFx);
    b.toggle("no-select", !a.select);
    const root = document.documentElement.style;
    root.setProperty("--ts", String(clamp(Number(S.settings.textScale) || 1, 0.7, 1.6)));
    root.setProperty("--tempo", String(clamp(a.tempo, 0.2, 3)));
    root.setProperty("--k", String(1 / clamp(a.speed, 0.25, 3)));
    root.setProperty("--glow", String(a.all ? clamp(a.glow, 0, 1) : clamp(a.glow, 0, 1)));
    root.setProperty("--waves", String(clamp(Math.round(a.waves), 1, 5)));
    root.setProperty("--power", clamp(S.settings.button.size, 160, 280) + "px");
    $$(".ring").forEach((r, i) => r.classList.toggle("used", i < clamp(Math.round(a.waves), 1, 5)));
  }

  const Power = (() => {
    let root;
    function build() {
      const NS = "http://www.w3.org/2000/svg";
      const shape = document.createElementNS(NS, "svg");
      shape.setAttribute("class", "shape");
      shape.setAttribute("viewBox", "-112 -112 224 224");
      const path = document.createElementNS(NS, "path");
      const bt = (S && S.settings && S.settings.button) || DEFAULTS.button;
      const lobes = clamp(Math.round(bt.lobes), 3, 16);
      let d = "";
      for (let i = 0; i < 120; i++) {
        const a = (i / 120) * Math.PI * 2;
        const r = 98 * (1 + 0.05 * Math.cos(lobes * a));
        d += (i ? "L" : "M") + (Math.cos(a) * r).toFixed(2) + " " + (Math.sin(a) * r).toFixed(2);
      }
      path.setAttribute("d", d + "Z");
      shape.append(path);
      root = h("button", { class: "power", "aria-label": "Подключить или отключить" },
        h("span", { class: "halo" }), h("span", { class: "arc" }), h("span", { class: "disc" }, shape, icon("power", "pwr")));
      root.addEventListener("click", toggle);
      return root;
    }
    function kick() {
      const b = document.body.classList;
      b.toggle("on", S.status === "On");
      b.toggle("busy", S.status === "Connecting" || S.status === "Stopping");
    }
    function toggle() {
      if (S.settings.anim.haptics) Bridge.act("haptic", S.status === "Off" ? "strong" : "light");
      if (S.status === "Off") {
        if (!S.selected) { snack("Сначала добавьте сервер"); go("servers"); return; }
        S.status = "Connecting";
        updateHome();
        Bridge.act("connect").then((r) => {
          if (r && r.conflict) { S.status = "Off"; updateHome(); conflictSheet(r.conflict); }
          else if (r && r.error) { S.status = "Off"; updateHome(); snack(r.error); }
          setTimeout(syncNow, 700);
        });
      } else if (S.status === "On" || S.status === "Connecting") {
        S.status = "Stopping";
        updateHome();
        Bridge.act("disconnect").then(() => setTimeout(syncNow, 700));
      }
    }
    return { build, kick, pause() {} };
  })();

  function snack(text) {
    const el = $("#snack");
    el.textContent = text;
    el.classList.add("show");
    clearTimeout(snack.t);
    snack.t = setTimeout(() => el.classList.remove("show"), 2600);
  }

  let current = "home";
  const order = ["home", "servers", "settings"];
  function go(name) {
    if (name === current) return;
    const from = order.indexOf(current), to = order.indexOf(name);
    $$(".view").forEach((v) => {
      const idx = order.indexOf(v.dataset.view);
      v.classList.toggle("active", v.dataset.view === name);
      v.classList.toggle("left", idx < to);
    });
    $$(".nav-item").forEach((n) => n.classList.toggle("active", n.dataset.go === name));
    current = name;
    if (name === "servers") { srv.fresh = true; srv.sig = ""; renderServers(); }
    if (name === "settings" && dirty) { dirty = false; refreshers.forEach((f) => f()); fixCorners(); }
    const nv = $("#view-" + name);
    if (nv && name !== "servers" && !document.body.classList.contains("no-cards")) {
      Array.from(nv.querySelectorAll(".tile,.chip-card,:scope > .list,:scope > .group-title,:scope > .plugin-group")).slice(0, 14).forEach((el, i) => el.style.setProperty("--n", String(i)));
      nv.classList.add("enter");
      setTimeout(() => nv.classList.remove("enter"), 1300);
    }
    Power.pause(380);
    $("#fab") && $("#fab").classList.toggle("hide", name !== "servers");
    if (name === "home") Power.kick();
    void from;
  }

  async function syncNow() {
    try { onState(await Bridge.state()); } catch (e) {}
  }

  const home = {};
  const SVC_COLORS = ["#5865F2", "#27A7E7", "#FF0033"];
  const SVC_LETTERS = ["D", "T", "Y"];

  function tile(cls, title, ...kids) {
    return h("div", { class: "tile " + cls }, h("div", { class: "tile-title" }, title), ...kids);
  }

  function buildHome() {
    const v = $("#view-home");
    home.word = h("span", { class: "status-word" }, "Отключено");
    home.sub = h("div", { class: "body sub" }, "");
    home.down = h("b", {}, "0 Б/с");
    home.up = h("b", {}, "0 Б/с");
    home.time = h("span", {}, "00:00");
    home.proto = h("div", { class: "tile-big" }, "—");
    home.dots = h("div", { class: "svc-dots" });
    home.pingName = h("div", { class: "tile-small" }, "");
    home.pingVal = h("div", { class: "tile-big" }, "—");
    home.bestName = h("div", { class: "tile-small" }, "");
    home.bestVal = h("div", { class: "tile-big" }, "—");
    home.sig = {};

    const speed = tile("t-speed", "Скорость",
      h("div", { class: "spd" }, icon("down"), home.down),
      h("div", { class: "spd" }, icon("up"), home.up),
      h("div", { class: "tile-small" }, icon("clock"), home.time));
    const proto = tile("t-proto", "Протокол", home.proto, home.dots);
    home.pingTile = h("button", { class: "tile tap t-ping", onclick: () => {
      const sv = selectedServer();
      if (!sv) { snack("Сначала добавьте сервер"); go("servers"); return; }
      home.pingBusy = true; renderTiles();
      Bridge.act("pingOne", sv.id).then(() => { home.pingBusy = false; renderTiles(); });
    } }, h("div", { class: "tile-title" }, "Пинг сервера"), home.pingVal, home.pingName);
    home.bestTile = h("button", { class: "tile tap t-best", onclick: () => {
      if (!S.servers.length) { snack("Сначала добавьте сервер"); go("servers"); return; }
      home.allBusy = true; renderTiles();
      Bridge.act("checkServices");
      Bridge.act("ping").then(() => { home.allBusy = false; renderTiles(); });
    } }, h("div", { class: "tile-title" }, "Все подписки"), home.bestVal, home.bestName);

    v.append(
      h("div", { class: "home-head" }, h("h1", { class: "headline" }, home.word), home.sub),
      h("div", { class: "stage" }, Power.build()),
      h("div", { class: "tiles" }, speed, proto, home.pingTile, home.bestTile)
    );
  }

  const WORDS = { Off: "Отключено", Connecting: "Подключаю…", On: "Подключено", Stopping: "Отключаю…" };
  function updateHome() {
    const sv = selectedServer();
    const word = WORDS[S.status] || "";
    if (home.word.textContent !== word) home.word.textContent = word;
    let sub;
    if (S.error) sub = S.error;
    else if (!sv) sub = "Добавьте сервер, чтобы начать";
    else if (S.status === "On") sub = "Защищённое соединение через " + sv.name;
    else sub = "Нажмите, чтобы подключиться к " + sv.name;
    if (home.sub.textContent !== sub) home.sub.textContent = sub;
    home.down.textContent = fmtBytes(S.down) + "/с";
    home.up.textContent = fmtBytes(S.up) + "/с";
    home.time.textContent = fmtTime(S.uptime);
    Power.kick();
    renderTiles();
  }

  function setText(el, t) { if (el.textContent !== t) el.textContent = t; }

  function renderTiles() {
    const sv = selectedServer();
    setText(home.proto, sv ? sv.proto : "—");
    const names = S.services.names;
    if (!home.chips || home.chips.length !== names.length) {
      home.chips = names.map((n, i) => h("span", { class: "svc-chip" }, h("i", { style: "background:" + SVC_COLORS[i % 3] }, SVC_LETTERS[i % 3]), n));
      home.dots.replaceChildren(...home.chips);
    }
    const c = sv ? checkOf(sv.id) : { ms: [null, null, null], busy: false };
    home.chips.forEach((el, i) => {
      const raw = c.ms[i];
      const val = raw === -4 ? null : raw;
      const cls = c.busy && val == null ? "busy" : val == null ? "" : val > 0 ? (val < 400 ? "ok" : "slow") : val === -2 ? "slow" : "bad";
      const want = "svc-chip" + (cls ? " " + cls : "");
      if (el.className !== want) el.className = want;
    });
    const p = sv ? pingOf(sv.id) : { ms: null, busy: false };
    const busy = home.pingBusy || p.busy;
    setText(home.pingVal, busy ? "…" : p.ms > 0 ? p.ms + " мс" : "—");
    home.pingVal.className = "tile-big " + (busy || !(p.ms > 0) ? "" : p.ms < 150 ? "ok" : p.ms < 350 ? "slow" : "bad");
    setText(home.pingName, sv ? sv.name : "Сервер не выбран");
    let best = null, bestMs = 1e9;
    for (const x of S.servers) {
      const q = pingOf(x.id);
      if (q.ms > 0 && q.ms < bestMs) { bestMs = q.ms; best = x; }
    }
    const allBusy = home.allBusy || pingBusy();
    setText(home.bestVal, allBusy ? "Проверяю…" : best ? best.name : "Проверить");
    setText(home.bestName, best && !allBusy ? bestMs + " мс · " + S.servers.length + " серв." : "Пинг и сервисы всех серверов");
  }

  function pingOf(id) {
    const v = S.pings ? S.pings[id] : null;
    if (v && typeof v === "object") return { ms: v.ms == null ? null : v.ms, busy: !!v.busy };
    return { ms: v == null ? null : v, busy: false };
  }

  function pingChip(id) {
    const p = pingOf(id);
    if (p.busy) return h("span", { class: "ping" }, "…");
    if (p.ms == null || p.ms <= 0) return h("span", { class: "ping" }, "—");
    const c = p.ms < 150 ? "var(--good)" : p.ms < 350 ? "var(--warn)" : "var(--error)";
    return h("span", { class: "ping", style: "--c:" + c }, p.ms + " мс");
  }

  function sortKey(id) {
    const p = pingOf(id);
    return p.ms == null || p.ms <= 0 ? 1e9 : p.ms;
  }

  function groupUrl(name) {
    const g = (S.groups || []).find((x) => x.name === name);
    return g ? g.url : "";
  }

  const srv = { local: false, sig: "" };
  function pingBusy() { return srv.local || Object.keys(S.pings || {}).some((k) => pingOf(k).busy); }
  function checkOf(id) {
    const c = S.services && S.services.checks ? S.services.checks[id] : null;
    return c || { ms: [null, null, null], busy: false };
  }
  function dotsFor(id) {
    const c = checkOf(id);
    const names = S.services.names;
    return h("span", { class: "dots", title: names.join(", ") }, [0, 1, 2].map((i) => {
      const raw = c.ms[i];
      const v = raw === -4 ? null : raw;
      const cls = c.busy && v == null ? "busy" : v == null ? "" : v > 0 ? (v < 400 ? "ok" : "slow") : v === -2 ? "slow" : "bad";
      return h("i", { class: "dot " + cls });
    }));
  }
  const drag = { on: false, suppress: false };
  function collapsedList() {
    if (Array.isArray(S.collapsed)) return S.collapsed;
    try { return JSON.parse(localStorage.getItem("tunn.collapsed") || "[]"); } catch (e) { return []; }
  }
  function isCollapsed(g) { return collapsedList().indexOf(g) >= 0; }
  function toggleCollapsed(g) {
    const l = collapsedList().slice();
    const i = l.indexOf(g);
    if (i >= 0) l.splice(i, 1); else l.push(g);
    S.collapsed = l;
    srv.toggled = g;
    try { localStorage.setItem("tunn.collapsed", JSON.stringify(l)); } catch (e) {}
    Bridge.act("setCollapsed", l);
    srv.sig = "";
    renderServers();
    fixCorners();
  }
  function fmtBytes(n) {
    const u = ["Б", "КБ", "МБ", "ГБ", "ТБ"];
    let i = 0, x = Math.max(0, n);
    while (x >= 1024 && i < 4) { x /= 1024; i++; }
    return (i < 2 || x >= 100 ? Math.round(x) : x.toFixed(1)) + " " + u[i];
  }
  function fmtDate(sec) {
    const d = new Date(sec * 1000);
    const p = (n) => (n < 10 ? "0" : "") + n;
    return p(d.getDate()) + "." + p(d.getMonth() + 1) + "." + d.getFullYear();
  }
  function groupInfo(name) {
    const g = (S.groups || []).find((x) => x.name === name);
    if (!g) return null;
    const parts = [];
    let bar = null;
    if (g.total != null && g.total >= 0) {
      parts.push(h("span", { class: "gi-main" }, "Израсходовано " + fmtBytes(g.used || 0) + " из " + (g.total === 0 ? "∞" : fmtBytes(g.total))));
      if (g.total > 0) bar = h("div", { class: "gi-bar" }, h("i", { style: "width:" + clamp(((g.used || 0) / g.total) * 100, 0, 100) + "%" }));
    }
    if (g.expire && g.expire > 0) {
      const days = Math.ceil((g.expire * 1000 - Date.now()) / 864e5);
      parts.push(h("span", { class: "gi-main" + (days <= 0 ? " bad" : days <= 3 ? " warn" : "") }, days > 0 ? "Подписка до " + fmtDate(g.expire) + " · осталось " + days + " дн." : "Подписка истекла " + fmtDate(g.expire)));
    }
    if (!parts.length) return null;
    return h("div", { class: "group-info" }, parts, bar);
  }
  const PEEK_MS = 800;
  let peekEl = null;
  function peekLines(el) {
    const txt = (n) => (n && n.textContent ? n.textContent.trim() : "");
    const q = (sel) => Array.from(el.querySelectorAll(sel)).map(txt).filter(Boolean);
    if (el.classList.contains("tile")) {
      const title = txt(el.querySelector(".tile-title"));
      const chips = Array.from(el.querySelectorAll(".svc-chip")).map((c) => {
        const st = c.classList.contains("ok") ? " · работает" : c.classList.contains("slow") ? " · медленно" : c.classList.contains("bad") ? " · недоступен" : "";
        return txt(c.lastChild) + st;
      });
      const rest = q(".tile-big, .spd, .tile-small").concat(chips);
      return { title: rest[0] || title, sub: [title].concat(rest.slice(1)) };
    }
    const t = txt(el.querySelector(".t, .name, .hp-sub-name, .headline"));
    const s = q(".s, .sub, .body, .hp-sub-meta, em");
    if (t) {
      const extra = el.dataset.sid ? q(".ping") : [];
      return { title: t, sub: s.concat(extra) };
    }
    const lines = (el.innerText || "").split("\n").map((x) => x.trim()).filter(Boolean);
    return { title: lines[0] || "", sub: lines.slice(1) };
  }
  function closePeek(fast) {
    const p = peekEl;
    if (!p) return false;
    peekEl = null;
    const end = () => { p.card.remove(); p.scrim.remove(); };
    if (fast) { end(); return true; }
    p.scrim.style.opacity = "0";
    const a = p.card.animate([{ opacity: 1, transform: "scale(1)" }, { opacity: 0, transform: "scale(.96)" }], { duration: 170, easing: "ease-in", fill: "forwards" });
    a.onfinish = end;
    return true;
  }
  function openPeek(el) {
    closePeek(true);
    const info = peekLines(el);
    if (!info.title && !info.sub.length) return;
    try { navigator.vibrate && navigator.vibrate(12); } catch (e) {}
    const app = $("#app").getBoundingClientRect();
    const r = el.getBoundingClientRect();
    const scrim = h("div", { class: "peek-scrim", onclick: () => closePeek() });
    const card = h("div", { class: "peek" },
      h("div", { class: "peek-t" }, info.title),
      info.sub.map((x, i) => h("div", { class: "peek-s", style: "--d:" + (120 + i * 60) + "ms" }, x)));
    $("#app").append(scrim, card);
    const w = Math.min(app.width - 32, 520);
    const left = (app.width - w) / 2;
    card.style.width = w + "px";
    card.style.left = left + "px";
    const nat = card.offsetHeight;
    const top = clamp(r.top - app.top + r.height / 2 - nat / 2, 24, app.height - nat - 24);
    card.style.top = top + "px";
    const fromTop = r.top - app.top, fromLeft = r.left - app.left;
    card.animate([
      { top: fromTop + "px", left: fromLeft + "px", width: r.width + "px", height: r.height + "px", opacity: .2 },
      { top: top + "px", left: left + "px", width: w + "px", height: nat + "px", opacity: 1 }
    ], { duration: 420, easing: "cubic-bezier(.05,.7,.1,1)" });
    requestAnimationFrame(() => { scrim.style.opacity = "1"; });
    peekEl = { card, scrim };
  }
  (function () {
    const SKIP = "input,textarea,.switch,.btn,.icon-btn,.seg,.rgb-slider,.swatch,.fab,.nav-item,.peek,.row[data-sid]";
    const HOST = ".row,.tile,.chip-card,.hp-sub,.group-title,.svc-cell";
    let timer = 0, sx = 0, sy = 0, fired = 0;
    const stop = () => { clearTimeout(timer); timer = 0; };
    document.addEventListener("pointerdown", (e) => {
      stop();
      if (e.pointerType === "mouse" && e.button !== 0) return;
      const t = e.target.closest ? e.target : null;
      if (!t || t.closest(SKIP)) return;
      const host = t.closest(HOST);
      if (!host || host.classList.contains("drag-ghost")) return;
      sx = e.clientX; sy = e.clientY;
      timer = setTimeout(() => { timer = 0; fired = performance.now(); openPeek(host); }, PEEK_MS);
    }, { passive: true });
    document.addEventListener("pointermove", (e) => { if (timer && Math.hypot(e.clientX - sx, e.clientY - sy) > 10) stop(); }, { passive: true });
    document.addEventListener("pointerup", stop, { passive: true });
    document.addEventListener("pointercancel", stop, { passive: true });
    document.addEventListener("scroll", stop, { capture: true, passive: true });
    document.addEventListener("contextmenu", (e) => { if (e.target.closest && e.target.closest(HOST)) e.preventDefault(); });
    document.addEventListener("click", (e) => { if (performance.now() - fired < 450) { e.stopPropagation(); e.preventDefault(); } }, true);
  })();

  function attachDrag(row, s) {
    let timer = null, sx = 0, sy = 0, pid = null, down = false, lastTap = 0, lx = 0, ly = 0;
    const cancel = () => { clearTimeout(timer); timer = null; };
    row.addEventListener("contextmenu", (e) => { e.preventDefault(); cancel(); if (!drag.on) openPeek(row); });
    row.addEventListener("pointerdown", (e) => {
      if (e.pointerType === "mouse" && e.button !== 0) return;
      if (e.target.closest(".icon-btn")) return;
      sx = e.clientX; sy = e.clientY; pid = e.pointerId; down = true;
      cancel();
      timer = setTimeout(() => { timer = null; down = false; startDrag(row, s, sx, sy, pid); }, 420);
    });
    row.addEventListener("pointermove", (e) => {
      if (!down) return;
      const far = Math.hypot(e.clientX - sx, e.clientY - sy);
      if (e.pointerType === "mouse" && far > 6 && !drag.on) { cancel(); down = false; startDrag(row, s, sx, sy, pid); return; }
      if (timer && far > 10) cancel();
    });
    const end = () => { down = false; cancel(); };
    row.addEventListener("pointerup", end);
    row.addEventListener("pointercancel", end);
    row.addEventListener("click", (e) => {
      if (e.target.closest(".icon-btn") || drag.suppress) return;
      const now = performance.now();
      if (now - lastTap < 340 && Math.hypot(e.clientX - lx, e.clientY - ly) < 32) { lastTap = 0; openPeek(row); return; }
      lastTap = now; lx = e.clientX; ly = e.clientY;
    });
  }
  function startDrag(row, s, sx, sy, pid) {
    const view = $("#view-servers");
    drag.on = true;
    const rect = row.getBoundingClientRect();
    const ghost = row.cloneNode(true);
    ghost.classList.remove("first", "last", "selected");
    ghost.classList.add("drag-ghost");
    ghost.style.cssText = "position:fixed;left:" + rect.left + "px;top:" + rect.top + "px;width:" + rect.width + "px;margin:0;z-index:60;pointer-events:none";
    $("#app").append(ghost);
    row.classList.add("drag-src");
    try { navigator.vibrate && navigator.vibrate(18); } catch (e) {}
    try { view.setPointerCapture(pid); } catch (e) {}
    let cy = sy, target = null, lastX = sx;
    const block = (e) => { if (e.cancelable) e.preventDefault(); };
    document.addEventListener("touchmove", block, { passive: false });
    const clear = () => {
      $$(".drop-before,.drop-after,.drop-head").forEach((x) => x.classList.remove("drop-before", "drop-after", "drop-head"));
    };
    const calc = (x, y) => {
      const els = document.elementsFromPoint(x, y);
      clear();
      target = null;
      for (const el of els) {
        const r = el.closest && el.closest(".row[data-sid]");
        if (r && r.getAttribute("data-sid") !== s.id && !r.classList.contains("drag-ghost")) {
          const box = r.getBoundingClientRect();
          const list = r.parentElement;
          const rows = Array.from(list.querySelectorAll(".row[data-sid]")).filter((x) => x.getAttribute("data-sid") !== s.id);
          const idx = rows.indexOf(r);
          const after = y > box.top + box.height / 2;
          const nextRow = after ? rows[idx + 1] : r;
          target = { group: list.getAttribute("data-gid") || "", before: nextRow ? nextRow.getAttribute("data-sid") : null };
          r.classList.add(after ? "drop-after" : "drop-before");
          return;
        }
        const t = el.closest && el.closest(".group-title[data-gid]");
        if (t) {
          const gid = t.getAttribute("data-gid") || "";
          const first = (srv.groupIds[gid] || []).filter((x) => x !== s.id)[0];
          target = { group: gid, before: first || null };
          t.classList.add("drop-head");
          return;
        }
      }
    };
    const place = () => { ghost.style.transform = "translateY(" + (cy - sy) + "px) scale(1.02)"; };
    const vr = view.getBoundingClientRect();
    const loop = setInterval(() => {
      if (cy < vr.top + 90) view.scrollTop -= 16;
      else if (cy > vr.bottom - 140) view.scrollTop += 16;
      else return;
      calc(lastX, cy);
    }, 16);
    const move = (e) => {
      cy = e.clientY; lastX = e.clientX; place(); calc(e.clientX, e.clientY);
    };
    const finish = (apply) => {
      clearInterval(loop);
      document.removeEventListener("touchmove", block);
      view.removeEventListener("pointermove", move);
      view.removeEventListener("pointerup", up);
      view.removeEventListener("pointercancel", cancelUp);
      const t = apply ? target : null;
      clear();
      ghost.remove();
      row.classList.remove("drag-src");
      drag.on = false;
      drag.suppress = true;
      setTimeout(() => { drag.suppress = false; }, 350);
      srv.sig = "";
      if (t) {
        if (S.settings.sortPing && t.group === (s.group || "")) { setPath("sortPing", false); snack("Сортировка по пингу выключена, порядок сохранён"); }
        Bridge.act("moveServer", { id: s.id, group: t.group, before: t.before });
      }
      renderServers();
    };
    const up = () => finish(true);
    const cancelUp = () => finish(false);
    view.addEventListener("pointermove", move);
    view.addEventListener("pointerup", up);
    view.addEventListener("pointercancel", cancelUp);
    place();
  }
  function renameSheet(title, current, onSave) {
    const input = h("input", { class: "hex wide", value: current, spellcheck: "false", maxlength: 60 });
    const ok = h("button", { class: "btn filled", onclick: () => { const t = input.value.trim(); if (!t) return; closeSheet(); onSave(t); } }, "Сохранить");
    openSheet(h("div", {}, h("h2", {}, title), h("div", { style: "margin:16px 0" }, input), h("div", { class: "actions" }, h("button", { class: "btn text", onclick: closeSheet }, "Отмена"), ok)));
    setTimeout(() => input.focus(), 350);
  }
  function fixList(l) {
    const kids = Array.from(l.children);
    kids.forEach((x) => x.classList.remove("first", "last"));
    const vis = kids.filter((x) => !x.hidden);
    if (vis.length) { vis[0].classList.add("first"); vis[vis.length - 1].classList.add("last"); }
  }
  srv.rows = new Map();
  srv.top = null;
  srv.body = null;
  srv.topSig = "";
  function enterRow(el, i) {
    const b = document.body.classList;
    if (b.contains("no-list") || b.contains("no-motion") || i > 14) return;
    el.style.setProperty("--i", String(i));
    el.classList.add("enter");
    setTimeout(() => { el.classList.remove("enter"); }, 1200);
  }
  document.addEventListener("pointerdown", (e) => {
    const b = document.body.classList;
    if (b.contains("no-ripple") || b.contains("no-motion") || b.contains("no-controls")) return;
    const el = e.target.closest && e.target.closest(".row:not(.drag-ghost),.btn,.icon-btn,.chip-card,.fab,.dev,.seg button");
    if (!el || el.disabled) return;
    const r = el.getBoundingClientRect();
    const size = Math.max(r.width, r.height) * 2.2;
    const sp = document.createElement("span");
    sp.className = "rp";
    sp.style.cssText = "width:" + size + "px;height:" + size + "px;left:" + (e.clientX - r.left - size / 2) + "px;top:" + (e.clientY - r.top - size / 2) + "px";
    el.classList.add("rp-host");
    el.append(sp);
    setTimeout(() => sp.remove(), 800);
  }, { passive: true });
  const VIRT_MIN = 40;
  srv.virt = [];
  srv.groupIds = {};
  srv.byId = {};
  srv.rowH = 68;
  srv.raf = 0;
  srv.fresh = true;
  srv.toggled = null;
  srv.enterI = 0;
  function updateWindows(force, scroll, again) {
    const v = $("#view-servers");
    if (!srv.virt.length || !v.clientHeight) return;
    const st = scroll == null ? v.scrollTop : scroll;
    const vr = v.getBoundingClientRect();
    const buf = v.clientHeight;
    let remeasure = false;
    srv.virt.forEach((w) => {
      const n = w.ids.length;
      const rh = srv.rowH;
      const boxTop = w.box.getBoundingClientRect().top - vr.top + v.scrollTop;
      let s0 = Math.max(0, Math.floor((st - buf - boxTop) / rh));
      let e0 = Math.min(n, Math.max(s0, Math.ceil((st + v.clientHeight + buf - boxTop) / rh)));
      if (!force && w.start >= 0 && s0 >= w.start && e0 <= w.end) return;
      s0 = Math.max(0, s0 - 8);
      e0 = Math.min(n, e0 + 8);
      w.start = s0;
      w.end = e0;
      const els = [];
      for (let i = s0; i < e0; i++) {
        const sv = srv.byId[w.ids[i]] || S.servers.find((x) => x.id === w.ids[i]);
        if (!sv) continue;
        let r = srv.rows.get(sv.id);
        if (!r) { r = makeRow(sv); srv.rows.set(sv.id, r); }
        paintRow(r, sv);
        if (srv.fresh && w.start0 === undefined) enterRow(r.el, srv.enterI++);
        r.el.classList.toggle("first", i === 0);
        r.el.classList.toggle("last", i === n - 1);
        els.push(r.el);
      }
      w.top.style.height = (s0 * rh) + "px";
      w.bottom.style.height = ((n - e0) * rh) + "px";
      w.box.replaceChildren(w.top, ...els, w.bottom);
      w.start0 = 1;
      if (els.length) {
        const m = els[0].getBoundingClientRect().height + 2;
        if (m > 20 && Math.abs(m - srv.rowH) > 0.5) { srv.rowH = m; remeasure = true; }
      }
    });
    if (remeasure && !again) updateWindows(true, st, true);
  }
  function onServersScroll() {
    if (srv.raf) return;
    srv.raf = requestAnimationFrame(() => { srv.raf = 0; updateWindows(false); });
  }
  function makeRow(s) {
    const id = s.id;
    const cur = () => S.servers.find((x) => x.id === id) || s;
    const avatar = h("div", { class: "avatar" });
    const t = h("div", { class: "t" });
    const sub = h("div", { class: "s" });
    const side = h("div", { class: "side" });
    const pingEl = h("span", { class: "ping" }, "—");
    const dotsEl = h("span", { class: "dots" }, [0, 1, 2].map(() => h("i", { class: "dot" })));
    side.append(pingEl, dotsEl);
    const row = h("button", { class: "row", "data-sid": id, onclick: () => { if (drag.suppress) return; Bridge.act("select", id); } },
      avatar,
      h("div", { class: "grow" }, t, sub),
      side,
      h("span", { class: "icon-btn", role: "button", "aria-label": "Переименовать", onclick: (e) => { e.stopPropagation(); renameSheet("Название сервера", cur().name, (x) => Bridge.act("renameServer", { id, name: x })); } }, icon("edit")),
      h("span", { class: "icon-btn", role: "button", "aria-label": "Удалить", onclick: (e) => { e.stopPropagation(); Bridge.act("remove", id); snack("Сервер удалён"); } }, icon("del")));
    attachDrag(row, s);
    return { el: row, avatar, t, sub, side, pingEl, dotsEl, sig: "", sel: false };
  }
  function paintRow(r, s) {
    const p = pingOf(s.id);
    const sel = s.id === S.selected;
    const sig = s.name + "|" + s.proto + "|" + s.endpoint + "|" + sel + "|" + (p.busy ? "b" : p.ms) + "|" + JSON.stringify(checkOf(s.id));
    if (sig === r.sig) return;
    r.sig = sig;
    const pingSig = p.busy ? "b" : String(p.ms);
    const pingChanged = r.pingSig !== undefined && r.pingSig !== pingSig && !p.busy;
    r.pingSig = pingSig;
    r.el.classList.toggle("selected", sel);
    if (sel && r.wasSel === false) {
      r.el.classList.add("sel-pop");
      setTimeout(() => r.el.classList.remove("sel-pop"), 500);
    }
    r.wasSel = sel;
    r.avatar.textContent = Array.from(s.name)[0].toUpperCase();
    r.t.textContent = s.name;
    r.sub.textContent = s.proto + " · " + s.endpoint;
    const pe = r.pingEl;
    const txt = p.busy ? "…" : p.ms == null || p.ms <= 0 ? "—" : p.ms + " мс";
    if (pe.textContent !== txt) pe.textContent = txt;
    if (p.busy || p.ms == null || p.ms <= 0) pe.style.removeProperty("--c");
    else pe.style.setProperty("--c", p.ms < 150 ? "var(--good)" : p.ms < 350 ? "var(--warn)" : "var(--error)");
    if (pingChanged) { pe.classList.remove("pop"); void pe.offsetWidth; pe.classList.add("pop"); }
    const ck = checkOf(s.id);
    for (let i = 0; i < 3; i++) {
      const raw = ck.ms[i];
      const v = raw === -4 ? null : raw;
      const cls = ck.busy && v == null ? "busy" : v == null ? "" : v > 0 ? (v < 400 ? "ok" : "slow") : v === -2 ? "slow" : "bad";
      const d = r.dotsEl.children[i];
      const want = "dot" + (cls ? " " + cls : "");
      if (d.className !== want) d.className = want;
    }
  }
  function renderServers() {
    if (drag.on) { srv.dirty = true; return; }
    const v = $("#view-servers");
    if (!srv.top) {
      srv.top = h("div", {});
      srv.body = h("div", {});
      v.replaceChildren(srv.top, srv.body);
    }
    const busy = pingBusy();
    const chk = !!S.services.checking;
    const topSig = busy + "|" + chk + "|" + (S.servers.length ? 1 : 0) + "|" + !!srv.importing;
    if (topSig !== srv.topSig) {
      srv.topSig = topSig;
      const pingBtn = h("button", { class: "btn tonal" + (busy ? " busy" : ""), onclick: async () => {
        if (pingBusy()) return;
        srv.local = true; srv.topSig = ""; renderServers();
        await Bridge.act("ping");
        srv.local = false; srv.topSig = ""; renderServers();
      } }, icon(busy ? "refresh" : "bolt"), busy ? "Проверяю…" : "Проверить пинг");
      const svcBtn = h("button", { class: "btn tonal" + (chk ? " busy" : ""), onclick: () => { if (!S.services.checking && S.servers.length) Bridge.act("checkServices"); } }, icon(chk ? "refresh" : "check"), chk ? "Проверяю…" : "Сервисы");
      const parts = [h("div", { class: "top-row two" }, h("h1", { class: "headline" }, "Серверы"), h("div", { class: "tools" }, svcBtn, pingBtn))];
      if (srv.importing) {
        parts.push(h("div", { class: "importing" },
          h("div", { class: "busy-line" }, h("div", { class: "bar" }), h("div", { class: "body" }, "Загружаю серверы…")),
          [0, 1, 2, 3, 4].map((i) => h("div", { class: "skel", style: "--i:" + i }, h("i", {}), h("div", {}, h("b", {}), h("s", {}))))));
      } else if (!S.servers.length) {
        parts.push(h("div", { class: "empty" }, h("div", { class: "title-large" }, "Серверов пока нет"), h("div", { class: "body" }, "Вставьте ссылку vless://, trojan://, ss:// или адрес подписки")));
      }
      srv.top.replaceChildren(...parts);
    }
    const groups = {};
    S.servers.forEach((s) => { (groups[s.group || ""] = groups[s.group || ""] || []).push(s); });
    const names = Object.keys(groups);
    if (!busy || !srv.rank) { srv.rank = {}; S.servers.forEach((s) => { srv.rank[s.id] = sortKey(s.id); }); }
    names.forEach((g) => { if (S.settings.sortPing) groups[g].sort((x, y) => (srv.rank[x.id] == null ? 1e9 : srv.rank[x.id]) - (srv.rank[y.id] == null ? 1e9 : srv.rank[y.id])); });
    const collapsed = collapsedList();
    const key = JSON.stringify([names.map((g) => [g, groups[g].map((x) => x.id)]), S.groups, collapsed]);
    if (key !== srv.sig) {
      srv.sig = key;
      const keep = v.scrollTop;
      const live = new Set(S.servers.map((x) => x.id));
      srv.rows.forEach((_, id) => { if (!live.has(id)) srv.rows.delete(id); });
      const frag = document.createDocumentFragment();
      const boxes = [];
      srv.virt = [];
      srv.groupIds = {};
      srv.enterI = 0;
      names.forEach((g) => {
        const list = groups[g];
        srv.groupIds[g] = list.map((x) => x.id);
        const folded = collapsed.indexOf(g) >= 0;
        const actions = h("div", { class: "group-actions" },
          h("button", { class: "icon-btn sm", "aria-label": "Проверить пинг группы", onclick: () => { Bridge.act("pingGroup", g); } }, icon("bolt")),
          g ? h("button", { class: "icon-btn sm", "aria-label": "Переименовать группу", onclick: () => renameSheet("Название группы", g, async (t) => { const r = await Bridge.act("renameGroup", { old: g, name: t }); if (r && r.error) snack(r.error); }) }, icon("edit")) : null,
          g && groupUrl(g) ? h("button", { class: "icon-btn sm", "aria-label": "Обновить подписку", onclick: async () => { const r = await Bridge.act("refreshGroup", g); snack(r && r.error ? r.error : "Подписка обновлена"); } }, icon("refresh")) : null,
          g ? h("button", { class: "icon-btn sm", "aria-label": "Удалить группу", onclick: () => { Bridge.act("removeGroup", g); snack("Группа удалена"); } }, icon("del")) : null);
        const tg = srv.toggled === g;
        const fold = h("button", { class: "fold" + (folded ? " closed" : "") + (tg ? " tg" : ""), "aria-label": folded ? "Развернуть группу" : "Свернуть группу", onclick: () => { toggleCollapsed(g); } },
          icon("chevron"), h("span", {}, g || "Мои серверы"), folded ? h("em", {}, String(list.length)) : null);
        frag.append(h("div", { class: "group-title", "data-gid": g }, fold, actions));
        const info = groupInfo(g);
        if (info) frag.append(info);
        if (folded) return;
        const box = h("div", { class: "list", "data-gid": g });
        if (tg && !folded && !document.body.classList.contains("no-fold")) box.classList.add("unfold");
        if (list.length > VIRT_MIN) {
          const top = h("div", { class: "vspace" });
          const bottom = h("div", { class: "vspace" });
          box.classList.add("virt");
          box.append(top, bottom);
          srv.virt.push({ box, ids: srv.groupIds[g], top, bottom, start: -1, end: -1 });
        } else {
          const rowsFrag = document.createDocumentFragment();
          list.forEach((s) => {
            let r = srv.rows.get(s.id);
            if (!r) { r = makeRow(s); srv.rows.set(s.id, r); }
            rowsFrag.append(r.el);
            if (srv.fresh) enterRow(r.el, srv.enterI++);
          });
          box.append(rowsFrag);
          boxes.push(box);
        }
        frag.append(box);
      });
      srv.body.replaceChildren(frag);
      boxes.forEach(fixList);
      updateWindows(true, keep);
      v.scrollTop = keep;
      srv.fresh = false;
      srv.toggled = null;
    }
    srv.byId = {};
    S.servers.forEach((s) => { srv.byId[s.id] = s; });
    S.servers.forEach((s) => {
      const r = srv.rows.get(s.id);
      if (r && r.el.isConnected) paintRow(r, s);
    });
  }

  let liveRender = null;
  function openLive(render) {
    openSheet(render());
    liveRender = render;
  }
  function refreshLive() {
    if (!liveRender || !$("#sheet").classList.contains("show")) return;
    const sh = $("#sheet");
    const top = sh.scrollTop;
    sh.replaceChildren(h("div", { class: "grab" }), liveRender());
    sh.scrollTop = top;
  }
  function openSheet(content) {
    liveRender = null;
    const sh = $("#sheet");
    sh.replaceChildren(h("div", { class: "grab" }), content);
    $("#scrim").classList.add("show");
    requestAnimationFrame(() => requestAnimationFrame(() => sh.classList.add("show")));
  }
  function closeSheet() {
    liveRender = null;
    $("#sheet").classList.remove("show");
    $("#scrim").classList.remove("show");
  }

  function addSheet() {
    const ta = h("textarea", { class: "field", placeholder: "vless://…  trojan://…  ss://…  wireguard://…  awg://…  vpn://…  https://подписка", spellcheck: "false" });
    const paste = h("button", { class: "btn text", onclick: async () => {
      const r = await Bridge.act("clipboard");
      if (r && r.text) ta.value = ta.value ? ta.value + "\n" + r.text : r.text;
    } }, icon("paste"), "Вставить");
    const ok = h("button", { class: "btn filled", onclick: async () => {
      const text = ta.value.trim();
      if (!text) return;
      if (srv.importing) { snack("Сначала дождитесь загрузки"); return; }
      srv.importing = true;
      srv.topSig = "";
      closeSheet();
      if (current !== "servers") go("servers"); else renderServers();
      const fab = $("#fab"); if (fab) fab.classList.add("disabled");
      let r;
      try { r = await Bridge.act("add", text); } finally {
        srv.importing = false;
        srv.topSig = "";
        if (fab) fab.classList.remove("disabled");
      }
      renderServers();
      snack(r && r.error ? r.error : r && r.added ? "Добавлено: " + r.added : "Уже есть в списке");
    } }, "Добавить");
    openSheet(h("div", {}, h("h2", {}, "Добавить сервер"), h("div", { class: "body" }, "Одна или несколько ссылок, либо адрес подписки"), ta,
      h("div", { class: "actions" }, paste, h("button", { class: "btn text", onclick: closeSheet }, "Отмена"), ok)));
  }


  const plg = { msg: "" };
  function plainRow(title, sub, ctl, onTap, selected) {
    return h(onTap ? "button" : "div", { class: "row" + (selected ? " selected" : ""), onclick: onTap }, h("div", { class: "grow" }, h("div", { class: "t" }, title), sub ? h("div", { class: "s" }, sub) : null), ctl);
  }
  function busyLine() {
    const b = S.plugins.busy;
    return b ? h("div", { class: "busy-line" }, h("div", { class: "bar" }), h("div", { class: "body" }, b)) : null;
  }
  function pluginFieldRow(p, f) {
    const cur = p.values[f.key];
    const set = (v) => { p.values[f.key] = v; Bridge.act("pluginSet", { key: p.key, name: f.key, value: v }); };
    if (f.type === "switch") {
      const sw_ = h("span", { class: "switch" + (cur ? " on" : "") });
      return h("button", { class: "row", onclick: () => { const v = !p.values[f.key]; sw_.classList.toggle("on", v); set(v); } }, h("div", { class: "grow" }, h("div", { class: "t" }, f.title || f.key), f.sub ? h("div", { class: "s" }, f.sub) : null), sw_);
    }
    if (f.type === "select") {
      const opts = f.options || [];
      const box = h("div", { class: "seg plain" }, opts.map((o, i) => h("button", { class: Number(cur) === i ? "on" : "", onclick: () => { set(i); box.querySelectorAll("button").forEach((b, j) => b.classList.toggle("on", j === i)); } }, o)));
      return h("div", { class: "row block" }, h("div", { class: "head" }, h("div", { class: "t" }, f.title || f.key)), box);
    }
    if (f.type === "text") {
      const ta = h("textarea", { class: "inline", placeholder: f.placeholder || "", spellcheck: "false" });
      ta.value = cur == null ? "" : String(cur);
      let t = 0;
      ta.addEventListener("input", () => { clearTimeout(t); t = setTimeout(() => set(ta.value), 250); });
      return h("div", { class: "row block" }, h("div", { class: "head" }, h("div", { class: "t" }, f.title || f.key)), ta);
    }
    const min = f.min != null ? f.min : 0, max = f.max != null ? f.max : 100, step = f.step || 1;
    const input = h("input", { type: "range", min, max, step });
    const val = h("div", { class: "v" }, "");
    const paint = (v) => { input.style.setProperty("--p", ((v - min) / (max - min)) * 100 + "%"); val.textContent = String(v); };
    input.value = Number(cur) || 0;
    paint(Number(input.value));
    input.addEventListener("input", () => { const v = Number(input.value); paint(v); set(v); });
    const prow = h("div", { class: "row block" }, h("div", { class: "head" }, h("div", { class: "t" }, f.title || f.key), val), input);
    guard(prow, input);
    return prow;
  }

  function openPluginSettings(key) {
    openSheet(h("div", {}, ...(() => {
      const p = S.plugins.list.find((x) => x.key === key);
      if (!p) return [h("h2", {}, "Плагин не найден")];
      return [h("h2", {}, p.name), h("div", { class: "body" }, (p.description || "") + " v" + p.version), h("div", { class: "list", style: "margin-top:12px" }, (p.schema || []).map((f) => pluginFieldRow(p, f))),
        h("div", { class: "actions split" }, h("button", { class: "btn filled", onclick: closeSheet }, "Готово"))];
    })()));
    fixCorners();
  }

  function urlSheet() {
    const input = h("input", { class: "hex wide", placeholder: "https://…/plugin.zip", spellcheck: "false" });
    const go_ = h("button", { class: "btn filled", onclick: async () => { const u = input.value.trim(); if (!u) return; closeSheet(); snack("Скачиваю…"); const r = await Bridge.act("installPluginUrl", { url: u, select: false }); snack(r && r.error ? r.error : "Плагин установлен"); } }, "Установить");
    openSheet(h("div", {}, h("h2", {}, "Плагин по ссылке"), h("div", { class: "body" }, "Прямая ссылка https на .zip с plugin.json"), h("div", { style: "margin:16px 0" }, input), h("div", { class: "actions" }, h("button", { class: "btn text", onclick: closeSheet }, "Отмена"), go_)));
  }

  function dpiLine(d) {
    if (!d) return "";
    if (d.state === "searching") return "подбор " + (d.done || 0) + "/" + (d.total || 0);
    return (d.strategy ? "стратегия: " + d.strategy : "ещё не подбиралась") + " · соединений: " + (d.ok || 0) + ", ошибок: " + (d.fail || 0);
  }
  let dpiPolling = false;
  async function pollDpi() {
    if (dpiPolling) return;
    dpiPolling = true;
    for (let i = 0; i < 150; i++) {
      await new Promise((r) => setTimeout(r, 2500));
      const r = await Bridge.act("dpiStatus");
      if (S.corePlugins && r) { S.corePlugins.dpi = r; refreshLive(); }
      if (!r || r.state !== "searching") break;
    }
    dpiPolling = false;
  }
  function corePluginRows() {
    const CP = S.corePlugins || { list: [], dpi: {} };
    return CP.list.map((p) => h("div", { class: "row plug" },
      h("div", { class: "grow" },
        h("div", { class: "t" }, p.name + (p.primary ? " · основное" : "")),
        h("div", { class: "s" }, p.description + (p.id === "dpi" && p.present ? " · " + dpiLine(CP.dpi) : ""))),
      p.core && p.present && p.enabled && !p.primary ? h("button", { class: "btn text sm", onclick: () => Bridge.act("corePlugin", { id: p.id, op: "primary" }) }, "Основным") : null,
      p.id === "dpi" && p.present && p.enabled ? h("button", { class: "btn text sm", onclick: () => { Bridge.act("dpiSearch"); snack("Подбор начался, это может занять до 5 минут и идёт в фоне"); pollDpi(); } }, "Заново") : null,
      p.present ? h("button", { class: "switch" + (p.enabled ? " on" : ""), "aria-label": "Включить", onclick: () => Bridge.act("corePlugin", { id: p.id, op: "enable", value: !p.enabled }) }) : null,
      p.present ? h("button", { class: "icon-btn", "aria-label": "Удалить", onclick: () => Bridge.act("corePlugin", { id: p.id, op: "delete" }) }, icon("del"))
        : (p.id === "xray" ? h("button", { class: "btn tonal sm", onclick: async () => { snack("Скачиваю Xray…"); const r = await Bridge.act("corePlugin", { id: "xray", op: "restore" }); if (r && r.error) snack(r.error); } }, icon("download"), "Скачать") : null)));
  }

  function openIconSheet() {
    openLive(() => {
      const I = S.appIcon || { current: "default", variants: [] };
      const swatch = { default: "linear-gradient(135deg,#5a3cf4,#14d2e6)", dark: "#0f1115", light: "#f3f1ff", you: "#6750a4", youlight: "#e9ddff" };
      const rows = I.variants.map((v) => h("div", { class: "row", onclick: () => Bridge.act("setIcon", v.id) },
        h("div", { style: "width:40px;height:40px;border-radius:12px;flex:none;background:" + (swatch[v.id] || "#888") }),
        h("div", { class: "grow" }, h("div", { class: "t" }, v.name), h("div", { class: "s" }, v.note)),
        h("div", { class: "t" }, I.current === v.id ? "✓" : "")));
      return h("div", {}, h("h2", {}, "Иконка приложения"),
        h("div", { class: "body" }, "На Android 13+ включите «Тематические значки» в настройках обоев и стиля: значок перекрасится под цвета обоев. Монохромный слой есть у всех вариантов. Смена значка может занять несколько секунд."),
        h("div", { class: "list" }, rows),
        h("div", { class: "actions" }, h("button", { class: "btn tonal", onclick: async () => { const r = await Bridge.act("pickIcon"); if (r && r.cancel) return; snack(r && r.error ? r.error : "Подтвердите добавление значка на главный экран"); } }, icon("add"), "Своя картинка")),
        h("div", { class: "body" }, "Своя картинка добавляется ярлыком на главный экран: заменить штатный значок произвольным изображением Android не позволяет."));
    });
  }

  function openPlugins() {
    plg.msg = "";
    if (S.corePlugins && S.corePlugins.dpi && S.corePlugins.dpi.state === "searching") pollDpi();
    openLive(() => {
      const P = S.plugins;
      const busy = !!P.busy;
      const kindName = { protocol: "протокол", rules: "правила", ui: "интерфейс" };
      const rows = P.list.map((p) => h("div", { class: "row plug" },
        h("div", { class: "grow" }, h("div", { class: "t" }, p.name + " " + p.version), h("div", { class: "s" }, (p.kinds || []).map((k) => kindName[k] || k).join(" · ") + (p.author ? " · " + p.author : ""))),
        (p.schema || []).length ? h("button", { class: "icon-btn", "aria-label": "Настройки плагина", onclick: () => openPluginSettings(p.key) }, icon("gear")) : null,
        h("button", { class: "switch" + (p.enabled ? " on" : ""), "aria-label": "Включить", onclick: () => { Bridge.act("pluginEnable", { key: p.key, value: !p.enabled }); if (p.hasUi) snack("Интерфейсные плагины применятся после перезапуска"); } }),
        h("button", { class: "icon-btn", "aria-label": "Удалить", onclick: () => Bridge.act("pluginDelete", p.key) }, icon("del"))));
      const out = h("div", {}, h("h2", {}, "Плагины"),
        h("div", { class: "body" }, "Расширения: свои протоколы (.so), правила обхода для приложений и доменов, дополнения интерфейса. Плагины выполняют код, ставьте только из доверенных источников."),
        busyLine(),
        h("div", { class: "group-title" }, h("span", {}, "Встроенные")),
        h("div", { class: "list" }, corePluginRows()),
        h("div", { class: "group-title" }, h("span", {}, "Установленные")),
        rows.length ? h("div", { class: "list" }, rows) : h("div", { class: "body" }, "Плагинов пока нет"),
        h("div", { class: "actions split" },
          h("button", { class: "btn tonal", disabled: busy, onclick: async () => { const r = await Bridge.act("pickPlugin"); if (r && r.cancel) return; plg.msg = r && r.error ? r.error : "Плагин установлен"; refreshLive(); } }, icon("add"), "Из файла"),
          h("button", { class: "btn tonal", disabled: busy, onclick: urlSheet }, icon("download"), "По ссылке")));
      if (plg.msg) out.append(h("div", { class: "msg" }, plg.msg));
      return out;
    });
    setTimeout(fixCorners, 0);
  }

  const pluginDefs = [];
  const pluginLoaded = new Set();
  let booted = false;
  const pluginUi = { groups: [], cards: [] };
  function pluginApi(id) {
    return {
      h, icon, snack, id,
      act: (name, payload) => Bridge.act(name, payload),
      state: () => S,
      onState: (cb) => Bridge.onState(cb),
      get: (name) => { const p = S.plugins.list.find((x) => x.id === id); return p ? p.values[name] : undefined; },
      set: (name, value) => { const p = S.plugins.list.find((x) => x.id === id); if (p) { p.values[name] = value; Bridge.act("pluginSet", { key: p.key, name, value }); } },
      addSettingsGroup: (title, nodes) => { const g = group(title, nodes); $("#view-settings").insertBefore(h("div", { class: "plugin-group" }, g), $("#view-settings .about")); fixCorners(); },
      addHomeCard: (node) => { $("#view-home").append(node); },
      sheet: (node) => openSheet(node),
      closeSheet,
    };
  }
  function runDef(d) {
    try { d.fn(pluginApi(d.id)); } catch (e) { console.error("plugin " + d.id, e); }
  }
  window.FasdeqPlugin = {
    define(id, fn) { const d = { id, fn }; pluginDefs.push(d); if (booted) runDef(d); },
  };
  function loadPluginUi() {
    const base = Bridge.native ? "https://appassets.androidplatform.net/plugins/" : "plugins/";
    (S.plugins.ui || []).forEach((entry) => {
      if (pluginLoaded.has(entry)) return;
      pluginLoaded.add(entry);
      const kind = entry.slice(0, entry.indexOf(":"));
      const path = entry.slice(entry.indexOf(":") + 1);
      if (kind === "css") document.head.append(h("link", { rel: "stylesheet", href: base + path }));
      else document.body.append(h("script", { src: base + path }));
    });
  }

  const coreUi = { rel: false, msg: "" };
  function fmtMb(n) { return (Number(n) / 1048576).toFixed(1) + " МБ"; }
  function openCores() {
    coreUi.msg = "";
    openLive(() => {
      const C = S.cores;
      const busy = !!C.busy;
      const coreRow = (selected, title, sub, onSelect, onDelete) => h("button", { class: "row" + (selected ? " selected" : ""), onclick: onSelect },
        h("div", { class: "avatar" }, selected ? icon("check") : icon("cpu")),
        h("div", { class: "grow" }, h("div", { class: "t" }, title), sub ? h("div", { class: "s" }, sub) : null),
        onDelete ? h("span", { class: "icon-btn", role: "button", "aria-label": "Удалить ядро", onclick: (e) => { e.stopPropagation(); onDelete(); } }, icon("del")) : null);
      const rows = [coreRow(!C.active, "Встроенное", C.builtin ? "Xray " + C.builtin : "Идёт в комплекте с приложением", () => Bridge.act("selectCore", ""), null)];
      C.list.forEach((c) => rows.push(coreRow(C.active === c.id, c.label, c.version ? "Xray " + c.version : "", () => Bridge.act("selectCore", c.id), () => Bridge.act("deleteCore", c.id))));
      const relList = C.releases === null || C.releases === undefined
        ? h("div", { class: "body" }, "Список версий загружается…")
        : h("div", { class: "list" }, C.releases.map((r) => h("div", { class: "row" },
          h("div", { class: "grow" }, h("div", { class: "t" }, r.tag), h("div", { class: "s" }, fmtMb(r.size))),
          h("button", { class: "btn tonal", disabled: busy, onclick: async () => { const x = await Bridge.act("downloadCore", r.tag); coreUi.msg = x && x.error ? x.error : "Ядро " + r.tag + " установлено и выбрано"; refreshLive(); } }, icon("download"), "Скачать"))));
      const out = h("div", {},
        h("h2", {}, "Ядро Xray"),
        h("div", { class: "body" }, "Своё ядро запускается отдельным процессом: встроенное создаёт туннель и отправляет трафик в запущенный Xray. Нужен исполняемый файл Xray для Android (arm64) или zip с ним. Применяется при следующем подключении."),
        busy ? h("div", { class: "busy-line" }, h("div", { class: "bar" }), h("div", { class: "body" }, C.busy)) : null,
        h("div", { class: "group-title" }, h("span", {}, "Активное ядро")),
        h("div", { class: "list" }, rows),
        h("div", { class: "actions split" },
          h("button", { class: "btn tonal", disabled: busy, onclick: async () => { const x = await Bridge.act("pickCore"); if (x && x.cancel) return; coreUi.msg = x && x.error ? x.error : "Ядро добавлено"; refreshLive(); } }, icon("add"), "Из файла"),
          h("button", { class: "btn tonal", disabled: busy, onclick: async () => { coreUi.rel = !coreUi.rel; if (coreUi.rel && !S.cores.releases) { const x = await Bridge.act("loadReleases"); if (x && x.error) coreUi.msg = x.error; } refreshLive(); } }, icon("download"), "Скачать версию")),
        coreUi.rel ? [h("div", { class: "group-title" }, h("span", {}, "Версии Xray-core")), relList] : null,
        coreUi.msg ? h("div", { class: "msg" }, coreUi.msg) : null);
      return out;
    });
  }

  function conflictSheet(names, left) {
    const list = names.join(", ");
    const msg = h("div", { class: "body", style: "margin-top:10px" }, left ? "Не удалось завершить: " + left.join(", ") + ". Закройте вручную или запустите FasdeqVPN от имени администратора." : "Запущено: " + list + ". Эти программы перехватывают трафик и мешают работе FasdeqVPN. Завершить их?");
    const kill = h("button", { class: "btn filled" }, "Завершить процесс");
    const close = h("button", { class: "btn text", onclick: closeSheet }, "Закрыть");
    kill.onclick = async () => {
      kill.disabled = true; kill.textContent = "Завершаю…";
      const r = await Bridge.act("killConflicts");
      if (r && r.ok) {
        closeSheet();
        S.status = "Connecting"; updateHome();
        const c = await Bridge.act("connectForce");
        if (c && c.error) { S.status = "Off"; updateHome(); snack(c.error); }
        setTimeout(syncNow, 700);
      } else {
        closeSheet();
        setTimeout(() => conflictSheet(names, (r && r.left) || names), 380);
      }
    };
    openSheet(h("div", {}, h("h2", {}, "Процесс мешает работе"), msg,
      h("div", { class: "body", style: "margin-top:8px" }, "Если закрыть окно, VPN не запустится."),
      h("div", { class: "actions", style: "margin-top:18px" }, close, kill)));
  }

  let noticeShown = false;
  function openNotice() {
    noticeShown = true;
    openSheet(h("div", {}, h("h2", {}, "Версия 3.2"), h("div", { class: "body" }, "Версия для ПК теперь на том же интерфейсе, что и на телефоне, с адаптацией под планшеты и широкие окна. Перед запуском проверяется, не мешают ли Happ, zapret и другие. Цвет принимает #fff, #ffffff и rgb(). Полное имя сервера: двойной тап или правый клик, долгое нажатие переносит."),
      h("div", { class: "actions" }, h("button", { class: "btn filled", onclick: () => { closeSheet(); Bridge.act("noticeSeen"); } }, "Понятно"))));
  }
  let shownUpdate = "";
  function openUpdate() {
    const u = S.update;
    shownUpdate = u.tag;
    openSheet(h("div", {},
      h("h2", {}, "Доступна новая версия"),
      h("div", { class: "body" }, "Установлена " + (u.current || "—") + ", на GitHub вышла " + u.tag + "."),
      u.notes ? h("div", { class: "notes" }, u.notes) : null,
      h("div", { class: "actions" },
        h("button", { class: "btn text", onclick: () => { closeSheet(); Bridge.act("dismissUpdate", u.tag); } }, "Позже"),
        h("button", { class: "btn filled", onclick: () => { closeSheet(); Bridge.act("open", u.url || "https://github.com/Fasdeq13/FasdeqVPN/releases/"); } }, "Перейти к релизам"))));
  }

  function getPath(path) { return path.split(".").reduce((o, k) => (o == null ? o : o[k]), S.settings); }
  const pend = new Map();
  function applyPend(settings) {
    const now = performance.now();
    pend.forEach((e, path) => {
      if (now - e.t > 1800) { pend.delete(path); return; }
      const keys = path.split(".");
      let o = settings;
      for (let i = 0; i < keys.length - 1; i++) { if (o[keys[i]] == null) o[keys[i]] = {}; o = o[keys[i]]; }
      o[keys[keys.length - 1]] = e.v;
    });
  }
  function setPath(path, value) {
    pend.set(path, { v: value, t: performance.now() });
    const keys = path.split(".");
    let o = S.settings;
    for (let i = 0; i < keys.length - 1; i++) o = o[keys[i]];
    o[keys[keys.length - 1]] = value;
    Bridge.act("set", { path, value });
    applyTheme();
    applyMotionFlags();
    Power.kick();
    refreshers.forEach((f) => f());
    fixCorners();
  }

  function row(title, sub, control, onTap) {
    return h(onTap ? "button" : "div", { class: "row", onclick: onTap }, h("div", { class: "grow" }, h("div", { class: "t" }, title), sub ? h("div", { class: "s" }, sub) : null), control);
  }
  function sw(path, title, sub, show) {
    const s = h("span", { class: "switch" });
    const r = row(title, sub, s, () => setPath(path, !getPath(path)));
    let last;
    refreshers.push(() => {
      const v = !!getPath(path);
      if (v !== last) { last = v; s.classList.toggle("on", v); }
      if (show) { const hd = !show(); if (r.hidden !== hd) r.hidden = hd; }
    });
    return r;
  }
  function seg(path, options, title, show) {
    const thumb = h("i", { class: "thumb" });
    const buttons = options.map((o, i) => h("button", { onclick: () => setPath(path, i) }, o));
    const box = h("div", { class: "seg" }, thumb, buttons);
    const r = h("div", { class: "row block" }, h("div", { class: "head" }, h("div", { class: "t" }, title)), box);
    let lastI = -1, lastW = 0;
    refreshers.push(() => {
      if (show) { const hd = !show(); if (r.hidden !== hd) r.hidden = hd; }
      const i = clamp(Number(getPath(path)) || 0, 0, options.length - 1);
      const w = box.clientWidth;
      if (i === lastI && w === lastW) return;
      if (r.hidden || !w) return;
      buttons.forEach((b, j) => b.classList.toggle("on", j === i));
      const b = buttons[i];
      if (b && b.offsetWidth) {
        if (lastW === 0 || w !== lastW) { thumb.style.transition = "none"; }
        thumb.style.width = b.offsetWidth + "px";
        thumb.style.transform = "translate3d(" + b.offsetLeft + "px,0,0)";
        if (thumb.style.transition === "none") { void thumb.offsetWidth; thumb.style.transition = ""; }
        lastI = i; lastW = w;
      }
    });
    return r;
  }
  function slider(path, title, min, max, step, fmt, show) {
    const input = h("input", { type: "range", min, max, step });
    const val = h("div", { class: "v" }, "");
    const paint = (v) => {
      input.style.setProperty("--p", ((v - min) / (max - min)) * 100 + "%");
      val.textContent = fmt(v);
    };
    const r = h("div", { class: "row block" }, h("div", { class: "head" }, h("div", { class: "t" }, title), val), input);
    input.addEventListener("input", () => { const v = Number(input.value); lastV = v; paint(v); setPath(path, v); });
    guard(r, input);
    let lastV;
    refreshers.push(() => {
      if (show) { const hd = !show(); if (r.hidden !== hd) r.hidden = hd; }
      const v = Number(getPath(path));
      if (v === lastV || document.activeElement === input) return;
      lastV = v; input.value = v; paint(v);
    });
    return r;
  }
  function textBox(path, title, placeholder, show) {
    const ta = h("textarea", { class: "inline", placeholder, spellcheck: "false" });
    const r = h("div", { class: "row block" }, h("div", { class: "head" }, h("div", { class: "t" }, title)), ta);
    let t = 0;
    ta.addEventListener("input", () => { clearTimeout(t); t = setTimeout(() => setPath(path, ta.value), 250); });
    refreshers.push(() => { if (show) r.hidden = !show(); if (document.activeElement !== ta) ta.value = String(getPath(path) || ""); });
    return r;
  }
  function action(title, sub, onTap) {
    return row(title, sub, h("span", { class: "icon-btn" }, icon("chevron")), onTap);
  }
  const DESK = !!(window.Bridge && window.Bridge.desktop);
  const only = (cond, node) => (cond ? node : []);
  function group(title, ...rows) {
    return [h("div", { class: "group-title" }, h("span", {}, title)), h("div", { class: "list" }, rows)];
  }

  function hwidSheet() {
    const code = h("div", { class: "rgb-preview" }, "…");
    let cur = "";
    Bridge.act("hwid").then((r) => { cur = (r && r.hwid) || ""; code.textContent = cur || "—"; });
    const copy = h("button", { class: "btn tonal", onclick: () => { if (cur) { Bridge.act("copy", cur); snack("HWID скопирован"); } } }, "Копировать");
    const reset = h("button", { class: "btn text", onclick: async () => { const r = await Bridge.act("hwidReset"); cur = (r && r.hwid) || ""; code.textContent = cur || "—"; snack("HWID сброшен"); } }, "Сбросить");
    openSheet(h("div", {}, h("h2", {}, "HWID"), h("div", { class: "body" }, "Отправляется провайдеру при загрузке подписки. После сброса у подписки может понадобиться заново привязать устройство."), h("div", { style: "margin:16px 0" }, code), h("div", { class: "actions" }, reset, copy, h("button", { class: "btn filled", onclick: closeSheet }, "Готово"))));
  }
  function guard(box, input) {
    let timer = 0, armed = false, sx = 0, sy = 0, id = -1;
    const zone = (e) => { const r = input.getBoundingClientRect(); return e.clientX >= r.left - 12 && e.clientX <= r.right + 12 && e.clientY >= r.top - 18 && e.clientY <= r.bottom + 18; };
    const move = (e) => {
      const r = input.getBoundingClientRect();
      const min = Number(input.min), max = Number(input.max), step = Number(input.step) || 1;
      let v = min + clamp((e.clientX - r.left) / r.width, 0, 1) * (max - min);
      v = Number(clamp(Math.round((v - min) / step) * step + min, min, max).toFixed(4));
      if (Number(input.value) !== v) { input.value = v; input.dispatchEvent(new Event("input")); }
    };
    const end = () => { clearTimeout(timer); timer = 0; armed = false; box.classList.remove("armed"); };
    input.style.pointerEvents = "none";
    box.addEventListener("pointerdown", (e) => {
      if (!zone(e)) return;
      sx = e.clientX; sy = e.clientY; id = e.pointerId;
      clearTimeout(timer);
      if (e.pointerType === "mouse") {
        if (e.button !== 0) return;
        armed = true; box.classList.add("armed");
        try { box.setPointerCapture(id); } catch (x) {}
        move(e);
        return;
      }
      timer = setTimeout(() => { armed = true; box.classList.add("armed"); try { box.setPointerCapture(id); } catch (x) {} Bridge.act("haptic", "strong"); }, 350);
    });
    box.addEventListener("pointermove", (e) => {
      if (armed) { e.preventDefault(); move(e); return; }
      if (timer && Math.hypot(e.clientX - sx, e.clientY - sy) > 8) { clearTimeout(timer); timer = 0; }
    });
    box.addEventListener("pointerup", end);
    box.addEventListener("pointercancel", end);
  }

  function palettePreview() {
    const roles = [["--primary", "Основной"], ["--primary-container", "Контейнер"], ["--secondary-container", "Вторичный"], ["--tertiary", "Третичный"], ["--sc-high", "Поверхность"], ["--outline", "Контур"]];
    return h("div", { class: "row block" }, h("div", { class: "head" }, h("div", { class: "t" }, "Палитра")),
      h("div", { class: "pal" }, roles.map((r) => h("div", { class: "pal-i" }, h("i", { style: "background:var(" + r[0] + ")" }), h("span", {}, r[1])))));
  }
  function useCustom(c) {
    if (S.settings.dynamic) { setPath("dynamic", false); snack("Системные цвета выключены, применён ваш цвет"); }
    setPath("accent", { r: c.r, g: c.g, b: c.b });
  }

  function colorEditor() {
    const prev = h("div", { class: "rgb-preview" });
    const name = h("span", {}, "");
    const code = h("span", {}, "");
    prev.append(name, code);
    const sw_ = PRESETS.map((p) => h("button", { class: "swatch", "aria-label": p[0], style: "background:" + toHex({ r: p[1], g: p[2], b: p[3] }), onclick: () => useCustom({ r: p[1], g: p[2], b: p[3] }) }));
    const swBox = h("div", { class: "swatches" }, sw_);
    const chan = (key, label, tint) => {
      const input = h("input", { type: "range", min: 0, max: 255, step: 1 });
      const val = h("div", { class: "v" }, "");
      const paint = (v) => { input.style.setProperty("--p", (v / 255) * 100 + "%"); val.textContent = String(v); };
      input.addEventListener("input", () => {
        const c = Object.assign({}, S.settings.accent);
        c[key] = Number(input.value);
        paint(c[key]);
        useCustom(c);
      });
      input.style.setProperty("--c", tint);
      const box = h("div", { class: "rgb-slider" }, h("div", { class: "head", style: "display:flex;justify-content:space-between" }, h("div", { class: "t" }, label), val), input);
      guard(box, input);
      return { box, input, paint };
    };
    const ch = [chan("r", "Красный", "#ff5c5c"), chan("g", "Зелёный", "#5cdc7a"), chan("b", "Синий", "#5c8cff")];
    const hex = h("input", { class: "hex", maxlength: 40, spellcheck: "false", autocomplete: "off", autocapitalize: "off", inputmode: "text", placeholder: "#RRGGBB", "aria-label": "Цвет: #fff, #ffffff, rgb(255,0,0) или название" });
    let hexTimer = 0;
    const applyHex = (final) => {
      const c = parseColor(hex.value);
      hex.classList.toggle("bad", !c && hex.value.trim() !== "");
      if (c) {
        const cur = S.settings.accent;
        if (cur.r !== c.r || cur.g !== c.g || cur.b !== c.b || S.settings.dynamic) useCustom(c);
        if (final) hex.value = toHex(c).toUpperCase();
      } else if (final) {
        hex.value = toHex(S.settings.accent).toUpperCase();
        hex.classList.remove("bad");
      }
    };
    hex.addEventListener("input", () => { clearTimeout(hexTimer); hexTimer = setTimeout(() => applyHex(false), 220); });
    hex.addEventListener("change", () => { clearTimeout(hexTimer); applyHex(true); });
    hex.addEventListener("keydown", (e) => { if (e.key === "Enter") { e.preventDefault(); clearTimeout(hexTimer); applyHex(true); hex.blur(); } });
    hex.addEventListener("blur", () => { clearTimeout(hexTimer); applyHex(true); });
    const r = h("div", { class: "row block" }, h("div", { class: "head" }, h("div", { class: "t" }, "Свой цвет")), prev, swBox, h("div", { class: "field-row" }, h("div", { class: "body", style: "flex:1" }, "Код цвета"), hex), ch.map((c) => c.box));
    refreshers.push(() => {
      const c = S.settings.accent;
      const hx = toHex(c).toUpperCase();
      prev.style.background = hx;
      const [L] = rgbToOklch(c.r, c.g, c.b);
      prev.style.color = L > 0.62 ? "#111" : "#fff";
      name.textContent = "RGB " + c.r + ", " + c.g + ", " + c.b;
      code.textContent = hx;
      if (document.activeElement !== hex) hex.value = hx;
      ch[0].input.value = c.r; ch[0].paint(c.r);
      ch[1].input.value = c.g; ch[1].paint(c.g);
      ch[2].input.value = c.b; ch[2].paint(c.b);
      sw_.forEach((b, i) => b.classList.toggle("on", PRESETS[i][1] === c.r && PRESETS[i][2] === c.g && PRESETS[i][3] === c.b));
      r.classList.toggle("dim", !!S.settings.dynamic);
    });
    return r;
  }

  function buildSettings() {
    const v = $("#view-settings");
    const put = (...x) => v.append(...x.flat(Infinity));
    const anim = () => S.settings.anim.all;

    put(
      h("div", { class: "top-row" }, h("h1", { class: "headline" }, "Настройки")),
      h("div", { class: "body" }, (DESK ? "Ползунки двигаются мышью." : "Чтобы изменить ползунок, зажмите его на долю секунды и ведите вбок. Прокрутка настройки не меняет.")),
      group("Цвет и тема",
        seg("theme", ["Система", "Светлая", "Тёмная"], "Тема"),
        colorEditor(),
        sw("dynamic", "Цвета системы", "Палитра из обоев вместо своего цвета. Android 12 и новее"),
        sw("tint", "Оттенять фон цветом", "Выключите для нейтральных серых поверхностей"),
        sw("amoled", "Чёрный фон", "Для OLED-экранов в тёмной теме")),
      group("Material You: детально",
        palettePreview(),
        seg("look.style", ["Базовая", "Яркая", "Живая", "Мягкая", "Моно"], "Стиль палитры", () => !S.settings.dynamic),
        seg("look.contrast", ["Стандарт", "Средний", "Высокий"], "Контрастность", () => !S.settings.dynamic),
        slider("look.tintLevel", "Насыщенность поверхностей", 0, 2, 0.05, (x) => Math.round(x * 100) + "%", () => !S.settings.dynamic),
        slider("look.secHue", "Сдвиг вторичного цвета", -90, 90, 5, (x) => (x > 0 ? "+" : "") + x + "°", () => !S.settings.dynamic),
        slider("look.terHue", "Оттенок третичного цвета", 0, 360, 5, (x) => x + "°", () => !S.settings.dynamic),
        slider("look.radius", "Скругление углов", 0.4, 1.6, 0.05, (x) => Math.round(x * 100) + "%"),
        slider("look.density", "Плотность списков", 0.8, 1.3, 0.05, (x) => Math.round(x * 100) + "%"),
        action("Сбросить оформление", "Вернуть значения Material You по умолчанию", () => { setPath("look", Object.assign({}, DEFAULTS.look)); snack("Оформление сброшено"); })),
      group("Анимации",
        sw("anim.all", "Все анимации", "Главный выключатель: выключите, и всё станет мгновенным"),
        sw("anim.pages", "Переходы между экранами", "Сдвиг при переключении вкладок", anim),
        sw("anim.controls", "Переключатели и нажатия", "Движение переключателей, ползунков, кнопок", anim),
        sw("anim.sheets", "Панели и уведомления", "Выезд нижних панелей и сообщений", anim),
        sw("anim.ripple", "Волна при нажатии", "Круговая волна на кнопках и строках", anim),
        sw("anim.list", "Появление списков", "Серверы выезжают по очереди", anim),
        sw("anim.cards", "Появление карточек", "Каскад при открытии экрана", anim),
        sw("anim.fold", "Сворачивание групп", "Плавное раскрытие и поворот стрелки", anim),
        sw("anim.pingFx", "Отклик пинга", "Пульс значения при обновлении", anim),
        sw("anim.select", "Выбор сервера", "Мягкая вспышка выбранной строки", anim),
        sw("anim.enabled", "Анимация кнопки питания", "Деформация, волны, вращение", anim),
        slider("anim.speed", "Скорость интерфейса", 0.25, 3, 0.05, (x) => "×" + x.toFixed(2), anim)),
      group("Кнопка питания",
        slider("button.size", "Размер", 160, 280, 4, (x) => x + " px"),
        seg("button.shape", ["Печенье", "Круг", "Квадрат", "Звезда"], "Форма"),
        slider("button.lobes", "Количество лепестков", 3, 16, 1, (x) => String(x)),
        slider("anim.intensity", "Сила деформации", 0, 2, 0.05, (x) => Math.round(x * 100) + "%", anim),
        slider("anim.tempo", "Темп движения", 0.2, 3, 0.05, (x) => "×" + x.toFixed(2), anim),
        slider("anim.glow", "Свечение при подключении", 0, 1, 0.05, (x) => Math.round(x * 100) + "%"),
        sw("anim.burst", "Волны при подключении", "Расходящиеся кольца", anim),
        slider("anim.waves", "Количество волн", 1, 5, 1, (x) => String(x), anim),
        sw("anim.idle", "Движение в покое", "Едва заметное дыхание", anim),
        sw("anim.iconFx", "Анимация значка", "Сжатие и покачивание при подключении", anim),
        sw("anim.press", "Эффект нажатия", "Кнопка уменьшается под пальцем", anim),
        sw("anim.haptics", "Вибрация", "Отклик при нажатии")),
      group("Подключение",
        sw("autoConnect", "Подключаться при запуске"),
        seg("dns", ["Cloudflare", "Google", "Quad9", "AdGuard", "Свой"], "DNS"),
        textBox("dnsCustom", "Свои DNS через запятую", "1.1.1.1, 8.8.8.8", () => S.settings.dns === 4),
        only(!DESK, slider("mtu", "MTU", 1280, 1500, 10, (x) => String(x)))),
      group("Низкоуровневые",
        sw("tuning.ipv6", "IPv6", "Выключите, если сайты зависают"),
        sw("tuning.sniff", "Sniffing", "Определять домен по HTTP, TLS и QUIC"),
        sw("tuning.quic", "Блокировать QUIC", "Браузеры перейдут на TCP"),
        sw("tuning.lan", "Локальная сеть напрямую", "10.x, 172.16.x, 192.168.x мимо VPN"),
        only(!DESK, sw("tuning.unm", "Сеть без лимита", "Система не считает трафик VPN мобильным")),
        sw("tuning.mux", "Mux", "Не работает с XTLS Vision"),
        slider("tuning.muxc", "Потоков Mux", 1, 64, 1, (x) => String(x), () => S.settings.tuning.mux),
        seg("tuning.log", ["Нет", "Ошибки", "Предупр.", "Инфо", "Отладка"], "Журнал Xray"),
        seg("tuning.ds", ["AsIs", "IPIfNonMatch", "IPOnDemand"], "Domain strategy"),
        slider("tuning.idle", "Таймаут простоя", 30, 1800, 30, (x) => x + " с"),
        slider("tuning.buf", "Буфер на соединение", 0, 2048, 64, (x) => (x === 0 ? "по умолчанию" : x + " КБ"))),
      only(!DESK, group("Приложения",
        seg("tuning.am", ["Все", "Исключить", "Только"], "Через VPN идут"),
        textBox("tuning.apps", "Пакеты через запятую", "org.telegram.messenger, com.discord", () => S.settings.tuning.am !== 0))),
      group("Ядро",
        action("Ядро Xray", "Встроенное или своё, версия и загрузка", openCores)),
      group("Сервисы",
        sw("autoCheck", "Проверять сервисы автоматически", "Discord, Telegram и YouTube через каждый сервер, пока VPN включён"),
        slider("checkInterval", "Интервал проверки", 5, 240, 5, (x) => x + " мин", () => S.settings.autoCheck),
        action("Проверить сейчас", "Запустить проверку сервисов для всех серверов", () => { Bridge.act("checkServices"); snack("Проверяю сервисы…"); go("home"); })),
      only(!DESK, group("Клиент для подписок",
        seg("mask", ["Tunn", "Happ", "v2RayTun", "Incy"], "Под кого маскироваться"),
        action("HWID", "Идентификатор устройства: показать, копировать, сбросить", hwidSheet))),
      only(!DESK, group("Иконка",
        action("Иконка приложения", "Варианты, Material You и своя картинка", openIconSheet))),
      only(!DESK, group("Плагины",
        action("Плагины", "Протоколы, правила обхода, дополнения интерфейса", openPlugins))),
      only(DESK, group("Окно и запуск",
        sw("closeToTray", "Сворачивать в трей", "Крестик скрывает окно, VPN продолжает работать"),
        sw("autostart", "Запускать вместе с системой"),
        action("Выйти из FasdeqVPN", "Отключить VPN и закрыть приложение", () => Bridge.act("quit")))),
      group("Проверка пинга",
        seg("pingMode", ["Авто", "TCP", "UDP", "Оба", "ICMP"], "Способ"),
        seg("pingTimeout", ["2 с", "4 с", "8 с"], "Таймаут"),
        sw("autoPing", "Проверять автоматически"),
        sw("sortPing", "Сортировать по пингу")),
      group("Обновления",
        sw("updates.auto", "Сообщать о новых версиях", "Проверка релизов на GitHub раз в несколько часов"),
        action("Проверить обновления", "github.com/Fasdeq13/FasdeqVPN/releases", async () => {
          snack("Проверяю…");
          const r = await Bridge.act("checkUpdate");
          await syncNow();
          if (r && r.error) snack(r.error);
          else if (r && r.available) openUpdate();
          else snack("Установлена последняя версия");
        }),
        action("Открыть страницу релизов", (DESK ? "Скачать новую версию вручную" : "Скачать APK вручную"), () => Bridge.act("open", "https://github.com/Fasdeq13/FasdeqVPN/releases/"))),
      group("Интерфейс",
        slider("textScale", "Размер текста", 0.8, 1.5, 0.05, (x) => Math.round(x * 100) + "%"))
    );
    const dev = (name, role, url) => h(url ? "button" : "div", { class: "dev" + (url ? "" : " static"), onclick: url ? () => Bridge.act("open", url) : null },
      h("div", { class: "avatar" }, Array.from(name)[0].toUpperCase()),
      h("div", { class: "grow" }, h("div", { class: "t" }, name), role ? h("div", { class: "s" }, role) : null));
    v.append(h("div", { class: "about" }, h("div", { class: "title-large" }, "Tunn"), h("div", { class: "body" }, "Версия 3.2 · создатель fasdeq13"),
      h("div", { class: "group-title tight" }, h("span", {}, "Участники")),
      h("div", { class: "devs" }, dev("Fasdeq13", "Создатель", "https://github.com/Fasdeq13/FasdeqVPN"), dev("montik", "", null), dev("fezix64", "", null))));
  }

  function fixCorners() {
    $$(".list").forEach((l) => { if (!l.closest("#view-servers")) fixList(l); });
  }

  function fillIcons() {
    $("#i-nav-home").setAttribute("d", ICONS.home);
    $("#i-nav-servers").setAttribute("d", ICONS.servers);
    $("#i-nav-settings").setAttribute("d", ICONS.settings);
  }

  let dirty = false, queued = null, queuedRaf = 0;
  function onState(next, light) {
    if (light && S && S.__ready) { if (current === "home") updateHome(); return; }
    queued = next;
    if (S && S.__ready && !first0) {
      if (!queuedRaf) queuedRaf = requestAnimationFrame(() => { queuedRaf = 0; const n = queued; queued = null; applyState(n); });
      return;
    }
    first0 = false;
    queued = null;
    applyState(next);
  }
  let first0 = true;

  let pingRaf = 0;
  function lightPings() {
    const busy = pingBusy();
    const chk = !!S.services.checking;
    const flip = busy !== srv.lastBusy || chk !== srv.lastChk;
    srv.lastBusy = busy;
    srv.lastChk = chk;
    if (current === "servers") {
      if (flip) renderServers();
      else srv.rows.forEach((r, id) => { if (r.el.isConnected) { const s = srv.byId[id]; if (s) paintRow(r, s); } });
    }
    if (current === "home") renderTiles();
  }
  function onPings(p) {
    if (!S || !S.__ready) return;
    S.pings = Object.assign(S.pings || {}, p.pings);
    S.services.checks = Object.assign(S.services.checks || {}, p.checks);
    S.services.best = p.best;
    S.services.checking = p.checking;
    if (!pingRaf) pingRaf = requestAnimationFrame(() => { pingRaf = 0; lightPings(); });
  }

  function applyState(next) {
    const first = !S || !S.__ready;
    next.settings = merge(DEFAULTS, next.settings || {});
    applyPend(next.settings);
    next.servers = next.servers || [];
    next.services = next.services || { names: ["Discord", "Telegram", "YouTube"], checking: false, checks: {}, best: [] };
    next.cores = next.cores || { active: "", builtin: "", busy: null, list: [], releases: null };
    next.update = next.update || { available: false };
    next.plugins = next.plugins || { list: [], engine: "", busy: null, ui: [], catalog: null, families: [] };
    next.groups = next.groups || [];
    next.pings = next.pings || {};
    next.__ready = true;
    S = next;
    applyTheme();
    applyMotionFlags();
    updateHome();
    if (current === "servers" || first) renderServers();
    if (current === "settings" || first) { refreshers.forEach((f) => f()); fixCorners(); dirty = false; } else dirty = true;
    refreshLive();
    loadPluginUi();
    if (S.notice && !noticeShown && !$("#sheet").classList.contains("show")) openNotice();
    if (S.update.available && S.settings.updates.auto && S.update.tag !== shownUpdate && !$("#sheet").classList.contains("show")) openUpdate();
    if (first) requestAnimationFrame(() => { refreshers.forEach((f) => f()); fixCorners(); });
  }

  async function boot() {
    fillIcons();
    S = await Bridge.state();
    S.settings = merge(DEFAULTS, S.settings || {});
    S.__ready = false;
    buildHome();
    buildSettings();
    const fab = h("button", { id: "fab", class: "fab hide", onclick: addSheet }, icon("add"), "Добавить");
    $("#app").append(fab);
    $$(".nav-item").forEach((n) => n.addEventListener("click", () => go(n.dataset.go)));
    $("#scrim").addEventListener("click", closeSheet);
    Bridge.onState(onState);
    if (Bridge.onPings) Bridge.onPings(onPings);
    $("#view-servers").addEventListener("scroll", onServersScroll, { passive: true });
    window.addEventListener("resize", () => { srv.rowH = 68; updateWindows(true); });
    $$(".view").forEach((v) => v.classList.toggle("left", v.dataset.view !== "home"));
    $("#view-home").classList.add("active");
    $(".nav-item[data-go=home]").classList.add("active");
    onState(S);
    try { Bridge.act("refreshInfo"); } catch (e) {}
    const q = new URLSearchParams(location.search);
    if (q.get("view")) { document.body.style.setProperty("--x", "1"); go(q.get("view")); }
    window.addEventListener("resize", () => refreshers.forEach((f) => f()));
    window.__back = function () {
      if (closePeek()) return true;
      if ($("#sheet").classList.contains("show")) { closeSheet(); return true; }
      if (current !== "home") { go("home"); return true; }
      return false;
    };
    Bridge.ready();
    window.__go = go;
    window.__addSheet = addSheet;
    window.__Power = Power;
    booted = true;
    pluginDefs.forEach(runDef);
  }
  boot();
})();
