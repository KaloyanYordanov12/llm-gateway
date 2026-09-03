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
