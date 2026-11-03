fn main() {
    let config = slint_build::CompilerConfiguration::new().with_style("material".into()).embed_resources(slint_build::EmbedResourcesKind::EmbedForSoftwareRenderer);
    slint_build::compile_with_config("ui/app.slint", config).expect("slint ui");
    let target = std::env::var("CARGO_CFG_TARGET_OS").unwrap_or_default();
    // privileged helper (C), embedded into the binary on Linux
    let out = std::path::PathBuf::from(std::env::var("OUT_DIR").unwrap()).join("tunn-helper");
    println!("cargo:rerun-if-changed=helper/tunn-helper.c");
    if target == "linux" {
        let cc = cc::Build::new().get_compiler();
        let status = cc.to_command().args(["-O2", "-Wall", "-o"]).arg(&out).arg("helper/tunn-helper.c").status();
        if !matches!(status, Ok(s) if s.success()) {
            println!("cargo:warning=tunn-helper (C) was not built, WireGuard userspace mode is disabled");
            let _ = std::fs::write(&out, b"");
        }
    } else {
        let _ = std::fs::write(&out, b"");
    }
    if target == "windows" {
        let mut res = winresource::WindowsResource::new();
        res.set_icon("assets/app.ico");
        res.set("ProductName", "Tunn");
        res.set("FileDescription", "Tunn");
        let _ = res.compile();
    }
}
