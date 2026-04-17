const BASE = '/api';

async function request(path, options = {}) {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  return res.json();
}

// Schema
export const getTypes = () => request('/schema/types');
export const getType = (name) => request(`/schema/types/${name}`);
export const getLinks = () => request('/schema/links');
export const getActions = () => request('/schema/actions');
export const getHistory = (table) =>
  request(`/schema/history${table ? `?table=${table}` : ''}`);

// Objects
export const listObjects = (type, filterKey, filterValue) => {
  const params = filterKey ? `?${filterKey}=${filterValue}` : '';
  return request(`/objects/${type}${params}`);
};
export const getObject = (type, id) => request(`/objects/${type}/${id}`);
export const createObject = (type, data) =>
  request(`/objects/${type}`, { method: 'POST', body: JSON.stringify(data) });
export const updateObject = (type, id, data) =>
  request(`/objects/${type}/${id}`, { method: 'PUT', body: JSON.stringify(data) });

// Graph
export const traverse = (type, id, depth = 3) =>
  request(`/graph/traverse/${type}/${id}?depth=${depth}`);
export const traceAnomaly = (taskId) =>
  request(`/graph/trace-anomaly/Task/${taskId}`);

// Actions
export const executeAction = (name, payload) =>
  request(`/actions/${name}`, { method: 'POST', body: JSON.stringify(payload) });

// AI Chat — Python sidecar (port 8421, proxied via /ai)
export const chatStatus = () =>
  fetch('/ai/chat/status').then((r) => r.json());

export function chatStream(message, conversationId, onEvent) {
  const body = JSON.stringify({ message, conversationId });

  fetch('/ai/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body,
  }).then((res) => {
    if (!res.ok) {
      onEvent({ type: 'text', content: `HTTP ${res.status} error` });
      onEvent({ type: 'done' });
      return;
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    function processChunk({ done, value }) {
      if (done) {
        onEvent({ type: 'done' });
        return;
      }

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop(); // keep incomplete line in buffer

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const jsonStr = line.slice(5).trim();
          if (!jsonStr) continue;
          try {
            onEvent(JSON.parse(jsonStr));
          } catch (e) {
            console.error('[chatStream] parse error:', e);
          }
        }
      }

      reader.read().then(processChunk).catch((e) => {
        console.error('[chatStream] read error:', e);
        onEvent({ type: 'done' });
      });
    }

    reader.read().then(processChunk).catch((e) => {
      console.error('[chatStream] first read error:', e);
      onEvent({ type: 'done' });
    });
  }).catch((err) => {
    onEvent({ type: 'text', content: `Connection error: ${err.message}` });
    onEvent({ type: 'done' });
  });
}
