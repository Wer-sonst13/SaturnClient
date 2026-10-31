const { invoke } = window.__TAURI__.core;
const $ = (s) => document.querySelector(s);
let selected = localStorage.getItem('saturn.mc');

function status(text, kind = '') { const e = $('#status'); e.textContent = text; e.className = 'status ' + kind; }
function busy(b) { $('#install').disabled = b || !selected; $('#play').disabled = b; }

function render(versions) {
  const box = $('#versions');
  box.innerHTML = '';
  if (!versions.includes(selected)) selected = versions[0] || null;
  versions.forEach((v) => {
    const b = document.createElement('button');
    b.className = 'chip' + (v === selected ? ' on' : '');
    b.textContent = v;
    b.onclick = () => { selected = v; localStorage.setItem('saturn.mc', v); render(versions); busy(false); };
    box.appendChild(b);
  });
  busy(false);
}

async function init() {
  try {
    const r = await invoke('get_versions');
    $('#foot').textContent = r.tag ? 'Release ' + r.tag : '';
    if (!r.versions.length) return status('Im neuesten Release liegen noch keine Saturn-Dateien.', 'err');
    render(r.versions);
    await autoUpdate(r);
  } catch (e) {
    $('#foot').textContent = 'Offline';
    status('Keine Verbindung zu GitHub: ' + e, 'err');
  }
}

// Beim Start: jede bereits installierte Version automatisch auf das neueste Release bringen.
async function autoUpdate(r) {
  let updated = 0;
  for (const v of r.versions) {
    const t = await invoke('installed_tag', { mc: v });
    if (!t || t === r.tag) continue;
    busy(true);
    status('Update auf ' + r.tag + ': Saturn ' + v + ' …');
    try { await invoke('install', { mc: v }); updated++; }
    catch (e) { status('Update fehlgeschlagen (' + v + '): ' + e, 'err'); busy(false); return; }
  }
  busy(false);
  if (updated) status(updated + ' Version(en) auf ' + r.tag + ' aktualisiert.', 'ok');
}

$('#install').onclick = async () => {
  busy(true); status('Installiere Saturn ' + selected + ' …');
  try { status(await invoke('install', { mc: selected }), 'ok'); }
  catch (e) { status('Fehler: ' + e, 'err'); }
  busy(false);
};

$('#play').onclick = async () => {
  try { await invoke('play'); } catch (e) { status('Minecraft Launcher nicht gefunden: ' + e, 'err'); }
};

init();
