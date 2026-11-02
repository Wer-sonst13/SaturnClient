use crate::net::*;
use serde::{Deserialize, Serialize};
use base64::{engine::general_purpose::STANDARD, Engine};
use serde_json::{json, Value};
use std::{
    collections::{HashMap, HashSet},
    fs,
    path::PathBuf,
    process::{Command, Stdio},
};
use tauri::{AppHandle, Emitter};

// Wird im GitHub-Workflow per SATURN_REPO=<owner>/<repo> gesetzt.
pub const REPO: &str = match option_env!("SATURN_REPO") {
    Some(r) => r,
    None => "OWNER/saturn",
};
const FABRIC_API_ID: &str = "P7dR8mSH";

#[cfg(windows)]
const OS: &str = "windows";
#[cfg(target_os = "macos")]
const OS: &str = "osx";
#[cfg(not(any(windows, target_os = "macos")))]
const OS: &str = "linux";

#[cfg(windows)]
pub fn root() -> PathBuf {
    PathBuf::from(std::env::var("APPDATA").unwrap_or_default()).join("Saturn")
}
#[cfg(target_os = "macos")]
pub fn root() -> PathBuf {
    PathBuf::from(std::env::var("HOME").unwrap_or_default()).join("Library/Application Support/Saturn")
}
#[cfg(not(any(windows, target_os = "macos")))]
pub fn root() -> PathBuf {
    PathBuf::from(std::env::var("HOME").unwrap_or_default()).join(".local/share/saturn")
}

// ------------------------------------------------------------------ Instanzen
#[derive(Serialize, Deserialize, Clone)]
pub struct Instance {
    pub id: String,
    pub name: String,
    pub mc: String,
    #[serde(default = "default_ram")]
    pub ram: u32,
    #[serde(default)]
    pub jvm: String,
}
fn default_ram() -> u32 {
    3072
}

pub fn inst_dir(id: &str) -> PathBuf {
    root().join("instances").join(id)
}

pub fn check_id(id: &str) -> Result<(), String> {
    if !id.is_empty() && id.chars().all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_') {
        Ok(())
    } else {
        Err("Ungültige Instanz".into())
    }
}

pub fn list() -> Vec<Instance> {
    let mut v = Vec::new();
    if let Ok(rd) = fs::read_dir(root().join("instances")) {
        for d in rd.flatten() {
            if let Ok(s) = fs::read_to_string(d.path().join("instance.json")) {
                if let Ok(i) = serde_json::from_str::<Instance>(&s) {
                    v.push(i);
                }
            }
        }
    }
    v.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
    v
}

pub fn get(id: &str) -> Result<Instance, String> {
    check_id(id)?;
    let s = fs::read_to_string(inst_dir(id).join("instance.json")).map_err(|_| "Instanz nicht gefunden".to_string())?;
    serde_json::from_str(&s).map_err(e)
}

pub fn save(i: &Instance) -> Result<(), String> {
    let dir = inst_dir(&i.id);
    fs::create_dir_all(dir.join("game")).map_err(e)?;
    fs::write(dir.join("instance.json"), serde_json::to_vec_pretty(i).map_err(e)?).map_err(e)
}

pub fn create(name: String, version: String) -> Result<Instance, String> {
    let name = name.trim().to_string();
    if name.is_empty() || name.len() > 40 {
        return Err("Name fehlt oder ist zu lang".into());
    }
    if version.is_empty() || !version.chars().all(|c| c.is_ascii_alphanumeric() || c == '.' || c == '-') {
        return Err("Ungültige Version".into());
    }
    let mut slug: String = name
        .to_lowercase()
        .chars()
        .map(|c| if c.is_ascii_alphanumeric() { c } else { '-' })
        .collect();
    slug.truncate(24);
    let slug = slug.trim_matches('-').to_string();
    let slug = if slug.is_empty() { "instanz".to_string() } else { slug };
    let secs = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    let inst = Instance { id: format!("{}-{}", slug, secs % 100000), name, mc: version, ram: default_ram(), jvm: String::new() };
    save(&inst)?;
    Ok(inst)
}

pub fn delete(id: &str) -> Result<(), String> {
    check_id(id)?;
    fs::remove_dir_all(inst_dir(id)).map_err(e)
}

pub fn update_instance(id: &str, name: String, ram: u32, jvm: String) -> Result<(), String> {
    let mut i = get(id)?;
    let name = name.trim().to_string();
    if name.is_empty() || name.len() > 40 {
        return Err("Name fehlt oder ist zu lang".into());
    }
    i.name = name;
    i.ram = ram.clamp(1024, 32768);
    i.jvm = jvm.trim().chars().take(300).collect();
    save(&i)
}

pub fn open_dir(id: &str) -> Result<(), String> {
    check_id(id)?;
    let p = inst_dir(id).join("game");
    fs::create_dir_all(&p).map_err(e)?;
    let prog = if cfg!(windows) { "explorer" } else if cfg!(target_os = "macos") { "open" } else { "xdg-open" };
    Command::new(prog).arg(p).spawn().map(|_| ()).map_err(e)
}

// ------------------------------------------------------------------ Konto (vorerst offline)
#[derive(Serialize, Deserialize, Clone)]
pub struct Account {
    pub name: String,
    #[serde(default = "default_model")]
    pub model: String,
    #[serde(default)]
    pub skin: Option<String>,
}
fn default_model() -> String {
    "classic".into()
}

fn save_account(a: &Account) -> Result<(), String> {
    fs::create_dir_all(root()).map_err(e)?;
    fs::write(root().join("account.json"), serde_json::to_vec_pretty(a).map_err(e)?).map_err(e)
}

pub fn account_get() -> Account {
    fs::read_to_string(root().join("account.json"))
        .ok()
        .and_then(|s| serde_json::from_str(&s).ok())
        .unwrap_or(Account { name: "Player".into(), model: default_model(), skin: None })
}

pub fn account_set(name: String, model: String) -> Result<Account, String> {
    let name = name.trim().to_string();
    if name.len() < 3 || name.len() > 16 || !name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_') {
        return Err("Name: 3–16 Zeichen, nur Buchstaben, Zahlen und _".into());
    }
    if model != "slim" && model != "classic" {
        return Err("Ungültiges Modell".into());
    }
    let mut a = account_get();
    a.name = name;
    a.model = model;
    save_account(&a)?;
    Ok(a)
}

pub fn account_skin(skin: Option<String>) -> Result<Account, String> {
    if let Some(s) = &skin {
        check_skin_name(s)?;
        if !skin_dir().join(format!("{s}.png")).exists() {
            return Err("Skin nicht gefunden".into());
        }
    }
    let mut a = account_get();
    a.skin = skin;
    save_account(&a)?;
    Ok(a)
}

pub fn offline_uuid(name: &str) -> String {
    let mut h = md5::compute(format!("OfflinePlayer:{name}")).0;
    h[6] = (h[6] & 0x0f) | 0x30;
    h[8] = (h[8] & 0x3f) | 0x80;
    let s: String = h.iter().map(|b| format!("{:02x}", b)).collect();
    format!("{}-{}-{}-{}-{}", &s[0..8], &s[8..12], &s[12..16], &s[16..20], &s[20..32])
}

// ------------------------------------------------------------------ Skins
fn skin_dir() -> PathBuf {
    root().join("skins")
}

fn check_skin_name(n: &str) -> Result<(), String> {
    if !n.is_empty() && n.len() <= 40 && n.chars().all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_') {
        Ok(())
    } else {
        Err("Ungültiger Skin-Name".into())
    }
}

pub fn skins_list() -> Vec<Value> {
    let mut v = Vec::new();
    if let Ok(rd) = fs::read_dir(skin_dir()) {
        for f in rd.flatten() {
            let n = f.file_name().to_string_lossy().to_string();
            if let Some(name) = n.strip_suffix(".png") {
                if let Ok(b) = fs::read(f.path()) {
                    v.push(json!({ "name": name, "data": format!("data:image/png;base64,{}", STANDARD.encode(b)) }));
                }
            }
        }
    }
    v.sort_by(|a, b| a["name"].as_str().unwrap_or("").cmp(b["name"].as_str().unwrap_or("")));
    v
}

pub fn skin_add(name: String, data: String) -> Result<String, String> {
    let bytes = STANDARD.decode(data.trim()).map_err(e)?;
    if bytes.len() < 33 || bytes.len() > 300_000 || !bytes.starts_with(b"\x89PNG\r\n\x1a\n") {
        return Err("Das ist keine gültige PNG-Datei".into());
    }
    let w = u32::from_be_bytes(<[u8; 4]>::try_from(&bytes[16..20]).map_err(e)?);
    let h = u32::from_be_bytes(<[u8; 4]>::try_from(&bytes[20..24]).map_err(e)?);
    if w != 64 || (h != 64 && h != 32) {
        return Err("Der Skin muss 64x64 oder 64x32 Pixel groß sein".into());
    }
    let mut base: String = name
        .trim_end_matches(".png")
        .chars()
        .map(|c| if c.is_ascii_alphanumeric() || c == '-' || c == '_' { c } else { '-' })
        .collect();
    base.truncate(30);
    if base.is_empty() {
        base = "skin".into();
    }
    let mut fin = base.clone();
    let mut n = 2;
    while skin_dir().join(format!("{fin}.png")).exists() {
        fin = format!("{base}-{n}");
        n += 1;
    }
    fs::create_dir_all(skin_dir()).map_err(e)?;
    fs::write(skin_dir().join(format!("{fin}.png")), bytes).map_err(e)?;
    Ok(fin)
}

pub fn skin_delete(name: &str) -> Result<(), String> {
    check_skin_name(name)?;
    let _ = fs::remove_file(skin_dir().join(format!("{name}.png")));
    if account_get().skin.as_deref() == Some(name) {
        account_skin(None)?;
    }
    Ok(())
}

/// Offline-Skin: wird über CustomSkinLoader als lokaler Skin in die Instanz gelegt.
async fn apply_skin(c: &reqwest::Client, inst: &Instance, acc: &Account) {
    let game = inst_dir(&inst.id).join("game");
    let dir = game.join("CustomSkinLoader").join("LocalSkin").join("skins");
    let file = dir.join(format!("{}.png", acc.name));
    match &acc.skin {
        Some(s) if check_skin_name(s).is_ok() && skin_dir().join(format!("{s}.png")).exists() => {
            let mods = game.join("mods");
            if !has_prefixed(&mods, "customskinloader") {
                let _ = install_project(c, inst, "customskinloader").await;
            }
            let _ = fs::create_dir_all(&dir);
            let _ = fs::copy(skin_dir().join(format!("{s}.png")), file);
        }
        _ => {
            let _ = fs::remove_file(file);
        }
    }
}

// ------------------------------------------------------------------ Server-Favoriten
pub fn servers_list() -> Vec<Value> {
    fs::read_to_string(root().join("servers.json"))
        .ok()
        .and_then(|s| serde_json::from_str(&s).ok())
        .unwrap_or_default()
}

fn servers_save(v: &[Value]) -> Result<(), String> {
    fs::create_dir_all(root()).map_err(e)?;
    fs::write(root().join("servers.json"), serde_json::to_vec_pretty(v).map_err(e)?).map_err(e)
}

fn valid_address(a: &str) -> bool {
    a.len() >= 3 && a.len() <= 80 && a.chars().all(|c| c.is_ascii_alphanumeric() || c == '.' || c == ':' || c == '-')
}

pub fn servers_add(name: String, address: String) -> Result<(), String> {
    let name = name.trim().to_string();
    let address = address.trim().to_string();
    if name.is_empty() || name.len() > 30 || !valid_address(&address) {
        return Err("Name oder Adresse ungültig".into());
    }
    let mut v = servers_list();
    v.retain(|s| s["address"] != address.as_str());
    v.push(json!({ "name": name, "address": address }));
    servers_save(&v)
}

pub fn servers_remove(address: &str) -> Result<(), String> {
    let mut v = servers_list();
    v.retain(|s| s["address"] != address);
    servers_save(&v)
}

pub fn read_log(id: &str) -> Result<String, String> {
    check_id(id)?;
    let d = inst_dir(id);
    let p = if d.join("game/logs/latest.log").exists() { d.join("game/logs/latest.log") } else { d.join("launch.log") };
    let b = fs::read(&p).map_err(|_| "Noch kein Log vorhanden".to_string())?;
    let start = b.len().saturating_sub(40_000);
    Ok(String::from_utf8_lossy(&b[start..]).to_string())
}

pub fn mod_add_file(id: &str, name: &str, data: &str) -> Result<(), String> {
    check_id(id)?;
    if !safe_name(name) || !name.to_lowercase().ends_with(".jar") {
        return Err("Nur .jar-Dateien".into());
    }
    let bytes = STANDARD.decode(data.trim()).map_err(e)?;
    let mods = inst_dir(id).join("game").join("mods");
    fs::create_dir_all(&mods).map_err(e)?;
    fs::write(mods.join(name), bytes).map_err(e)
}

// ------------------------------------------------------------------ Hilfen
fn emit(app: &AppHandle, stage: &str, done: usize, total: usize) {
    let _ = app.emit("progress", json!({ "stage": stage, "done": done, "total": total }));
}

fn rules_ok(rules: &Value) -> bool {
    let Some(arr) = rules.as_array() else { return true };
    let mut allowed = false;
    for r in arr {
        let allow = r["action"].as_str() == Some("allow");
        let mut m = true;
        if let Some(os) = r.get("os") {
            if let Some(n) = os["name"].as_str() {
                m &= n == OS;
            }
            if os["arch"].as_str() == Some("x86") {
                m = false;
            }
        }
        if r.get("features").is_some() {
            m = false;
        }
        if m {
            allowed = allow;
        }
    }
    allowed
}

fn collect_args(v: &Value, key: &str, out: &mut Vec<String>) {
    let Some(a) = v["arguments"][key].as_array() else { return };
    for x in a {
        if let Some(s) = x.as_str() {
            out.push(s.to_string());
        } else if rules_ok(&x["rules"]) {
            match &x["value"] {
                Value::String(s) => out.push(s.clone()),
                Value::Array(vs) => {
                    for s in vs {
                        if let Some(s) = s.as_str() {
                            out.push(s.to_string());
                        }
                    }
                }
                _ => {}
            }
        }
    }
}

fn maven_path(name: &str) -> Option<String> {
    let p: Vec<&str> = name.split(':').collect();
    if p.len() < 3 {
        return None;
    }
    let (g, a, v) = (p[0].replace('.', "/"), p[1], p[2]);
    Some(format!("{g}/{a}/{v}/{a}-{v}.jar"))
}

fn runtime_platform() -> &'static str {
    if cfg!(windows) {
        "windows-x64"
    } else if cfg!(target_os = "macos") {
        if cfg!(target_arch = "aarch64") { "mac-os-arm64" } else { "mac-os" }
    } else {
        "linux"
    }
}

fn java_bin(dir: &PathBuf) -> PathBuf {
    if cfg!(windows) {
        dir.join("bin").join("java.exe")
    } else if cfg!(target_os = "macos") {
        dir.join("jre.bundle/Contents/Home/bin/java")
    } else {
        dir.join("bin/java")
    }
}

async fn ensure_java(app: &AppHandle, c: &reqwest::Client, vanilla: &Value) -> Result<PathBuf, String> {
    let comp = vanilla["javaVersion"]["component"].as_str().unwrap_or("java-runtime-delta").to_string();
    let dir = root().join("runtime").join(&comp);
    let bin = java_bin(&dir);
    if bin.exists() && dir.join(".ok").exists() {
        return Ok(bin);
    }
    emit(app, "Java wird geladen", 0, 0);
    let all = get_json(c, "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json").await?;
    let murl = all[runtime_platform()][comp.as_str()][0]["manifest"]["url"]
        .as_str()
        .ok_or("Keine Java-Laufzeit für dieses System gefunden")?
        .to_string();
    let man = get_json(c, &murl).await?;
    let mut items: Vec<(String, PathBuf)> = Vec::new();
    let mut exec: Vec<PathBuf> = Vec::new();
    if let Some(files) = man["files"].as_object() {
        for (p, f) in files {
            if f["type"] == "file" {
                if let Some(u) = f["downloads"]["raw"]["url"].as_str() {
                    let dest = dir.join(p);
                    items.push((u.to_string(), dest.clone()));
                    if f["executable"] == true {
                        exec.push(dest);
                    }
                }
            }
        }
    }
    let a = app.clone();
    download_many(c, items, move |d, t| emit(&a, "Java wird geladen", d, t)).await?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        for p in &exec {
            let _ = fs::set_permissions(p, fs::Permissions::from_mode(0o755));
        }
    }
    let _ = &exec;
    fs::write(dir.join(".ok"), "1").map_err(e)?;
    Ok(bin)
}

// ------------------------------------------------------------------ Mods
pub async fn latest_tag(c: &reqwest::Client) -> String {
    get_json(c, &format!("https://api.github.com/repos/{REPO}/releases/latest"))
        .await
        .ok()
        .and_then(|v| v["tag_name"].as_str().map(String::from))
        .unwrap_or_default()
}

pub async fn install_project(c: &reqwest::Client, inst: &Instance, project: &str) -> Result<(), String> {
    let mods = inst_dir(&inst.id).join("game").join("mods");
    fs::create_dir_all(&mods).map_err(e)?;
    let mut queue = vec![project.to_string()];
    let mut seen: HashSet<String> = HashSet::new();
    while let Some(p) = queue.pop() {
        if !seen.insert(p.clone()) || (p == FABRIC_API_ID && p != project) {
            continue;
        }
        let url = format!(
            "https://api.modrinth.com/v2/project/{}/version?game_versions=%5B%22{}%22%5D&loaders=%5B%22fabric%22%5D",
            enc(&p),
            enc(&inst.mc)
        );
        let vers = get_json(c, &url).await?;
        let v = &vers[0];
        if v.is_null() {
            if p == project {
                return Err("Keine passende Version für diese Minecraft-Version".into());
            }
            continue;
        }
        let files = v["files"].as_array().cloned().unwrap_or_default();
        let f = files.iter().find(|f| f["primary"] == true).or(files.first()).ok_or("Keine Datei gefunden")?;
        let u = f["url"].as_str().ok_or("URL fehlt")?;
        let n = f["filename"].as_str().ok_or("Dateiname fehlt")?;
        if !safe_name(n) {
            return Err("Ungültiger Dateiname".into());
        }
        download(c, u, &mods.join(n)).await?;
        for d in v["dependencies"].as_array().cloned().unwrap_or_default() {
            if d["dependency_type"] == "required" {
                if let Some(pid) = d["project_id"].as_str() {
                    queue.push(pid.to_string());
                }
            }
        }
    }
    Ok(())
}

fn remove_prefixed(dir: &PathBuf, prefix: &str) {
    if let Ok(rd) = fs::read_dir(dir) {
        for f in rd.flatten() {
            let n = f.file_name().to_string_lossy().to_lowercase();
            if n.starts_with(prefix) && n.ends_with(".jar") {
                let _ = fs::remove_file(f.path());
            }
        }
    }
}

fn has_prefixed(dir: &PathBuf, prefix: &str) -> bool {
    fs::read_dir(dir)
        .map(|rd| {
            rd.flatten().any(|f| {
                let n = f.file_name().to_string_lossy().to_lowercase();
                n.starts_with(prefix) && n.ends_with(".jar")
            })
        })
        .unwrap_or(false)
}

/// Fabric API und Saturn aktuell halten (Saturn nur, wenn es für diese Version ein Release-Jar gibt).
async fn ensure_mods(c: &reqwest::Client, inst: &Instance, tag: &str) -> Result<(), String> {
    let game = inst_dir(&inst.id).join("game");
    let mods = game.join("mods");
    fs::create_dir_all(&mods).map_err(e)?;
    let tagf = game.join(".saturn-tag");
    let old = fs::read_to_string(&tagf).unwrap_or_default();
    let changed = !tag.is_empty() && old.trim() != tag;

    if changed || !has_prefixed(&mods, "fabric-api") {
        remove_prefixed(&mods, "fabric-api");
        install_project(c, inst, FABRIC_API_ID).await?;
    }
    if changed || !has_prefixed(&mods, "saturn-") {
        remove_prefixed(&mods, "saturn-");
        let url = format!("https://github.com/{REPO}/releases/latest/download/saturn-{}.jar", inst.mc);
        let _ = download(c, &url, &mods.join(format!("saturn-{}.jar", inst.mc))).await;
    }
    if !tag.is_empty() {
        let _ = fs::write(&tagf, tag);
    }
    Ok(())
}

pub fn instance_mods(id: &str) -> Result<Vec<Value>, String> {
    check_id(id)?;
    let mods = inst_dir(id).join("game").join("mods");
    let mut v = Vec::new();
    if let Ok(rd) = fs::read_dir(&mods) {
        for f in rd.flatten() {
            let n = f.file_name().to_string_lossy().to_string();
            let enabled = n.ends_with(".jar");
            if enabled || n.ends_with(".jar.disabled") {
                let size = f.metadata().map(|m| m.len()).unwrap_or(0);
                v.push(json!({ "file": n, "enabled": enabled, "size": size }));
            }
        }
    }
    v.sort_by(|a, b| a["file"].as_str().unwrap_or("").to_lowercase().cmp(&b["file"].as_str().unwrap_or("").to_lowercase()));
    Ok(v)
}

pub fn mod_remove(id: &str, file: &str) -> Result<(), String> {
    check_id(id)?;
    if !safe_name(file) {
        return Err("Ungültiger Dateiname".into());
    }
    fs::remove_file(inst_dir(id).join("game").join("mods").join(file)).map_err(e)
}

pub fn mod_toggle(id: &str, file: &str) -> Result<(), String> {
    check_id(id)?;
    if !safe_name(file) {
        return Err("Ungültiger Dateiname".into());
    }
    let dir = inst_dir(id).join("game").join("mods");
    let from = dir.join(file);
    let to = match file.strip_suffix(".disabled") {
        Some(base) => dir.join(base),
        None => dir.join(format!("{file}.disabled")),
    };
    fs::rename(from, to).map_err(e)
}

pub async fn mod_search(query: &str, version: &str) -> Result<Value, String> {
    let facets = format!("[[\"project_type:mod\"],[\"categories:fabric\"],[\"versions:{version}\"]]");
    let url = format!(
        "https://api.modrinth.com/v2/search?query={}&facets={}&limit=24&index=downloads",
        enc(query),
        enc(&facets)
    );
    let r = get_json(&client(), &url).await?;
    let hits: Vec<Value> = r["hits"]
        .as_array()
        .cloned()
        .unwrap_or_default()
        .iter()
        .map(|h| json!({
            "id": h["project_id"], "title": h["title"], "description": h["description"],
            "icon": h["icon_url"], "downloads": h["downloads"]
        }))
        .collect();
    Ok(Value::Array(hits))
}

pub async fn versions() -> Result<Vec<String>, String> {
    let m = get_json(&client(), "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").await?;
    let key = |s: &str| s.split('.').map(|p| p.parse::<u32>().unwrap_or(0)).collect::<Vec<_>>();
    let min = vec![1u32, 21, 1];
    Ok(m["versions"]
        .as_array()
        .cloned()
        .unwrap_or_default()
        .iter()
        .filter(|v| v["type"] == "release")
        .filter_map(|v| v["id"].as_str())
        .filter(|id| key(id) >= min)
        .map(String::from)
        .collect())
}

// ------------------------------------------------------------------ Starten
pub async fn play(app: AppHandle, id: String, server: Option<String>) -> Result<String, String> {
    let inst = get(&id)?;
    let acc = account_get();
    let c = client();
    let root = root();

    // 1) Versionsdaten
    emit(&app, "Versionsdaten werden geladen", 0, 0);
    let vpath = root.join("versions").join(&inst.mc).join(format!("{}.json", inst.mc));
    if !vpath.exists() {
        let manifest = get_json(&c, "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").await?;
        let url = manifest["versions"]
            .as_array()
            .and_then(|a| a.iter().find(|v| v["id"] == inst.mc.as_str()))
            .and_then(|v| v["url"].as_str())
            .ok_or("Minecraft-Version unbekannt")?
            .to_string();
        download(&c, &url, &vpath).await?;
    }
    let vanilla: Value = serde_json::from_str(&fs::read_to_string(&vpath).map_err(e)?).map_err(e)?;

    let loaders = get_json(&c, &format!("https://meta.fabricmc.net/v2/versions/loader/{}", inst.mc)).await?;
    let loader = loaders
        .as_array()
        .and_then(|a| a.iter().find(|l| l["loader"]["stable"] == true))
        .and_then(|l| l["loader"]["version"].as_str())
        .ok_or("Für diese Version gibt es noch keinen Fabric Loader")?
        .to_string();
    let fpath = root.join("versions").join(format!("fabric-{}-{}.json", loader, inst.mc));
    if !fpath.exists() {
        let url = format!("https://meta.fabricmc.net/v2/versions/loader/{}/{}/profile/json", inst.mc, loader);
        download(&c, &url, &fpath).await?;
    }
    let fabric: Value = serde_json::from_str(&fs::read_to_string(&fpath).map_err(e)?).map_err(e)?;

    // 2) Client-Jar + Bibliotheken
    let client_jar = root.join("versions").join(&inst.mc).join(format!("{}.jar", inst.mc));
    let mut items: Vec<(String, PathBuf)> = Vec::new();
    items.push((
        vanilla["downloads"]["client"]["url"].as_str().ok_or("Client-Download fehlt")?.to_string(),
        client_jar.clone(),
    ));
    let mut cp: Vec<PathBuf> = Vec::new();
    let mut seen: HashSet<PathBuf> = HashSet::new();
    for lib in vanilla["libraries"].as_array().cloned().unwrap_or_default() {
        if !rules_ok(&lib["rules"]) {
            continue;
        }
        let art = &lib["downloads"]["artifact"];
        if let (Some(p), Some(u)) = (art["path"].as_str(), art["url"].as_str()) {
            let dest = root.join("libraries").join(p);
            if seen.insert(dest.clone()) {
                items.push((u.to_string(), dest.clone()));
                cp.push(dest);
            }
        }
    }
    for lib in fabric["libraries"].as_array().cloned().unwrap_or_default() {
        let (Some(name), Some(path)) = (lib["name"].as_str(), lib["name"].as_str().and_then(maven_path)) else { continue };
        let _ = name;
        let mut base = lib["url"].as_str().unwrap_or("https://maven.fabricmc.net/").to_string();
        if !base.ends_with('/') {
            base.push('/');
        }
        let dest = root.join("libraries").join(&path);
        if seen.insert(dest.clone()) {
            items.push((format!("{base}{path}"), dest.clone()));
            cp.push(dest);
        }
    }
    cp.push(client_jar);
    let a = app.clone();
    download_many(&c, items, move |d, t| emit(&a, "Bibliotheken werden geladen", d, t)).await?;

    // 3) Assets
    let ai_id = vanilla["assetIndex"]["id"].as_str().ok_or("Asset-Index fehlt")?.to_string();
    let ai_url = vanilla["assetIndex"]["url"].as_str().ok_or("Asset-Index-URL fehlt")?.to_string();
    let idx_path = root.join("assets").join("indexes").join(format!("{ai_id}.json"));
    if !idx_path.exists() {
        download(&c, &ai_url, &idx_path).await?;
    }
    let idx: Value = serde_json::from_str(&fs::read_to_string(&idx_path).map_err(e)?).map_err(e)?;
    let mut assets: Vec<(String, PathBuf)> = Vec::new();
    if let Some(obj) = idx["objects"].as_object() {
        for (_, v) in obj {
            if let Some(h) = v["hash"].as_str() {
                if h.len() < 2 {
                    continue;
                }
                let pre = &h[..2];
                assets.push((
                    format!("https://resources.download.minecraft.net/{pre}/{h}"),
                    root.join("assets").join("objects").join(pre).join(h),
                ));
            }
        }
    }
    let a = app.clone();
    download_many(&c, assets, move |d, t| emit(&a, "Spieldateien werden geladen", d, t)).await?;

    // 4) Java + Mods
    let java = ensure_java(&app, &c, &vanilla).await?;
    emit(&app, "Mods werden geprüft", 0, 0);
    let tag = latest_tag(&c).await;
    ensure_mods(&c, &inst, &tag).await?;
    apply_skin(&c, &inst, &acc).await;

    // 5) Startbefehl
    let idir = inst_dir(&inst.id);
    let game = idir.join("game");
    let natives = idir.join("natives");
    fs::create_dir_all(&natives).map_err(e)?;
    let sep = if cfg!(windows) { ";" } else { ":" };
    let classpath = cp.iter().map(|p| p.to_string_lossy().to_string()).collect::<Vec<_>>().join(sep);

    let mut vars: HashMap<&str, String> = HashMap::new();
    vars.insert("auth_player_name", acc.name.clone());
    vars.insert("auth_uuid", offline_uuid(&acc.name));
    vars.insert("auth_access_token", "0".into());
    vars.insert("auth_xuid", "0".into());
    vars.insert("clientid", "saturn".into());
    vars.insert("user_type", "msa".into());
    vars.insert("version_name", inst.mc.clone());
    vars.insert("version_type", "Saturn".into());
    vars.insert("game_directory", game.to_string_lossy().to_string());
    vars.insert("assets_root", root.join("assets").to_string_lossy().to_string());
    vars.insert("assets_index_name", ai_id);
    vars.insert("natives_directory", natives.to_string_lossy().to_string());
    vars.insert("launcher_name", "Saturn".into());
    vars.insert("launcher_version", "2.0".into());
    vars.insert("classpath", classpath);
    vars.insert("classpath_separator", sep.into());
    vars.insert("library_directory", root.join("libraries").to_string_lossy().to_string());
    let sub = |s: &str| -> String {
        let mut o = s.to_string();
        for (k, v) in &vars {
            o = o.replace(&format!("${{{}}}", k), v);
        }
        o
    };

    let mut jvm: Vec<String> = Vec::new();
    collect_args(&vanilla, "jvm", &mut jvm);
    collect_args(&fabric, "jvm", &mut jvm);
    let mut gm: Vec<String> = Vec::new();
    collect_args(&vanilla, "game", &mut gm);
    collect_args(&fabric, "game", &mut gm);

    let mut args: Vec<String> = vec![format!("-Xmx{}M", inst.ram), "-Xms512M".into(), "-XX:+UseG1GC".into()];
    args.extend(inst.jvm.split_whitespace().map(String::from));
    args.extend(jvm.iter().map(|s| sub(s)));
    args.push(fabric["mainClass"].as_str().ok_or("Fabric-Hauptklasse fehlt")?.to_string());
    args.extend(gm.iter().map(|s| sub(s)));
    if let Some(a) = &server {
        if !valid_address(a) {
            return Err("Ungültige Serveradresse".into());
        }
        args.push("--quickPlayMultiplayer".into());
        args.push(a.clone());
    }

    let log = fs::File::create(idir.join("launch.log")).map_err(e)?;
    let log2 = log.try_clone().map_err(e)?;
    let mut cmd = Command::new(java);
    cmd.args(args).current_dir(&game).stdout(Stdio::from(log)).stderr(Stdio::from(log2));
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(0x0800_0000);
    }
    let mut child = cmd.spawn().map_err(|x| format!("Java konnte nicht gestartet werden: {x}"))?;
    let a = app.clone();
    std::thread::spawn(move || {
        let code = child.wait().ok().and_then(|s| s.code()).unwrap_or(-1);
        let _ = a.emit("game-exit", code);
    });
    Ok(format!("{} wurde gestartet.", inst.name))
}
