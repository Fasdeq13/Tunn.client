fn main() {
    let target = std::env::var("CARGO_CFG_TARGET_OS").unwrap_or_default();
    if target == "windows" {
        let mut res = winresource::WindowsResource::new();
        res.set_icon("assets/app.ico");
        res.set("ProductName", "Tunn");
        res.set("FileDescription", "Tunn");
        let _ = res.compile();
    }
}
