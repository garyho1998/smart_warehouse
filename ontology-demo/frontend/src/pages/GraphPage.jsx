import { useState, useEffect } from 'react';
import { traverse, traceAnomaly, getTypes, getLinks, listObjects } from '../api/client';
import GraphView from '../components/GraphView';
import NodeDetail from '../components/NodeDetail';
import InsightBanner from '../components/InsightBanner';
import SchemaGraph from '../components/SchemaGraph';
import SchemaNodeDetail from '../components/SchemaNodeDetail';

function TabBar({ activeTab, onChange }) {
  const tabs = [
    { id: 'schema', label: 'Schema 圖' },
    { id: 'instance', label: '實例圖' },
  ];
  return (
    <div className="flex gap-1 mb-3">
      {tabs.map((t) => (
        <button
          key={t.id}
          onClick={() => onChange(t.id)}
          className={`px-4 py-1.5 rounded text-sm font-medium ${
            activeTab === t.id
              ? 'bg-indigo-600 text-white'
              : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
          }`}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}

export default function GraphPage() {
  const [activeTab, setActiveTab] = useState('schema');

  // --- Schema tab state ---
  const [schemaTypes, setSchemaTypes] = useState([]);
  const [schemaLinks, setSchemaLinks] = useState([]);
  const [selectedTypeDef, setSelectedTypeDef] = useState(null);

  // --- Instance tab state ---
  const [types, setTypes] = useState([]);
  const [selectedType, setSelectedType] = useState('Task');
  const [instances, setInstances] = useState([]);
  const [objectId, setObjectId] = useState('');
  const [depth, setDepth] = useState(3);
  const [graphResult, setGraphResult] = useState(null);
  const [selectedNode, setSelectedNode] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [instanceLoaded, setInstanceLoaded] = useState(false);

  // Load schema data
  useEffect(() => {
    Promise.all([getTypes(), getLinks()]).then(([t, l]) => {
      setSchemaTypes(Array.isArray(t) ? t : []);
      setSchemaLinks(Array.isArray(l) ? l : []);
      setTypes(Array.isArray(t) ? t : []);
    });
  }, []);

  // Load instances when type changes
  useEffect(() => {
    if (!selectedType) return;
    listObjects(selectedType)
      .then((objs) => {
        const sorted = (Array.isArray(objs) ? objs : []).sort((a, b) =>
          String(a.id).localeCompare(String(b.id))
        );
        setInstances(sorted);
        if (sorted.length > 0) setObjectId(sorted[0].id);
        else setObjectId('');
      })
      .catch(() => setInstances([]));
  }, [selectedType]);

  // Load instance graph on first switch to instance tab
  useEffect(() => {
    if (activeTab === 'instance' && !instanceLoaded && objectId) {
      handleTraverse();
      setInstanceLoaded(true);
    }
  }, [activeTab, objectId]);

  async function handleTraverse() {
    setLoading(true);
    setError(null);
    setSelectedNode(null);
    try {
      const result = await traverse(selectedType, objectId, depth);
      setGraphResult(result);
    } catch (e) {
      setError(e.message);
      setGraphResult(null);
    } finally {
      setLoading(false);
    }
  }

  async function handleTraceAnomaly() {
    if (!objectId) return;
    setLoading(true);
    setError(null);
    setSelectedNode(null);
    try {
      const result = await traceAnomaly(objectId);
      setGraphResult(result);
    } catch (e) {
      setError(e.message);
      setGraphResult(null);
    } finally {
      setLoading(false);
    }
  }

  function handleViewInstances(typeId) {
    setSelectedType(typeId);
    setObjectId('');
    setActiveTab('instance');
  }

  return (
    <div className="h-full flex flex-col p-6">
      <TabBar activeTab={activeTab} onChange={setActiveTab} />

      {activeTab === 'schema' && (
        <>
          <div className="flex-1 flex gap-4 min-h-0">
            <div className="flex-1 border border-gray-200 rounded-lg bg-white overflow-hidden">
              <SchemaGraph
                types={schemaTypes}
                links={schemaLinks}
                onNodeClick={setSelectedTypeDef}
              />
            </div>

            {selectedTypeDef && (
              <SchemaNodeDetail
                typeDef={selectedTypeDef}
                links={schemaLinks}
                onClose={() => setSelectedTypeDef(null)}
                onViewInstances={handleViewInstances}
              />
            )}
          </div>
        </>
      )}

      {activeTab === 'instance' && (
        <>
          {/* Controls */}
          <div className="flex items-center gap-3 mb-3 flex-wrap">
            <select
              value={selectedType}
              onChange={(e) => setSelectedType(e.target.value)}
              className="border border-gray-300 rounded px-3 py-1.5 text-sm bg-white"
            >
              {types.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.id}
                </option>
              ))}
              {types.length === 0 && <option value="Task">Task</option>}
            </select>

            <select
              value={objectId}
              onChange={(e) => setObjectId(e.target.value)}
              className="border border-gray-300 rounded px-3 py-1.5 text-sm"
            >
              {instances.map((obj) => (
                <option key={obj.id} value={obj.id}>
                  {obj.id}{obj.name ? ` — ${obj.name}` : obj.code ? ` — ${obj.code}` : obj.status ? ` (${obj.status})` : ''}
                </option>
              ))}
              {instances.length === 0 && <option value="">（無資料）</option>}
            </select>

            <label className="text-sm text-gray-600 flex items-center gap-1">
              深度:
              <input
                type="number"
                value={depth}
                onChange={(e) => setDepth(Number(e.target.value))}
                min={1}
                max={4}
                className="border border-gray-300 rounded px-2 py-1.5 text-sm w-16"
              />
            </label>

            <button
              onClick={handleTraverse}
              disabled={loading || !objectId}
              className="bg-indigo-600 text-white px-4 py-1.5 rounded text-sm font-medium hover:bg-indigo-700 disabled:opacity-50"
            >
              遍歷
            </button>

            <button
              onClick={handleTraceAnomaly}
              disabled={loading}
              className="bg-red-600 text-white px-4 py-1.5 rounded text-sm font-medium hover:bg-red-700 disabled:opacity-50"
            >
              追蹤異常
            </button>

            {loading && <span className="text-sm text-gray-400">載入中...</span>}

            {graphResult && (
              <span className="text-xs text-gray-400 ml-auto">
                {graphResult.nodes.length} 節點 &middot; {graphResult.edges.length} 關係
              </span>
            )}
          </div>

          {/* Error */}
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 rounded px-4 py-2 text-sm mb-3">
              {error}
            </div>
          )}

          {/* Insights */}
          {graphResult && <InsightBanner insights={graphResult.insights} />}

          {/* Graph + Detail */}
          <div className="flex-1 flex gap-4 min-h-0">
            <div className="flex-1 border border-gray-200 rounded-lg bg-white overflow-hidden">
              <GraphView
                key={graphResult ? JSON.stringify(graphResult.start) : 'empty'}
                graphResult={graphResult}
                onNodeClick={setSelectedNode}
              />
            </div>

            {selectedNode && (
              <NodeDetail node={selectedNode} onClose={() => setSelectedNode(null)} />
            )}
          </div>
        </>
      )}
    </div>
  );
}
