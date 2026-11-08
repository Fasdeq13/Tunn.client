pub mod api;
pub mod plugin;
pub mod uihost;

use jni::objects::{JClass, JString};
use jni::sys::{jint, jlong, jstring};
use jni::JNIEnv;
use serde_json::{json, Value};

fn text(env: &mut JNIEnv, s: &JString) -> String {
    env.get_string(s).map(|v| v.into()).unwrap_or_default()
}

fn reply(env: &mut JNIEnv, v: Value) -> jstring {
    env.new_string(v.to_string())
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

fn guarded(env: &mut JNIEnv, f: impl FnOnce(&mut JNIEnv) -> Value + std::panic::UnwindSafe) -> jstring {
    let out = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| f(env)))
        .unwrap_or_else(|_| json!({"ok": false, "error": "internal"}));
    reply(env, out)
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_parseLink(mut env: JNIEnv, _: JClass, conf: JString) -> jstring {
    let conf = text(&mut env, &conf);
    guarded(&mut env, move |_| api::parse_link(&conf))
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_prepareImport(mut env: JNIEnv, _: JClass, input: JString) -> jstring {
    let input = text(&mut env, &input);
    guarded(&mut env, move |_| api::prepare_import(&input))
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_fetchSubscription(mut env: JNIEnv, _: JClass, url: JString) -> jstring {
    let url = text(&mut env, &url);
    guarded(&mut env, move |_| api::fetch_subscription(&url))
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_ping(
    mut env: JNIEnv,
    _: JClass,
    conf: JString,
    endpoint: JString,
    mode: jint,
    timeout_ms: jlong,
) -> jstring {
    let conf = text(&mut env, &conf);
    let endpoint = text(&mut env, &endpoint);
    guarded(&mut env, move |_| api::measure_ping(&conf, &endpoint, mode, timeout_ms.max(500) as u64))
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_innerConfig(
    mut env: JNIEnv,
    _: JClass,
    outbound: JString,
    socks: jint,
    http: jint,
    dns: JString,
) -> jstring {
    let outbound = text(&mut env, &outbound);
    let dns: Vec<String> = text(&mut env, &dns)
        .split(',')
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
        .collect();
    guarded(&mut env, move |_| api::inner_config(&outbound, socks as u16, http as u16, &dns))
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_pluginOpen(mut env: JNIEnv, _: JClass, path: JString) -> jstring {
    let path = text(&mut env, &path);
    guarded(&mut env, move |_| plugin::open(&path))
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_pluginCall(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    op: JString,
    config: JString,
    fd: jint,
    arg: jlong,
) -> jstring {
    let op = text(&mut env, &op);
    let config = text(&mut env, &config);
    guarded(&mut env, move |_| plugin::call(handle, &op, &config, fd, arg))
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_pluginClose(mut env: JNIEnv, _: JClass, handle: jlong) -> jstring {
    guarded(&mut env, move |_| plugin::close(handle))
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_journalInit(mut env: JNIEnv, _: JClass, path: JString) {
    let path = text(&mut env, &path);
    tunn_backend::engine::journal_init(&path);
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_journalAdd(mut env: JNIEnv, _: JClass, level: JString, tag: JString, msg: JString) {
    let (l, t, m) = (text(&mut env, &level), text(&mut env, &tag), text(&mut env, &msg));
    let _ = std::panic::catch_unwind(|| tunn_backend::engine::journal_add(&l, &t, &m));
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_journalRead(env: JNIEnv, _: JClass, lines: jint) -> jstring {
    let s = tunn_backend::engine::journal_read(lines.max(1) as usize);
    env.new_string(s).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_journalClear(_: JNIEnv, _: JClass) {
    tunn_backend::engine::journal_clear();
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_retryDelay(_: JNIEnv, _: JClass, attempt: jint) -> jlong {
    tunn_backend::engine::retry_delay(attempt.max(0) as u32).map(|v| v as jlong).unwrap_or(-1)
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_startTimeout(mut env: JNIEnv, _: JClass, kind: JString, attempt: jint) -> jlong {
    let k = text(&mut env, &kind);
    tunn_backend::engine::start_timeout_ms(&k, attempt.max(0) as u32) as jlong
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_settleMs(mut env: JNIEnv, _: JClass, prev: JString) -> jlong {
    let k = text(&mut env, &prev);
    tunn_backend::engine::settle_ms(&k) as jlong
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_pickNext(mut env: JNIEnv, _: JClass, candidates: JString, failed: JString, current: JString) -> jstring {
    let c = text(&mut env, &candidates);
    let f: Vec<String> = text(&mut env, &failed).split(',').filter(|s| !s.is_empty()).map(|s| s.to_string()).collect();
    let cur = text(&mut env, &current);
    let v: Value = serde_json::from_str(&c).unwrap_or(Value::Null);
    let id = tunn_backend::engine::pick_next(&v, &f, &cur).unwrap_or_default();
    env.new_string(id).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_subHeaders(mut env: JNIEnv, _: JClass, headers: JString) -> jstring {
    let h = text(&mut env, &headers);
    let v: Value = serde_json::from_str(&h).unwrap_or(Value::Null);
    reply(&mut env, tunn_backend::engine::parse_sub_headers(&v))
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_updatePlan(mut env: JNIEnv, _: JClass, file: JString, manifest: JString, version: JString, code: jint, applied: JString) -> jstring {
    let f = text(&mut env, &file);
    let m: Value = serde_json::from_str(&text(&mut env, &manifest)).unwrap_or(Value::Null);
    let v = text(&mut env, &version);
    let a = text(&mut env, &applied);
    reply(&mut env, tunn_backend::update::plan(&f, &m, &v, code as i64, &a))
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_uiOpen(mut env: JNIEnv, _: JClass, path: JString, w: jint, h: jint, density: jni::sys::jfloat) -> jstring {
    let p = text(&mut env, &path);
    reply(&mut env, uihost::open(&p, w, h, density))
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_uiClose(_: JNIEnv, _: JClass) {
    uihost::close();
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_uiResize(_: JNIEnv, _: JClass, w: jint, h: jint) {
    uihost::resize(w, h);
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_uiState(mut env: JNIEnv, _: JClass, json: JString) {
    let s = text(&mut env, &json);
    uihost::state(&s);
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_uiTouch(_: JNIEnv, _: JClass, kind: jint, x: jni::sys::jfloat, y: jni::sys::jfloat) {
    uihost::touch(kind, x, y);
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_uiBack(_: JNIEnv, _: JClass) {
    uihost::back();
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_uiRender(env: JNIEnv, _: JClass, buf: jni::objects::JByteBuffer, w: jint, h: jint, stride: jint, t: jint) -> jint {
    let Ok(ptr) = env.get_direct_buffer_address(&buf) else { return -2 };
    let cap = env.get_direct_buffer_capacity(&buf).unwrap_or(0);
    if (stride as i64) * (h as i64) > cap as i64 {
        return -3;
    }
    unsafe { uihost::render(ptr, w, h, stride, t as u32) }
}

#[no_mangle]
pub extern "system" fn Java_tunn_client_Native_uiPoll(env: JNIEnv, _: JClass) -> jstring {
    let s = uihost::poll().unwrap_or_default();
    env.new_string(s).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}
