import { useCallback, useEffect, useState } from 'react';
import {
  createClient,
  fetchClients,
  fetchStats,
  fetchUsage,
  updateClient,
} from './api.js';

// The admin key is held in React state only, never in localStorage or
// sessionStorage: it is a privileged credential, so it lives only for the life of
// the tab and must be re-entered after a refresh.
const REFRESH_MS = 10000;

const int = new Intl.NumberFormat('en-US');
const money = (value) => `$${Number(value ?? 0).toFixed(4)}`;

function cacheHitRate(stats) {
  const total = (stats.cache_hits ?? 0) + (stats.cache_misses ?? 0);
  if (total === 0) {
    return '—';
  }
  return `${(((stats.cache_hits ?? 0) / total) * 100).toFixed(1)}%`;
}

// Per-client spend against the hard budget cap. No cap => no meter. Same
// underlying data as before (spend vs budget); the extra fields are only for
// rendering it as a progress bar: `pct` is clamped to [0,100] for the fill width
// while `text` keeps the true percentage, and `level` picks the fill color.
function budgetUse(client) {
  if (client.budget == null) {
    return { has: false, text: '—', over: false, pct: 0, level: 'accent' };
  }
  const spend = Number(client.usage?.total_cost ?? 0);
  const cap = Number(client.budget);
  const rawPct = cap > 0 ? (spend / cap) * 100 : 100;
  const over = spend >= cap;
  const level = over ? 'red' : rawPct >= 80 ? 'amber' : 'accent';
  return {
    has: true,
    text: `${rawPct.toFixed(0)}%`,
    over,
    pct: Math.max(0, Math.min(100, rawPct)),
    level,
  };
}

// Validate an optional positive-integer rate limit from a text field.
function parseRateLimit(text) {
  if (text.trim() === '') {
    return { value: null };
  }
  const n = Number(text);
  if (!Number.isInteger(n) || n <= 0) {
    return { error: 'Rate limit must be a positive whole number.' };
  }
  return { value: n };
}

// Validate an optional non-negative budget from a text field (kept as a string so
// the decimal is sent to the backend exactly as typed).
function parseBudget(text) {
  if (text.trim() === '') {
    return { value: null };
  }
  const n = Number(text);
  if (Number.isNaN(n) || n < 0) {
    return { error: 'Budget must be a number that is zero or greater.' };
  }
  return { value: text.trim() };
}

function readableError(e) {
  return e.message === 'unauthorized' ? 'Admin key rejected.' : e.message;
}

// A KPI/latency card. `tone` colors the top accent bar (and the latency dot);
// `warn` colors the hero number when a metric is in an alert state. Purely
// presentational: the value passed in already comes from the real API.
function Gauge({ label, value, sub, tone = 'accent', dot = false, warn = false }) {
  return (
    <div className={`card kpi tone-${tone}`}>
      <div className="kpi-label">
        {dot ? <span className="kpi-dot" aria-hidden="true" /> : null}
        {label}
      </div>
      <div className={warn ? 'kpi-value warn' : 'kpi-value'}>{value}</div>
      {sub ? <div className="kpi-sub">{sub}</div> : null}
    </div>
  );
}

// One-time reveal of a freshly created client's raw key. The key lives only in
// this component's props (transient React state in the parent); it is never
// written to storage or logged, and it vanishes on dismiss or refresh.
function KeyReveal({ reveal, onDismiss }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(reveal.apiKey);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };
  return (
    <div className="keyreveal">
      <div className="keyreveal-head">
        Client <strong>{reveal.name}</strong> created. Copy this key now. You will not be able to see it
        again.
      </div>
      <div className="keyreveal-row">
        <code className="keyreveal-key">{reveal.apiKey}</code>
        <button type="button" onClick={copy}>{copied ? 'Copied' : 'Copy'}</button>
        <button type="button" className="ghost" onClick={onDismiss}>Done</button>
      </div>
    </div>
  );
}

function CreateClientForm({ onCreate }) {
  const [name, setName] = useState('');
  const [rate, setRate] = useState('');
  const [budget, setBudget] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    setError(null);
    if (name.trim() === '') {
      setError('Name is required.');
      return;
    }
    const rateResult = parseRateLimit(rate);
    if (rateResult.error) {
      setError(rateResult.error);
      return;
    }
    const budgetResult = parseBudget(budget);
    if (budgetResult.error) {
      setError(budgetResult.error);
      return;
    }
    const body = { name: name.trim() };
    if (rateResult.value != null) {
      body.rate_limit = rateResult.value;
    }
    if (budgetResult.value != null) {
      body.budget = budgetResult.value;
    }
    setBusy(true);
    try {
      await onCreate(body);
      setName('');
      setRate('');
      setBudget('');
    } catch (e) {
      setError(readableError(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="panel form">
      <div className="form-row">
        <input aria-label="Client name" placeholder="client name" value={name}
          onChange={(e) => setName(e.target.value)} />
        <input aria-label="Rate limit" placeholder="rate limit (optional)" inputMode="numeric" value={rate}
          onChange={(e) => setRate(e.target.value)} />
        <input aria-label="Budget" placeholder="budget (optional)" inputMode="decimal" value={budget}
          onChange={(e) => setBudget(e.target.value)} />
        <button type="button" onClick={submit} disabled={busy}>Create client</button>
      </div>
      {error ? <div className="form-error">{error}</div> : null}
    </div>
  );
}

function ClientRow({ client, adminKey, onChanged, readOnly }) {
  const [editing, setEditing] = useState(false);
  const [rate, setRate] = useState('');
  const [budget, setBudget] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const use = budgetUse(client);

  const startEdit = () => {
    setRate(client.rate_limit != null ? String(client.rate_limit) : '');
    setBudget(client.budget != null ? String(client.budget) : '');
    setError(null);
    setEditing(true);
  };

  const cancel = () => {
    setError(null);
    setEditing(false);
  };

  const save = async () => {
    setError(null);
    const body = {};

    const rateResult = parseRateLimit(rate);
    if (rateResult.error) {
      setError(rateResult.error);
      return;
    }
    if (rateResult.value == null) {
      if (client.rate_limit != null) {
        body.clear_rate_limit = true;
      }
    } else {
      body.rate_limit = rateResult.value;
    }

    const budgetResult = parseBudget(budget);
    if (budgetResult.error) {
      setError(budgetResult.error);
      return;
    }
    if (budgetResult.value == null) {
      if (client.budget != null) {
        // Clearing a cap is impactful: require an explicit confirm.
        if (!window.confirm(`Clear the budget cap for ${client.name}? This removes its spend limit.`)) {
          return;
        }
        body.clear_budget = true;
      }
    } else {
      body.budget = budgetResult.value;
    }

    if (Object.keys(body).length === 0) {
      setEditing(false);
      return;
    }
    setBusy(true);
    try {
      await updateClient(adminKey, client.id, body);
      setEditing(false);
      await onChanged();
    } catch (e) {
      setError(readableError(e));
    } finally {
      setBusy(false);
    }
  };

  const toggleEnabled = async () => {
    // Disabling locks a client out, so confirm it; enabling is low-risk.
    if (client.enabled
        && !window.confirm(`Disable ${client.name}? It will be unable to authenticate until re-enabled.`)) {
      return;
    }
    setError(null);
    setBusy(true);
    try {
      await updateClient(adminKey, client.id, { enabled: !client.enabled });
      await onChanged();
    } catch (e) {
      setError(readableError(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <tr>
        <td className="name" data-label="Client">{client.name}</td>
        <td data-label="Status">
          <span className={client.enabled ? 'badge on' : 'badge off'}>
            {client.enabled ? 'enabled' : 'disabled'}
          </span>
        </td>
        <td className="num" data-label="Rate limit">
          {editing
            ? <input className="cell-input" aria-label="Rate limit" value={rate} inputMode="numeric"
                onChange={(e) => setRate(e.target.value)} />
            : (client.rate_limit ?? 'default')}
        </td>
        <td className="num" data-label="Budget">
          {editing
            ? <input className="cell-input" aria-label="Budget" value={budget} inputMode="decimal"
                onChange={(e) => setBudget(e.target.value)} />
            : (client.budget != null ? money(client.budget) : '—')}
        </td>
        <td className="num" data-label="Requests">{int.format(client.usage?.request_count ?? 0)}</td>
        <td className="num" data-label="Input tok">{int.format(client.usage?.total_input_tokens ?? 0)}</td>
        <td className="num" data-label="Output tok">{int.format(client.usage?.total_output_tokens ?? 0)}</td>
        <td className="num" data-label="Spend">{money(client.usage?.total_cost)}</td>
        <td className="num spendcap" data-label="Spend / cap">
          {use.has ? (
            <div className="bar-wrap">
              <div className="bar-track">
                <div className={`bar-fill ${use.level}`} style={{ width: `${use.pct}%` }} />
              </div>
              <span className={use.over ? 'bar-pct over' : 'bar-pct'}>{use.text}</span>
            </div>
          ) : '—'}
        </td>
        <td className="actions" data-label="Actions">
          {readOnly ? null : editing ? (
            <>
              <button type="button" onClick={save} disabled={busy}>Save</button>
              <button type="button" className="ghost" onClick={cancel} disabled={busy}>Cancel</button>
            </>
          ) : (
            <>
              <button type="button" className="ghost" onClick={startEdit} disabled={busy}>Edit</button>
              <button type="button" className={client.enabled ? 'ghost warn-action' : 'ghost go-action'}
                onClick={toggleEnabled} disabled={busy}>
                {client.enabled ? 'Disable' : 'Enable'}
              </button>
            </>
          )}
        </td>
      </tr>
      {error ? (
        <tr><td className="row-error" colSpan="10">{error}</td></tr>
      ) : null}
    </>
  );
}

export default function App() {
  const [adminKey, setAdminKey] = useState('');
  const [draftKey, setDraftKey] = useState('');
  const [stats, setStats] = useState(null);
  const [clients, setClients] = useState([]);
  const [error, setError] = useState(null);
  const [loadedAt, setLoadedAt] = useState(null);
  const [reveal, setReveal] = useState(null);

  // Load the read-only telemetry. This runs with or without a key: in demo mode
  // the backend serves these GETs unauthenticated (public read-only view), so an
  // empty key still loads; in live mode the same call returns 401 and we fall
  // back to the "enter admin key" prompt. Writes always require a key.
  const load = useCallback(async (key) => {
    try {
      const [statsData, clientList] = await Promise.all([fetchStats(key), fetchClients(key)]);
      const withUsage = await Promise.all(
        clientList.map(async (client) => ({ ...client, usage: await fetchUsage(key, client.id) })),
      );
      setStats(statsData);
      setClients(withUsage);
      setError(null);
      setLoadedAt(new Date());
    } catch (e) {
      setError(e.message === 'unauthorized' ? 'unauthorized' : 'unreachable');
    }
  }, []);

  useEffect(() => {
    load(adminKey);
    const timer = setInterval(() => load(adminKey), REFRESH_MS);
    return () => clearInterval(timer);
  }, [adminKey, load]);

  const connect = () => {
    setAdminKey(draftKey);
  };

  const handleCreate = async (body) => {
    const created = await createClient(adminKey, body);
    // The raw key is shown once, in transient state only. Never stored or logged.
    setReveal({ name: created.name, apiKey: created.api_key });
    await load(adminKey);
  };

  const authed = adminKey !== '';
  const hasData = stats !== null;
  // No data yet and no error means the first load is still in flight.
  const loading = !hasData && error === null;
  // Data with no key entered can only mean the demo public read view: in live
  // mode a keyless read is rejected, so we would have no data instead.
  const publicDemo = hasData && !authed;

  return (
    <div className="console">
      <header className="masthead">
        <div className="brand">
          <div className="logo-tile" aria-hidden="true">G</div>
          <div>
            <h1 className="wordmark">Gateway<span className="dot">.</span>console</h1>
            <div className="tagline">LLM proxy telemetry</div>
          </div>
        </div>
        <div className="keybar">
          <input
            type="password"
            aria-label="Admin key"
            placeholder="admin key"
            value={draftKey}
            onChange={(e) => setDraftKey(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && connect()}
          />
          <button type="button" onClick={connect}>Connect</button>
        </div>
      </header>

      {!hasData ? (
        loading ? (
          <p className="notice" style={{ marginTop: 28 }}>
            Connecting to gateway telemetry…
          </p>
        ) : error === 'unreachable' ? (
          <p className="notice alert" style={{ marginTop: 28 }}>
            Cannot reach the gateway API.
          </p>
        ) : authed ? (
          <p className="notice alert" style={{ marginTop: 28 }}>
            Admin key rejected. Check the key and connect again.
          </p>
        ) : (
          <p className="notice" style={{ marginTop: 28 }}>
            Enter the admin key to read gateway telemetry and manage clients.
          </p>
        )
      ) : (
        <>
          {publicDemo ? (
            <div className="demobar">
              <span className="demobar-tag">Demo mode</span>
              <span className="demobar-text">
                Read-only public view. Telemetry runs on the no-spend stub provider. Enter the
                admin key above to manage clients.
              </span>
            </div>
          ) : null}

          <div className="rail">System</div>
          <div className="gauges">
            <Gauge label="Requests" value={int.format(stats?.total_requests ?? 0)} sub="billable calls"
              tone="accent" />
            <Gauge label="Spend" value={money(stats?.total_cost)} sub="all clients" tone="amber" />
            <Gauge label="Cache hit rate" value={stats ? cacheHitRate(stats) : '—'}
              sub={`${int.format(stats?.cache_hits ?? 0)} hit / ${int.format(stats?.cache_misses ?? 0)} miss`}
              tone="lime" />
            <Gauge label="Rate-limit rejections" value={int.format(stats?.rate_limit_rejections ?? 0)}
              sub="429 responses" tone="red" warn={(stats?.rate_limit_rejections ?? 0) > 0} />
          </div>

          <div className="rail">Latency</div>
          <div className="gauges">
            <Gauge label="p50" value={`${int.format(stats?.p50_millis ?? 0)} ms`} sub="median"
              tone="lime" dot />
            <Gauge label="p95" value={`${int.format(stats?.p95_millis ?? 0)} ms`} sub="95th percentile"
              tone="amber" dot />
            <Gauge label="p99" value={`${int.format(stats?.p99_millis ?? 0)} ms`} sub="99th percentile"
              tone="red" dot />
          </div>

          {authed ? (
            <>
              <div className="rail">Create client</div>
              <CreateClientForm onCreate={handleCreate} />
              {reveal ? <KeyReveal reveal={reveal} onDismiss={() => setReveal(null)} /> : null}
            </>
          ) : null}

          <div className="rail">Clients</div>
          <div className="panel">
            <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Client</th>
                  <th>Status</th>
                  <th className="num">Rate limit</th>
                  <th className="num">Budget</th>
                  <th className="num">Requests</th>
                  <th className="num">Input tok</th>
                  <th className="num">Output tok</th>
                  <th className="num">Spend</th>
                  <th className="num">Spend / cap</th>
                  <th className="actions">Actions</th>
                </tr>
              </thead>
              <tbody>
                {clients.map((client) => (
                  <ClientRow key={client.id} client={client} adminKey={adminKey}
                    onChanged={() => load(adminKey)} readOnly={!authed} />
                ))}
                {clients.length === 0 ? (
                  <tr><td className="empty" colSpan="10">No clients registered.</td></tr>
                ) : null}
              </tbody>
            </table>
            </div>
          </div>

          <div className="refresh">
            <button type="button" onClick={() => load(adminKey)}>Refresh</button>
          </div>
          <div className="rail" style={{ justifyContent: 'flex-start' }}>
            <span className="status">
              <span className="pulse" />
              {publicDemo ? 'demo · read-only' : 'live'} · updated{' '}
              {loadedAt ? loadedAt.toLocaleTimeString() : '—'}
            </span>
          </div>
        </>
      )}
    </div>
  );
}
