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
    // optional picture for the hidden screen: icon.png / icon.jpg / icon.jpeg in the project root
    let icon_out = std::path::PathBuf::from(std::env::var("OUT_DIR").unwrap()).join("icon.bin");
    let mut icon: Vec<u8> = Vec::new();
    for name in ["icon.png", "icon.jpg", "icon.jpeg", "Icon.png", "Icon.jpg", "Icon.jpeg", "icon.PNG", "icon.JPG", "icon.JPEG"] {
        let p = std::path::Path::new("..").join(name);
        println!("cargo:rerun-if-changed=../{}", name);
        if icon.is_empty() {
            if let Ok(b) = std::fs::read(&p) {
                icon = b;
            }
        }
    }
    let _ = std::fs::write(&icon_out, &icon);
    if target == "windows" {
        let mut res = winresource::WindowsResource::new();
        res.set_icon("assets/app.ico");
        res.set("ProductName", "Tunn");
        res.set("FileDescription", "Tunn");
        let _ = res.compile();
    }
}
