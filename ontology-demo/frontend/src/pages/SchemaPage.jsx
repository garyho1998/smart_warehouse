import { useState, useEffect } from 'react';
import { getTypes, getLinks, getActions } from '../api/client';
import TypeList from '../components/TypeList';
import TypeDetail from '../components/TypeDetail';

export default function SchemaPage() {
  const [types, setTypes] = useState([]);
  const [links, setLinks] = useState([]);
  const [actions, setActions] = useState([]);
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    Promise.all([getTypes(), getLinks(), getActions()])
      .then(([t, l, a]) => {
        const typesArr = Array.isArray(t) ? t : [];
        setTypes(typesArr);
        setLinks(Array.isArray(l) ? l : []);
        setActions(Array.isArray(a) ? a : []);
        if (typesArr.length > 0) setSelected(typesArr[0].id);
      })
      .catch((e) => setError(e.message));
  }, []);

  const selectedType = types.find((t) => t.id === selected) || null;

  return (
    <div className="flex gap-6 h-[calc(100vh-7rem)]">
      {/* Left: Type list */}
      <div className="w-56 shrink-0 bg-white border border-gray-200 rounded-lg p-3 overflow-y-auto">
        <h2 className="text-sm font-semibold text-gray-600 mb-2">
          Object Types ({types.length})
        </h2>
        {error && <div className="text-red-500 text-xs mb-2">{error}</div>}
        <TypeList types={types} selected={selected} onSelect={setSelected} />
      </div>

      {/* Right: Detail */}
      <div className="flex-1 bg-white border border-gray-200 rounded-lg p-5 overflow-y-auto">
        <TypeDetail typeDef={selectedType} links={links} actions={actions} />
      </div>
    </div>
  );
}
