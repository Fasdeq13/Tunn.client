pub mod api;
pub mod plugin;

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
pub extern "system" fn Java_app_fasdeq_vpn_Native_parseLink(mut env: JNIEnv, _: JClass, conf: JString) -> jstring {
    let conf = text(&mut env, &conf);
    guarded(&mut env, move |_| api::parse_link(&conf))
}

#[no_mangle]
pub extern "system" fn Java_app_fasdeq_vpn_Native_prepareImport(mut env: JNIEnv, _: JClass, input: JString) -> jstring {
    let input = text(&mut env, &input);
    guarded(&mut env, move |_| api::prepare_import(&input))
}

#[no_mangle]
pub extern "system" fn Java_app_fasdeq_vpn_Native_fetchSubscription(mut env: JNIEnv, _: JClass, url: JString) -> jstring {
    let url = text(&mut env, &url);
    guarded(&mut env, move |_| api::fetch_subscription(&url))
}

#[no_mangle]
pub extern "system" fn Java_app_fasdeq_vpn_Native_ping(
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
pub extern "system" fn Java_app_fasdeq_vpn_Native_innerConfig(
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
pub extern "system" fn Java_app_fasdeq_vpn_Native_pluginOpen(mut env: JNIEnv, _: JClass, path: JString) -> jstring {
    let path = text(&mut env, &path);
    guarded(&mut env, move |_| plugin::open(&path))
}

#[no_mangle]
pub extern "system" fn Java_app_fasdeq_vpn_Native_pluginCall(
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
pub extern "system" fn Java_app_fasdeq_vpn_Native_pluginClose(mut env: JNIEnv, _: JClass, handle: jlong) -> jstring {
    guarded(&mut env, move |_| plugin::close(handle))
}

#[no_mangle]
pub extern "system" fn Java_app_fasdeq_vpn_Native_journalInit(mut env: JNIEnv, _: JClass, path: JString) {
    let path = text(&mut env, &path);
    fasdeq_core::engine::journal_init(&path);
}

#[no_mangle]
pub extern "system" fn Java_app_fasdeq_vpn_Native_journalAdd(mut env: JNIEnv, _: JClass, level: JString, tag: JString, msg: JString) {
    let (l, t, m) = (text(&mut env, &level), text(&mut env, &tag), text(&mut env, &msg));
    let _ = std::panic::catch_unwind(|| fasdeq_core::engine::journal_add(&l, &t, &m));
}

#[no_mangle]
pub extern "system" fn Java_app_fasdeq_vpn_Native_journalRead(mut env: JNIEnv, _: JClass, lines: jint) -> jstring {
    let s = fasdeq_core::engine::journal_read(lines.max(1) as usize);
    env.new_string(s).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_app_fasdeq_vpn_Native_journalClear(_: JNIEnv, _: JClass) {
    fasdeq_core::engine::journal_clear();
}

#[no_mangle]
pub extern "system" fn Java_app_fasdeq_vpn_Native_retryDelay(_: JNIEnv, _: JClass, attempt: jint) -> jlong {
    fasdeq_core::engine::retry_delay(attempt.max(0) as u32).map(|v| v as jlong).unwrap_or(-1)
}

#[no_mangle]
pub extern "system" fn Java_app_fasdeq_vpn_Native_startTimeout(mut env: JNIEnv, _: JClass, kind: JString, attempt: jint) -> jlong {
    let k = text(&mut env, &kind);
    fasdeq_core::engine::start_timeout_ms(&k, attempt.max(0) as u32) as jlong
}

#[no_mangle]
pub extern "system" fn Java_app_fasdeq_vpn_Native_settleMs(mut env: JNIEnv, _: JClass, prev: JString) -> jlong {
    let k = text(&mut env, &prev);
    fasdeq_core::engine::settle_ms(&k) as jlong
}

#[no_mangle]
pub extern "system" fn Java_app_fasdeq_vpn_Native_pickNext(mut env: JNIEnv, _: JClass, candidates: JString, failed: JString, current: JString) -> jstring {
    let c = text(&mut env, &candidates);
    let f: Vec<String> = text(&mut env, &failed).split(',').filter(|s| !s.is_empty()).map(|s| s.to_string()).collect();
    let cur = text(&mut env, &current);
    let v: Value = serde_json::from_str(&c).unwrap_or(Value::Null);
    let id = fasdeq_core::engine::pick_next(&v, &f, &cur).unwrap_or_default();
    env.new_string(id).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_app_fasdeq_vpn_Native_subHeaders(mut env: JNIEnv, _: JClass, headers: JString) -> jstring {
    let h = text(&mut env, &headers);
    let v: Value = serde_json::from_str(&h).unwrap_or(Value::Null);
    reply(&mut env, fasdeq_core::engine::parse_sub_headers(&v))
}
