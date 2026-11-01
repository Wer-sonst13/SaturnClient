#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod mc;
mod net;

use serde_json::{json, Value};
use tauri::AppHandle;

#[tauri::command]
fn instances() -> Vec<mc::Instance> {
    mc::list()
}

#[tauri::command]
fn create_instance(name: String, version: String) -> Result<mc::Instance, String> {
    mc::create(name, version)
}

#[tauri::command]
fn delete_instance(id: String) -> Result<(), String> {
    mc::delete(&id)
}

#[tauri::command]
fn set_ram(id: String, ram: u32) -> Result<(), String> {
    mc::set_ram(&id, ram)
}

#[tauri::command]
fn open_dir(id: String) -> Result<(), String> {
    mc::open_dir(&id)
}

#[tauri::command]
async fn mc_versions() -> Result<Vec<String>, String> {
    mc::versions().await
}

#[tauri::command]
async fn play(app: AppHandle, id: String) -> Result<String, String> {
    mc::play(app, id).await
}

#[tauri::command]
fn instance_mods(id: String) -> Result<Vec<Value>, String> {
    mc::instance_mods(&id)
}

#[tauri::command]
async fn mod_search(query: String, version: String) -> Result<Value, String> {
    mc::mod_search(&query, &version).await
}

#[tauri::command]
async fn mod_install(id: String, project: String) -> Result<(), String> {
    let inst = mc::get(&id)?;
    mc::install_project(&net::client(), &inst, &project).await
}

#[tauri::command]
fn mod_remove(id: String, file: String) -> Result<(), String> {
    mc::mod_remove(&id, &file)
}

#[tauri::command]
fn mod_toggle(id: String, file: String) -> Result<(), String> {
    mc::mod_toggle(&id, &file)
}

#[tauri::command]
fn account_get() -> mc::Account {
    mc::account_get()
}

#[tauri::command]
fn account_set(name: String) -> Result<mc::Account, String> {
    mc::account_set(name)
}

#[tauri::command]
async fn app_info() -> Value {
    let tag = mc::latest_tag(&net::client()).await;
    json!({ "root": mc::root().to_string_lossy(), "tag": tag, "repo": mc::REPO })
}

fn main() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            instances, create_instance, delete_instance, set_ram, open_dir, mc_versions, play,
            instance_mods, mod_search, mod_install, mod_remove, mod_toggle, account_get, account_set, app_info
        ])
        .run(tauri::generate_context!())
        .expect("Saturn Launcher konnte nicht starten");
}
