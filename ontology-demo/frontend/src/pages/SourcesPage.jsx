import { useState, useEffect } from 'react';
import { getSources, syncSource } from '../api/client';

function fmtTime(iso) {
  if (!iso) return '從未';
  const d = new Date(iso);
  return d.toLocaleString('zh-HK', { hour12: false });
}

function wmsLabel(name) {
  if (name === 'wangdiantong') return '旺店通';
  if (name === 'jushuitan') return '聚水潭';
  return name;
}

export default function SourcesPage() {
  const [sources, setSources] = useState([]);
  const [error, setError] = useState(null);
  const [syncing, setSyncing] = useState(null);

  const refresh = () => {
    getSources()
      .then(setSources)
      .catch((e) => setError(e.message));
  };

  useEffect(() => {
    refresh();
    const iv = setInterval(refresh, 5000);
    return () => clearInterval(iv);
  }, []);

  const triggerSync = async (name) => {
    setSyncing(name);
    try {
      await syncSource(name);
      refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setSyncing(null);
    }
  };

  return (
    <div className="max-w-5xl mx-auto">
      <h1 className="text-2xl font-bold mb-2">WMS 資料源</h1>
      <p className="text-sm text-gray-600 mb-4">
        已連接的 WMS 系統。每 30 秒自動增量同步，手動 sync 亦可。
      </p>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-2 rounded mb-4">
          {error}
        </div>
      )}

      <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="text-left px-4 py-3 font-medium text-gray-700">WMS</th>
              <th className="text-left px-4 py-3 font-medium text-gray-700">最後同步</th>
              <th className="text-left px-4 py-3 font-medium text-gray-700">倉庫</th>
              <th className="text-left px-4 py-3 font-medium text-gray-700">儲位</th>
              <th className="text-left px-4 py-3 font-medium text-gray-700">SKU</th>
              <th className="text-left px-4 py-3 font-medium text-gray-700">庫存</th>
              <th className="text-left px-4 py-3 font-medium text-gray-700">操作</th>
            </tr>
          </thead>
          <tbody>
            {sources.length === 0 && (
              <tr>
                <td colSpan={7} className="px-4 py-6 text-center text-gray-400">載入中…</td>
              </tr>
            )}
            {sources.map((s) => (
              <tr key={s.name} className="border-b border-gray-100 last:border-0">
                <td className="px-4 py-3 font-medium text-gray-800">
                  {wmsLabel(s.name)}
                  <span className="ml-2 text-xs text-gray-400">{s.name}</span>
                </td>
                <td className="px-4 py-3 text-gray-600">{fmtTime(s.lastSyncAt)}</td>
                <td className="px-4 py-3 text-gray-700">{s.counts?.Warehouse ?? 0}</td>
                <td className="px-4 py-3 text-gray-700">{s.counts?.Location ?? 0}</td>
                <td className="px-4 py-3 text-gray-700">{s.counts?.Sku ?? 0}</td>
                <td className="px-4 py-3 text-gray-700">{s.counts?.Inventory ?? 0}</td>
                <td className="px-4 py-3">
                  <button
                    onClick={() => triggerSync(s.name)}
                    disabled={syncing === s.name}
                    className="px-3 py-1 bg-indigo-600 text-white rounded text-xs font-medium hover:bg-indigo-700 disabled:bg-gray-300"
                  >
                    {syncing === s.name ? '同步中…' : '立即同步'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="mt-6 text-xs text-gray-500">
        <p>
          <strong>Ontology = 部署加速器：</strong>
          新 WMS 接入只需一個 adapter。上面兩個 WMS API
          結構完全唔同（PHP form-encoded vs JSON REST），但 Graph / AI / Rules
          全部零改動即刻 work。
        </p>
      </div>
    </div>
  );
}
