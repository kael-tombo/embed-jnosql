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

/* ============================================================
   Explicit action states
   Every user action resolves into exactly one of these, and the UI
   must say which — never a blank panel and never a silent failure.
   ============================================================ */
const STATES = {
  IDLE: 'idle',
  LOADING: 'loading',
  SUCCESS: 'success',
  EMPTY: 'empty',
  VALIDATION: 'validation',
  BACKEND: 'backend',
  TIMEOUT: 'timeout',
  PERMISSION: 'permission',
  CONFLICT: 'conflict',
  RATE: 'rate_limited',
  RECOVERY: 'recovery_required',
};

/** Badge text + CSS class for each state. */
const STATE_UI = {
  [STATES.IDLE]:      { label: 'idle',             cls: 'badge' },
  [STATES.LOADING]:   { label: 'loading…',         cls: 'badge' },
  [STATES.SUCCESS]:   { label: 'success',          cls: 'badge ok' },
  [STATES.EMPTY]:     { label: 'empty',            cls: 'badge warn' },
  [STATES.VALIDATION]:{ label: 'validation error', cls: 'badge err' },
  [STATES.BACKEND]:   { label: 'backend error',    cls: 'badge err' },
  [STATES.TIMEOUT]:   { label: 'timeout',          cls: 'badge err' },
  [STATES.PERMISSION]:{ label: 'permission denied',cls: 'badge err' },
  [STATES.CONFLICT]:  { label: 'conflict',         cls: 'badge err' },
  [STATES.RATE]:      { label: 'rate limited',     cls: 'badge err' },
  [STATES.RECOVERY]:  { label: 'recovery required',cls: 'badge err' },
};

/** Default per-request ceiling so a stalled engine surfaces as a timeout, not a hang. */
const REQUEST_TIMEOUT_MS = 20000;

/** Structured failure carrying the state, HTTP status, correlation id, and data-safety answer. */
class ApiError extends Error {
  constructor(message, { state, status = 0, correlationId = null, data = null, dataChanged }) {
    super(message);
    this.name = 'ApiError';
    this.state = state;
    this.status = status;
    this.correlationId = correlationId;
    this.data = data;
    this.dataChanged = dataChanged;
  }
  /** Human answer to "did my data change?" — required for every failure surface. */
  get impact() {
    switch (this.state) {
      case STATES.VALIDATION:
      case STATES.PERMISSION:
      case STATES.CONFLICT:
      case STATES.RATE:
        return 'No data was changed — the engine rejected the request.';
      case STATES.BACKEND:
        return 'The server reported an error; the outcome of this operation is unconfirmed. '
             + 'Reload the affected data before retrying.';
      case STATES.TIMEOUT:
        return 'The request timed out. It may still be running server-side — reload before retrying, '
             + 'because the write may have been applied.';
      case STATES.RECOVERY:
        return 'The server is recovering; writes are refused until recovery completes.';
      default:
        return 'The request never reached the engine.';
    }
  }
}

function stateForStatus(status) {
  if (status === 400 || status === 422) return STATES.VALIDATION;
  if (status === 401 || status === 403) return STATES.PERMISSION;
  if (status === 404) return STATES.VALIDATION;
  if (status === 409) return STATES.CONFLICT;
  if (status === 429) return STATES.RATE;
  if (status === 503) return STATES.RECOVERY;
  return STATES.BACKEND;
}

async function api(path, opts = {}) {
  const { timeoutMs = REQUEST_TIMEOUT_MS, signal, ...rest } = opts;
  const ctrl = new AbortController();
  const relayAbort = () => ctrl.abort();
  if (signal) {
    if (signal.aborted) ctrl.abort(); else signal.addEventListener('abort', relayAbort, { once: true });
  }
  let timedOut = false;
  const timer = timeoutMs > 0
    ? setTimeout(() => { timedOut = true; ctrl.abort(); }, timeoutMs)
    : null;

  let res;
  try {
    res = await fetch('/api' + path, {
      headers: { 'Content-Type': 'application/json' },
      ...rest,
      signal: ctrl.signal,
      body: rest.body != null ? JSON.stringify(rest.body) : undefined,
    });
  } catch (e) {
    // Distinguish a real timeout from an operator cancellation from a dead server:
    // each has a different data-safety answer, so they must not collapse into one error.
    if (timedOut) {
      throw new ApiError(`Timed out after ${Math.round(timeoutMs / 1000)}s waiting for the engine`,
        { state: STATES.TIMEOUT });
    }
    if (signal && signal.aborted) {
      throw new ApiError('Cancelled by user', { state: STATES.IDLE, dataChanged: false });
    }
    throw new ApiError('Cannot reach the JunifyDB server — is it still running?',
      { state: STATES.BACKEND, dataChanged: false });
  } finally {
    if (timer) clearTimeout(timer);
    if (signal) signal.removeEventListener('abort', relayAbort);
  }

  const correlationId = res.headers.get('X-Correlation-Id');
  let data = null;
  const text = await res.text();
  try { data = text ? JSON.parse(text) : null; } catch { data = { raw: text }; }
  if (!res.ok) {
    const msg = (data && (data.error || data.message)) || `HTTP ${res.status}`;
    const err = new ApiError(msg, {
      state: stateForStatus(res.status),
      status: res.status,
      correlationId: (data && data.correlationId) || correlationId,
      data,
      dataChanged: false,
    });
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
/**
 * Minimal text-input dialog (a styled stand-in for prompt(), which the CSP forbids
 * and whose look cannot be controlled). Resolves with the text, or null on cancel.
 */
function toast(msg, kind = 'info', ms = 3200) {
  const box = document.createElement('div');
  box.className = `toast ${kind}`;
  box.setAttribute('role', kind === 'err' ? 'alert' : 'status');
  box.innerHTML = `<span>${esc(msg)}</span><button class="x" aria-label="Dismiss">×</button>`;
  box.querySelector('.x').onclick = () => box.remove();
  $('#toasts').appendChild(box);
  if (ms) setTimeout(() => box.remove(), ms);
}

/** Applies a state to a badge element. One place, so no surface can invent its own wording. */
function setBadge(el, state, extra = '') {
  const ui = STATE_UI[state] || STATE_UI[STATES.IDLE];
  el.textContent = extra ? `${ui.label} · ${extra}` : ui.label;
  el.className = ui.cls;
  el.dataset.state = state;
  // CD-12: screen readers get an out-of-band announcement for every status
  // change (loading → success/error), not just the visual badge.
  announce(`${el.dataset.scope ? el.dataset.scope + ': ' : ''}${el.textContent}`);
}

/** Single polite live region (CD-12) — created lazily so it works on any page. */
function announce(msg) {
  let live = document.getElementById('liveRegion');
  if (!live) {
    live = document.createElement('div');
    live.id = 'liveRegion';
    live.className = 'sr-only';
    live.setAttribute('aria-live', 'polite');
    live.setAttribute('role', 'status');
    document.body.appendChild(live);
  }
  live.textContent = msg;
}

/**
 * Turns (value, isEmpty) into SUCCESS or EMPTY. Used so "0 rows" is never rendered
 * as a successful result — an empty answer is its own explicit state.
 */
function successOrEmpty(count) {
  return count === 0 ? STATES.EMPTY : STATES.SUCCESS;
}

/**
 * Renders a failure as a full explanation: what failed, why, whether data changed,
 * how to fix it, where to learn more, and the correlation id to quote in a bug report.
 */
function errorBanner(err, { title = 'Request failed', learnMore = '' } = {}) {
  const state = err.state || STATES.BACKEND;
  const ui = STATE_UI[state] || STATE_UI[STATES.BACKEND];
  const severity = state === STATES.TIMEOUT || state === STATES.BACKEND ? 'err' : 'warn';
  const id = err.correlationId
    ? `<div class="state-meta">Correlation ID: <code>${esc(err.correlationId)}</code>`
      + ' — quote this when reporting the problem.</div>'
    : '<div class="state-meta">No correlation ID was returned (the request may not have reached the server).</div>';
  return `<div class="state-banner ${severity}" role="alert">
    <div>
      <div class="state-title">✖ ${esc(title)} — ${esc(ui.label)}</div>
      <div style="margin-top:4px"><strong>What failed:</strong> ${esc(err.message)}</div>
      <div><strong>Did data change?</strong> ${esc(err.impact || 'Unknown — verify before retrying.')}</div>
      <div><strong>How to fix it:</strong> ${esc(fixHint(state))}</div>
      ${learnMore ? `<div class="state-meta">Learn more: ${learnMore}</div>` : ''}
      ${id}
    </div>
  </div>`;
}

function fixHint(state) {
  switch (state) {
    case STATES.VALIDATION: return 'Correct the highlighted input and submit again.';
    case STATES.PERMISSION: return 'Sign in again, or use an API key that is allowed to perform this operation.';
    case STATES.CONFLICT:  return 'Reload the resource, then reapply the change on the current version.';
    case STATES.RATE:      return 'Wait a moment (the server rate limit is per minute), then retry.';
    case STATES.TIMEOUT:   return 'Check the Server panel and the log, reload the data, then retry.';
    case STATES.RECOVERY:  return 'Wait for recovery to finish, then retry; the data directory is still intact.';
    default:               return 'Reload the data; if it persists, check the server log for this correlation ID.';
  }
}

/* ---------------- destructive-action confirmation ----------------
   A confirm() dialog cannot state a target or an impact, so destructive actions
   go through this dialog instead: it names what is about to change and quantifies
   the damage before anything is sent to the engine. */
function confirmAction({ title, target, impact, irrecoverable = true, confirmLabel = 'Confirm', hint = '' }) {
  return new Promise((resolve) => {
    const backdrop = $('#confirmBackdrop');
    const accept = $('#confirmAccept');
    const cancel = $('#confirmCancel');
    const previousFocus = document.activeElement;

    $('#confirmTitle').textContent = title;
    $('#confirmBody').innerHTML = `<dl>
        <dt>Target</dt><dd>${esc(target)}</dd>
        <dt>Impact</dt><dd>${esc(impact)}</dd>
        <dt>Undo</dt><dd>${irrecoverable
          ? 'Not reversible from the Console. Create a backup first if you need a way back.'
          : 'Reversible by restoring a backup from the Backup panel.'}</dd>
      </dl>`;
    $('#confirmHint').textContent = hint;
    accept.textContent = confirmLabel;
    backdrop.hidden = false;
    cancel.focus();

    const close = (answer) => {
      backdrop.hidden = true;
      accept.onclick = null;
      cancel.onclick = null;
      backdrop.onclick = null;
      document.removeEventListener('keydown', onKey);
      if (previousFocus && previousFocus.focus) previousFocus.focus();
      resolve(answer);
    };
    const onKey = (e) => { if (e.key === 'Escape') { e.preventDefault(); close(false); } };

    accept.onclick = () => close(true);
    cancel.onclick = () => close(false);
    backdrop.onclick = (e) => { if (e.target === backdrop) close(false); };
    document.addEventListener('keydown', onKey);
  });
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
    <tbody>${rows.map((r) => `<tr>${r.map((c) => String(c).trim().toLowerCase().startsWith('<td') ? c : `<td>${c}</td>`).join('')}</tr>`).join('')}</tbody></table>`;
}

/* ============================================================
   Router
   ============================================================ */
const ICONS = {
  overview: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="9" rx="1"/><rect x="14" y="3" width="7" height="5" rx="1"/><rect x="14" y="12" width="7" height="9" rx="1"/><rect x="3" y="16" width="7" height="5" rx="1"/></svg>',
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

/* Sidebar groups describe the one NoSQL product: data models first, then
   maintenance surfaces, then diagnostics. There is no engine split to show. */
const GROUPS = [
  { name: 'General',     items: ['overview'] },
  { name: 'Data',        items: ['collections', 'kv', 'columns', 'vectors'] },
  { name: 'Operations',  items: ['schema', 'tx', 'indexes', 'backup', 'cdc', 'audit'] },
  { name: 'Diagnostics', items: ['server'] },
];

let activePanel = 'overview';

function buildNav() {
  const nav = $('#nav');
  // Keep the search box; rebuild only the items.
  $$('.nav-group, .nav-item').forEach((el) => el.remove());
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
      b.dataset.label = p.label.toLowerCase();
      b.innerHTML = `${ICONS[p.id]}<span class="nav-label">${esc(p.label)}</span>${p.key ? `<span class="nav-key">${p.key}</span>` : ''}`;
      b.title = p.sub; // tooltip in rail / collapsed mode where the label is hidden
      b.setAttribute('aria-label', p.label);
      b.onclick = () => goto(p.id);
      nav.appendChild(b);
    }
  }
}

/** Sidebar filter: hides non-matching items and empty groups. */
function filterNav(query) {
  const q = query.trim().toLowerCase();
  $$('.nav-item').forEach((b) => {
    b.style.display = !q || b.dataset.label.includes(q) ? '' : 'none';
  });
  $$('.nav-group').forEach((gh) => {
    let el = gh.nextElementSibling;
    let any = false;
    while (el && !el.classList.contains('nav-group')) {
      if (el.classList.contains('nav-item') && el.style.display !== 'none') { any = true; break; }
      el = el.nextElementSibling;
    }
    gh.style.display = any || !q ? '' : 'none';
  });
}

const CRUMB_ROOT = 'Console';
function renderCrumbs(p) {
  const host = $('#crumbs');
  if (!host) return;
  const g = GROUPS.find((grp) => grp.items.includes(p.id));
  host.innerHTML = g
    ? `<span class="crumb-root">${esc(CRUMB_ROOT)}</span><span class="crumb-sep" aria-hidden="true">/</span><span aria-current="page">${esc(g.name)}</span><span class="crumb-sep" aria-hidden="true">/</span><span aria-current="page" class="crumb-here">${esc(p.label)}</span>`
    : `<span class="crumb-root">${esc(CRUMB_ROOT)}</span><span class="crumb-sep" aria-hidden="true">/</span><span aria-current="page">${esc(p.label)}</span>`;
}

function goto(id) {
  activePanel = id;
  const p = PANELS.find((x) => x.id === id);
  $$('.panel').forEach((s) => s.classList.remove('active'));
  $(`#panel-${id}`).classList.add('active');
  $$('.nav-item').forEach((b) => {
    const active = b.dataset.panel === id;
    b.classList.toggle('active', active);
    if (active) b.setAttribute('aria-current', 'page'); else b.removeAttribute('aria-current');
  });
  $('#panelTitle').textContent = p.label;
  $('#panelSub').textContent = p.sub;
  renderCrumbs(p);
  history.replaceState(null, '', '#' + id);
  refreshPanel(id);
  onFirstOpen(id);
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
    <div class="empty" style="padding:10px 0 0">Documents, key-value, wide-column, and vector data all live in this one embedded NoSQL engine.</div>`;

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
   Collections
   ============================================================ */
let colDocs = [];

/** Collection chips from the live engine, so the user never has to guess a name. */
async function loadColPicker() {
  const host = $('#colPicker');
  if (!host) return;
  try {
    const r = await api('/collections');
    const list = r.collections ?? [];
    host.innerHTML = list.length
      ? list.map((c) => `<button class="chip" aria-pressed="false" data-col="${esc(c.name)}" title="Load ${esc(c.name)} (${c.count} docs)">${esc(c.name)} <span class="muted">${c.count}</span></button>`).join('')
      : '<span class="hint">No collections yet — insert the first document below.</span>';
    $$('#colPicker .chip').forEach((b) => b.onclick = () => {
      $$('#colPicker .chip.active').forEach((x) => { x.classList.remove('active'); x.setAttribute('aria-pressed', 'false'); });
      b.classList.add('active');
      b.setAttribute('aria-pressed', 'true');
      $('#colSel').value = b.dataset.col;
      loadCollection().catch((e) => toast(e.message, 'err'));
    });
  } catch { host.innerHTML = '<span class="hint">Collection list unavailable.</span>'; }
}

async function loadCollection() {
  const name = $('#colSel').value.trim();
  if (!name) { toast('Enter a collection name', 'err'); return; }
  const docs = await api(`/collections/${encodeURIComponent(name)}`);
  colDocs = Array.isArray(docs) ? docs : [];
  const fields = collectFields(colDocs);
  $('#colCount').textContent = `${colDocs.length} docs`;
  setActiveContext(`collection ${name} · ${colDocs.length} doc${colDocs.length === 1 ? '' : 's'}`);
  $('#colTable').innerHTML = tbl(['id', ...fields, 'expires'], colDocs.map((d) =>
    [`<td><a href="#" class="mono" data-doc="${esc(d.id)}">${esc(d.id)}</a></td>`,
      ...fields.map((f) => { const v = d.fields?.[f]; return `<td>${esc(v == null ? '' : typeof v === 'object' ? JSON.stringify(v) : String(v))}</td>`; }),
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
    // The picker is the panel's source of truth for "what exists": refresh it
    // here too, or a collection created moments ago stays invisible until the
    // user notices the dedicated ⟳ button.
    loadColPicker();
    return;
  }
  await loadCollection();
}

function collectFields(docs) {
  const set = new Set();
  for (const d of docs) Object.keys(d.fields ?? {}).forEach((k) => set.add(k));
  return [...set].filter((f) => f !== '_entity').slice(0, 8);
}

let currentDoc = null;
let docViewMode = 'json';

function showDoc(d) {
  currentDoc = d;
  if (!d) {
    $('#docDetail').innerHTML = '<div class="empty">Document not found</div>';
    return;
  }
  const body = docViewMode === 'tree' ? jsonTreeHtml({ id: d.id, ...d.fields }) : `<pre class="code json" id="docJson"></pre>`;
  $('#docDetail').innerHTML = `${body}
    <div style="display:flex;gap:8px;margin-top:10px;flex-wrap:wrap">
      <button class="btn sm" id="docEdit">Load into editor</button>
      <button class="btn sm" id="docCopy">Copy JSON</button>
      <button class="btn sm danger" id="docDel">Delete</button>
    </div>`;
  if (docViewMode === 'json') renderJson($('#docJson'), d);
  $('#docEdit').onclick = () => {
    $('#insCol').value = $('#colSel').value;
    $('#insJson').value = JSON.stringify({ id: d.id, ...d.fields }, null, 2);
    $('#insCol').focus();
  };
  $('#docCopy').onclick = () => navigator.clipboard?.writeText(JSON.stringify(d, null, 2))
    .then(() => toast('Document JSON copied', 'ok'));
  $('#docDel').onclick = async () => {
    const ok = await confirmAction({
      title: 'Delete document',
      target: `document "${d.id}" in collection "${$('#colSel').value}" (Non-Relational NoSQL Engine)`,
      impact: 'Removes this document permanently. The collection and its other documents remain.',
      irrecoverable: true,
      confirmLabel: 'Delete document',
    });
    if (!ok) { toast('Deletion cancelled — nothing was changed', 'info'); return; }
    try {
      await api(`/collections/${encodeURIComponent($('#colSel').value)}/${encodeURIComponent(d.id)}`,
        { method: 'DELETE' });
      toast(`Deleted ${d.id}`, 'ok');
      loadCollection();
      $('#docDetail').innerHTML = '<span class="empty" style="padding:12px">Deleted.</span>';
    } catch (e) { toast(e.message, 'err'); }
  };
}

/** Renders nested values as an expandable tree (details/summary), not [object Object]. */
function jsonTreeHtml(value) {
  const node = (v, key) => {
    const k = key != null ? `<span class="jt-key">"${esc(key)}"</span>: ` : '';
    if (v === null) return `<li>${k}<span class="jt-null">null</span></li>`;
    if (Array.isArray(v)) {
      return `<li><details open><summary>${k}[${v.length}]</summary><ul>${v.map((x) => node(x)).join('')}</ul></details></li>`;
    }
    if (typeof v === 'object') {
      const entries = Object.entries(v);
      return `<li><details open><summary>${k}{${entries.length}}</summary><ul>${entries.map(([kk, vv]) => node(vv, kk)).join('')}</ul></details></li>`;
    }
    if (typeof v === 'number') return `<li>${k}<span class="jt-num">${esc(String(v))}</span></li>`;
    if (typeof v === 'boolean') return `<li>${k}<span class="jt-bool">${v}</span></li>`;
    return `<li>${k}<span class="jt-str">"${esc(String(v))}"</span></li>`;
  };
  return `<ul class="json-tree">${node(value)}</ul>`;
}

function setDocView(mode) {
  docViewMode = mode;
  $('#docViewJson').classList.toggle('active', mode === 'json');
  $('#docViewJson').setAttribute('aria-pressed', String(mode === 'json'));
  $('#docViewTree').classList.toggle('active', mode === 'tree');
  $('#docViewTree').setAttribute('aria-pressed', String(mode === 'tree'));
  if (currentDoc) showDoc(currentDoc);
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
    // A first insert materializes a brand-new collection: surface its chip now,
    // not after the next panel re-entry.
    loadColPicker();
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
  loadColPicker();
}

async function dropDocs() {
  const col = $('#colSel').value.trim();
  if (!col) { toast('Load a collection first', 'err'); return; }
  const docs = await api(`/collections/${encodeURIComponent(col)}`);
  const count = (docs ?? []).length;
  if (count === 0) {
    toast(`"${col}" is already empty — nothing to delete`, 'info');
    return;
  }
  // Name the target and quantify the damage before deleting anything.
  const ok = await confirmAction({
    title: 'Delete every document',
    target: `collection "${col}" (Non-Relational NoSQL Engine) — ${count} document${count === 1 ? '' : 's'}`,
    impact: `Permanently deletes all ${count} document${count === 1 ? '' : 's'} in "${col}". `
          + 'The collection itself remains and stays usable.',
    irrecoverable: true,
    confirmLabel: `Delete ${count} document${count === 1 ? '' : 's'}`,
    hint: 'Use the Backup panel first if you need a way back.',
  });
  if (!ok) { toast('Deletion cancelled — nothing was changed', 'info'); return; }

  const status = $('#colCount');
  let deleted = 0;
  const failures = [];
  for (const d of docs) {
    // Delete and re-verify each document, so partial failure is reported as partial
    // rather than as an unqualified success.
    try {
      await api(`/collections/${encodeURIComponent(col)}/${encodeURIComponent(d.id)}`, { method: 'DELETE' });
      deleted++;
      if (status) status.textContent = `deleting… ${deleted}/${count}`;
    } catch (e) {
      failures.push({ id: d.id, err: e });
    }
  }
  const remaining = await api(`/collections/${encodeURIComponent(col)}`).catch(() => null);
  const left = Array.isArray(remaining) ? remaining.length : null;

  if (failures.length) {
    $('#colTable').insertAdjacentHTML('afterbegin', errorBanner(failures[0].err, {
      title: `Deleted ${deleted} of ${count} documents — ${failures.length} failed`,
    }));
    toast(`Deleted ${deleted}/${count}; ${failures.length} failed`, 'err', 6000);
  } else {
    toast(`Deleted ${deleted} document${deleted === 1 ? '' : 's'} from "${col}"`
      + (left != null ? ` — ${left} remaining` : ''), 'ok');
  }
  await loadCollection().catch(() => {});
  loadColPicker();
}

/* ============================================================
   NoSQL query builder (structure → QueryParser JSON → /query)
   ============================================================ */
const QB_OPERATORS = ['$eq', '$ne', '$gt', '$gte', '$lt', '$lte', '$in', '$nin', '$regex', '$exists'];

function qbAddRow(field = '', op = '$eq', value = '') {
  const row = document.createElement('div');
  row.className = 'qb-row';
  row.innerHTML = `
    <div><label class="fld">Field</label><input type="text" class="qb-field" placeholder="price" value="${esc(field)}"></div>
    <div><label class="fld">Operator</label>
      <select class="qb-op">${QB_OPERATORS.map((o) => `<option${o === op ? ' selected' : ''}>${o}</option>`).join('')}</select></div>
    <div><label class="fld">Value (JSON — "text", 42, true, ["a","b"])</label><input type="text" class="qb-value" placeholder='e.g. 10 or "admin"'></div>
    <button class="btn sm danger qb-del" aria-label="Remove condition">×</button>`;
  row.querySelector('.qb-del').onclick = () => { row.remove(); qbPreviewUpdate(); };
  row.querySelector('.qb-value').addEventListener('input', qbPreviewUpdate);
  row.querySelector('.qb-field').addEventListener('input', qbPreviewUpdate);
  row.querySelector('.qb-op').addEventListener('change', qbPreviewUpdate);
  $('#qbRows').appendChild(row);
}

function qbParseValue(raw) {
  const s = raw.trim();
  if (s === '') return '';
  try { return JSON.parse(s); } catch { return s; /* bare words stay strings */ }
}

function qbBuildFilter() {
  const conds = [];
  $$('#qbRows .qb-row').forEach((row) => {
    const field = row.querySelector('.qb-field').value.trim();
    const op = row.querySelector('.qb-op').value;
    const value = qbParseValue(row.querySelector('.qb-value').value);
    if (!field) return;
    conds.push({ field, op, value });
  });
  if (!conds.length) return {};
  const filter = {};
  for (const c of conds) {
    if (c.op === '$exists') filter[c.field] = { $exists: c.value === '' ? true : !!c.value };
    else filter[c.field] = { [c.op]: c.value };
  }
  return filter;
}

function qbBuildPayload() {
  // sort/limit/offset travel as reserved body keys; the /query endpoint strips
  // them before parsing and applies them server-side via Query.sortBy/limit/offset.
  const payload = { ...qbBuildFilter() };
  const sortField = $('#qbSortField').value.trim();
  if (sortField) {
    payload.sortField = sortField;
    payload.sortDir = $('#qbSortDir').value === 'desc' ? 'desc' : 'asc';
  }
  const limit = Number($('#qbLimit').value);
  const offset = Number($('#qbOffset').value);
  if (limit > 0) payload.limit = limit;
  if (offset > 0) payload.offset = offset;
  return payload;
}

function qbPreviewUpdate() {
  renderJson($('#qbPreview'), qbBuildPayload());
  $('#qbPreview').title = 'the whole payload is sent to the engine; sortField/sortDir/limit/offset are applied server-side';
}

/* --- server-side pagination: limit/offset live in the payload; the pager
   steps the offset by one page and uses a limit+1 probe for has-more. --- */
function qbRenderPager(state) {
  const pager = $('#qbPager');
  if (!pager) return;
  const { shown, limit, offset, hasMore, total } = state;
  pager.hidden = !(limit > 0 && (offset > 0 || hasMore || total > limit));
  $('#qbPrev').disabled = offset <= 0;
  $('#qbNext').disabled = !hasMore;
  const from = total === 0 ? 0 : offset + 1;
  const to = offset + shown;
  $('#qbPageInfo').textContent = `rows ${from}–${to} of ${total}${hasMore ? '+' : ''} matched`
    + (hasMore ? ' — Next shows the following page' : '');
}

async function qbRun() {
  const col = $('#colSel').value.trim();
  const status = $('#qbStatus');
  if (!col) { toast('Load a collection first — the filter runs against it', 'err'); return; }
  const payload = qbBuildPayload();
  qbPreviewUpdate();
  status.textContent = 'running…';
  status.className = 'badge';
  try {
    const limit = payload.limit > 0 ? payload.limit : 0;
    const offset = payload.offset > 0 ? payload.offset : 0;
    // limit+1 probe: one extra row reveals hasMore without a count endpoint.
    const probe = limit > 0 ? { ...payload, limit: limit + 1 } : payload;
    const results = await api(`/collections/${encodeURIComponent(col)}/query`, { method: 'POST', body: probe });
    let rows = Array.isArray(results) ? results : [];
    const hasMore = limit > 0 && rows.length > limit;
    if (hasMore) rows = rows.slice(0, limit);
    // Rows actually seen up to this page; a trailing "+" marks an unknown total.
    const total = offset + rows.length;
    qbRenderPager({ shown: rows.length, limit, offset, hasMore, total });
    status.textContent = hasMore
      ? `${rows.length}+ match${rows.length === 1 ? '' : 'es'} (paged)`
      : `${rows.length} match${rows.length === 1 ? '' : 'es'}`;
    status.className = rows.length ? 'badge ok' : 'badge warn';
    setActiveContext(`query on ${col} · rows ${offset + 1}–${offset + rows.length}`);
    const fields = collectFields(rows.map((r) => ({ fields: r })));
    $('#qbOut').innerHTML = rows.length
      ? `<div class="tbl-wrap">${tbl(['id', ...fields], rows.map((r) =>
          [`<td><code>${esc(r.id ?? '')}</code></td>`, ...fields.map((f) => `<td>${esc(typeof r[f] === 'object' ? JSON.stringify(r[f]) : r[f] ?? '')}</td>`)]))}</div>`
      : `<div class="empty">No document matches this filter. Loosen an operator (try $gt instead of $gte) or clear a condition.</div>`;
  } catch (e) {
    status.textContent = 'failed';
    status.className = 'badge err';
    $('#qbOut').innerHTML = errorBanner(e, { title: 'Filter rejected' });
  }
}

/* ============================================================
   KV & Redis structures
   ============================================================ */
const KV_TABS = {
  kv: {
    title: 'Simple KV — bucket browser',
    form: `
      <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:flex-end">
        <div><label class="fld" for="kvBucket">Bucket</label><input type="text" id="kvBucket" value="sessions" style="width:150px"></div>
        <button class="btn sm" id="kvBrowse">Browse keys</button>
        <span class="badge" id="kvBrowseCount"></span>
        <div class="spacer"></div>
        <button class="btn sm" id="kvNewKey">+ New key</button>
      </div>
      <div style="margin-top:10px;display:flex;gap:8px;flex-wrap:wrap;align-items:flex-end">
        <div><label class="fld" for="kvPrefix">Key prefix filter</label><input type="text" id="kvPrefix" placeholder="e.g. user-" style="width:180px"></div>
        <button class="btn sm" id="kvPrefixGo">Apply</button>
      </div>
      <div style="margin-top:12px" id="kvBrowser" aria-live="polite"><div class="empty">Enter a bucket name and press <b>Browse keys</b> to list what the engine actually holds — keys, values, and TTL.</div></div>`,
  },
  list: {
    title: 'Lists',
    form: `
      <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:flex-end">
        <div><label class="fld" for="lsBucket">Bucket</label><input type="text" id="lsBucket" value="queue" style="width:130px"></div>
        <div><label class="fld" for="lsKey">Key</label><input type="text" id="lsKey" value="jobs" style="width:130px"></div>
        <div><label class="fld" for="lsVals">Values (comma-sep)</label><input type="text" id="lsVals" style="width:220px"></div>
        <button class="btn primary sm" id="lsPush">Push</button>
        <button class="btn sm" id="lsPop">Pop</button>
        <button class="btn sm" id="lsRange">Read all</button>
      </div><div style="margin-top:12px" id="kvOut"></div>`,
  },
  set: {
    title: 'Sets',
    form: `
      <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:flex-end">
        <div><label class="fld" for="stBucket">Bucket</label><input type="text" id="stBucket" value="tags" style="width:130px"></div>
        <div><label class="fld" for="stKey">Key</label><input type="text" id="stKey" style="width:130px"></div>
        <div><label class="fld" for="stVals">Members (comma-sep)</label><input type="text" id="stVals" style="width:220px"></div>
        <button class="btn primary sm" id="stAdd">Add</button>
        <button class="btn sm danger" id="stRem">Remove</button>
        <button class="btn sm" id="stMembers">Members</button>
      </div><div style="margin-top:12px" id="kvOut"></div>`,
  },
  hash: {
    title: 'Hashes',
    form: `
      <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:flex-end">
        <div><label class="fld" for="hsBucket">Bucket</label><input type="text" id="hsBucket" value="user" style="width:130px"></div>
        <div><label class="fld" for="hsKey">Key</label><input type="text" id="hsKey" style="width:130px"></div>
        <div><label class="fld" for="hsField">Field</label><input type="text" id="hsField" style="width:130px"></div>
        <div><label class="fld" for="hsVal">Value</label><input type="text" id="hsVal" style="width:180px"></div>
        <button class="btn primary sm" id="hsSet">HSet</button>
        <button class="btn sm" id="hsGet">HGet</button>
        <button class="btn sm" id="hsAll">HGetAll</button>
      </div><div style="margin-top:12px" id="kvOut"></div>`,
  },
};

let kvTab = 'kv';

/** Renders the bucket browser: every key the engine holds, with value + TTL. */
async function kvBrowse() {
  const bucketName = $('#kvBucket').value.trim();
  const host = $('#kvBrowser');
  if (!bucketName) { toast('Enter a bucket name to browse', 'err'); return; }
  host.innerHTML = '<div class="loading-row"><span class="spinner"></span>Loading keys…</div>';
  const prefix = $('#kvPrefix')?.value.trim() ?? '';
  try {
    const qs = prefix ? `?prefix=${encodeURIComponent(prefix)}` : '';
    const r = await api(`/kv-meta/${encodeURIComponent(bucketName)}${qs}`);
    const keys = r.keys ?? [];
    $('#kvBrowseCount').textContent = `${r.count} key${r.count === 1 ? '' : 's'}${prefix ? ` · prefix "${prefix}"` : ''}`;
    setActiveContext(`KV bucket ${bucketName} · ${r.count} key${r.count === 1 ? '' : 's'}`);
    if (!keys.length) {
      host.innerHTML = '<div class="empty"><svg class="empty-logo" aria-hidden="true"><use href="#brand-mark"/></svg><br>'
        + (prefix ? `No key in "${esc(bucketName)}" starts with "${esc(prefix)}".` : `Bucket "${esc(bucketName)}" is empty.`)
        + '<br>Create one with <b>+ New key</b>.</div>';
      return;
    }
    // Fetch values in bulk (bounded) so the table shows real content, not just names.
    const shown = keys.slice(0, 200);
    const rows = await Promise.all(shown.map(async (k) => {
      let value = '—';
      try { const v = await api(`/kv-meta/${encodeURIComponent(bucketName)}/${encodeURIComponent(k.key)}`); value = v.value; }
      catch { value = '(unreadable)'; }
      const ttl = k.hasTtl && k.expiresAt
        ? new Date(k.expiresAt).toLocaleString()
        : '';
      return [`<td><code>${esc(k.key)}</code></td>`,
        `<td>${esc(value == null ? '' : String(value).slice(0, 80))}</td>`,
        `<td>${ttl || '<span class="muted">no TTL</span>'}</td>`,
        `<td>${k.expired ? '<span class="badge err">expired</span>' : '<span class="badge ok">active</span>'}</td>`,
        `<td><button class="btn sm danger" data-kvdel="${esc(k.key)}" aria-label="Delete key ${esc(k.key)}">Delete</button></td>`];
    }));
    host.innerHTML = `<div class="tbl-wrap">${tbl(['Key', 'Value', 'Expires', 'State', ''], rows)}</div>`
      + (keys.length > shown.length ? `<div class="hint" style="margin-top:6px">Showing the first ${shown.length} of ${keys.length} keys — narrow the prefix filter to see more.</div>` : '');
    $$('#kvBrowser [data-kvdel]').forEach((b) => b.onclick = async () => {
      const ok = await confirmAction({
        title: 'Delete key',
        target: `key "${b.dataset.kvdel}" in bucket "${bucketName}" (Non-Relational NoSQL Engine)`,
        impact: 'Removes this key and its value. TTL metadata for the key is dropped with it.',
        irrecoverable: true,
        confirmLabel: 'Delete key',
      });
      if (!ok) { toast('Deletion cancelled — nothing was changed', 'info'); return; }
      try {
        await api(`/kv-meta/${encodeURIComponent(bucketName)}/${encodeURIComponent(b.dataset.kvdel)}`, { method: 'DELETE' });
        toast(`Deleted ${b.dataset.kvdel}`, 'ok');
        kvBrowse();
      } catch (e) { toast(e.message, 'err'); }
    });
  } catch (e) {
    host.innerHTML = errorBanner(e, { title: `Could not browse bucket "${bucketName}"` });
  }
}

/** New-key form (inline, replaces the browser until saved). */
function kvNewKeyForm() {
  const host = $('#kvBrowser');
  host.innerHTML = `<div class="card-pad" style="border:1px dashed var(--line-1);border-radius:8px">
    <label class="fld" for="kvNewName">Key name</label>
    <input type="text" id="kvNewName" placeholder="e.g. user-1042">
    <label class="fld" style="margin-top:10px" for="kvNewValue">Value</label>
    <input type="text" id="kvNewValue" placeholder="value">
    <label class="fld" style="margin-top:10px" for="kvNewTtl">TTL (seconds — leave blank for no expiration)</label>
    <input type="number" id="kvNewTtl" min="1" placeholder="3600" style="width:160px">
    <div style="display:flex;gap:8px;margin-top:12px">
      <button class="btn primary sm" id="kvNewSave">Save key</button>
      <button class="btn sm" id="kvNewCancel">Cancel</button>
      <span class="badge" id="kvNewStatus"></span>
    </div></div>`;
  $('#kvNewCancel').onclick = () => kvBrowse();
  $('#kvNewSave').onclick = async () => {
    const name = $('#kvNewName').value.trim();
    const bucketName = $('#kvBucket').value.trim();
    const status = $('#kvNewStatus');
    if (!name) { status.textContent = 'key name required'; status.className = 'badge err'; return; }
    const body = { value: $('#kvNewValue').value };
    const ttlRaw = $('#kvNewTtl').value.trim();
    if (ttlRaw) body.ttlSeconds = Number(ttlRaw);
    try {
      await api(`/kv-meta/${encodeURIComponent(bucketName)}/${encodeURIComponent(name)}`, { method: 'PUT', body });
      status.textContent = `saved ${name}`; status.className = 'badge ok';
      toast(`Key ${name} written to ${bucketName}`, 'ok');
      setTimeout(() => kvBrowse(), 400);
    } catch (e) {
      status.textContent = e.message; status.className = 'badge err';
    }
  };
}

function bindKvTab() {
  const spec = KV_TABS[kvTab];
  $('#kvHead').textContent = spec.title;
  $('#kvBody').innerHTML = spec.form;
  const out = () => $('#kvOut');
  const show = (v) => renderJson(out(), v);
  $$('#kvTabs .tab').forEach((t) => t.setAttribute('aria-selected', String(t.dataset.kvtab === kvTab)));

  if (kvTab === 'kv') {
    $('#kvBrowse').onclick = () => kvBrowse().catch((e) => toast(e.message, 'err'));
    $('#kvPrefixGo').onclick = () => kvBrowse().catch((e) => toast(e.message, 'err'));
    $('#kvPrefix').addEventListener('keydown', (e) => e.key === 'Enter' && kvBrowse().catch((e) => toast(e.message, 'err')));
    $('#kvBucket').addEventListener('keydown', (e) => e.key === 'Enter' && kvBrowse().catch((e) => toast(e.message, 'err')));
    $('#kvNewKey').onclick = () => kvNewKeyForm();
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
  setActiveContext(`indexes on ${$('#idxCol').value.trim()}`);
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
  const file = $('#bkFile').value.trim();
  if (!file) { toast('Enter the path of the backup file to restore', 'err'); return; }
  // Restoring overwrites live data, so it gets the same target + impact treatment
  // as any other destructive action — this one is named for the whole database.
  const ok = await confirmAction({
    title: 'Restore from backup',
    target: `database (Both Engines) from file "${file}"`,
    impact: 'Restores the snapshot over the current data. Documents written since the snapshot may be replaced or lost.',
    irrecoverable: true,
    confirmLabel: 'Restore now',
    hint: 'Create a fresh backup of the current state first if you may need it.',
  });
  if (!ok) { toast('Restore cancelled — nothing was changed', 'info'); return; }

  $('#bkStatus').className = 'badge';
  $('#bkStatus').textContent = 'restoring…';
  try {
    const r = await api('/backup/restore', { method: 'POST', body: { backupFile: file } });
    $('#bkStatus').className = 'badge ok';
    $('#bkStatus').textContent = `restored from ${r.file ?? 'file'}`;
    toast('Restore complete', 'ok');
    pollStatus();
    bkRefresh();
  } catch (e) {
    $('#bkStatus').className = 'badge err';
    $('#bkStatus').textContent = 'restore failed';
    $('#bkInfo').insertAdjacentHTML('afterbegin', errorBanner(e, { title: 'Restore failed' }));
  }
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

/* ---------- status bar: orientation context that is always visible ---------- */
let activeContextLabel = 'none';

/** Records what the user is currently looking at (collection / schema / index). */
function setActiveContext(label) {
  activeContextLabel = label || 'none';
  const el = $('#sbContext');
  if (el) el.textContent = 'context: ' + activeContextLabel;
}

function sbItem(id, label, value, cls = '', title = '') {
  const el = $('#' + id);
  if (!el) return;
  el.className = 'sb-item' + (cls ? ' ' + cls : '');
  el.innerHTML = `${esc(label)} <b>${esc(value)}</b>`;
  el.title = title || '';
}

/**
 * Single reader for /api/health: drives the topbar chips AND the status bar, so the
 * two can never disagree about engine, storage, or connection state.
 */
async function pollStatus() {
  let h = null;
  try {
    h = await api('/health');
  } catch (e) {
    const chip = $('#chipHealth');
    chip.className = 'topbar-chip err';
    $('#chipHealthText').textContent = e?.status === 401 ? 'Sign-in required' : 'Unreachable';
    sbItem('sbConnection', 'connection', 'unreachable', 'err');
    sbItem('sbTx', 'tx', 'unknown');
    sbItem('sbUser', 'user', 'unknown');
    return;
  }

  const chip = $('#chipHealth');
  chip.className = 'topbar-chip ok';
  $('#chipHealthText').textContent = h.status === 'ok' ? 'Healthy' : 'Degraded';
  $('#chipEngine').textContent = h.engine ?? '—';
  $('#chipUptime').textContent = 'up ' + fmtUptime(h.uptime ?? 0);
  const versionChip = $('#chipVersion');
  if (versionChip) versionChip.textContent = 'v' + (h.version ?? '?');

  const c = h.context || {};
  sbItem('sbEngine', 'engine', c.engine ?? h.engine ?? '—', 'ok',
    'NoSQL embedded engine');
  sbItem('sbStorage', 'storage', `${c.storageMode ?? '—'} · ${c.durability ?? 'durability unknown'}`,
    c.storageMode === 'in-memory' ? 'warn' : '', c.durability || '');
  sbItem('sbDatabase', 'database', c.database ?? '—', '',
    c.dataDir ? 'data dir: ' + c.dataDir : 'in-memory — nothing is written to disk');
  sbItem('sbConnection', 'connection', h.open ? 'online' : 'offline', h.open ? 'ok' : 'err');
  const tx = c.activeTransactions ?? 0;
  sbItem('sbTx', 'tx',
    tx ? `${tx} active · console writes bypass tx` : 'none active · console writes bypass tx',
    tx ? 'warn' : '', c.transactionScope || '');
  sbItem('sbUser', 'user',
    `${c.user ?? 'anonymous'}${c.authEnabled ? '' : ' (auth disabled)'}`,
    c.authEnabled ? 'ok' : 'warn',
    c.authEnabled ? 'Authenticated session' : 'Authentication is disabled on this server');
  setActiveContext(activeContextLabel);
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

/** Panels that need a data refresh when first opened get it from here. */
function onFirstOpen(id) {
  if (id === 'collections') loadColPicker();
  if (id === 'backup' || id === 'overview') loadStorageStatus().catch(() => {});
}

/* --- storage/WAL status (Overview detail card) --- */
let lastStorageStatus = null;
async function loadStorageStatus() {
  const host = $('#ovStorage');
  if (!host) return;
  try {
    lastStorageStatus = await api('/storage/status');
    const s = lastStorageStatus;
    const wal = s.wal ?? {};
    const disk = s.disk ?? {};
    host.innerHTML = `
      <div class="grid kpi">
        ${setKpi('Engine', esc(s.engine ?? '—'))}
        ${setKpi('Storage', esc(s.storageMode ?? '—'))}
        ${setKpi('WAL', wal.present ? `${wal.fileCount ?? 0} file${(wal.fileCount ?? 0) === 1 ? '' : 's'}` : (s.walSupported ? 'no WAL dir yet' : 'not applicable'), wal.present ? '' : 'warn')}
        ${setKpi('Data on disk', disk.exists ? fmtBytes(disk.totalBytes) : '—')}
        ${setKpi('Files', disk.exists ? (disk.fileCount ?? 0) : '—')}
        ${setKpi('Backups', String(s.backupCount ?? 0), (s.backupCount ?? 0) === 0 ? 'warn' : '')}
      </div>
      <div class="muted" style="margin-top:8px;font-size:12px">${esc(s.durability ?? '')}</div>`;
  } catch (e) {
    host.innerHTML = errorBanner(e, { title: 'Storage status unavailable' });
  }
}

function refreshPanel(id) { REFRESH[id]?.().catch((e) => toast(e.message, 'err')); }

/* ============================================================
   Boot
   ============================================================ */
function bind() {
  // global nav
  $$('[data-goto]').forEach((b) => b.onclick = () => goto(b.dataset.goto));

  // topbar
  $('#btnTheme').onclick = () => setTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark');
  $('#btnRefreshAll').onclick = () => { refreshPanel(activePanel); pollStatus(); };
  $('#btnHelp').onclick = () => openHelp();
  $('#helpClose').onclick = () => { $('#helpBackdrop').hidden = true; $('#btnHelp').focus(); };
  $('#helpBackdrop').onclick = (e) => { if (e.target === $('#helpBackdrop')) { $('#helpBackdrop').hidden = true; $('#btnHelp').focus(); } };
  $('#navSearch').addEventListener('input', (e) => filterNav(e.target.value));
  $('#navSearch').addEventListener('keydown', (e) => {
    if (e.key === 'Escape') { e.target.value = ''; filterNav(''); e.target.blur(); }
  });
  $('#btnLogout').onclick = async () => {
    try { await api('/auth/logout', { method: 'POST' }); } catch { /* best-effort */ }
    try { localStorage.removeItem('apiKey'); sessionStorage.clear(); } catch { /* ignore */ }
    const next = encodeURIComponent('/#');
    window.location.href = '/login.html?next=' + next;
  };

  // collections
  $('#colLoad').onclick = () => loadCollection().catch((e) => toast(e.message, 'err'));
  $('#colRefresh').onclick = () => loadCollection().catch((e) => toast(e.message, 'err'));
  $('#colPickerRefresh').onclick = () => loadColPicker();
  $('#colNew').onclick = () => { $('#insCol').focus(); goto('collections'); };
  $('#colCleanup').onclick = () => cleanupExpired().catch((e) => toast(e.message, 'err'));
  $('#colDrop').onclick = () => dropDocs().catch((e) => toast(e.message, 'err'));
  $('#insSubmit').onclick = () => insertDoc();
  $('#colSel').addEventListener('keydown', (e) => e.key === 'Enter' && loadCollection());
  $('#docViewJson').onclick = () => setDocView('json');
  $('#docViewTree').onclick = () => setDocView('tree');

  // query builder
  $('#qbAddRow').onclick = () => { qbAddRow(); qbPreviewUpdate(); };
  $('#qbClear').onclick = () => { $('#qbRows').innerHTML = ''; qbAddRow(); qbPreviewUpdate(); $('#qbOut').innerHTML = ''; const p = $('#qbPager'); if (p) p.hidden = true; setBadge($('#qbStatus'), STATES.IDLE); };
  $('#qbRun').onclick = () => qbRun().catch((e) => toast(e.message, 'err'));
  $('#qbSortField').addEventListener('input', qbPreviewUpdate);
  $('#qbSortDir').addEventListener('change', qbPreviewUpdate);
  $('#qbLimit').addEventListener('input', qbPreviewUpdate);
  $('#qbPrev').onclick = () => {
    const off = Number($('#qbOffset').value) || 0;
    const lim = Number($('#qbLimit').value) || 100;
    $('#qbOffset').value = Math.max(0, off - lim);
    $('#qbOffset').dispatchEvent(new Event('input', { bubbles: true }));
    qbRun().catch((e) => toast(e.message, 'err'));
  };
  $('#qbNext').onclick = () => {
    const lim = Number($('#qbLimit').value) || 100;
    $('#qbOffset').value = (Number($('#qbOffset').value) || 0) + lim;
    $('#qbOffset').dispatchEvent(new Event('input', { bubbles: true }));
    qbRun().catch((e) => toast(e.message, 'err'));
  };
  $('#qbOffset').addEventListener('input', qbPreviewUpdate);
  qbAddRow();
  qbPreviewUpdate();

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

  // storage & WAL card on Overview
  $('#ovStorageRefresh').onclick = () => loadStorageStatus().catch((e) => toast(e.message, 'err'));

  // keyboard shortcuts: 1-9/0 to switch panels (when not typing)
  document.addEventListener('keydown', (e) => {
    const tag = document.activeElement?.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || e.ctrlKey || e.metaKey || e.altKey) return;
    const p = PANELS.find((x) => x.key === e.key);
    if (p) goto(p.id);
  });
}

function openHelp() {
  $('#helpVersion').textContent = `JunifyDB Console · server version ${$('#chipVersion')?.textContent || 'unknown'}`;
  $('#helpBackdrop').hidden = false;
  $('#helpClose').focus();
  document.addEventListener('keydown', helpEscape);
}
function helpEscape(e) {
  if (e.key === 'Escape') {
    $('#helpBackdrop').hidden = true;
    document.removeEventListener('keydown', helpEscape);
    $('#btnHelp').focus();
  }
}

function boot() {
  buildNav();
  initTheme();
  bind();
  bindKvTab();
  // Deep-link support: honor #panel on load and keep browser
  // back/forward navigation in sync with the active panel.
  const hash = location.hash.slice(1);
  goto(PANELS.some((p) => p.id === hash) ? hash : 'overview');
  window.addEventListener('hashchange', () => {
    const id = location.hash.slice(1);
    if (PANELS.some((p) => p.id === id) && id !== activePanel) goto(id);
  });
  pollStatus();
  setInterval(pollStatus, 10000);
  setInterval(() => { if (activePanel === 'overview') refreshOverview().catch(() => {}); }, 5000);
}

boot();
