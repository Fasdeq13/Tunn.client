//! Tunn compiles its UI from .slint files in build.rs and never calls `slint!`.
//! The real crate is a proc-macro that pulls in the whole Slint compiler a second time
//! and fails to load in some build environments, so a no-op macro stands in for it.
#[macro_export]
macro_rules! slint {
    ($($t:tt)*) => {
        compile_error!("slint! is not available in this build")
    };
}
