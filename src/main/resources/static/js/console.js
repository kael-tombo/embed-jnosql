/* ============================================================
   JunifyDB Console v2 — application logic
   Vanilla ES2020, no dependencies. Talks only to /api/*.
   ============================================================ */
'use strict';

/* ---------------- tiny helpers ---------------- */
const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

const esc = (v) => String(v ?? '').replace(/[&<>"']/g,
  (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

let authRedirecting = false;

async function api(path, opts = {}) {
  const res = await fetch('/api' + path, {
    headers: { 'Content-Type': 'application/json' },
    ...opts,
    body: opts.body != null ? JSON.stringify(opts.body) : undefined,
  });
  let data = null;
  const text = await res.text();
  try { data = text ? JSON.parse(text) : null; } catch { data = { raw: text }; }
  if (!res.ok) {
    const msg = (data && (data.error || data.message)) || `HTTP ${res.status}`;
    const err = new Error(msg);
    err.status = res.status;
    err.data = data;
    // Session expired / not signed in: send the user to the login page
    // once, instead of spraying "Unauthorized" toasts on every poll.
    if (res.status === 401 && !path.startsWith('/auth') && !authRedirecting) {
      authRedirecting = true;
      toast('Session expired — redirecting to sign-in…', 'err', 5000);
      setTimeout(() => {
        const next = encodeURIComponent(location.pathname + location.hash);
        window.location.href = '/login.html?next=' + next;
      }, 700);
    }
    throw err;
  }
  return data;
}

/* ---------------- toasts ---------------- */
function toast(msg, kind = 'info', ms = 3200) {
  const box = document.createElement('div');
  box.className = `toast ${kind}`;
  box.innerHTML = `<span>${esc(msg)}</span><button class="x" aria-label="Dismiss">×</button>`;
  box.querySelector('.x').onclick = () => box.remove();
  $('#toasts').appendChild(box);
  if (ms) setTimeout(() => box.remove(), ms);
}

/* ---------------- JSON highlighter ---------------- */
function renderJson(el, value) {
  const json = JSON.stringify(value, null, 2) ?? 'null';
  el.innerHTML = esc(json).replace(
    /("(\\u[a-fA-F0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false)\b|\bnull\b|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)/g,
    (m) => {
      let cls = 'n';
      if (/^"/.test(m)) cls = /:$/.test(m) ? 'k' : 's';
      else if (/true|false/.test(m)) cls = 'b';
      else if (/null/.test(m)) cls = 'nil';
      return `<span class="${cls}">${m}</span>`;
    });
}

function fmtBytes(n) {
  if (n == null) return '—';
  const u = ['B', 'KB', 'MB', 'GB'];
  let i = 0; let v = n;
  while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
  return `${v.toFixed(v >= 100 || i === 0 ? 0 : 1)} ${u[i]}`;
}

function fmtUptime(ms) {
  const s = Math.floor(ms / 1000);
  const d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600), m = Math.floor((s % 3600) / 60);
  if (d) return `${d}d ${h}h`;
  if (h) return `${h}h ${m}m`;
  if (m) return `${m}m ${s % 60}s`;
  return `${s}s`;
}

function tbl(headers, rows) {
  if (!rows.length) return `<div class="empty"><svg class="empty-logo" aria-hidden="true"><use href="#brand-mark"/></svg><br>No data.</div>`;
  return `<table class="tbl"><thead><tr>${headers.map((h) => `<th>${esc(h)}</th>`).join('')}</tr></thead>
    <tbody>${rows.map((r) => `<tr>${r.map((c) => `<td>${c}</td>`).join('')}</tr>`).join('')}</tbody></table>`;
}

/* ============================================================
   Router
   ============================================================ */
const ICONS = {
  overview: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="9" rx="1"/><rect x="14" y="3" width="7" height="5" rx="1"/><rect x="14" y="12" width="7" height="9" rx="1"/><rect x="3" y="16" width="7" height="5" rx="1"/></svg>',
  sql: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5"/><path d="M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3"/></svg>',
  collections: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 7l9-4 9 4-9 4-9-4z"/><path d="M3 12l9 4 9-4"/><path d="M3 17l9 4 9-4"/></svg>',
  kv: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="8" cy="12" r="4"/><path d="M12 12h9"/><path d="M18 12v3"/></svg>',
  columns: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16"/><path d="M15 4v16"/></svg>',
  vectors: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="6" cy="6" r="2.5"/><circle cx="18" cy="7" r="2.5"/><circle cx="8" cy="18" r="2.5"/><circle cx="17" cy="17" r="2.5"/><path d="M8 7.5l7.5-.2M7 15.8L6.8 8.5M10 17.5l4.5-.3M16 9.5l.8 5"/></svg>',
  schema: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 3l8 4.5v9L12 21l-8-4.5v-9L12 3z"/><path d="M12 12l8-4.5M12 12v9M12 12L4 7.5"/></svg>',
  tx: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12a9 9 0 11-3-6.7"/><path d="M21 3v5h-5"/></svg>',
  indexes: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 6h16M4 12h10M4 18h6"/></svg>',
  backup: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 3v10"/><path d="M8 9l4 4 4-4"/><path d="M4 17v2a2 2 0 002 2h12a2 2 0 002-2v-2"/></svg>',
  cdc: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 12h4l2-7 4 14 2-7h4"/></svg>',
  audit: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 3H6a2 2 0 00-2 2v14a2 2 0 002 2h12a2 2 0 002-2V9z"/><path d="M14 3v6h6"/><path d="M9 14l2 2 4-4"/></svg>',
  server: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="7" rx="2"/><rect x="3" y="13" width="18" height="7" rx="2"/><path d="M7 8h.01M7 17h.01"/></svg>',
};

const PANELS = [
  { id: 'overview',    label: 'Overview',     sub: 'Database at a glance',                    key: '1' },
  { id: 'sql',         label: 'SQL Studio',   sub: 'Relational SQL Engine — built-in dialect', key: '2' },
  { id: 'collections', label: 'Collections',  sub: 'Documents — inspect, edit, TTL, schema',   key: '3' },
  { id: 'kv',          label: 'Key-Value',    sub: 'KV, lists, sets, hashes',                  key: '4' },
  { id: 'columns',     label: 'Column Family',sub: 'Wide-column rows and ranges',              key: '5' },
  { id: 'vectors',     label: 'Vectors',      sub: 'HNSW k-NN search (experimental)',          key: '6' },
  { id: 'schema',      label: 'Schema',       sub: 'Insert-time validation rules',             key: '7' },
  { id: 'tx',          label: 'Transactions', sub: 'Begin, commit, roll back (MVCC)',          key: '8' },
  { id: 'indexes',     label: 'Indexes',      sub: 'Secondary field indexes',                  key: '9' },
  { id: 'backup',      label: 'Backup',       sub: 'Snapshot and restore',                     key: '0' },
  { id: 'cdc',         label: 'CDC / Events', sub: 'Change data capture stream' },
  { id: 'audit',       label: 'Audit Trail',  sub: 'Mutation history' },
  { id: 'server',      label: 'Server',       sub: 'Health and JVM telemetry' },
];

/* Sidebar groups make the engine split explicit: relational vs
   non-relational capabilities, then shared engine-wide surfaces. */
const GROUPS = [
  { name: 'General',                 items: ['overview'] },
  { name: 'Relational SQL Engine',   items: ['sql'] },
  { name: 'Non-Relational NoSQL Engine', items: ['collections', 'kv', 'columns', 'vectors'] },
  { name: 'Data Model',              items: ['schema', 'tx', 'indexes'] },
  { name: 'Both Engines',            items: ['backup', 'cdc', 'audit', 'server'] },
];

let activePanel = 'overview';

function buildNav() {
  const nav = $('#nav');
  nav.innerHTML = '';
  for (const g of GROUPS) {
    const gh = document.createElement('div');
    gh.className = 'nav-group';
    gh.textContent = g.name;
    nav.appendChild(gh);
    for (const id of g.items) {
      const p = PANELS.find((x) => x.id === id);
      const b = document.createElement('button');
      b.className = 'nav-item';
      b.dataset.panel = p.id;
      b.innerHTML = `${ICONS[p.id]}<span class="nav-label">${esc(p.label)}</span>${p.key ? `<span class="nav-key">${p.key}</span>` : ''}`;
      b.title = p.sub; // tooltip in rail / collapsed mode where the label is hidden
      b.setAttribute('aria-label', p.label);
      b.onclick = () => goto(p.id);
      nav.appendChild(b);
    }
  }
}

function goto(id) {
  activePanel = id;
  const p = PANELS.find((x) => x.id === id);
  $$('.panel').forEach((s) => s.classList.remove('active'));
  $(`#panel-${id}`).classList.add('active');
  $$('.nav-item').forEach((b) => b.classList.toggle('active', b.dataset.panel === id));
  $('#panelTitle').textContent = p.label;
  $('#panelSub').textContent = p.sub;
  history.replaceState(null, '', '#' + id);
  refreshPanel(id);
  $('#main').scrollTop = 0;
}

/* ============================================================
   Overview
   ============================================================ */
const sparkData = [];

function setKpi(label, value, cls = '') {
  return `<div><div class="kpi-label">${esc(label)}</div><div class="kpi-value ${cls}">${value}</div></div>`;
}

async function refreshOverview() {
  const [health, metrics, cols] = await Promise.all([
    api('/health'), api('/metrics'), api('/collections').catch(() => ({ collections: [] })),
  ]);

  const totalOps = metrics.totalOperations ?? 0;
  const ops = Math.round(metrics.opsPerSecond ?? 0);
  sparkData.push(ops);
  if (sparkData.length > 60) sparkData.shift();

  $('#kpiRow').innerHTML =
    setKpi('Status', health.status === 'ok' ? '● ONLINE' : '● DOWN', health.status === 'ok' ? 'ok' : 'err') +
    setKpi('Total ops', totalOps.toLocaleString()) +
    setKpi('Ops / sec', ops.toLocaleString()) +
    setKpi('Inserts', (metrics.inserts ?? 0).toLocaleString()) +
    setKpi('Reads', (metrics.reads ?? 0).toLocaleString()) +
    setKpi('Tx commits', (metrics.transactionCommits ?? 0).toLocaleString());

  // sparkline
  const max = Math.max(1, ...sparkData);
  const pts = sparkData.map((v, i) => `${(i / Math.max(1, sparkData.length - 1)) * 600},${38 - (v / max) * 34}`).join(' ');
  $('#opsSpark').innerHTML = `<polyline points="${pts}" />`;
  $('#sparkNow').textContent = `${ops} ops/s`;

  const mem = health.memory ?? {};
  $('#engineInfo').innerHTML = `
    <div class="grid kpi">
      ${setKpi('Engine', esc(health.engine ?? '—'))}
      ${setKpi('Open', health.open ? 'yes' : 'no', health.open ? 'ok' : 'err')}
      ${setKpi('Threads', health.threads?.active ?? '—')}
      ${setKpi('Heap used', fmtBytes(mem.used))}
      ${setKpi('Heap max', fmtBytes(mem.max))}
    </div>
    <div class="empty" style="padding:10px 0 0">Relational SQL and Non-Relational NoSQL queries both run against this engine.</div>`;

  const list = cols.collections ?? [];
  $('#ovCollections').innerHTML = list.length
    ? tbl(['Collection', 'Docs'], list.map((c) => [`<td>${esc(c.name)}</td>`, `<td class="num">${c.count}</td>`]))
    : '<div class="empty"><svg class="empty-logo" aria-hidden="true"><use href="#brand-mark"/></svg><br>No collections yet — insert a document on the Collections panel.</div>';

  const events = (await api('/audit/logs').catch(() => ({ events: [] }))).events ?? [];
  $('#ovAudit').innerHTML = events.length
    ? tbl(['Time', 'Op', 'Resource'], events.slice(0, 8).map((e) => [
        new Date(e.timestamp).toLocaleTimeString(), esc(e.operation), esc(e.resource ?? '')]))
    : '<div class="empty">No mutations recorded yet.</div>';
}

/* ============================================================
   SQL Studio
   ============================================================ */
const SQL_EXAMPLES = [
  'SELECT * FROM products',
  "INSERT INTO products (id, name, price) VALUES ('p2', 'Mouse', 25.0)",
  'SELECT category, COUNT(*) AS n, AVG(price) AS avg FROM products GROUP BY category',
  'SELECT * FROM products WHERE price > 10 ORDER BY price DESC',
];

async function runSql() {
  const sql = $('#sqlInput').value.trim();
  const status = $('#sqlStatus');
  if (!sql) { status.textContent = 'empty'; status.className = 'badge err'; return; }
  status.textContent = 'running…'; status.className = 'badge';
  try {
    const t0 = performance.now();
    const res = await api('/sql', { method: 'POST', body: { query: sql } });
    const ms = Math.max(1, Math.round(performance.now() - t0));
    status.textContent = `ok · ${ms} ms`; status.className = 'badge ok';
    $('#sqlMeta').textContent = `${res.rowCount} row${res.rowCount === 1 ? '' : 's'} · server ${res.executionTimeMs} ms`;
    renderSqlResult(res);
    saveHistory(sql);
  } catch (e) {
    status.textContent = 'error'; status.className = 'badge err';
    $('#sqlMeta').textContent = '';
    $('#sqlOut').innerHTML = `<div class="card card-pad" style="border-color:var(--err-dim)" role="alert">
      <strong style="color:var(--err)">✖ SQL error — ${esc(e.message)}</strong>
      ${e.data && e.data.message ? `<div style="margin-top:6px;color:var(--text-1)">${esc(e.data.message)}</div>` : ''}
      <div style="margin-top:8px;font-size:12px;color:var(--text-2)">The Relational SQL Engine runs a built-in dialect (SELECT · INSERT · UPDATE · DELETE · JOIN · GROUP BY · CREATE/DROP TABLE). Views, sequences, and stored procedures are not supported.</div>
    </div>`;
  }
}

function renderSqlResult(res) {
  if (!res.columns || !res.columns.length) {
    $('#sqlOut').innerHTML = '<div class="empty">Statement executed — no result set</div>';
    return;
  }
  $('#sqlOut').innerHTML = `<div class="tbl-wrap">${tbl(res.columns,
    res.rows.map((r) => res.columns.map((c) => esc(r[c] ?? 'NULL'))))}</div>`;
}

/* --- query history (localStorage) --- */
const HKEY = 'junifydb.sql.history';
function saveHistory(sql) {
  try {
    const h = JSON.parse(localStorage.getItem(HKEY) || '[]').filter((s) => s !== sql);
    h.unshift(sql);
    localStorage.setItem(HKEY, JSON.stringify(h.slice(0, 30)));
  } catch { /* storage unavailable */ }
}
function renderHistory() {
  let h = [];
  try { h = JSON.parse(localStorage.getItem(HKEY) || '[]'); } catch { /* ignore */ }
  $('#sqlExamples').innerHTML = h.slice(0, 6).map((s) =>
    `<button class="btn sm" data-sql="${esc(s)}">${esc(s.length > 46 ? s.slice(0, 46) + '…' : s)}</button>`).join('');
}

/* ============================================================
   Collections
   ============================================================ */
let colDocs = [];

async function loadCollection() {
  const name = $('#colSel').value.trim();
  if (!name) { toast('Enter a collection name', 'err'); return; }
  const docs = await api(`/collections/${encodeURIComponent(name)}`);
  colDocs = Array.isArray(docs) ? docs : [];
  const fields = collectFields(colDocs);
  $('#colCount').textContent = `${colDocs.length} docs`;
  $('#colTable').innerHTML = tbl(['id', ...fields, 'expires'], colDocs.map((d) =>
    [`<td><a href="#" class="mono" data-doc="${esc(d.id)}">${esc(d.id)}</a></td>`,
      ...fields.map((f) => `<td>${esc(d.fields?.[f] ?? '')}</td>`),
      `<td>${d.expiresAt ? new Date(d.expiresAt).toLocaleString() : ''}</td>`]));
  $$('#colTable [data-doc]').forEach((a) => a.onclick = (e) => {
    e.preventDefault();
    showDoc(colDocs.find((d) => d.id === a.dataset.doc));
  });
}

/* Auto-refresh entry point: unlike loadCollection(), it must not bark
   at the user when the name box is still empty — show a hint instead. */
async function refreshCollections() {
  const name = $('#colSel').value.trim();
  if (!name) {
    $('#colCount').textContent = '';
    $('#colTable').innerHTML = '<div class="empty"><svg class="empty-logo" aria-hidden="true"><use href="#brand-mark"/></svg><br>No collection loaded.<br>Type a name above (e.g. <b>users</b>) and press <b>Load</b>.</div>';
    return;
  }
  await loadCollection();
}

function collectFields(docs) {
  const set = new Set();
  for (const d of docs) Object.keys(d.fields ?? {}).forEach((k) => set.add(k));
  return [...set].filter((f) => f !== '_entity').slice(0, 8);
}

function showDoc(d) {
  $('#docDetail').innerHTML = d
    ? `<pre class="code json" id="docJson"></pre>
       <div style="display:flex;gap:8px;margin-top:10px">
         <button class="btn sm" id="docEdit">Load into editor</button>
         <button class="btn sm danger" id="docDel">Delete</button>
       </div>`
    : '<div class="empty">Document not found</div>';
  if (d) {
    renderJson($('#docJson'), d);
    $('#docEdit').onclick = () => {
      $('#insCol').value = $('#colSel').value;
      $('#insJson').value = JSON.stringify({ id: d.id, ...d.fields }, null, 2);
      $('#insCol').focus();
    };
    $('#docDel').onclick = async () => {
      try {
        await api(`/collections/${encodeURIComponent($('#colSel').value)}/${encodeURIComponent(d.id)}`,
          { method: 'DELETE' });
        toast(`Deleted ${d.id}`, 'ok');
        loadCollection();
        $('#docDetail').innerHTML = '<span class="empty" style="padding:12px">Deleted.</span>';
      } catch (e) { toast(e.message, 'err'); }
    };
  }
}

async function insertDoc() {
  const col = $('#insCol').value.trim();
  const status = $('#insStatus');
  if (!col) { toast('Collection name required', 'err'); return; }
  let body;
  try { body = JSON.parse($('#insJson').value); }
  catch { status.textContent = 'invalid JSON'; status.className = 'badge err'; return; }
  try {
    const saved = await api(`/collections/${encodeURIComponent(col)}`,
      { method: 'POST', body });
    status.textContent = `saved ${saved.id ?? ''}`; status.className = 'badge ok';
    toast(`Document saved to ${col}`, 'ok');
    if ($('#colSel').value.trim() === col) loadCollection();
  } catch (e) {
    status.textContent = e.message; status.className = 'badge err';
    toast(e.message, 'err');
  }
}

async function cleanupExpired() {
  const col = $('#colSel').value.trim();
  if (!col) return toast('Load a collection first', 'err');
  const r = await api(`/collections/${encodeURIComponent(col)}/cleanup`, { method: 'POST' });
  toast(`Removed ${r.deleted} expired docs`, 'ok');
  loadCollection();
}

async function dropDocs() {
  const col = $('#colSel').value.trim();
  if (!col || !confirm(`Delete ALL documents in "${col}"?`)) return;
  const docs = await api(`/collections/${encodeURIComponent(col)}`);
  for (const d of (docs ?? [])) {
    await api(`/collections/${encodeURIComponent(col)}/${encodeURIComponent(d.id)}`, { method: 'DELETE' });
  }
  toast('Collection emptied', 'ok');
  loadCollection();
}

/* ============================================================
   KV & Redis structures
   ============================================================ */
const KV_TABS = {
  kv: {
    title: 'Simple KV',
    form: `
      <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:flex-end">
        <div><label class="fld">Bucket</label><input type="text" id="kvBucket" value="sessions" style="width:150px"></div>
        <div><label class="fld">Key</label><input type="text" id="kvKey" style="width:150px"></div>
        <div><label class="fld">Value</label><input type="text" id="kvVal" style="width:220px"></div>
        <button class="btn primary sm" id="kvPut">Put</button>
        <button class="btn sm" id="kvGet">Get</button>
        <button class="btn sm danger" id="kvDel">Delete</button>
      </div><div style="margin-top:12px" id="kvOut"></div>`,
  },
  list: {
    title: 'Lists',
    form: `
      <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:flex-end">
        <div><label class="fld">Bucket</label><input type="text" id="lsBucket" value="queue" style="width:130px"></div>
        <div><label class="fld">Key</label><input type="text" id="lsKey" value="jobs" style="width:130px"></div>
        <div><label class="fld">Values (comma-sep)</label><input type="text" id="lsVals" style="width:220px"></div>
        <button class="btn primary sm" id="lsPush">Push</button>
        <button class="btn sm" id="lsPop">Pop</button>
        <button class="btn sm" id="lsRange">Read all</button>
      </div><div style="margin-top:12px" id="kvOut"></div>`,
  },
  set: {
    title: 'Sets',
    form: `
      <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:flex-end">
        <div><label class="fld">Bucket</label><input type="text" id="stBucket" value="tags" style="width:130px"></div>
        <div><label class="fld">Key</label><input type="text" id="stKey" style="width:130px"></div>
        <div><label class="fld">Members (comma-sep)</label><input type="text" id="stVals" style="width:220px"></div>
        <button class="btn primary sm" id="stAdd">Add</button>
        <button class="btn sm danger" id="stRem">Remove</button>
        <button class="btn sm" id="stMembers">Members</button>
      </div><div style="margin-top:12px" id="kvOut"></div>`,
  },
  hash: {
    title: 'Hashes',
    form: `
      <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:flex-end">
        <div><label class="fld">Bucket</label><input type="text" id="hsBucket" value="user" style="width:130px"></div>
        <div><label class="fld">Key</label><input type="text" id="hsKey" style="width:130px"></div>
        <div><label class="fld">Field</label><input type="text" id="hsField" style="width:130px"></div>
        <div><label class="fld">Value</label><input type="text" id="hsVal" style="width:180px"></div>
        <button class="btn primary sm" id="hsSet">HSet</button>
        <button class="btn sm" id="hsGet">HGet</button>
        <button class="btn sm" id="hsAll">HGetAll</button>
      </div><div style="margin-top:12px" id="kvOut"></div>`,
  },
};

let kvTab = 'kv';

function bindKvTab() {
  const spec = KV_TABS[kvTab];
  $('#kvHead').textContent = spec.title;
  $('#kvBody').innerHTML = spec.form;
  const out = () => $('#kvOut');
  const show = (v) => renderJson(out(), v);

  if (kvTab === 'kv') {
    $('#kvPut').onclick = async () => { show(await api(`/kv/${v('#kvBucket')}/${v('#kvKey')}`, { method: 'PUT', body: { value: v('#kvVal') } })); flash(); };
    $('#kvGet').onclick = async () => { try { show(await api(`/kv/${v('#kvBucket')}/${v('#kvKey')}`)); flash(); } catch (e) { show({ error: e.message }); } };
    $('#kvDel').onclick = async () => { await api(`/kv/${v('#kvBucket')}/${v('#kvKey')}`, { method: 'DELETE' }); show({ deleted: true }); flash(); };
  } else if (kvTab === 'list') {
    const vals = () => v('#lsVals').split(',').map((s) => s.trim()).filter(Boolean);
    $('#lsPush').onclick = async () => { show(await api(`/kv/lists/${v('#lsBucket')}/${v('#lsKey')}/rpush`, { method: 'POST', body: { values: vals() } })); flash(); };
    $('#lsPop').onclick = async () => { show(await api(`/kv/lists/${v('#lsBucket')}/${v('#lsKey')}/rpop`, { method: 'POST', body: {} })); flash(); };
    $('#lsRange').onclick = async () => show(await api(`/kv/lists/${v('#lsBucket')}/${v('#lsKey')}/lrange`));
  } else if (kvTab === 'set') {
    const vals = () => v('#stVals').split(',').map((s) => s.trim()).filter(Boolean);
    $('#stAdd').onclick = async () => { show(await api(`/kv/sets/${v('#stBucket')}/${v('#stKey')}/sadd`, { method: 'POST', body: { members: vals() } })); flash(); };
    $('#stRem').onclick = async () => { show(await api(`/kv/sets/${v('#stBucket')}/${v('#stKey')}/srem`, { method: 'POST', body: { members: vals() } })); flash(); };
    $('#stMembers').onclick = async () => show(await api(`/kv/sets/${v('#stBucket')}/${v('#stKey')}/smembers`));
  } else if (kvTab === 'hash') {
    $('#hsSet').onclick = async () => { show(await api(`/kv/hashes/${v('#hsBucket')}/${v('#hsKey')}/hset`, { method: 'POST', body: { field: v('#hsField'), value: v('#hsVal') } })); flash(); };
    $('#hsGet').onclick = async () => show(await api(`/kv/hashes/${v('#hsBucket')}/${v('#hsKey')}/hget?field=${encodeURIComponent(v('#hsField'))}`));
    $('#hsAll').onclick = async () => show(await api(`/kv/hashes/${v('#hsBucket')}/${v('#hsKey')}/hgetall`));
  }

  function v(id) { return $('#' + String(id).replace(/^#/, '')).value.trim(); }
  function flash() { const s = $('#kvStatus'); s.textContent = 'ok'; s.className = 'badge ok'; setTimeout(() => { s.textContent = ''; }, 1500); }
}

/* ============================================================
   Column families
   ============================================================ */
async function cfFetch() {
  const fam = $('#cfFam').value.trim(), row = $('#cfRow').value.trim();
  try {
    const r = await api(`/columns/${encodeURIComponent(fam)}/${encodeURIComponent(row)}`);
    $('#cfOut').innerHTML = `<pre class="code json"></pre>`;
    renderJson($('#cfOut .code'), r);
  } catch (e) {
    $('#cfOut').innerHTML = `<div class="empty">${esc(e.message)}</div>`;
  }
}
async function cfStats() {
  const r = await api(`/columns/${encodeURIComponent($('#cfFam').value.trim())}/stats`).catch((e) => ({ error: e.message }));
  $('#cfOut').innerHTML = `<pre class="code json"></pre>`;
  renderJson($('#cfOut .code'), r);
}
async function cfPut() {
  let cols;
  try { cols = JSON.parse($('#cfPutCols').value || '{}'); }
  catch { return toast('Columns must be valid JSON', 'err'); }
  const r = await api(`/columns/${encodeURIComponent($('#cfFam').value.trim())}/${encodeURIComponent($('#cfPutRow').value.trim())}`,
    { method: 'PUT', body: cols });
  toast(`Row written to ${$('#cfFam').value}`, 'ok');
  $('#cfRow').value = $('#cfPutRow').value.trim();
  cfFetch();
}

/* ============================================================
   Vectors
   ============================================================ */
async function vecInfo() {
  // GET /api/vectors/{index}/{id} returns index-level stats for any id value
  const r = await api(`/vectors/${encodeURIComponent($('#vecIdx').value.trim())}/_`);
  $('#vecStatus').textContent = `${r.size ?? 0} vecs · ${r.dimensions ?? '?'}d (experimental)`;
}
async function vecSearch() {
  let vector;
  try { vector = JSON.parse($('#vecQuery').value); }
  catch { return toast('Vector must be a JSON array', 'err'); }
  try {
    const r = await api(`/vectors/${encodeURIComponent($('#vecIdx').value.trim())}/search`,
      { method: 'POST', body: { vector, k: Number($('#vecK').value) || 5 } });
    // Engine returns a list of id strings (or objects, depending on version) — handle both
    const rows = (r.results ?? []).map((x) => typeof x === 'string'
      ? [`<td><code>${esc(x)}</code></td>`, '<td class="num">—</td>']
      : [`<td><code>${esc(x.id ?? '')}</code></td>`, `<td class="num">${(+x.distance ?? 0).toFixed(4)}</td>`]);
    $('#vecOut').innerHTML = rows.length
      ? tbl(['id', 'distance'], rows)
      : '<div class="empty">No results — index is empty or no match.</div>';
  } catch (e) {
    $('#vecOut').innerHTML = `<div class="empty">${esc(e.message)}</div>`;
  }
}

/* ============================================================
   Schema
   ============================================================ */
async function schRefresh() {
  const r = await api('/schema');
  const names = r.schemas ?? [];
  const details = await Promise.all(names.map(async (n) => {
    const s = await api(`/schema/${encodeURIComponent(n)}`).catch(() => null);
    return [esc(n), s && s.fields ? s.fields.map((f) => `${esc(f.name)}:${esc(f.type)}${f.required ? '•' : ''}`).join(', ') : '—'];
  }));
  $('#schList').innerHTML = details.length
    ? tbl(['Collection', 'Fields'], details)
    : '<div class="empty">No schemas registered — collections accept any valid document.</div>';
}
async function schSave() {
  let fields;
  try { fields = JSON.parse($('#schFields').value); }
  catch { return toast('Fields must be valid JSON', 'err'); }
  await api(`/schema/${encodeURIComponent($('#schCol').value.trim())}`,
    { method: 'POST', body: { fields, strict: $('#schStrict').checked } });
  toast(`Schema registered for ${$('#schCol').value}`, 'ok');
  schRefresh();
}

/* ============================================================
   Transactions
   ============================================================ */
async function txRefresh() {
  const r = await api('/transactions');
  const ids = r.ids ?? [];
  $('#txCount').textContent = `${ids.length} active`;
  $('#txTable').innerHTML = tbl(['Transaction ID', ''],
    ids.map((id) => [`<td><code>${esc(String(id))}</code></td>`,
      `<td><button class="btn sm" data-tx="${esc(String(id))}" data-act="commit">Commit</button>
       <button class="btn sm danger" data-tx="${esc(String(id))}" data-act="rollback">Rollback</button></td>`]));
  $$('#txTable [data-tx]').forEach((b) => b.onclick = async () => {
    await api('/transactions', { method: 'POST', body: { action: b.dataset.act, transactionId: Number(b.dataset.tx) } });
    toast(`Transaction ${b.dataset.act}ted`, 'ok');
    txRefresh();
  });
}
async function txBegin() {
  const r = await api('/transactions', { method: 'POST', body: { action: 'begin' } });
  toast(`Started tx #${r.transactionId}`, 'ok');
  txRefresh();
}

/* ============================================================
   Indexes
   ============================================================ */
async function idxList() {
  const r = await api(`/indexes/${encodeURIComponent($('#idxCol').value.trim())}`);
  const entries = Object.entries(r.indexes ?? {});
  $('#idxOut').innerHTML = entries.length
    ? tbl(['Field', 'Type', 'Unique values', 'Entries'],
        entries.map(([f, i]) => [esc(f), esc(i.type ?? 'secondary'), i.uniqueValues ?? 0, i.totalIndexed ?? 0]))
    : '<div class="empty">No indexes on this collection.</div>';
}
async function idxCreate() {
  const col = $('#idxCol').value.trim(), field = $('#idxField').value.trim();
  if (!col || !field) return toast('Collection and field required', 'err');
  await api(`/indexes/${encodeURIComponent(col)}`, { method: 'POST', body: { field } });
  $('#idxStatus').textContent = `created ${col}.${field}`;
  idxList();
}

/* ============================================================
   Backup
   ============================================================ */
async function bkRefresh() {
  const r = await api('/backup');
  const backups = r.backups ?? [];
  $('#bkInfo').innerHTML = `
    <div class="grid kpi">
      ${setKpi('Engine', esc(r.database?.engine ?? '—'))}
      ${setKpi('Collections', Object.keys(r.collections ?? {}).length)}
      ${setKpi('Disk usage', esc(r.diskUsage?.totalMB ?? '—'))}
      ${setKpi('Files', r.diskUsage?.fileCount ?? '—')}
    </div>
    <div class="muted" style="margin-top:10px;font-size:12px">
      Backup directory: <code>${esc(r.backup?.directory ?? '—')}</code> · ${backups.length} existing snapshot${backups.length === 1 ? '' : 's'}
    </div>`;
}
async function bkCreate() {
  const r = await api('/backup', { method: 'POST', body: {} }).catch((e) => ({ error: e.message }));
  if (r.error) { toast(r.error, 'err'); return; }
  const docs = r.documents ?? 0;
  const cols = Object.entries(r.collections ?? {});
  $('#bkStatus').textContent = `backup → ${r.file ?? 'ok'} · ${docs} document${docs === 1 ? '' : 's'}`;
  // Report what the snapshot actually holds; a backup of zero documents is a real
  // state (empty database) but must never look identical to a populated one.
  if (docs === 0) {
    toast('Backup created, but it contains 0 documents', 'warn');
  } else {
    toast(`Backup created · ${docs} documents in ${cols.length} collection${cols.length === 1 ? '' : 's'}`, 'ok');
  }
  bkRefresh();
}
async function bkRestore() {
  const r = await api('/backup/restore', { method: 'POST', body: { backupFile: $('#bkFile').value.trim() } })
    .catch((e) => ({ error: e.message }));
  if (r.error) { toast(r.error, 'err'); return; }
  $('#bkStatus').textContent = `restored from ${r.file ?? 'file'}`;
  toast('Restore complete', 'ok');
  bkRefresh();
}

/* ============================================================
   CDC
   ============================================================ */
async function cdcRefresh() {
  const r = await api('/cdc');
  const state = $('#cdcState');
  state.textContent = r.enabled ? 'enabled' : 'disabled';
  state.className = `badge ${r.enabled ? 'ok' : 'err'}`;
  $('#cdcStatus').innerHTML = `
    <div class="grid kpi">
      ${setKpi('Subscribers', r.subscribers ?? 0)}
      ${setKpi('Events in log', r.eventsInLog ?? 0)}
      ${setKpi('File connectors', (r.fileConnectors ?? []).length)}
      ${setKpi('Kafka connectors', (r.kafkaConnectors ?? []).length)}
    </div>`;
  const conns = [
    ...(r.fileConnectors ?? []).map((c) => [esc(c), 'file', '']),
    ...(r.kafkaConnectors ?? []).map((c) => [esc(c), 'kafka', '']),
  ];
  $('#cdcConnectors').innerHTML = conns.length
    ? tbl(['Name', 'Type', ''], conns.map((c) => [...c,
        `<button class="btn sm danger" data-cdc="${c[0]}">Remove</button>`]))
    : '<div class="empty">No connectors configured.</div>';
  $$('#cdcConnectors [data-cdc]').forEach((b) => b.onclick = async () => {
    await api(`/cdc/connectors/${encodeURIComponent(b.dataset.cdc)}`, { method: 'DELETE' });
    cdcRefresh();
  });
}
async function cdcAdd() {
  const name = $('#cdcName').value.trim(), type = $('#cdcType').value, param = $('#cdcParam').value.trim();
  if (!name) return toast('Connector name required', 'err');
  const body = type === 'file' ? { type: 'file', outputDir: param } : { type: 'kafka', bootstrapServers: param, topic: name };
  await api(`/cdc/connectors/${encodeURIComponent(name)}`, { method: 'POST', body });
  toast(`Connector ${name} added`, 'ok');
  cdcRefresh();
}
async function cdcToggle() {
  const r = await api('/cdc');
  await api(`/cdc/${r.enabled ? 'disable' : 'enable'}`, { method: 'POST' }).catch(() => {});
  cdcRefresh();
}
async function cdcEvents() {
  const r = await api('/cdc/events').catch(() => ({ events: [] }));
  const ev = r.events ?? [];
  $('#cdcTable').innerHTML = tbl(['Time', 'Type', 'Collection', 'Key'],
    ev.map((e) => [new Date(e.timestamp).toLocaleTimeString(), esc(e.eventType ?? ''), esc(e.collection ?? ''), esc(e.key ?? '')]));
}

/* ============================================================
   Audit & Server
   ============================================================ */
async function audRefresh() {
  const r = await api('/audit/logs');
  const ev = r.events ?? [];
  $('#audTable').innerHTML = tbl(['Time', 'Operation', 'Resource', 'Document', 'Status', 'Client'],
    ev.map((e) => [
      new Date(e.timestamp).toLocaleTimeString(), esc(e.operation), esc(e.resource ?? ''),
      esc(e.documentId ?? ''), esc(e.status ?? ''), esc(e.clientIp ?? ''),
    ]));
}
async function srvRefresh() {
  renderJson($('#srvHealth'), await api('/health'));
  renderJson($('#srvMetrics'), await api('/metrics'));
}

/* ============================================================
   Topbar: health, engine, uptime, theme
   ============================================================ */
function setTheme(t) {
  document.documentElement.dataset.theme = t;
  try { localStorage.setItem('junifydb.theme', t); } catch { /* ignore */ }
}
function initTheme() {
  let t = 'dark';
  try { t = localStorage.getItem('junifydb.theme') || 'dark'; } catch { /* ignore */ }
  setTheme(t);
}

async function pollHealth() {
  try {
    const h = await api('/health');
    const chip = $('#chipHealth');
    chip.className = 'topbar-chip ok';
    $('#chipHealthText').textContent = h.status === 'ok' ? 'Healthy' : 'Degraded';
    $('#chipEngine').textContent = h.engine ?? '—';
    $('#chipUptime').textContent = 'up ' + fmtUptime(h.uptime ?? 0);
  } catch (e) {
    const chip = $('#chipHealth');
    chip.className = 'topbar-chip err';
    $('#chipHealthText').textContent = e?.status === 401 ? 'Sign-in required' : 'Unreachable';
  }
}

/* ============================================================
   Panel registry & global refresh
   ============================================================ */
const REFRESH = {
  overview: refreshOverview,
  collections: refreshCollections,
  tx: txRefresh,
  schema: schRefresh,
  backup: bkRefresh,
  cdc: cdcRefresh,
  audit: audRefresh,
  server: srvRefresh,
};

function refreshPanel(id) { REFRESH[id]?.().catch((e) => toast(e.message, 'err')); }

/* ============================================================
   Boot
   ============================================================ */
function bind() {
  // global nav
  $$('[data-goto]').forEach((b) => b.onclick = () => goto(b.dataset.goto));

  // topbar
  $('#btnTheme').onclick = () => setTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark');
  $('#btnRefreshAll').onclick = () => { refreshPanel(activePanel); pollHealth(); };
  $('#btnLogout').onclick = async () => {
    try { await api('/auth/logout', { method: 'POST' }); } catch { /* best-effort */ }
    try { localStorage.removeItem('apiKey'); sessionStorage.clear(); } catch { /* ignore */ }
    const next = encodeURIComponent('/#');
    window.location.href = '/login.html?next=' + next;
  };

  // SQL
  $('#sqlRun').onclick = runSql;
  $('#sqlClear').onclick = () => { $('#sqlInput').value = ''; $('#sqlOut').innerHTML = ''; $('#sqlMeta').textContent = ''; };
  $('#sqlCopy').onclick = () => navigator.clipboard?.writeText($('#sqlInput').value).then(() => toast('Copied', 'ok'));
  $('#sqlInput').addEventListener('keydown', (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { e.preventDefault(); runSql(); }
  });
  $('#sqlExamples').addEventListener('click', (e) => {
    const b = e.target.closest('[data-sql]');
    if (b) { $('#sqlInput').value = b.dataset.sql; runSql(); }
  });

  // collections
  $('#colLoad').onclick = () => loadCollection().catch((e) => toast(e.message, 'err'));
  $('#colRefresh').onclick = () => loadCollection().catch((e) => toast(e.message, 'err'));
  $('#colNew').onclick = () => { $('#insCol').focus(); goto('collections'); };
  $('#colCleanup').onclick = () => cleanupExpired().catch((e) => toast(e.message, 'err'));
  $('#colDrop').onclick = () => dropDocs().catch((e) => toast(e.message, 'err'));
  $('#insSubmit').onclick = () => insertDoc();
  $('#colSel').addEventListener('keydown', (e) => e.key === 'Enter' && loadCollection());

  // kv tabs
  $$('#kvTabs .tab').forEach((t) => t.onclick = () => {
    $$('#kvTabs .tab').forEach((x) => x.classList.remove('active'));
    t.classList.add('active');
    kvTab = t.dataset.kvtab;
    bindKvTab();
  });

  // columns
  $('#cfGet').onclick = () => cfFetch().catch((e) => toast(e.message, 'err'));
  $('#cfStats').onclick = () => cfStats().catch((e) => toast(e.message, 'err'));
  $('#cfPutGo').onclick = () => cfPut().catch((e) => toast(e.message, 'err'));
  $('#cfPutBtn').onclick = () => $('#cfPutRow').focus();

  // vectors
  $('#vecInfo').onclick = () => vecInfo().catch((e) => toast(e.message, 'err'));
  $('#vecSearch').onclick = () => vecSearch().catch((e) => toast(e.message, 'err'));

  // schema
  $('#schRefresh').onclick = () => schRefresh().catch((e) => toast(e.message, 'err'));
  $('#schSave').onclick = () => schSave().catch((e) => toast(e.message, 'err'));

  // tx
  $('#txBegin').onclick = () => txBegin().catch((e) => toast(e.message, 'err'));

  // indexes
  $('#idxList').onclick = () => idxList().catch((e) => toast(e.message, 'err'));
  $('#idxCreate').onclick = () => idxCreate().catch((e) => toast(e.message, 'err'));

  // backup
  $('#bkRefresh').onclick = () => bkRefresh().catch((e) => toast(e.message, 'err'));
  $('#bkCreate').onclick = () => bkCreate();
  $('#bkRestore').onclick = () => bkRestore();

  // cdc
  $('#cdcRefresh').onclick = () => cdcRefresh().catch((e) => toast(e.message, 'err'));
  $('#cdcAdd').onclick = () => cdcAdd().catch((e) => toast(e.message, 'err'));
  $('#cdcToggle').onclick = () => cdcToggle().catch((e) => toast(e.message, 'err'));
  $('#cdcEvents').onclick = () => cdcEvents().catch((e) => toast(e.message, 'err'));

  // audit / server
  $('#audRefresh').onclick = () => audRefresh().catch((e) => toast(e.message, 'err'));
  $('#srvRefresh').onclick = () => srvRefresh().catch((e) => toast(e.message, 'err'));

  // keyboard shortcuts: 1-9/0 to switch panels (when not typing)
  document.addEventListener('keydown', (e) => {
    const tag = document.activeElement?.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || e.ctrlKey || e.metaKey || e.altKey) return;
    const p = PANELS.find((x) => x.key === e.key);
    if (p) goto(p.id);
  });
}

function boot() {
  buildNav();
  initTheme();
  bind();
  bindKvTab();
  renderHistory();
  // Deep-link support: honor #panel on load and keep browser
  // back/forward navigation in sync with the active panel.
  const hash = location.hash.slice(1);
  goto(PANELS.some((p) => p.id === hash) ? hash : 'overview');
  window.addEventListener('hashchange', () => {
    const id = location.hash.slice(1);
    if (PANELS.some((p) => p.id === id) && id !== activePanel) goto(id);
  });
  pollHealth();
  setInterval(pollHealth, 10000);
  setInterval(() => { if (activePanel === 'overview') refreshOverview().catch(() => {}); }, 5000);
}

boot();
