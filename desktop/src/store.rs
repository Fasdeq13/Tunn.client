use crate::model::{Group, Server, Settings};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashMap;
use std::path::PathBuf;

#[derive(Default, Serialize, Deserialize)]
#[serde(default)]
pub struct Store {
    pub servers: Vec<Server>,
    pub groups: Vec<Group>,
    pub selected: Option<String>,
    pub settings: Settings,
    pub checks: HashMap<String, [i32; 3]>,
    pub checked_at: u64,
    pub meta: HashMap<String, [u64; 3]>,
    pub web: Value,
    pub collapsed: Vec<String>,
    pub notice: bool,
    pub hwid: String,
    pub skip_update: String,
}

pub fn data_dir() -> PathBuf {
    let dir = directories::ProjectDirs::from("app", "fasdeq", "FasdeqVPN")
        .map(|d| d.data_dir().to_path_buf())
        .unwrap_or_else(|| std::env::temp_dir().join("FasdeqVPN"));
    let _ = std::fs::create_dir_all(&dir);
    dir
}

fn file() -> PathBuf {
    data_dir().join("state.json")
}

impl Store {
    pub fn load() -> Store {
        std::fs::read_to_string(file())
            .ok()
            .and_then(|raw| serde_json::from_str(&raw).ok())
            .unwrap_or_default()
    }

    pub fn save(&self) {
        if let Ok(raw) = serde_json::to_string(self) {
            let path = file();
            let tmp = path.with_extension("tmp");
            if std::fs::write(&tmp, raw).is_ok() {
                let _ = std::fs::rename(&tmp, &path);
            }
        }
    }
}

pub fn now_millis() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}
