
#[derive(Clone, Copy)]
pub struct Rgb(pub u8, pub u8, pub u8);

pub struct Scheme {
    pub bg: Rgb,
    pub container: Rgb,
    pub container_high: Rgb,
    pub container_highest: Rgb,
    pub primary: Rgb,
    pub on_primary: Rgb,
    pub primary_container: Rgb,
    pub on_primary_container: Rgb,
    pub secondary_container: Rgb,
    pub on_secondary_container: Rgb,
    pub on_surface: Rgb,
    pub on_variant: Rgb,
    pub outline: Rgb,
    pub error: Rgb,
}

pub fn parse_hex(text: &str) -> Option<Rgb> {
    let mut t = text.trim().trim_start_matches('#').to_string();
    if t.len() == 3 {
        t = t.chars().flat_map(|c| [c, c]).collect();
    }
    if t.len() != 6 {
        return None;
    }
    let v = u32::from_str_radix(&t, 16).ok()?;
    Some(Rgb((v >> 16) as u8, (v >> 8) as u8, v as u8))
}

fn to_hsl(c: Rgb) -> (f32, f32, f32) {
    let (r, g, b) = (c.0 as f32 / 255.0, c.1 as f32 / 255.0, c.2 as f32 / 255.0);
    let mx = r.max(g).max(b);
    let mn = r.min(g).min(b);
    let l = (mx + mn) / 2.0;
    let d = mx - mn;
    if d < 1e-5 {
        return (0.0, 0.0, l);
    }
    let s = d / (1.0 - (2.0 * l - 1.0).abs());
    let h = if mx == r {
        ((g - b) / d) % 6.0
    } else if mx == g {
        (b - r) / d + 2.0
    } else {
        (r - g) / d + 4.0
    } * 60.0;
    (if h < 0.0 { h + 360.0 } else { h }, s.clamp(0.0, 1.0), l)
}

pub fn hsl(h: f32, s: f32, l: f32) -> Rgb {
    let c = (1.0 - (2.0 * l - 1.0).abs()) * s;
    let hp = (((h % 360.0) + 360.0) % 360.0) / 60.0;
    let x = c * (1.0 - (hp % 2.0 - 1.0).abs());
    let (r1, g1, b1) = match hp {
        v if v < 1.0 => (c, x, 0.0),
        v if v < 2.0 => (x, c, 0.0),
        v if v < 3.0 => (0.0, c, x),
        v if v < 4.0 => (0.0, x, c),
        v if v < 5.0 => (x, 0.0, c),
        _ => (c, 0.0, x),
    };
    let m = l - c / 2.0;
    let f = |v: f32| ((v + m).clamp(0.0, 1.0) * 255.0 + 0.5) as u8;
    Rgb(f(r1), f(g1), f(b1))
}

fn lum(c: Rgb) -> f32 {
    0.2126 * c.0 as f32 / 255.0 + 0.7152 * c.1 as f32 / 255.0 + 0.0722 * c.2 as f32 / 255.0
}

fn on(c: Rgb) -> Rgb {
    if lum(c) > 0.55 {
        Rgb(0x10, 0x14, 0x14)
    } else {
        Rgb(255, 255, 255)
    }
}

pub fn scheme(accent: Rgb, dark: bool, bg: Option<Rgb>, amoled: bool) -> Scheme {
    let (h, sa, la) = to_hsl(accent);
    let s = sa.max(if sa < 0.08 { 0.0 } else { 0.35 }).min(1.0);
    let tint = if sa < 0.08 { 0.0 } else { 1.0 };
    let primary = if dark { hsl(h, s, la.max(0.68)) } else { hsl(h, s, la.min(0.42)) };
    let use_dark = match bg {
        Some(b) => lum(b) <= 0.55,
        None => dark,
    };
    let amoled = amoled && use_dark && bg.is_none();
    let base = bg.unwrap_or(if use_dark { hsl(h, 0.10 * tint, if amoled { 0.0 } else { 0.075 }) } else { hsl(h, 0.16 * tint, 0.97) });
    let (bh, bs, bl) = to_hsl(base);
    let step = |k: f32| if use_dark { hsl(bh, bs, (bl + k).min(0.95)) } else { hsl(bh, bs, (bl - k).max(0.05)) };
    let pc = if use_dark { hsl(h, s * 0.55, 0.24) } else { hsl(h, s * 0.75, 0.88) };
    let sc = if use_dark { hsl(h, s * 0.25, 0.27) } else { hsl(h, s * 0.35, 0.89) };
    Scheme {
        bg: base,
        container: if amoled { Rgb(0x0d, 0x0d, 0x0d) } else if use_dark { step(0.05) } else { step(0.04) },
        container_high: step(if use_dark { 0.08 } else { 0.07 }),
        container_highest: step(if use_dark { 0.12 } else { 0.10 }),
        primary,
        on_primary: on(primary),
        primary_container: pc,
        on_primary_container: if use_dark { hsl(h, 0.6 * tint, 0.9) } else { hsl(h, 0.7 * tint, 0.12) },
        secondary_container: sc,
        on_secondary_container: if use_dark { hsl(h, 0.3 * tint, 0.9) } else { hsl(h, 0.4 * tint, 0.12) },
        on_surface: if use_dark { hsl(bh, 0.06, 0.9) } else { hsl(bh, 0.1, 0.1) },
        on_variant: if use_dark { hsl(bh, 0.06, 0.72) } else { hsl(bh, 0.08, 0.32) },
        outline: hsl(bh, 0.05, if use_dark { 0.55 } else { 0.5 }),
        error: if use_dark { Rgb(0xff, 0xb4, 0xab) } else { Rgb(0xba, 0x1a, 0x1a) },
    }
}
