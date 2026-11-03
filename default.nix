# Tunn desktop for NixOS: nix-build  (or: nix-env -if .)
# Xray and sing-box are downloaded by the app itself into ~/.local/share/tunn (userspace), nothing else to install.
{ pkgs ? import <nixpkgs> { } }:

pkgs.rustPlatform.buildRustPackage {
  pname = "tunn";
  version = "3.7.0";
  src = pkgs.lib.cleanSource ./.;
  sourceRoot = "source/desktop";
  cargoLock.lockFile = ./desktop/Cargo.lock;
  nativeBuildInputs = with pkgs; [ pkg-config makeWrapper gcc ];
  buildInputs = with pkgs; [ gtk3 glib libayatana-appindicator libxkbcommon wayland xorg.libX11 xorg.libXcursor xorg.libXrandr xorg.libXi ];
  postInstall = ''
    wrapProgram $out/bin/tunn --prefix LD_LIBRARY_PATH : ${pkgs.lib.makeLibraryPath (with pkgs; [ libayatana-appindicator gtk3 libxkbcommon wayland xorg.libX11 xorg.libXcursor xorg.libXrandr xorg.libXi ])}
  '';
  meta = { description = "Tunn VPN client"; mainProgram = "tunn"; };
}
