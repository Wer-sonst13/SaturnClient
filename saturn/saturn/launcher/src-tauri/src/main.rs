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
fn update_instance(id: String, name: String, ram: u32, jvm: String) -> Result<(), String> {
    mc::update_instance(&id, name, ram, jvm)
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
async fn play(app: AppHandle, id: String, server: Option<String>) -> Result<String, String> {
    mc::play(app, id, server).await
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

fn account_json(a: mc::Account) -> Value {
    json!({ "name": a.name, "model": a.model, "skin": a.skin, "uuid": mc::offline_uuid(&a.name) })
}

#[tauri::command]
fn account_get() -> Value {
    account_json(mc::account_get())
}

#[tauri::command]
fn account_set(name: String, model: String) -> Result<Value, String> {
    mc::account_set(name, model).map(account_json)
}

#[tauri::command]
fn account_skin(skin: Option<String>) -> Result<Value, String> {
    mc::account_skin(skin).map(account_json)
}

#[tauri::command]
fn skins_list() -> Vec<Value> {
    mc::skins_list()
}

#[tauri::command]
fn skin_add(name: String, data: String) -> Result<String, String> {
    mc::skin_add(name, data)
}

#[tauri::command]
fn skin_delete(name: String) -> Result<(), String> {
    mc::skin_delete(&name)
}

#[tauri::command]
fn servers_list() -> Vec<Value> {
    mc::servers_list()
}

#[tauri::command]
fn servers_add(name: String, address: String) -> Result<(), String> {
    mc::servers_add(name, address)
}

#[tauri::command]
fn servers_remove(address: String) -> Result<(), String> {
    mc::servers_remove(&address)
}

#[tauri::command]
fn mod_add_file(id: String, name: String, data: String) -> Result<(), String> {
    mc::mod_add_file(&id, &name, &data)
}

#[tauri::command]
fn read_log(id: String) -> Result<String, String> {
    mc::read_log(&id)
}

#[tauri::command]
async fn app_info() -> Value {
    let tag = mc::latest_tag(&net::client()).await;
    json!({ "root": mc::root().to_string_lossy(), "tag": tag, "repo": mc::REPO })
}

fn main() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            instances, create_instance, delete_instance, update_instance, open_dir, mc_versions, play,
            instance_mods, mod_search, mod_install, mod_remove, mod_toggle, mod_add_file, read_log,
            account_get, account_set, account_skin, skins_list, skin_add, skin_delete,
            servers_list, servers_add, servers_remove, app_info
        ])
        .run(tauri::generate_context!())
        .expect("Saturn Launcher konnte nicht starten");
}
