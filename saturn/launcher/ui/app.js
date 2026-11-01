const T = window.__TAURI__;
const invoke = T.core.invoke;
const win = T.window.getCurrentWindow();
const $ = (s, r = document) => r.querySelector(s);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const S = {
  page: 'play', insts: [], sel: localStorage.getItem('sel'), q: '',
  name: 'Player', info: { tag: '', root: '' }, versions: [],
  running: false, prog: null, msg: '', err: false, mods: [], hits: [], searching: false, installing: null,
};
const cur = () => S.insts.find((i) => i.id === S.sel);

function toast(t) {
  const el = $('#toast'); el.textContent = t; el.classList.remove('hidden');
  clearTimeout(toast.t); toast.t = setTimeout(() => el.classList.add('hidden'), 4000);
}

// ---------------------------------------------------------------- Daten
async function loadInstances() {
  S.insts = await invoke('instances');
  if (!S.insts.find((i) => i.id === S.sel)) S.sel = S.insts[0]?.id ?? null;
  if (S.sel) localStorage.setItem('sel', S.sel);
}

// ---------------------------------------------------------------- Zeichnen
function renderTop() {
  document.querySelectorAll('#nav button').forEach((b) => b.classList.toggle('on', b.dataset.page === S.page));
  $('#chip').textContent = S.name;
}

function renderList() {
  const q = S.q.toLowerCase();
  const items = S.insts.filter((i) => i.name.toLowerCase().includes(q));
  $('#count').textContent = S.insts.length;
  $('#list').innerHTML = items.map((i) => `
    <div class="inst ${i.id === S.sel ? 'on' : ''}" data-act="select" data-id="${esc(i.id)}">
      <div class="ico">${esc(i.name.charAt(0).toUpperCase())}</div>
      <div><div class="n">${esc(i.name)}</div><div class="m">Fabric &bull; ${esc(i.mc)}</div></div>
    </div>`).join('') || '<div class="m" style="color:var(--muted);padding:10px">Keine Instanzen</div>';
}

function renderPlay() {
  const i = cur();
  if (!i) {
    return `<div class="empty"><h1>Noch keine Instanz</h1><p>Lege deine erste Instanz an. Saturn und Fabric werden automatisch eingerichtet.</p>
      <div class="actions" style="justify-content:center"><button class="btn primary" data-act="newInst">Instanz erstellen</button></div></div>`;
  }
  const p = S.prog;
  const pct = p && p.total ? Math.round((p.done / p.total) * 100) : 0;
  return `
    <div class="card hero">
      <div class="tag">INSTANZ</div>
      <h1>${esc(i.name)}</h1>
      <div class="sub" style="margin:0">Fabric &bull; Minecraft ${esc(i.mc)} &bull; ${(i.ram / 1024).toFixed(1)} GB RAM</div>
      <div class="actions">
        <button class="btn primary" data-act="play" ${S.running ? 'disabled' : ''}>${S.running ? 'Läuft …' : 'Spielen'}</button>
        <button class="btn" data-act="openDir">Ordner öffnen</button>
        <button class="btn danger" data-act="delInst" ${S.running ? 'disabled' : ''}>Löschen</button>
      </div>
      ${p ? `<div class="bar ${p.total ? '' : 'ind'}"><i id="bar" style="width:${pct}%"></i></div>
             <div class="stage" id="stage">${esc(p.stage)}${p.total ? ` (${p.done}/${p.total})` : ''}</div>` : ''}
      ${S.msg ? `<div class="msg ${S.err ? 'err' : ''}">${esc(S.msg)}</div>` : ''}
    </div>
    <div class="grid3">
      <div class="card"><h2>Arbeitsspeicher</h2>
        <input type="range" id="ram" min="1" max="16" step="0.5" value="${(i.ram / 1024)}">
        <div class="kv"><span>Zugewiesen</span><b id="ramv">${(i.ram / 1024).toFixed(1)} GB</b></div></div>
      <div class="card"><h2>Saturn</h2>
        <div class="kv"><span>Neuestes Release</span><b>${esc(S.info.tag || 'unbekannt')}</b></div>
        <div class="kv"><span>Updates</span><b>automatisch beim Start</b></div></div>
      <div class="card"><h2>Konto</h2>
        <div class="kv"><span>Name</span><b>${esc(S.name)}</b></div>
        <div class="kv"><span>Typ</span><b>Offline</b></div></div>
    </div>`;
}

function modsInstalledHtml() {
  if (!S.mods.length) return '<div class="d" style="color:var(--muted)">Keine Mods installiert.</div>';
  return S.mods.map((m) => `
    <div class="row ${m.enabled ? '' : 'off'}">
      <div class="grow2"><div class="t">${esc(m.file.replace('.disabled', ''))}</div><div class="d">${(m.size / 1024).toFixed(0)} KB${m.enabled ? '' : ' &bull; deaktiviert'}</div></div>
      <button class="btn small" data-act="toggle" data-file="${esc(m.file)}">${m.enabled ? 'Aus' : 'An'}</button>
      <button class="btn small danger" data-act="remove" data-file="${esc(m.file)}">Löschen</button>
    </div>`).join('');
}

function hitsHtml() {
  if (S.searching) return '<div class="d" style="color:var(--muted)">Suche …</div>';
  if (!S.hits.length) return '<div class="d" style="color:var(--muted)">Keine Treffer.</div>';
  return S.hits.map((h) => `
    <div class="row">
      ${h.icon ? `<img src="${esc(h.icon)}" alt="">` : '<div class="noimg"></div>'}
      <div class="grow2"><div class="t">${esc(h.title)}</div><div class="d">${esc(h.description)}</div></div>
      <button class="btn small" data-act="install" data-id="${esc(h.id)}" ${S.installing ? 'disabled' : ''}>${S.installing === h.id ? '…' : 'Installieren'}</button>
    </div>`).join('');
}

function renderMods() {
  const i = cur();
  if (!i) return '<div class="empty"><h1>Keine Instanz</h1><p>Lege zuerst eine Instanz an.</p></div>';
  return `<h1>Mods</h1><div class="sub">Instanz: ${esc(i.name)} &bull; ${esc(i.mc)}</div>
    <div class="cols">
      <div class="card"><h2>Installiert</h2><div id="installed">${modsInstalledHtml()}</div></div>
      <div class="card"><h2>Modrinth</h2>
        <input id="msearch" class="input" placeholder="Mods suchen …" autocomplete="off" style="margin-bottom:8px">
        <div id="hits">${hitsHtml()}</div></div>
    </div>`;
}

function renderAccounts() {
  return `<h1>Konto</h1><div class="sub">Der Microsoft-Login wird später ergänzt. Bis dahin spielst du mit einem Offline-Namen.</div>
    <div class="card" style="max-width:480px"><h2>Offline-Konto</h2>
      <input id="accname" class="input" value="${esc(S.name)}" maxlength="16">
      <div class="actions" style="margin-top:14px"><button class="btn primary" style="padding:9px 22px;font-size:14px" data-act="saveName">Speichern</button>
      <button class="btn" disabled>Mit Microsoft anmelden (folgt)</button></div></div>`;
}

const COLORS = ['#e3b04b', '#5b9cf5', '#5fbf8a', '#e0675f', '#b68af0', '#d9d9d9'];
function renderSettings() {
  const acc = getComputedStyle(document.documentElement).getPropertyValue('--accent').trim().toLowerCase();
  return `<h1>Einstellungen</h1><div class="sub">Aussehen und Speicherort.</div>
    <div class="card" style="max-width:560px"><h2>Akzentfarbe</h2>
      <div class="sw">${COLORS.map((c) => `<button data-act="accent" data-c="${c}" style="background:${c}" class="${c === acc ? 'on' : ''}"></button>`).join('')}</div></div>
    <div class="card" style="max-width:560px;margin-top:14px"><h2>Daten</h2>
      <div class="kv"><span>Ordner</span><b style="user-select:text">${esc(S.info.root)}</b></div>
      <div class="kv"><span>GitHub</span><b>${esc(S.info.repo || '')}</b></div></div>`;
}

function renderPage() {
  const el = $('#page');
  el.innerHTML = { play: renderPlay, mods: renderMods, accounts: renderAccounts, settings: renderSettings }[S.page]();
  if (S.page === 'play') {
    const r = $('#ram');
    if (r) {
      r.oninput = () => { $('#ramv').textContent = Number(r.value).toFixed(1) + ' GB'; };
      r.onchange = async () => { await invoke('set_ram', { id: S.sel, ram: Math.round(r.value * 1024) }); await loadInstances(); };
    }
  }
  if (S.page === 'mods' && cur()) {
    $('#msearch').oninput = (ev) => { clearTimeout(renderPage.t); renderPage.t = setTimeout(() => searchMods(ev.target.value), 350); };
  }
}

function render() { renderTop(); renderList(); renderPage(); }

// ---------------------------------------------------------------- Mods
async function loadMods() {
  if (!cur()) return;
  try { S.mods = await invoke('instance_mods', { id: S.sel }); } catch { S.mods = []; }
  const el = $('#installed'); if (el) el.innerHTML = modsInstalledHtml();
}
async function searchMods(q = '') {
  const i = cur(); if (!i) return;
  S.searching = true; const h = $('#hits'); if (h) h.innerHTML = hitsHtml();
  try { S.hits = await invoke('mod_search', { query: q, version: i.mc }); } catch (e) { S.hits = []; toast('Suche fehlgeschlagen: ' + e); }
  S.searching = false; const h2 = $('#hits'); if (h2) h2.innerHTML = hitsHtml();
}
async function enterMods() { S.page = 'mods'; render(); await loadMods(); await searchMods(''); }

// ---------------------------------------------------------------- Dialog
async function openModal() {
  const m = $('#modal'); m.classList.remove('hidden');
  m.innerHTML = '<div class="dlg"><h1>Neue Instanz</h1><div class="d" style="color:var(--muted)">Lade Versionen …</div></div>';
  try { if (!S.versions.length) S.versions = await invoke('mc_versions'); }
  catch (e) { m.innerHTML = `<div class="dlg"><h1>Keine Verbindung</h1><p class="msg err">${esc(e)}</p><div class="actions"><button class="btn" data-act="closeModal">Schließen</button></div></div>`; return; }
  m.innerHTML = `<div class="dlg"><h1>Neue Instanz</h1>
    <label>Name</label><input id="nname" class="input" maxlength="40" placeholder="z. B. PvP" autocomplete="off">
    <label>Minecraft-Version</label><select id="nver" class="input">${S.versions.map((v) => `<option>${esc(v)}</option>`).join('')}</select>
    <div class="actions" style="justify-content:flex-end"><button class="btn" data-act="closeModal">Abbrechen</button>
    <button class="btn primary" style="padding:9px 22px;font-size:14px" data-act="createInst">Erstellen</button></div></div>`;
  $('#nname').focus();
}
const closeModal = () => $('#modal').classList.add('hidden');

// ---------------------------------------------------------------- Aktionen
const A = {
  async nav(d) { S.page = d.page; if (d.page === 'mods') return enterMods(); render(); if (d.page === 'settings') { S.info = { ...S.info, ...(await invoke('app_info')) }; renderPage(); } },
  select(d) { S.sel = d.id; localStorage.setItem('sel', d.id); S.msg = ''; render(); if (S.page === 'mods') enterMods(); },
  newInst: openModal,
  closeModal,
  async createInst() {
    const name = $('#nname').value, version = $('#nver').value;
    try { const i = await invoke('create_instance', { name, version }); await loadInstances(); S.sel = i.id; localStorage.setItem('sel', i.id); closeModal(); S.page = 'play'; render(); }
    catch (e) { toast(String(e)); }
  },
  async delInst() {
    const i = cur(); if (!i || !confirm(`Instanz „${i.name}“ samt Welten und Mods löschen?`)) return;
    try { await invoke('delete_instance', { id: i.id }); await loadInstances(); S.msg = ''; render(); } catch (e) { toast(String(e)); }
  },
  async openDir() { try { await invoke('open_dir', { id: S.sel }); } catch (e) { toast(String(e)); } },
  async play() {
    if (S.running || !cur()) return;
    S.running = true; S.err = false; S.msg = ''; S.prog = { stage: 'Starte …', done: 0, total: 0 }; renderPage();
    try { S.msg = await invoke('play', { id: S.sel }); } catch (e) { S.msg = 'Fehler: ' + e; S.err = true; S.running = false; }
    S.prog = null; renderPage();
  },
  async install(d) {
    S.installing = d.id; $('#hits').innerHTML = hitsHtml();
    try { await invoke('mod_install', { id: S.sel, project: d.id }); toast('Mod installiert'); await loadMods(); }
    catch (e) { toast('Installation fehlgeschlagen: ' + e); }
    S.installing = null; $('#hits').innerHTML = hitsHtml();
  },
  async remove(d) { try { await invoke('mod_remove', { id: S.sel, file: d.file }); await loadMods(); } catch (e) { toast(String(e)); } },
  async toggle(d) { try { await invoke('mod_toggle', { id: S.sel, file: d.file }); await loadMods(); } catch (e) { toast(String(e)); } },
  async saveName() {
    try { const a = await invoke('account_set', { name: $('#accname').value }); S.name = a.name; renderTop(); toast('Gespeichert'); } catch (e) { toast(String(e)); }
  },
  accent(d) { document.documentElement.style.setProperty('--accent', d.c); localStorage.setItem('accent', d.c); renderPage(); },
};

document.addEventListener('click', (ev) => {
  const el = ev.target.closest('[data-act]');
  if (el && A[el.dataset.act]) A[el.dataset.act](el.dataset, el);
});
$('#q').oninput = (ev) => { S.q = ev.target.value; renderList(); };
$('#wmin').onclick = () => win.minimize();
$('#wmax').onclick = () => win.toggleMaximize();
$('#wclose').onclick = () => win.close();

// ---------------------------------------------------------------- Ereignisse
T.event.listen('progress', (ev) => {
  S.prog = ev.payload;
  const st = $('#stage'), bar = $('#bar');
  if (st) st.textContent = S.prog.stage + (S.prog.total ? ` (${S.prog.done}/${S.prog.total})` : '');
  if (bar) bar.style.width = (S.prog.total ? Math.round((S.prog.done / S.prog.total) * 100) : 0) + '%';
  if (S.page === 'play' && !!S.prog.total !== !!$('.bar:not(.ind)')) renderPage();
});
T.event.listen('game-exit', (ev) => {
  S.running = false;
  if (ev.payload !== 0) { S.err = true; S.msg = `Minecraft wurde mit Code ${ev.payload} beendet. Details: launch.log im Instanz-Ordner.`; }
  else { S.err = false; S.msg = 'Minecraft wurde beendet.'; }
  if (S.page === 'play') renderPage();
});

// ---------------------------------------------------------------- Start
(async function init() {
  const c = localStorage.getItem('accent'); if (c) document.documentElement.style.setProperty('--accent', c);
  try { S.name = (await invoke('account_get')).name; } catch {}
  await loadInstances();
  render();
  try { S.info = { ...S.info, ...(await invoke('app_info')) }; if (S.page === 'play') renderPage(); } catch {}
})();
