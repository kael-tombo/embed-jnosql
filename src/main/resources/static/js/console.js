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

/** The statement currently in flight, so it can be cancelled by the user. */
let sqlController = null;
/** Last result set, kept so it can be exported without re-running the query. */
let lastSqlResult = null;

/**
 * Classifies a statement as destructive and explains why, or returns null.
 * This guards the two ways a console query silently destroys data: a DROP, and a
 * DELETE/UPDATE with no WHERE clause (which reads as a filter to a tired human).
 */
function destructiveReason(sql) {
  const stripped = sql.replace(/--[^\n]*/g, ' ').replace(/\/\*[\s\S]*?\*\//g, ' ').trim();
  const upper = stripped.toUpperCase();
  // Keyword detection runs on the uppercased copy; the target is read from the
  // original so the dialog echoes the user's own spelling, not a shouting version.
  if (/^\s*(DROP)\b/.test(upper)) {
    const m = stripped.match(/^\s*DROP\s+(TABLE|INDEX)\s+(IF\s+EXISTS\s+)?([\w."]+)/i);
    return { kind: 'DROP', target: m ? m[3] : 'object named in the statement',
             impact: 'Drops the object and its contents. Rows are gone permanently.' };
  }
  const write = upper.match(/^\s*(DELETE|UPDATE)\b/);
  if (write) {
    const hasWhere = /\bWHERE\b/.test(upper);
    const target = (stripped.match(/^\s*(?:DELETE\s+FROM|UPDATE)\s+([\w."]+)/i) || [])[1]
      || 'table in the statement';
    if (!hasWhere) {
      return { kind: write[1], target,
               impact: `Applies to EVERY row in ${target} — the statement has no WHERE clause.` };
    }
    return { kind: write[1], target,
             impact: `Modifies every row in ${target} that matches the WHERE clause. Run it as a SELECT first if you want to preview the rows.` };
  }
  return null;
}

/** Live warning badge: the user sees the danger before pressing Run. */
function refreshDestructiveBadge() {
  const badge = $('#sqlDestructive');
  const reason = destructiveReason($('#sqlInput').value);
  if (!reason) { badge.hidden = true; badge.textContent = ''; return; }
  badge.hidden = false;
  badge.className = 'badge err';
  badge.textContent = `⚠ destructive ${reason.kind}`;
  badge.title = reason.impact;
}

async function runSql(onlySelection = false) {
  const input = $('#sqlInput');
  const status = $('#sqlStatus');
  const selected = onlySelection ? input.value.slice(input.selectionStart, input.selectionEnd).trim() : '';
  const sql = (selected || input.value).trim();

  if (!sql) {
    setBadge(status, STATES.VALIDATION, 'empty statement');
    $('#sqlOut').innerHTML = '<div class="state-banner warn" role="alert"><div>'
      + '<div class="state-title">✖ Nothing to run — validation error</div>'
      + '<div><strong>What failed:</strong> the editor is empty.</div>'
      + '<div><strong>Did data change?</strong> No data was changed — no statement was sent.</div>'
      + '<div><strong>How to fix it:</strong> type a statement, or pick one from the history buttons below.</div>'
      + '</div></div>';
    return;
  }

  const reason = destructiveReason(sql);
  if (reason) {
    const ok = await confirmAction({
      title: `Confirm destructive ${reason.kind}`,
      target: `${reason.target} (Relational SQL Engine)`,
      impact: reason.impact,
      confirmLabel: `Run ${reason.kind}`,
      hint: 'Tip: run the equivalent SELECT first to see exactly which rows you are about to change.',
    });
    if (!ok) {
      setBadge(status, STATES.IDLE, 'cancelled by user');
      return;
    }
  }

  sqlController = new AbortController();
  $('#sqlCancel').hidden = false;
  setBadge(status, STATES.LOADING);
  $('#sqlMeta').textContent = '';
  try {
    const t0 = performance.now();
    const res = await api('/sql', { method: 'POST', body: { query: sql }, signal: sqlController.signal });
    const ms = Math.max(1, Math.round(performance.now() - t0));
    lastSqlResult = res;
    const rows = res.rowCount ?? (res.rows || []).length;
    const hasColumns = !!(res.columns && res.columns.length);
    // A statement that returns no result set is not "empty" — only a 0-row result set is.
    const state = hasColumns ? successOrEmpty(rows) : STATES.SUCCESS;
    setBadge(status, state, `${ms} ms`);
    $('#sqlMeta').textContent = `${rows} row${rows === 1 ? '' : 's'} · server ${res.executionTimeMs} ms`;
    setActiveContext(hasColumns ? `SQL result · ${rows} row${rows === 1 ? '' : 's'}` : 'SQL statement (no result set)');
    $('#sqlExportCsv').hidden = !hasColumns || rows === 0;
    $('#sqlExportJson').hidden = rows === 0 && !hasColumns;
    renderSqlResult(res);
    saveHistory(sql);
    renderHistory();
  } catch (e) {
    lastSqlResult = null;
    $('#sqlExportCsv').hidden = true;
    $('#sqlExportJson').hidden = true;
    if (e.state === STATES.IDLE) {
      setBadge(status, STATES.IDLE, 'cancelled');
      $('#sqlMeta').textContent = '';
      $('#sqlOut').innerHTML = '<div class="state-banner info"><div>'
        + '<div class="state-title">Query cancelled</div>'
        + '<div><strong>Did data change?</strong> The client stopped waiting; a statement already '
        + 'executing server-side may still complete. Reload the affected data to confirm.</div>'
        + '</div></div>';
      return;
    }
    setBadge(status, e.state || STATES.BACKEND);
    $('#sqlOut').innerHTML = errorBanner(e, {
      title: 'SQL statement failed',
      learnMore: 'the built-in dialect supports SELECT · INSERT · UPDATE · DELETE · JOIN · GROUP BY · '
        + 'CREATE/DROP TABLE with PRIMARY KEY · UNIQUE · NOT NULL · FOREIGN KEY · CHECK. Views, sequences, '
        + 'stored procedures, triggers, functions, and CREATE INDEX are not supported.',
    });
    if (e.data && e.data.message && e.data.message !== e.message) {
      $('#sqlOut').insertAdjacentHTML('beforeend',
        `<div class="state-banner info" style="margin-top:8px"><div><div class="state-title">Engine detail</div>${esc(e.data.message)}</div></div>`);
    }
  } finally {
    sqlController = null;
    $('#sqlCancel').hidden = true;
  }
}

function renderSqlResult(res) {
  if (!res.columns || !res.columns.length) {
    $('#sqlOut').innerHTML = '<div class="state-banner ok"><div>'
      + '<div class="state-title">✔ Statement executed</div>'
      + '<div>No result set was returned (this is a write or a DDL statement). '
      + 'Check the Audit Trail panel for the recorded change.</div></div></div>';
    return;
  }
  if (!res.rows || res.rows.length === 0) {
    $('#sqlOut').innerHTML = '<div class="state-banner warn"><div>'
      + '<div class="state-title">Result set is empty</div>'
      + `<div>The statement ran successfully but matched no rows, so there is nothing to show or export. `
      + `Columns requested: <code>${esc(res.columns.join(', '))}</code>.</div></div></div>`;
    return;
  }
  $('#sqlOut').innerHTML = `<div class="tbl-wrap">${tbl(res.columns,
    res.rows.map((r) => res.columns.map((c) => esc(r[c] ?? 'NULL'))))}</div>`;
}

/* --- result export (from the last result set, no re-run) --- */
function csvCell(v) {
  if (v == null) return '';
  const s = typeof v === 'object' ? JSON.stringify(v) : String(v);
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}
function downloadText(filename, text, mime) {
  const blob = new Blob([text], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
function exportSql(format) {
  if (!lastSqlResult) { toast('Run a query first — there is no result to export', 'err'); return; }
  const cols = lastSqlResult.columns || [];
  const rows = lastSqlResult.rows || [];
  if (!rows.length && !cols.length) { toast('Statement produced no result set to export', 'err'); return; }
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  if (format === 'csv') {
    if (!cols.length) { toast('Nothing to export as CSV — the statement returned no columns', 'err'); return; }
    const text = [cols.map(csvCell).join(','),
      ...rows.map((r) => cols.map((c) => csvCell(r[c])).join(','))].join('\n');
    downloadText(`junifydb-result-${stamp}.csv`, text, 'text/csv');
  } else {
    downloadText(`junifydb-result-${stamp}.json`, JSON.stringify(rows, null, 2), 'application/json');
  }
  toast(`Exported ${rows.length} row${rows.length === 1 ? '' : 's'} as ${format.toUpperCase()}`, 'ok');
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
  // Never leave the example strip blank: history first, then the built-in examples,
  // so a first-time user always has something clickable to run.
  const items = h.length ? h.slice(0, 6) : SQL_EXAMPLES;
  const label = h.length ? '' : '<span class="hint" style="align-self:center">Try one:</span>';
  $('#sqlExamples').innerHTML = label + items.map((s) =>
    `<button class="btn sm" data-sql="${esc(s)}" title="${esc(s)}">${esc(s.length > 46 ? s.slice(0, 46) + '…' : s)}</button>`).join('');
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
  setActiveContext(`collection ${name} · ${colDocs.length} doc${colDocs.length === 1 ? '' : 's'}`);
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

  const c = h.context || {};
  sbItem('sbEngine', 'engine', c.engine ?? h.engine ?? '—', 'ok',
    c.relationalEngine && c.nosqlEngine
      ? `${c.relationalEngine} + ${c.nosqlEngine}` : '');
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
  $('#btnLogout').onclick = async () => {
    try { await api('/auth/logout', { method: 'POST' }); } catch { /* best-effort */ }
    try { localStorage.removeItem('apiKey'); sessionStorage.clear(); } catch { /* ignore */ }
    const next = encodeURIComponent('/#');
    window.location.href = '/login.html?next=' + next;
  };

  // SQL
  $('#sqlRun').onclick = () => runSql();
  $('#sqlRunSel').onclick = () => runSql(true);
  $('#sqlCancel').onclick = () => { if (sqlController) sqlController.abort(); };
  $('#sqlExportCsv').onclick = () => exportSql('csv');
  $('#sqlExportJson').onclick = () => exportSql('json');
  $('#sqlClear').onclick = () => {
    $('#sqlInput').value = '';
    lastSqlResult = null;
    $('#sqlExportCsv').hidden = true;
    $('#sqlExportJson').hidden = true;
    $('#sqlDestructive').hidden = true;
    setBadge($('#sqlStatus'), STATES.IDLE);
    $('#sqlOut').innerHTML = '<div class="empty"><svg class="empty-logo" aria-hidden="true"><use href="#brand-mark"/></svg><br>Editor cleared — write SQL above and press <kbd>Ctrl</kbd>+<kbd>Enter</kbd>.</div>';
    $('#sqlMeta').textContent = '';
    $('#sqlInput').focus();
  };
  $('#sqlCopy').onclick = () => navigator.clipboard?.writeText($('#sqlInput').value).then(() => toast('SQL copied', 'ok'));
  // Warn as soon as a dangerous statement is typed, not only when Run is pressed.
  $('#sqlInput').addEventListener('input', refreshDestructiveBadge);
  $('#sqlInput').addEventListener('keydown', (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault();
      runSql(e.shiftKey);
    }
  });
  $('#sqlExamples').addEventListener('click', (e) => {
    const b = e.target.closest('[data-sql]');
    if (b) { $('#sqlInput').value = b.dataset.sql; refreshDestructiveBadge(); runSql(); }
  });
  setBadge($('#sqlStatus'), STATES.IDLE);

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
  pollStatus();
  setInterval(pollStatus, 10000);
  setInterval(() => { if (activePanel === 'overview') refreshOverview().catch(() => {}); }, 5000);
}

boot();
