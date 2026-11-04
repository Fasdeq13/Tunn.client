#![cfg(unix)]

use std::ffi::OsString;
use std::io::Write;
use std::os::unix::fs::MetadataExt;
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::{Condvar, Mutex};
use std::time::Duration;

#[derive(Clone)]
pub enum Way {
    Root,
    SudoNoPass(PathBuf),
    SudoPass(PathBuf, String),
    Pkexec(PathBuf),
}

#[derive(Default)]
struct Ask {
    pending: bool,
    wrong: bool,
    answer: Option<Option<String>>,
}

static ASK: (Mutex<Ask>, Condvar) = (Mutex::new(Ask { pending: false, wrong: false, answer: None }), Condvar::new());
static CACHE: Mutex<Option<String>> = Mutex::new(None);

pub fn pending() -> Option<bool> {
    let a = ASK.0.lock().ok()?;
    if a.pending {
        Some(a.wrong)
    } else {
        None
    }
}

pub fn answer(pw: Option<String>) {
    if let Ok(mut a) = ASK.0.lock() {
        if a.pending {
            a.answer = Some(pw);
            ASK.1.notify_all();
        }
    }
}

fn ask(wrong: bool) -> Option<String> {
    let mut a = ASK.0.lock().ok()?;
    a.pending = true;
    a.wrong = wrong;
    a.answer = None;
    let (mut a, _) = ASK.1.wait_timeout_while(a, Duration::from_secs(180), |s| s.answer.is_none()).ok()?;
    a.pending = false;
    a.answer.take().flatten()
}

fn is_root() -> bool {
    std::fs::metadata("/proc/self").map(|m| m.uid() == 0).unwrap_or(false)
}

fn find(name: &str, extra: &[&str]) -> Option<PathBuf> {
    for p in extra {
        if Path::new(p).is_file() {
            return Some(PathBuf::from(p));
        }
    }
    crate::xray::which(name)
}

fn sudo() -> Option<PathBuf> {
    find("sudo", &["/run/wrappers/bin/sudo", "/usr/bin/sudo", "/bin/sudo"])
}

fn pkexec() -> Option<PathBuf> {
    find("pkexec", &["/run/wrappers/bin/pkexec", "/usr/bin/pkexec", "/bin/pkexec"])
}

pub fn available() -> bool {
    is_root() || sudo().is_some() || pkexec().is_some()
}

enum Check {
    Ok,
    BadPassword,
    NotAllowed,
}

fn verify(sudo: &Path, pw: &str) -> Check {
    let child = Command::new(sudo)
        .args(["-S", "-p", "", "-k", "true"])
        .stdin(Stdio::piped())
        .stdout(Stdio::null())
        .stderr(Stdio::piped())
        .spawn();
    let Ok(mut child) = child else {
        return Check::NotAllowed;
    };
    if let Some(mut stdin) = child.stdin.take() {
        let _ = stdin.write_all(format!("{}\n", pw).as_bytes());
    }
    let Ok(out) = child.wait_with_output() else {
        return Check::NotAllowed;
    };
    if out.status.success() {
        return Check::Ok;
    }
    let text = String::from_utf8_lossy(&out.stderr).to_lowercase();
    if text.contains("not in the sudoers") || text.contains("may not run") || text.contains("not allowed") {
        Check::NotAllowed
    } else {
        Check::BadPassword
    }
}

pub fn prepare() -> Result<Way, String> {
    if is_root() {
        return Ok(Way::Root);
    }
    if let Some(s) = sudo() {
        let free = Command::new(&s)
            .args(["-n", "true"])
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status()
            .map(|st| st.success())
            .unwrap_or(false);
        if free {
            return Ok(Way::SudoNoPass(s));
        }
        let cached = CACHE.lock().ok().and_then(|c| c.clone());
        if let Some(pw) = cached {
            match verify(&s, &pw) {
                Check::Ok => return Ok(Way::SudoPass(s, pw)),
                Check::NotAllowed => {}
                Check::BadPassword => {
                    if let Ok(mut c) = CACHE.lock() {
                        *c = None;
                    }
                }
            }
        }
        let mut wrong = false;
        let mut allowed = true;
        for _ in 0..3 {
            let Some(pw) = ask(wrong) else {
                return Err("Доступ отклонён: пароль не введён".into());
            };
            match verify(&s, &pw) {
                Check::Ok => {
                    if let Ok(mut c) = CACHE.lock() {
                        *c = Some(pw.clone());
                    }
                    return Ok(Way::SudoPass(s, pw));
                }
                Check::BadPassword => wrong = true,
                Check::NotAllowed => {
                    allowed = false;
                    break;
                }
            }
        }
        if allowed {
            return Err("Неверный пароль".into());
        }
    }
    if let Some(p) = pkexec() {
        return Ok(Way::Pkexec(p));
    }
    Err("Нужны права администратора: пользователь не может пользоваться sudo, а pkexec не найден. Выберите режим «Системный прокси»".into())
}

pub fn base(way: &Way) -> Command {
    match way {
        Way::Root => Command::new("/usr/bin/env"),
        Way::SudoNoPass(s) => {
            let mut c = Command::new(s);
            c.args(["-n", "--"]);
            c
        }
        Way::SudoPass(s, _) => {
            let mut c = Command::new(s);
            c.args(["-S", "-p", "", "--"]);
            c
        }
        Way::Pkexec(p) => Command::new(p),
    }
}

pub fn spawn(way: &Way, mut cmd: Command) -> Result<Child, String> {
    let pass = match way {
        Way::SudoPass(_, pw) => Some(pw.clone()),
        _ => None,
    };
    cmd.stdin(if pass.is_some() { Stdio::piped() } else { Stdio::null() });
    let mut child = cmd.spawn().map_err(|e| format!("Не удалось получить права: {}", e))?;
    if let Some(pw) = pass {
        if let Some(mut stdin) = child.stdin.take() {
            let _ = stdin.write_all(format!("{}\n", pw).as_bytes());
        }
    }
    Ok(child)
}

pub fn hint(code: Option<i32>, last: &str) -> String {
    match code {
        Some(126) => "Доступ отклонён".to_string(),
        Some(1) if last.to_lowercase().contains("password") => "Неверный пароль".to_string(),
        Some(127) => {
            if last.is_empty() {
                "Не удалось запустить помощника с правами администратора".to_string()
            } else {
                last.to_string()
            }
        }
        _ => if last.is_empty() { "Не удалось запустить с правами администратора".to_string() } else { last.to_string() },
    }
}

#[allow(dead_code)]
fn _unused(_: OsString) {}
