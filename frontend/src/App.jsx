import { useCallback, useEffect, useState } from 'react';
import { fetchClients, fetchStats, fetchUsage } from './api.js';

const KEY_STORAGE = 'llmgw.adminKey';
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

// Per-client spend against the hard budget cap. No cap => no meter.
function budgetUse(client) {
  if (client.budget == null) {
    return { text: '—', over: false };
  }
  const spend = Number(client.usage?.total_cost ?? 0);
  const cap = Number(client.budget);
  const pct = cap > 0 ? (spend / cap) * 100 : 100;
  return { text: `${pct.toFixed(0)}%`, over: spend >= cap };
}

function Gauge({ label, value, sub, warn }) {
  return (
    <div className={warn ? 'gauge warn' : 'gauge'}>
      <div className="label">{label}</div>
      <div className="readout">{value}</div>
      {sub ? <div className="sub">{sub}</div> : null}
    </div>
  );
}

export default function App() {
  const [adminKey, setAdminKey] = useState(() => localStorage.getItem(KEY_STORAGE) ?? '');
  const [draftKey, setDraftKey] = useState(adminKey);
  const [stats, setStats] = useState(null);
  const [clients, setClients] = useState([]);
  const [error, setError] = useState(null);
  const [loadedAt, setLoadedAt] = useState(null);

  const load = useCallback(async (key) => {
    if (!key) {
      return;
    }
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
    if (!adminKey) {
      return undefined;
    }
    const timer = setInterval(() => load(adminKey), REFRESH_MS);
    return () => clearInterval(timer);
  }, [adminKey, load]);

  const connect = () => {
    localStorage.setItem(KEY_STORAGE, draftKey);
    setAdminKey(draftKey);
  };

  return (
    <div className="console">
      <header className="masthead">
        <div>
          <h1 className="wordmark">Gateway<span className="dot">.</span>console</h1>
          <div className="tagline">LLM proxy telemetry</div>
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

      {!adminKey ? (
        <p className="notice" style={{ marginTop: 28 }}>
          Enter the admin key to read live gateway telemetry.
        </p>
      ) : error === 'unauthorized' ? (
        <p className="notice alert" style={{ marginTop: 28 }}>
          Admin key rejected. Check the key and connect again.
        </p>
      ) : error === 'unreachable' ? (
        <p className="notice alert" style={{ marginTop: 28 }}>
          Cannot reach the gateway API.
        </p>
      ) : (
        <>
          <div className="rail">System</div>
          <div className="gauges">
            <Gauge label="Requests" value={int.format(stats?.total_requests ?? 0)} sub="billable calls" />
            <Gauge label="Spend" value={money(stats?.total_cost)} sub="all clients" />
            <Gauge label="Cache hit rate" value={stats ? cacheHitRate(stats) : '—'}
              sub={`${int.format(stats?.cache_hits ?? 0)} hit / ${int.format(stats?.cache_misses ?? 0)} miss`} />
            <Gauge label="Rate-limit rejections" value={int.format(stats?.rate_limit_rejections ?? 0)}
              sub="429 responses" warn={(stats?.rate_limit_rejections ?? 0) > 0} />
          </div>

          <div className="rail">Latency</div>
          <div className="gauges">
            <Gauge label="p50" value={`${int.format(stats?.p50_millis ?? 0)} ms`} sub="median" />
            <Gauge label="p95" value={`${int.format(stats?.p95_millis ?? 0)} ms`} sub="95th percentile" />
            <Gauge label="p99" value={`${int.format(stats?.p99_millis ?? 0)} ms`} sub="99th percentile" />
          </div>

          <div className="rail">Clients</div>
          <div className="panel">
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
                </tr>
              </thead>
              <tbody>
                {clients.map((client) => {
                  const use = budgetUse(client);
                  return (
                    <tr key={client.id}>
                      <td className="name">{client.name}</td>
                      <td>
                        <span className={client.enabled ? 'badge on' : 'badge off'}>
                          {client.enabled ? 'enabled' : 'disabled'}
                        </span>
                      </td>
                      <td className="num">{client.rate_limit ?? 'default'}</td>
                      <td className="num">{client.budget != null ? money(client.budget) : '—'}</td>
                      <td className="num">{int.format(client.usage?.request_count ?? 0)}</td>
                      <td className="num">{int.format(client.usage?.total_input_tokens ?? 0)}</td>
                      <td className="num">{int.format(client.usage?.total_output_tokens ?? 0)}</td>
                      <td className="num">{money(client.usage?.total_cost)}</td>
                      <td className={use.over ? 'num cap-over' : 'num'}>{use.text}</td>
                    </tr>
                  );
                })}
                {clients.length === 0 ? (
                  <tr><td className="empty" colSpan="9">No clients registered.</td></tr>
                ) : null}
              </tbody>
            </table>
          </div>

          <div className="refresh">
            <button type="button" onClick={() => load(adminKey)}>Refresh</button>
          </div>
          <div className="rail" style={{ justifyContent: 'flex-start' }}>
            <span className="status">
              <span className="pulse" />
              live · updated {loadedAt ? loadedAt.toLocaleTimeString() : '—'}
            </span>
          </div>
        </>
      )}
    </div>
  );
}
