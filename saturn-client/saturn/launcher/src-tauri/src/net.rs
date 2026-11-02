use futures::stream::{self, StreamExt, TryStreamExt};
use serde_json::Value;
use std::{
    collections::HashSet,
    fs,
    path::{Path, PathBuf},
    sync::atomic::{AtomicUsize, Ordering},
};

pub fn e<E: std::fmt::Display>(x: E) -> String {
    x.to_string()
}

pub fn client() -> reqwest::Client {
    reqwest::Client::builder()
        .user_agent("saturn-launcher/2.0")
        .build()
        .expect("http client")
}

pub async fn get_json(c: &reqwest::Client, url: &str) -> Result<Value, String> {
    c.get(url)
        .send().await.map_err(e)?
        .error_for_status().map_err(e)?
        .json::<Value>().await.map_err(e)
}

pub async fn download(c: &reqwest::Client, url: &str, to: &Path) -> Result<(), String> {
    if let Some(p) = to.parent() {
        fs::create_dir_all(p).map_err(e)?;
    }
    let bytes = c.get(url)
        .send().await.map_err(e)?
        .error_for_status().map_err(e)?
        .bytes().await.map_err(e)?;
    let tmp = PathBuf::from(format!("{}.part", to.display()));
    fs::write(&tmp, &bytes).map_err(e)?;
    fs::rename(&tmp, to).map_err(e)
}

/// Lädt viele Dateien parallel; vorhandene werden übersprungen.
pub async fn download_many<P>(c: &reqwest::Client, items: Vec<(String, PathBuf)>, progress: P) -> Result<(), String>
where
    P: Fn(usize, usize) + Send + Sync,
{
    let mut seen = HashSet::new();
    let items: Vec<(String, PathBuf)> = items
        .into_iter()
        .filter(|(_, p)| !p.exists() && seen.insert(p.clone()))
        .collect();
    let total = items.len();
    if total == 0 {
        return Ok(());
    }
    let done = AtomicUsize::new(0);
    let done = &done;
    let progress = &progress;
    stream::iter(items)
        .map(|(u, p)| {
            let c = c.clone();
            async move { download(&c, &u, &p).await }
        })
        .buffer_unordered(24)
        .try_for_each(|_| async move {
            let n = done.fetch_add(1, Ordering::SeqCst) + 1;
            progress(n, total);
            Ok::<(), String>(())
        })
        .await
}

/// Prozent-Kodierung für URL-Parameter.
pub fn enc(s: &str) -> String {
    s.bytes()
        .map(|b| match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => (b as char).to_string(),
            _ => format!("%{:02X}", b),
        })
        .collect()
}

pub fn safe_name(n: &str) -> bool {
    !n.is_empty() && !n.contains('/') && !n.contains('\\') && !n.contains("..")
}
