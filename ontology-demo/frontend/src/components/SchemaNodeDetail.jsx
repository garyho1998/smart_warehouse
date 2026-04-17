export default function SchemaNodeDetail({ typeDef, links, onClose, onViewInstances }) {
  if (!typeDef) return null;

  const properties = typeDef.properties ? Object.values(typeDef.properties) : [];
  const relatedLinks = (links || []).filter(
    (l) => l.fromType === typeDef.id || l.toType === typeDef.id
  );

  return (
    <div className="bg-white border border-gray-200 rounded-lg shadow-sm p-4 w-80">
      <div className="flex items-center justify-between mb-3">
        <h3 className="font-semibold text-gray-800 text-sm">{typeDef.id}</h3>
        <button
          onClick={onClose}
          className="text-gray-400 hover:text-gray-600 text-lg leading-none"
        >
          &times;
        </button>
      </div>

      {typeDef.description && (
        <p className="text-xs text-gray-500 mb-3">{typeDef.description}</p>
      )}

      {/* Properties */}
      <div className="mb-3">
        <h4 className="text-xs font-medium text-gray-600 mb-1">
          屬性 ({properties.length})
        </h4>
        {properties.length > 0 ? (
          <table className="w-full text-xs">
            <thead>
              <tr className="text-gray-400 border-b border-gray-100">
                <th className="text-left py-1 pr-2 font-normal">名稱</th>
                <th className="text-left py-1 pr-2 font-normal">型別</th>
                <th className="text-left py-1 font-normal">必填</th>
              </tr>
            </thead>
            <tbody>
              {properties.map((p) => (
                <tr key={p.name} className="border-t border-gray-50">
                  <td className="py-1 pr-2 font-mono text-gray-700">{p.name}</td>
                  <td className="py-1 pr-2 text-gray-500">{p.type}</td>
                  <td className="py-1 text-gray-500">
                    {p.required ? '✓' : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <p className="text-xs text-gray-400">無屬性</p>
        )}
      </div>

      {/* Related Links */}
      <div className="mb-3">
        <h4 className="text-xs font-medium text-gray-600 mb-1">
          關係 ({relatedLinks.length})
        </h4>
        {relatedLinks.length > 0 ? (
          <ul className="text-xs space-y-1">
            {relatedLinks.map((l) => {
              const isFrom = l.fromType === typeDef.id;
              return (
                <li key={l.id} className="flex items-center gap-1 text-gray-600">
                  <span className="font-mono text-gray-700">
                    {l.id.replace(/_/g, ' ')}
                  </span>
                  <span className="text-gray-400">
                    {isFrom ? '→' : '←'} {isFrom ? l.toType : l.fromType}
                  </span>
                  <span className="text-gray-400 ml-auto">{l.cardinality}</span>
                </li>
              );
            })}
          </ul>
        ) : (
          <p className="text-xs text-gray-400">無關係</p>
        )}
      </div>

      {/* View Instances */}
      {onViewInstances && (
        <button
          onClick={() => onViewInstances(typeDef.id)}
          className="w-full text-center text-xs bg-indigo-50 text-indigo-600 hover:bg-indigo-100 rounded py-1.5 font-medium"
        >
          查看 {typeDef.id} 實例 →
        </button>
      )}
    </div>
  );
}
