#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use serde_json::{json, Value};
use std::{fs, path::{Path, PathBuf}, process::Command};

// Wird im GitHub-Workflow per SATURN_REPO=<owner>/<repo> gesetzt.
const REPO: &str = match option_env!("SATURN_REPO") {
    Some(r) => r,
    None => "OWNER/saturn",
};

fn err<E: std::fmt::Display>(e: E) -> String {
    e.to_string()
}

fn http() -> reqwest::Client {
    reqwest::Client::builder()
        .user_agent("saturn-launcher/1.0")
        .build()
        .expect("http client")
}

async fn json_of(c: &reqwest::Client, url: &str) -> Result<Value, String> {
    c.get(url)
        .send().await.map_err(err)?
        .error_for_status().map_err(err)?
        .json::<Value>().await.map_err(err)
}

async fn download(c: &reqwest::Client, url: &str, to: &Path) -> Result<(), String> {
    let bytes = c.get(url)
        .send().await.map_err(err)?
        .error_for_status().map_err(err)?
        .bytes().await.map_err(err)?;
    fs::write(to, bytes).map_err(err)
}

#[cfg(windows)]
fn mc_dir() -> PathBuf {
    PathBuf::from(std::env::var("APPDATA").unwrap_or_default()).join(".minecraft")
}
#[cfg(target_os = "macos")]
fn mc_dir() -> PathBuf {
    PathBuf::from(std::env::var("HOME").unwrap_or_default()).join("Library/Application Support/minecraft")
}
#[cfg(not(any(windows, target_os = "macos")))]
fn mc_dir() -> PathBuf {
    PathBuf::from(std::env::var("HOME").unwrap_or_default()).join(".minecraft")
}

/// Welche Minecraft-Versionen hat das neueste Release? (Dateien heißen saturn-<mc>.jar)
#[tauri::command]
async fn get_versions() -> Result<Value, String> {
    let rel = json_of(&http(), &format!("https://api.github.com/repos/{REPO}/releases/latest")).await?;
    let tag = rel["tag_name"].as_str().unwrap_or("").to_string();
    let mut versions: Vec<String> = rel["assets"].as_array().cloned().unwrap_or_default().iter()
        .filter_map(|a| a["name"].as_str())
        .filter_map(|n| n.strip_prefix("saturn-").and_then(|n| n.strip_suffix(".jar")))
        .map(String::from)
        .collect();
    let key = |s: &String| s.split('.').map(|p| p.parse::<u32>().unwrap_or(0)).collect::<Vec<_>>();
    versions.sort_by(|a, b| key(b).cmp(&key(a)));
    Ok(json!({ "tag": tag, "versions": versions }))
}

/// Fabric-Profil + Fabric API + Saturn in einen eigenen Spielordner installieren (aktualisiert dabei auch).
#[tauri::command]
async fn install(mc: String) -> Result<String, String> {
    if mc.is_empty() || !mc.chars().all(|c| c.is_ascii_alphanumeric() || c == '.' || c == '-') {
        return Err("Ungültige Version".into());
    }
    let c = http();
    let root = mc_dir();

    // 1) Fabric Loader (neueste stabile Version) -> Versionsprofil
    let loaders = json_of(&c, &format!("https://meta.fabricmc.net/v2/versions/loader/{mc}")).await?;
    let loader = loaders.as_array()
        .and_then(|a| a.iter().find(|l| l["loader"]["stable"] == true))
        .and_then(|l| l["loader"]["version"].as_str())
        .ok_or("Für diese Version gibt es keinen Fabric Loader")?
        .to_string();
    let profile = json_of(&c, &format!("https://meta.fabricmc.net/v2/versions/loader/{mc}/{loader}/profile/json")).await?;
    let id = profile["id"].as_str().ok_or("Fabric-Profil ohne ID")?.to_string();
    let vdir = root.join("versions").join(&id);
    fs::create_dir_all(&vdir).map_err(err)?;
    fs::write(vdir.join(format!("{id}.json")), serde_json::to_vec_pretty(&profile).map_err(err)?).map_err(err)?;

    // 2) eigener Spielordner, alte Saturn-/Fabric-API-Dateien entfernen
    let game = root.join("saturn").join(&mc);
    let mods = game.join("mods");
    fs::create_dir_all(&mods).map_err(err)?;
    if let Ok(rd) = fs::read_dir(&mods) {
        for e in rd.flatten() {
            let n = e.file_name().to_string_lossy().to_lowercase();
            if (n.starts_with("saturn-") || n.starts_with("fabric-api")) && n.ends_with(".jar") {
                let _ = fs::remove_file(e.path());
            }
        }
    }

    // 3) Fabric API von Modrinth
    let q = format!("https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%22{mc}%22%5D&loaders=%5B%22fabric%22%5D");
    let vers = json_of(&c, &q).await?;
    let files = vers[0]["files"].as_array().ok_or("Keine Fabric API für diese Version gefunden")?;
    let f = files.iter().find(|f| f["primary"] == true).or(files.first()).ok_or("Fabric API ohne Datei")?;
    let (url, name) = (f["url"].as_str().ok_or("URL fehlt")?, f["filename"].as_str().ok_or("Name fehlt")?);
    download(&c, url, &mods.join(name)).await?;

    // 4) Saturn selbst (immer das neueste Release); Tag merken, damit der Auto-Update-Check ihn vergleichen kann
    let tag = json_of(&c, &format!("https://api.github.com/repos/{REPO}/releases/latest")).await?["tag_name"]
        .as_str().unwrap_or("").to_string();
    let saturn = format!("https://github.com/{REPO}/releases/latest/download/saturn-{mc}.jar");
    download(&c, &saturn, &mods.join(format!("saturn-{mc}.jar"))).await?;
    fs::write(game.join(".saturn-tag"), &tag).map_err(err)?;

    // 5) Profil im offiziellen Minecraft Launcher anlegen (mit Sicherung)
    let lp = root.join("launcher_profiles.json");
    let mut doc: Value = fs::read_to_string(&lp).ok()
        .and_then(|s| serde_json::from_str(&s).ok())
        .unwrap_or_else(|| json!({ "profiles": {}, "version": 3 }));
    if lp.exists() { let _ = fs::copy(&lp, root.join("launcher_profiles.saturn-backup.json")); }
    if !doc["profiles"].is_object() { doc["profiles"] = json!({}); }
    let key = format!("saturn-{mc}");
    doc["profiles"][key.as_str()] = json!({
        "name": format!("Saturn {mc}"),
        "type": "custom",
        "icon": "Gold_Block",
        "lastVersionId": id,
        "gameDir": game.to_string_lossy()
    });
    fs::create_dir_all(&root).map_err(err)?;
    fs::write(&lp, serde_json::to_vec_pretty(&doc).map_err(err)?).map_err(err)?;

    Ok(format!("Saturn {mc} ist installiert. Wähle im Minecraft Launcher das Profil „Saturn {mc}“."))
}

/// Welches Release ist für diese Minecraft-Version lokal installiert? (None = nie installiert)
#[tauri::command]
fn installed_tag(mc: String) -> Option<String> {
    if mc.contains("..") || mc.contains('/') || mc.contains('\\') {
        return None;
    }
    fs::read_to_string(mc_dir().join("saturn").join(&mc).join(".saturn-tag"))
        .ok()
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
}

#[cfg(windows)]
fn open_launcher() -> Result<(), String> {
    for var in ["ProgramFiles(x86)", "ProgramFiles"] {
        let p = PathBuf::from(std::env::var(var).unwrap_or_default())
            .join("Minecraft Launcher").join("MinecraftLauncher.exe");
        if p.exists() {
            return Command::new(p).spawn().map(|_| ()).map_err(err);
        }
    }
    // Microsoft-Store-Version
    Command::new("explorer")
        .arg("shell:AppsFolder\\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft")
        .spawn().map(|_| ()).map_err(err)
}
#[cfg(not(windows))]
fn open_launcher() -> Result<(), String> {
    Command::new("minecraft-launcher").spawn().map(|_| ()).map_err(err)
}

#[tauri::command]
fn play() -> Result<(), String> {
    open_launcher()
}

fn main() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![get_versions, install, installed_tag, play])
        .run(tauri::generate_context!())
        .expect("Saturn Launcher konnte nicht starten");
}
