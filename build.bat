@echo off
rem Windows build: needs Rust (https://rustup.rs). Result: dist\\Tunn-3.6-windows.exe
cd /d "%~dp0\desktop" || exit /b 1
cargo build --release || exit /b 1
if not exist "..\dist" mkdir "..\dist"
copy /y target\release\tunn.exe "..\dist\Tunn-3.6-windows.exe"
