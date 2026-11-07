use serde_json::{json, Value};
use std::ffi::{c_char, c_float, c_int, c_void, CStr, CString};
use std::sync::Mutex;

type AbiFn = unsafe extern "C" fn() -> c_int;
type InfoFn = unsafe extern "C" fn() -> *const c_char;
type InitFn = unsafe extern "C" fn(c_int, c_int, c_float) -> c_int;
type ResizeFn = unsafe extern "C" fn(c_int, c_int);
type StateFn = unsafe extern "C" fn(*const c_char);
type TouchFn = unsafe extern "C" fn(c_int, c_float, c_float);
type BackFn = unsafe extern "C" fn();
type RenderFn = unsafe extern "C" fn(*mut u8, c_int, c_int, c_int, u32) -> c_int;
type PollFn = unsafe extern "C" fn() -> *const c_char;
type CloseFn = unsafe extern "C" fn();

struct Ui {
    lib: usize,
    init: InitFn,
    resize: Option<ResizeFn>,
    state: StateFn,
    touch: TouchFn,
    back: Option<BackFn>,
    render: RenderFn,
    poll: PollFn,
    close: Option<CloseFn>,
}

static CUR: Mutex<Option<Ui>> = Mutex::new(None);

unsafe fn sym<T: Copy>(lib: *mut c_void, name: &str) -> Option<T> {
    let c = CString::new(name).ok()?;
    let p = libc::dlsym(lib, c.as_ptr());
    if p.is_null() {
        None
    } else {
        Some(std::mem::transmute_copy::<*mut c_void, T>(&p))
    }
}

unsafe fn text(p: *const c_char) -> String {
    if p.is_null() {
        String::new()
    } else {
        CStr::from_ptr(p).to_string_lossy().into_owned()
    }
}

pub fn open(path: &str, w: i32, h: i32, density: f32) -> Value {
    let Ok(c) = CString::new(path) else {
        return json!({"ok": false, "error": "bad path"});
    };
    close();
    unsafe {
        let lib = libc::dlopen(c.as_ptr(), libc::RTLD_NOW | libc::RTLD_LOCAL);
        if lib.is_null() {
            let err = text(libc::dlerror());
            return json!({"ok": false, "error": if err.is_empty() { "dlopen failed".to_string() } else { err }});
        }
        let fail = |m: &str| {
            libc::dlclose(lib);
            json!({"ok": false, "error": m})
        };
        let Some(abi) = sym::<AbiFn>(lib, "fq_ui_abi") else { return fail("fq_ui_abi not found") };
        if abi() != 1 {
            return fail("unsupported UI ABI version");
        }
        let (Some(init), Some(state), Some(touch), Some(render), Some(poll)) = (
            sym::<InitFn>(lib, "fq_ui_init"),
            sym::<StateFn>(lib, "fq_ui_state"),
            sym::<TouchFn>(lib, "fq_ui_touch"),
            sym::<RenderFn>(lib, "fq_ui_render"),
            sym::<PollFn>(lib, "fq_ui_poll"),
        ) else {
            return fail("missing fq_ui_init/state/touch/render/poll");
        };
        let info = sym::<InfoFn>(lib, "fq_ui_info").map(|f| text(f())).unwrap_or_default();
        if init(w, h, density) != 0 {
            return fail("fq_ui_init failed");
        }
        let ui = Ui {
            lib: lib as usize,
            init,
            resize: sym(lib, "fq_ui_resize"),
            state,
            touch,
            back: sym(lib, "fq_ui_back"),
            render,
            poll,
            close: sym(lib, "fq_ui_close"),
        };
        let _ = ui.init;
        *CUR.lock().unwrap() = Some(ui);
        json!({"ok": true, "info": serde_json::from_str::<Value>(&info).unwrap_or(Value::Null)})
    }
}

pub fn close() {
    if let Some(u) = CUR.lock().unwrap().take() {
        unsafe {
            if let Some(f) = u.close {
                f();
            }
            libc::dlclose(u.lib as *mut c_void);
        }
    }
}

pub fn resize(w: i32, h: i32) {
    if let Some(u) = CUR.lock().unwrap().as_ref() {
        if let Some(f) = u.resize {
            unsafe { f(w, h) }
        }
    }
}

pub fn state(json: &str) {
    if let (Some(u), Ok(c)) = (CUR.lock().unwrap().as_ref(), CString::new(json)) {
        unsafe { (u.state)(c.as_ptr()) }
    }
}

pub fn touch(kind: i32, x: f32, y: f32) {
    if let Some(u) = CUR.lock().unwrap().as_ref() {
        unsafe { (u.touch)(kind, x, y) }
    }
}

pub fn back() {
    if let Some(u) = CUR.lock().unwrap().as_ref() {
        if let Some(f) = u.back {
            unsafe { f() }
        }
    }
}

pub unsafe fn render(buf: *mut u8, w: i32, h: i32, stride: i32, t: u32) -> i32 {
    match CUR.lock().unwrap().as_ref() {
        Some(u) if !buf.is_null() && w > 0 && h > 0 && stride >= w * 4 => (u.render)(buf, w, h, stride, t),
        _ => -1,
    }
}

pub fn poll() -> Option<String> {
    let g = CUR.lock().unwrap();
    let u = g.as_ref()?;
    let s = unsafe { text((u.poll)()) };
    if s.is_empty() {
        None
    } else {
        Some(s)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::process::Command;

    fn build_example() -> String {
        let dir = concat!(env!("CARGO_MANIFEST_DIR"), "/../plugins/sdk/ui-example-rs");
        let st = Command::new("cargo").args(["build", "--release", "--manifest-path", &format!("{dir}/Cargo.toml")]).status().unwrap();
        assert!(st.success());
        format!("{dir}/target/release/libfq_ui_example.so")
    }

    #[test]
    fn example_plugin_renders_and_reacts() {
        let path = build_example();
        let r = open(&path, 360, 720, 1.0);
        assert_eq!(r["ok"], true, "{r}");
        assert_eq!(r["info"]["name"], "Example UI");
        state(r#"{"status":"Off","server":{"id":"a","name":"Berlin 1"},"servers":[{"id":"a","name":"Berlin 1"},{"id":"b","name":"Tokyo"}]}"#);
        let (w, h) = (360, 720);
        let mut px = vec![0u8; (w * h * 4) as usize];
        assert_eq!(unsafe { render(px.as_mut_ptr(), w, h, w * 4, 0) }, 1);
        assert_eq!(&px[0..4], &[14, 16, 22, 255]);
        let lit = px.chunks(4).filter(|p| p[..3] != [14, 16, 22]).count();
        assert!(lit > 5000, "something must be drawn: {lit}");
        assert_eq!(unsafe { render(px.as_mut_ptr(), w, h, w * 4, 16) }, 0, "unchanged frame");
        touch(0, 180.0, 216.0);
        touch(2, 180.0, 216.0);
        assert_eq!(poll().as_deref(), Some(r#"{"op":"toggle"}"#));
        assert_eq!(poll(), None);
        let row = (216.0 + 72.0 + 40.0 + 28.0) + 56.0 + 8.0;
        touch(0, 100.0, row);
        touch(2, 100.0, row);
        let a: Value = serde_json::from_str(&poll().unwrap()).unwrap();
        assert_eq!(a, json!({"op": "select", "id": "b"}));
        touch(0, 10.0, 10.0);
        touch(2, 200.0, 10.0);
        assert_eq!(poll(), None, "a swipe is not a tap");
        back();
        assert_eq!(poll().as_deref(), Some(r#"{"op":"exit"}"#));
        state(r#"{"status":"On","server":{"id":"a","name":"Berlin 1"},"servers":[]}"#);
        assert_eq!(unsafe { render(px.as_mut_ptr(), w, h, w * 4, 32) }, 1);
        assert_eq!(unsafe { render(std::ptr::null_mut(), w, h, w * 4, 0) }, -1);
        close();
        assert_eq!(unsafe { render(px.as_mut_ptr(), w, h, w * 4, 0) }, -1);
    }

    #[test]
    fn rejects_garbage() {
        assert_eq!(open("/nonexistent/x.so", 10, 10, 1.0)["ok"], false);
        assert_eq!(open("/bin/ls", 10, 10, 1.0)["ok"], false);
    }
}
