// Thin fetch helpers for the management API. All logic lives in the backend;
// these just attach the admin key and parse JSON.

async function getJson(path, adminKey) {
  const response = await fetch(path, { headers: { 'x-admin-key': adminKey } });
  if (response.status === 401) {
    throw new Error('unauthorized');
  }
  if (!response.ok) {
    throw new Error(`request failed (${response.status})`);
  }
  return response.json();
}

export const fetchStats = (adminKey) => getJson('/api/stats', adminKey);
export const fetchClients = (adminKey) => getJson('/api/clients', adminKey);
export const fetchUsage = (adminKey, clientId) =>
  getJson(`/api/usage?client=${encodeURIComponent(clientId)}`, adminKey);

// Write helpers. On failure they surface the backend's error message from the
// locked error envelope ({ error: { message } }) so the UI can show a readable
// reason instead of a raw dump or a silent failure.
async function sendJson(method, path, adminKey, body) {
  const response = await fetch(path, {
    method,
    headers: { 'Content-Type': 'application/json', 'x-admin-key': adminKey },
    body: JSON.stringify(body),
  });
  if (response.status === 401) {
    throw new Error('unauthorized');
  }
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(data?.error?.message || `request failed (${response.status})`);
  }
  return data;
}

export const createClient = (adminKey, body) => sendJson('POST', '/api/clients', adminKey, body);
export const updateClient = (adminKey, id, body) =>
  sendJson('PATCH', `/api/clients/${encodeURIComponent(id)}`, adminKey, body);
