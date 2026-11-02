const T = window.__TAURI__;
const invoke = T.core.invoke;
const win = T.window.getCurrentWindow();
const $ = (s, r = document) => r.querySelector(s);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const S = {
  page: 'home', insts: [], sel: localStorage.getItem('sel'), q: '',
  acc: { name: 'Player', model: 'classic', skin: null, uuid: '' }, skins: [], prev: null, back: false, skinMsg: '', skinErr: false,
  info: { tag: '', root: '', repo: '' }, versions: [],
  running: false, prog: null, msg: '', err: false,
  mods: [], hits: [], searching: false, installing: null, modTab: 'installed', servers: [],
};
const cur = () => S.insts.find((i) => i.id === S.sel);

function toast(t) {
  const el = $('#toast'); el.textContent = t; el.classList.remove('hidden');
  clearTimeout(toast.t); toast.t = setTimeout(() => el.classList.add('hidden'), 4200);
}

// ---------------------------------------------------------------- Skin-Zeichnen (2D, vorne/hinten)
function makeSteve() {
  const c = document.createElement('canvas'); c.width = 64; c.height = 64;
  const x = c.getContext('2d');
  const r = (col, a, b, w, h) => { x.fillStyle = col; x.fillRect(a, b, w, h); };
  r('#b98a6a', 0, 8, 32, 8); r('#b98a6a', 8, 0, 16, 8);          // Kopf
  r('#4b3321', 8, 0, 16, 8); r('#4b3321', 8, 8, 8, 2); r('#4b3321', 0, 8, 8, 4); r('#4b3321', 16, 8, 16, 4); r('#4b3321', 24, 8, 8, 8);
  r('#ffffff', 9, 12, 2, 1); r('#ffffff', 13, 12, 2, 1); r('#5a4cc2', 10, 12, 1, 1); r('#5a4cc2', 13, 12, 1, 1);
  r('#2fa7a3', 16, 20, 24, 12);                                    // Körper
  r('#b98a6a', 40, 16, 16, 16); r('#2fa7a3', 40, 16, 16, 4);       // rechter Arm
  r('#b98a6a', 32, 48, 16, 16); r('#2fa7a3', 32, 48, 16, 4);       // linker Arm
  r('#3b3f8f', 0, 16, 16, 16); r('#3b3f8f', 16, 48, 16, 16);       // Beine
  return c;
}
const STEVE = makeSteve();

function drawFigure(cv, img, slim, back, k) {
  const c = cv.getContext('2d');
  c.imageSmoothingEnabled = false;
  c.clearRect(0, 0, cv.width, cv.height);
  const legacy = img.height === 32, aw = slim ? 3 : 4;
  const put = (sx, sy, w, h, dx, dy, flip) => {
    if (flip) { c.save(); c.translate((dx + w) * k, dy * k); c.scale(-1, 1); c.drawImage(img, sx, sy, w, h, 0, 0, w * k, h * k); c.restore(); }
    else c.drawImage(img, sx, sy, w, h, dx * k, dy * k, w * k, h * k);
  };
  const arm = (side, ov) => {
    const x0 = side === 'r' ? 40 : (ov ? 48 : 32), y0 = side === 'r' ? (ov ? 32 : 16) : 48;
    return [x0 + 4 + (back ? 4 + aw : 0), y0 + 4];
  };
  const leg = (side, ov) => {
    const x0 = side === 'r' ? 0 : (ov ? 0 : 16), y0 = side === 'r' ? (ov ? 32 : 16) : (ov ? 48 : 48);
    const yy = side === 'r' ? (ov ? 32 : 16) : 48;
    return [x0 + 4 + (back ? 8 : 0), yy + 4];
  };
  const limb = (kind, side, ov, dx, dy, w) => {
    if (legacy && side === 'l') { if (ov) return; side = 'r'; var flip = true; }
    const [sx, sy] = kind === 'arm' ? arm(side, ov) : leg(side, ov);
    put(sx, sy, w, 12, dx, dy, flip);
  };
  // Basis
  put(back ? 24 : 8, 8, 8, 8, 4, 0);
  put(back ? 32 : 20, 20, 8, 12, 4, 8);
  const vl = back ? 'l' : 'r', vr = back ? 'r' : 'l';
  limb('arm', vl, false, 4 - aw, 8, aw); limb('arm', vr, false, 12, 8, aw);
  limb('leg', vl, false, 4, 20, 4); limb('leg', vr, false, 8, 20, 4);
  // Overlay
  put(back ? 56 : 40, 8, 8, 8, 4, 0);
  if (!legacy) {
    put(back ? 32 : 20, 36, 8, 12, 4, 8);
    limb('arm', vl, true, 4 - aw, 8, aw); limb('arm', vr, true, 12, 8, aw);
    limb('leg', vl, true, 4, 20, 4); limb('leg', vr, true, 8, 20, 4);
  }
}
function drawHead(cv, img) {
  const c = cv.getContext('2d'); c.imageSmoothingEnabled = false; c.clearRect(0, 0, cv.width, cv.height);
  c.drawImage(img, 8, 8, 8, 8, 0, 0, cv.width, cv.height); c.drawImage(img, 40, 8, 8, 8, 0, 0, cv.width, cv.height);
}
const imgCache = {};
function getImg(name) {
  if (!name) return Promise.resolve(STEVE);
  const sk = S.skins.find((s) => s.name === name);
  if (!sk) return Promise.resolve(STEVE);
  if (imgCache[sk.data]) return imgCache[sk.data];
  return (imgCache[sk.data] = new Promise((res) => { const i = new Image(); i.onload = () => res(i); i.onerror = () => res(STEVE); i.src = sk.data; }));
}
async function paintSkins() {
  const cv = $('#fig');
  if (cv) drawFigure(cv, await getImg(S.prev), S.acc.model === 'slim', S.back, 14);
  for (const t of document.querySelectorAll('canvas[data-skin]')) drawFigure(t, await getImg(t.dataset.skin), S.acc.model === 'slim', false, 4);
}
async function paintAvatar() { drawHead($('#avatar'), await getImg(S.acc.skin)); }

// ---------------------------------------------------------------- Daten
async function loadInstances() {
  S.insts = await invoke('instances');
  if (!S.insts.find((i) => i.id === S.sel)) S.sel = S.insts[0]?.id ?? null;
  if (S.sel) localStorage.setItem('sel', S.sel);
}
async function loadAccount() {
  S.acc = await invoke('account_get');
  S.skins = await invoke('skins_list');
  if (S.prev && !S.skins.find((s) => s.name === S.prev)) S.prev = null;
}

// ---------------------------------------------------------------- Zeichnen
function renderTop() {
  document.querySelectorAll('#nav button').forEach((b) => b.classList.toggle('on', b.dataset.page === S.page));
  $('#chipname').textContent = S.acc.name;
  paintAvatar();
}

function renderList() {
  const q = S.q.toLowerCase();
  const items = S.insts.filter((i) => i.name.toLowerCase().includes(q));
  $('#count').textContent = S.insts.length;
  $('#list').innerHTML = items.map((i) => `
    <div class="inst ${i.id === S.sel ? 'on' : ''}" data-act="select" data-id="${esc(i.id)}">
      <div class="ico">${esc(i.name.charAt(0).toUpperCase())}</div>
      <div><div class="n">${esc(i.name)}</div><div class="m">Fabric &bull; ${esc(i.mc)}</div></div>
    </div>`).join('') || '<div style="color:var(--muted);padding:10px">Keine Instanzen</div>';
}

function renderBar() {
  const i = cur();
  $('#barinst').innerHTML = i
    ? `<div class="ico">${esc(i.name.charAt(0).toUpperCase())}</div><div><div class="n">${esc(i.name)}</div><div class="m">Fabric &bull; ${esc(i.mc)} &bull; ${(i.ram / 1024).toFixed(1)} GB</div></div>`
    : '<div class="m" style="color:var(--muted)">Keine Instanz gewählt</div>';
  const p = S.prog;
  $('#barmid').innerHTML = p
    ? `<div class="bar ${p.total ? '' : 'ind'}"><i id="barfill" style="width:${p.total ? Math.round((p.done / p.total) * 100) : 0}%"></i></div><div class="stage" id="stage">${esc(p.stage)}${p.total ? ` (${p.done}/${p.total})` : ''}</div>`
    : (S.msg ? `<div class="msg ${S.err ? 'err' : ''}">${esc(S.msg)}</div>` : '');
  const b = $('#playbtn');
  b.disabled = S.running || !i;
  b.textContent = S.running ? 'Läuft …' : 'Spielen';
}

function renderHome() {
  const a = S.acc, pv = S.prev;
  const skinThumbs = S.skins.map((s) => `
    <div class="skin ${s.name === pv ? 'on' : ''}" data-act="pickSkin" data-name="${esc(s.name)}" title="${esc(s.name)}">
      <canvas width="64" height="128" data-skin="${esc(s.name)}"></canvas>
      ${s.name === a.skin ? '<span class="tag">Offline</span>' : ''}
      <span class="del" data-act="delSkin" data-name="${esc(s.name)}">&times;</span>
    </div>`).join('') || '<div class="note">Lade eine PNG hoch, um sie hier zu speichern.</div>';
  return `
  <div class="pagehead"><h1>Garderobe</h1><span class="hint">SKIN</span></div>
  <div class="home">
    <section class="card figure">
      <div class="flip"><button class="${S.back ? '' : 'on'}" data-act="side" data-back="0">Vorne</button><button class="${S.back ? 'on' : ''}" data-act="side" data-back="1">Hinten</button></div>
      <canvas id="fig" width="224" height="448"></canvas>
    </section>
    <div>
      <section class="card">
        <div class="who">${esc(a.name)}</div>
        <div class="uuid mono">UUID ${esc(a.uuid)}</div>
        <div class="field"><label>MODELL</label>
          <select id="model" class="input"><option value="classic" ${a.model === 'classic' ? 'selected' : ''}>Classic (4-Pixel-Arme)</option><option value="slim" ${a.model === 'slim' ? 'selected' : ''}>Slim (3-Pixel-Arme)</option></select></div>
      </section>
      <section class="card">
        <div class="label">Skin ändern</div>
        <div class="actions"><button class="btn white" data-act="uploadSkin">PNG hochladen</button><button class="btn" data-act="resetSkin">Auf Steve zurücksetzen</button></div>
        <div class="status mono ${S.skinErr ? 'err' : ''}">${esc(S.skinMsg || 'Bereit. PNG (64x64 oder 64x32).')}</div>
        <div class="label">Gespeicherte Skins</div>
        <div class="skins">${skinThumbs}</div>
        <div class="label">Offline-Skin</div>
        <div class="offline"><span class="st">${a.skin ? esc(a.skin) : 'Keiner gesetzt'}</span>
          <span class="actions"><button class="btn" data-act="setOffline" ${pv ? '' : 'disabled'}>Offline-Skin setzen</button><button class="btn" data-act="clearOffline" ${a.skin ? '' : 'disabled'}>Entfernen</button></span></div>
        <div class="note">Der Offline-Skin wird beim Spielstart über CustomSkinLoader in die Instanz eingebunden (experimentell). Skin beim echten Konto ändern geht erst mit Microsoft-Login.</div>
      </section>
    </div>
  </div>`;
}

function modsInstalledHtml() {
  if (!S.mods.length) return '<div class="note">Keine Mods installiert.</div>';
  return S.mods.map((m) => `
    <div class="row ${m.enabled ? '' : 'off'}">
      <div class="grow2"><div class="t">${esc(m.file.replace('.disabled', ''))}</div><div class="d">${(m.size / 1024).toFixed(0)} KB${m.enabled ? '' : ' &bull; deaktiviert'}</div></div>
      <button class="btn small" data-act="toggle" data-file="${esc(m.file)}">${m.enabled ? 'Aus' : 'An'}</button>
      <button class="btn small danger" data-act="remove" data-file="${esc(m.file)}">Löschen</button>
    </div>`).join('');
}
function hitsHtml() {
  if (S.searching) return '<div class="note">Suche …</div>';
  if (!S.hits.length) return '<div class="note">Keine Treffer.</div>';
  return S.hits.map((h) => `
    <div class="row">
      ${h.icon ? `<img src="${esc(h.icon)}" alt="">` : '<div class="noimg"></div>'}
      <div class="grow2"><div class="t">${esc(h.title)}</div><div class="d">${esc(h.description)}</div></div>
      <button class="btn small white" data-act="install" data-id="${esc(h.id)}" ${S.installing ? 'disabled' : ''}>${S.installing === h.id ? '…' : 'Installieren'}</button>
    </div>`).join('');
}
function renderMods() {
  const i = cur();
  if (!i) return '<div class="empty"><h1>Keine Instanz</h1><p>Lege zuerst eine Instanz an.</p></div>';
  return `<div class="pagehead"><h1>Mods</h1><span class="hint">${esc(i.name.toUpperCase())} &bull; ${esc(i.mc)}</span></div>
    <div class="tabs"><button class="${S.modTab === 'installed' ? 'on' : ''}" data-act="modTab" data-tab="installed">Installiert (${S.mods.length})</button>
      <button class="${S.modTab === 'modrinth' ? 'on' : ''}" data-act="modTab" data-tab="modrinth">Modrinth</button></div>
    ${S.modTab === 'installed'
      ? `<section class="card"><div class="actions" style="margin-bottom:10px"><button class="btn white" data-act="addJar">Mod-Datei hinzufügen (.jar)</button><button class="btn" data-act="openDir">Ordner öffnen</button></div><div id="installed">${modsInstalledHtml()}</div></section>`
      : `<section class="card"><input id="msearch" class="input" placeholder="Mods auf Modrinth suchen …" autocomplete="off" style="margin-bottom:6px"><div id="hits">${hitsHtml()}</div></section>`}`;
}

function renderServers() {
  const rows = S.servers.map((s) => `
    <div class="row"><div class="ico">${esc(s.name.charAt(0).toUpperCase())}</div>
      <div class="grow2"><div class="t">${esc(s.name)}</div><div class="d mono">${esc(s.address)}</div></div>
      <button class="btn small white" data-act="joinServer" data-address="${esc(s.address)}" ${S.running || !cur() ? 'disabled' : ''}>Beitreten</button>
      <button class="btn small danger" data-act="delServer" data-address="${esc(s.address)}">Entfernen</button></div>`).join('') || '<div class="note">Noch keine Favoriten.</div>';
  return `<div class="pagehead"><h1>Server</h1><span class="hint">FAVORITEN</span></div>
    <section class="card"><div class="formrow"><input id="sname" class="input" placeholder="Name" maxlength="30" style="max-width:220px"><input id="saddr" class="input" placeholder="Adresse, z. B. play.beispiel.net" maxlength="80"><button class="btn white" data-act="addServer">Hinzufügen</button></div>${rows}
    <div class="note">„Beitreten“ startet die gewählte Instanz und verbindet direkt mit dem Server.</div></section>`;
}

function renderAccount() {
  return `<div class="pagehead"><h1>Konto</h1><span class="hint">ANMELDUNG</span></div>
    <section class="card" style="max-width:560px"><div class="label">Offline-Konto</div>
      <input id="accname" class="input" value="${esc(S.acc.name)}" maxlength="16">
      <div class="actions" style="margin-top:14px"><button class="btn white" data-act="saveName">Speichern</button>
      <button class="btn" disabled>Mit Microsoft anmelden (folgt)</button></div>
      <div class="note">Mit einem Offline-Konto spielst du Einzelspieler und auf Offline-Servern. Der Microsoft-Login wird später ergänzt.</div></section>`;
}

const COLORS = ['#e3b04b', '#5b9cf5', '#5fbf8a', '#e0675f', '#b68af0', '#d9d9d9'];
function renderThemes() {
  const acc = getComputedStyle(document.documentElement).getPropertyValue('--accent').trim().toLowerCase();
  const blur = Number(localStorage.getItem('blur') ?? 6), dim = Number(localStorage.getItem('dim') ?? 55);
  return `<div class="pagehead"><h1>Themes</h1><span class="hint">AUSSEHEN</span></div>
    <section class="card" style="max-width:620px"><div class="label">Akzentfarbe</div>
      <div class="sw">${COLORS.map((c) => `<button data-act="accent" data-c="${c}" style="background:${c}" class="${c === acc ? 'on' : ''}"></button>`).join('')}</div></section>
    <section class="card" style="max-width:620px"><div class="label">Hintergrundbild</div>
      <div class="actions"><button class="btn white" data-act="pickBg">Bild wählen</button><button class="btn" data-act="clearBg">Entfernen</button></div>
      <div class="kv" style="margin-top:14px"><span>Unschärfe</span><b id="blurv">${blur}px</b></div><input type="range" id="blur" min="0" max="24" value="${blur}">
      <div class="kv"><span>Helligkeit</span><b id="dimv">${dim}%</b></div><input type="range" id="dim" min="15" max="100" value="${dim}"></section>`;
}

function renderSettings() {
  return `<div class="pagehead"><h1>Einstellungen</h1><span class="hint">ALLGEMEIN</span></div>
    <section class="card" style="max-width:620px"><div class="label">Daten</div>
      <div class="kv"><span>Speicherort</span><b>${esc(S.info.root)}</b></div>
      <div class="kv"><span>GitHub</span><b>${esc(S.info.repo || '')}</b></div>
      <div class="kv"><span>Neuestes Saturn-Release</span><b>${esc(S.info.tag || 'unbekannt')}</b></div>
      <div class="note">Saturn und Fabric API werden bei jedem Spielstart automatisch aktualisiert. Instanzen lassen sich über die untere Leiste bearbeiten.</div></section>`;
}

function renderPage() {
  const el = $('#page');
  el.innerHTML = { home: renderHome, mods: renderMods, servers: renderServers, account: renderAccount, themes: renderThemes, settings: renderSettings }[S.page]();
  if (S.page === 'home') {
    $('#model').onchange = async (e) => { try { S.acc = await invoke('account_set', { name: S.acc.name, model: e.target.value }); paintSkins(); } catch (x) { toast(String(x)); } };
    paintSkins();
  }
  if (S.page === 'mods' && S.modTab === 'modrinth' && cur()) {
    $('#msearch').oninput = (ev) => { clearTimeout(renderPage.t); renderPage.t = setTimeout(() => searchMods(ev.target.value), 350); };
  }
  if (S.page === 'themes') {
    $('#blur').oninput = (e) => { localStorage.setItem('blur', e.target.value); $('#blurv').textContent = e.target.value + 'px'; applyBg(); };
    $('#dim').oninput = (e) => { localStorage.setItem('dim', e.target.value); $('#dimv').textContent = e.target.value + '%'; applyBg(); };
  }
}
function render() { renderTop(); renderList(); renderBar(); renderPage(); }

// ---------------------------------------------------------------- Hintergrund
function applyBg() {
  const r = document.documentElement.style, img = localStorage.getItem('bg');
  $('#bg').style.backgroundImage = img ? `url(${img})` : 'none';
  r.setProperty('--blur', (localStorage.getItem('blur') ?? 6) + 'px');
  r.setProperty('--dim', (Number(localStorage.getItem('dim') ?? 55) / 100));
}
function pickFile(accept) {
  return new Promise((res) => { const i = document.createElement('input'); i.type = 'file'; i.accept = accept; i.onchange = () => res(i.files[0] || null); i.click(); });
}
const b64 = (file) => new Promise((res, rej) => { const r = new FileReader(); r.onload = () => res(String(r.result).split(',')[1]); r.onerror = rej; r.readAsDataURL(file); });
function shrinkImage(file) {
  return new Promise((res, rej) => {
    const url = URL.createObjectURL(file), im = new Image();
    im.onload = () => { const s = Math.min(1, 1600 / im.width), c = document.createElement('canvas'); c.width = im.width * s; c.height = im.height * s; c.getContext('2d').drawImage(im, 0, 0, c.width, c.height); URL.revokeObjectURL(url); res(c.toDataURL('image/jpeg', 0.82)); };
    im.onerror = rej; im.src = url;
  });
}

// ---------------------------------------------------------------- Mods
async function loadMods() {
  if (!cur()) return;
  try { S.mods = await invoke('instance_mods', { id: S.sel }); } catch { S.mods = []; }
}
async function searchMods(q = '') {
  const i = cur(); if (!i) return;
  S.searching = true; const h = $('#hits'); if (h) h.innerHTML = hitsHtml();
  try { S.hits = await invoke('mod_search', { query: q, version: i.mc }); } catch (e) { S.hits = []; toast('Suche fehlgeschlagen: ' + e); }
  S.searching = false; const h2 = $('#hits'); if (h2) h2.innerHTML = hitsHtml();
}
async function enterMods() { S.page = 'mods'; await loadMods(); render(); if (S.modTab === 'modrinth') searchMods(''); }

// ---------------------------------------------------------------- Dialoge
const closeModal = () => $('#modal').classList.add('hidden');
function showModal(html) { const m = $('#modal'); m.innerHTML = html; m.classList.remove('hidden'); }

async function openCreate() {
  showModal('<div class="dlg"><h1>Neue Instanz</h1><div class="note">Lade Versionen …</div></div>');
  try { if (!S.versions.length) S.versions = await invoke('mc_versions'); }
  catch (e) { showModal(`<div class="dlg"><h1>Keine Verbindung</h1><div class="msg err">${esc(e)}</div><div class="actions"><button class="btn" data-act="closeModal">Schließen</button></div></div>`); return; }
  showModal(`<div class="dlg"><h1>Neue Instanz</h1>
    <label>Name</label><input id="nname" class="input" maxlength="40" placeholder="z. B. PvP" autocomplete="off">
    <label>Minecraft-Version</label><select id="nver" class="input">${S.versions.map((v) => `<option>${esc(v)}</option>`).join('')}</select>
    <div class="actions"><button class="btn" data-act="closeModal">Abbrechen</button><button class="btn white" data-act="createInst">Erstellen</button></div></div>`);
  $('#nname').focus();
}
function openEdit() {
  const i = cur(); if (!i) return;
  showModal(`<div class="dlg"><h1>Instanz bearbeiten</h1>
    <label>Name</label><input id="ename" class="input" maxlength="40" value="${esc(i.name)}">
    <label>Arbeitsspeicher: <span id="eramv">${(i.ram / 1024).toFixed(1)} GB</span></label><input type="range" id="eram" min="1" max="16" step="0.5" value="${i.ram / 1024}">
    <label>Zusätzliche JVM-Argumente (optional)</label><input id="ejvm" class="input mono" value="${esc(i.jvm || '')}" placeholder="-XX:+UseZGC">
    <div class="actions" style="justify-content:space-between"><button class="btn danger" data-act="delInst">Löschen</button>
      <span class="actions"><button class="btn" data-act="openDir">Ordner</button><button class="btn" data-act="closeModal">Abbrechen</button><button class="btn white" data-act="saveInst">Speichern</button></span></div></div>`);
  $('#eram').oninput = (e) => { $('#eramv').textContent = Number(e.target.value).toFixed(1) + ' GB'; };
}

// ---------------------------------------------------------------- Aktionen
async function startGame(server = null) {
  if (S.running || !cur()) return;
  S.running = true; S.err = false; S.msg = ''; S.prog = { stage: 'Starte …', done: 0, total: 0 }; renderBar();
  try { S.msg = await invoke('play', { id: S.sel, server }); } catch (e) { S.msg = 'Fehler: ' + e; S.err = true; S.running = false; }
  S.prog = null; renderBar();
}

const A = {
  async nav(d) { S.page = d.page; if (d.page === 'mods') return enterMods(); if (d.page === 'servers') S.servers = await invoke('servers_list'); if (d.page === 'settings') S.info = { ...S.info, ...(await invoke('app_info')) }; render(); },
  async select(d) { S.sel = d.id; localStorage.setItem('sel', d.id); S.msg = ''; if (S.page === 'mods') return enterMods(); render(); },
  newInst: openCreate, editInst: openEdit, closeModal,
  async createInst() {
    try { const i = await invoke('create_instance', { name: $('#nname').value, version: $('#nver').value }); await loadInstances(); S.sel = i.id; localStorage.setItem('sel', i.id); closeModal(); render(); }
    catch (e) { toast(String(e)); }
  },
  async saveInst() {
    try { await invoke('update_instance', { id: S.sel, name: $('#ename').value, ram: Math.round($('#eram').value * 1024), jvm: $('#ejvm').value }); await loadInstances(); closeModal(); render(); }
    catch (e) { toast(String(e)); }
  },
  async delInst() {
    const i = cur(); if (!i || !confirm(`Instanz „${i.name}“ samt Welten und Mods löschen?`)) return;
    try { await invoke('delete_instance', { id: i.id }); await loadInstances(); closeModal(); S.msg = ''; render(); } catch (e) { toast(String(e)); }
  },
  async openDir() { try { await invoke('open_dir', { id: S.sel }); } catch (e) { toast(String(e)); } },
  play: () => startGame(null),
  joinServer: (d) => startGame(d.address),
  async showLog() {
    if (!cur()) return toast('Keine Instanz gewählt');
    try { const t = await invoke('read_log', { id: S.sel }); showModal(`<div class="dlg wide"><h1>Spiel-Log</h1><pre class="mono">${esc(t)}</pre><div class="actions"><button class="btn" data-act="closeModal">Schließen</button></div></div>`); }
    catch (e) { toast(String(e)); }
  },
  // Skins
  side(d) { S.back = d.back === '1'; renderPage(); },
  pickSkin(d) { S.prev = d.name; renderPage(); },
  async uploadSkin() {
    const f = await pickFile('image/png'); if (!f) return;
    try { const n = await invoke('skin_add', { name: f.name, data: await b64(f) }); await loadAccount(); S.prev = n; S.skinMsg = `Gespeichert als „${n}“.`; S.skinErr = false; }
    catch (e) { S.skinMsg = String(e); S.skinErr = true; }
    renderPage();
  },
  async delSkin(d, el) {
    event.stopPropagation();
    try { await invoke('skin_delete', { name: d.name }); if (S.prev === d.name) S.prev = null; await loadAccount(); render(); } catch (e) { toast(String(e)); }
  },
  resetSkin() { S.prev = null; S.skinMsg = 'Vorschau: Steve.'; S.skinErr = false; renderPage(); },
  async setOffline() { try { S.acc = await invoke('account_skin', { skin: S.prev }); S.skinMsg = 'Offline-Skin gesetzt. Gilt ab dem nächsten Spielstart.'; S.skinErr = false; render(); } catch (e) { S.skinMsg = String(e); S.skinErr = true; renderPage(); } },
  async clearOffline() { try { S.acc = await invoke('account_skin', { skin: null }); S.skinMsg = 'Offline-Skin entfernt.'; S.skinErr = false; render(); } catch (e) { toast(String(e)); } },
  // Mods
  async modTab(d) { S.modTab = d.tab; renderPage(); if (d.tab === 'modrinth') searchMods(''); },
  async install(d) {
    S.installing = d.id; $('#hits').innerHTML = hitsHtml();
    try { await invoke('mod_install', { id: S.sel, project: d.id }); toast('Mod installiert'); await loadMods(); }
    catch (e) { toast('Installation fehlgeschlagen: ' + e); }
    S.installing = null; $('#hits').innerHTML = hitsHtml();
  },
  async addJar() {
    const f = await pickFile('.jar'); if (!f) return;
    try { await invoke('mod_add_file', { id: S.sel, name: f.name, data: await b64(f) }); toast('Mod hinzugefügt'); await loadMods(); renderPage(); } catch (e) { toast(String(e)); }
  },
  async remove(d) { try { await invoke('mod_remove', { id: S.sel, file: d.file }); await loadMods(); renderPage(); } catch (e) { toast(String(e)); } },
  async toggle(d) { try { await invoke('mod_toggle', { id: S.sel, file: d.file }); await loadMods(); renderPage(); } catch (e) { toast(String(e)); } },
  // Server
  async addServer() { try { await invoke('servers_add', { name: $('#sname').value, address: $('#saddr').value }); S.servers = await invoke('servers_list'); renderPage(); } catch (e) { toast(String(e)); } },
  async delServer(d) { await invoke('servers_remove', { address: d.address }); S.servers = await invoke('servers_list'); renderPage(); },
  // Konto / Look
  async saveName() { try { S.acc = await invoke('account_set', { name: $('#accname').value, model: S.acc.model }); render(); toast('Gespeichert'); } catch (e) { toast(String(e)); } },
  accent(d) { document.documentElement.style.setProperty('--accent', d.c); localStorage.setItem('accent', d.c); renderPage(); },
  async pickBg() {
    const f = await pickFile('image/*'); if (!f) return;
    try { localStorage.setItem('bg', await shrinkImage(f)); applyBg(); } catch { toast('Bild konnte nicht übernommen werden'); }
  },
  clearBg() { localStorage.removeItem('bg'); applyBg(); },
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
  const was = !!(S.prog && S.prog.total);
  S.prog = ev.payload;
  if (!!S.prog.total !== was) return renderBar();
  const st = $('#stage'), bar = $('#barfill');
  if (st) st.textContent = S.prog.stage + (S.prog.total ? ` (${S.prog.done}/${S.prog.total})` : '');
  if (bar) bar.style.width = (S.prog.total ? Math.round((S.prog.done / S.prog.total) * 100) : 0) + '%';
});
T.event.listen('game-exit', (ev) => {
  S.running = false;
  S.err = ev.payload !== 0;
  S.msg = S.err ? `Minecraft wurde mit Code ${ev.payload} beendet. Siehe „Log“ oben.` : 'Minecraft wurde beendet.';
  renderBar(); if (S.page === 'servers') renderPage();
});

// ---------------------------------------------------------------- Start
(async function init() {
  const c = localStorage.getItem('accent'); if (c) document.documentElement.style.setProperty('--accent', c);
  applyBg();
  try { await loadAccount(); } catch {}
  await loadInstances();
  render();
  try { S.info = { ...S.info, ...(await invoke('app_info')) }; } catch {}
})();
