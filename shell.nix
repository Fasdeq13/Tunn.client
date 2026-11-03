# NixOS 26.05: ./build.sh enters this environment automatically.
# Manual entry: nix-shell shell.nix
{ pkgs ? import <nixpkgs> { config.allowUnfree = true; } }:

let
  fhs = pkgs.buildFHSEnv {
    name = "tunn-build";
    targetPkgs = p: with p; [
      bashInteractive coreutils findutils gnugrep gnused gawk gnutar gzip bzip2 xz zip unzip which file
      git curl wget cacert openssl pkg-config gnumake cmake binutils patchelf
      gcc glibc zlib ncurses5 libxcrypt-legacy stdenv.cc.cc.lib libuuid fontconfig freetype
      jdk21
      rustup
      python3
      # desktop (Slint + winit, tray, xdotool)
      gtk3 glib cairo pango gdk-pixbuf atk libayatana-appindicator xdotool
      xorg.libX11 xorg.libXcursor xorg.libXrandr xorg.libXi libxkbcommon wayland libGL
      # Windows cross build
      pkgsCross.mingwW64.stdenv.cc
      pkgsCross.mingwW64.windows.pthreads
    ];
    multiPkgs = p: with p; [ zlib ncurses5 stdenv.cc.cc.lib ];
    profile = ''
      export JAVA_HOME=${pkgs.jdk21}/lib/openjdk
      export SSL_CERT_FILE=${pkgs.cacert}/etc/ssl/certs/ca-bundle.crt
      export TUNN_FHS=1
    '';
    runScript = "bash";
  };
in
fhs.env
