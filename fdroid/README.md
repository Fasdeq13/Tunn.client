# F-Droid

Build with `-Papp.fdroid=1`: the in-app updater and its settings page are hidden (`BuildConfig.FDROID`).
Note: F-Droid rejects prebuilt binaries. `libv2ray.aar`, the sing-box/Xray cores and the AmneziaWG library
must be built from source in the recipe before this can be accepted. `LICENSE` in the repo root must match `License:` above.
Fastlane metadata lives in `android/fastlane/metadata/android`.
