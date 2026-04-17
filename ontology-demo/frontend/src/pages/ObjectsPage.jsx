import { useState, useEffect, useCallback } from 'react';
import { getTypes, getType, listObjects, createObject, updateObject, getActions } from '../api/client';
import ObjectTable from '../components/ObjectTable';
import ObjectForm from '../components/ObjectForm';

export default function ObjectsPage() {
  const [types, setTypes] = useState([]);
  const [selectedType, setSelectedType] = useState('');
  const [properties, setProperties] = useState([]);
  const [objects, setObjects] = useState([]);
  const [actions, setActions] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [editData, setEditData] = useState(null);
  const [error, setError] = useState(null);

  // Load type list + actions
  useEffect(() => {
    Promise.all([getTypes(), getActions()])
      .then(([typesData, actionsData]) => {
        const arr = Array.isArray(typesData) ? typesData : [];
        setTypes(arr);
        setActions(Array.isArray(actionsData) ? actionsData : []);
        if (arr.length > 0) setSelectedType(arr[0].id);
      })
      .catch((e) => setError(e.message));
  }, []);

  // Load type detail + objects when type changes
  const loadData = useCallback(async () => {
    if (!selectedType) return;
    setError(null);
    try {
      const [typeDef, objs] = await Promise.all([
        getType(selectedType),
        listObjects(selectedType),
      ]);
      const props = typeDef.properties ? Object.values(typeDef.properties) : [];
      setProperties(props);
      setObjects(Array.isArray(objs) ? objs : []);
    } catch (e) {
      setError(e.message);
    }
  }, [selectedType]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const typeActions = actions.filter((a) => a.objectTypeId === selectedType);

  function handleCreate() {
    setEditData(null);
    setShowForm(true);
  }

  function handleEdit(obj) {
    setEditData(obj);
    setShowForm(true);
  }

  async function handleSubmit(payload) {
    setError(null);
    try {
      if (editData) {
        await updateObject(selectedType, editData.id, payload);
      } else {
        await createObject(selectedType, payload);
      }
      setShowForm(false);
      setEditData(null);
      await loadData();
    } catch (e) {
      setError(e.message);
    }
  }

  return (
    <div className="space-y-4 p-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <select
          value={selectedType}
          onChange={(e) => {
            setSelectedType(e.target.value);
            setShowForm(false);
          }}
          className="border border-gray-300 rounded px-3 py-1.5 text-sm bg-white"
        >
          {types.map((t) => (
            <option key={t.id} value={t.id}>
              {t.id}
            </option>
          ))}
        </select>

        <button
          onClick={handleCreate}
          className="bg-indigo-600 text-white px-4 py-1.5 rounded text-sm font-medium hover:bg-indigo-700"
        >
          新增
        </button>

        <span className="text-xs text-gray-400">{objects.length} 筆資料</span>
      </div>

      {/* Error */}
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 rounded px-4 py-2 text-sm">
          {error}
        </div>
      )}

      {/* Form */}
      {showForm && (
        <div className="bg-white border border-gray-200 rounded-lg p-5">
          <ObjectForm
            properties={properties}
            editData={editData}
            onSubmit={handleSubmit}
            onCancel={() => {
              setShowForm(false);
              setEditData(null);
            }}
          />
        </div>
      )}

      {/* Available Actions */}
      {typeActions.length > 0 && (
        <div className="bg-indigo-50 border border-indigo-200 rounded-lg p-4">
          <div className="text-xs font-medium text-indigo-700 mb-2">
            可用 Actions ({typeActions.length})
          </div>
          <div className="flex flex-wrap gap-2">
            {typeActions.map((a) => (
              <div
                key={a.id}
                className="bg-white border border-indigo-200 rounded px-3 py-1.5 text-xs"
              >
                <span className="font-mono font-medium text-indigo-800">{a.id}</span>
                {a.mode && a.mode !== 'UPDATE' && (
                  <span className="ml-1 text-[10px] bg-indigo-100 text-indigo-600 rounded px-1">
                    {a.mode}
                  </span>
                )}
                {a.description && (
                  <span className="ml-1.5 text-gray-500">{a.description}</span>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Table */}
      <div className="bg-white border border-gray-200 rounded-lg p-4">
        <ObjectTable properties={properties} objects={objects} onEdit={handleEdit} />
      </div>
    </div>
  );
}
