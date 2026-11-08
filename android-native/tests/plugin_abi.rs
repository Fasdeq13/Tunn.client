use std::process::Command;

#[test]
fn plugin_abi_roundtrip() {
    let dir = std::env::temp_dir().join("fq_plugin_abi_test");
    std::fs::create_dir_all(&dir).unwrap();
    let so = dir.join("libecho.so");
    let src = concat!(env!("CARGO_MANIFEST_DIR"), "/../plugins/sdk/example/echo.c");
    let status = Command::new("cc")
        .args(["-shared", "-fPIC", "-o"])
        .arg(&so)
        .arg(src)
        .status()
        .expect("cc");
    assert!(status.success());
    let opened = tunn_service::plugin::open(so.to_str().unwrap());
    assert_eq!(opened["ok"], true, "{}", opened);
    let h = opened["handle"].as_i64().unwrap();
    let prep = tunn_service::plugin::call(h, "prepare", "{\"conf\":\"x\"}", -1, 0);
    assert_eq!(prep["ok"], true);
    assert_eq!(prep["value"]["mtu"], 1400);
    let start = tunn_service::plugin::call(h, "start", "{}", 5, 0);
    assert_eq!(start["ok"], true);
    let ping = tunn_service::plugin::call(h, "ping", "{}", -1, 2000);
    assert_eq!(ping["ms"], 42);
    let stats = tunn_service::plugin::call(h, "stats", "", -1, 0);
    assert_eq!(stats["ok"], true);
    assert_eq!(tunn_service::plugin::call(h, "nope", "", -1, 0)["ok"], false);
    assert_eq!(tunn_service::plugin::close(h)["ok"], true);
    assert_eq!(tunn_service::plugin::call(h, "start", "{}", 5, 0)["ok"], false);
    let missing = tunn_service::plugin::open("/nonexistent/lib.so");
    assert_eq!(missing["ok"], false);
}
