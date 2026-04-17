export default function TypeDetail({ typeDef, links, actions }) {
  if (!typeDef) {
    return (
      <div className="text-gray-400 text-sm flex items-center justify-center h-full">
        請選擇一個 Object Type
      </div>
    );
  }

  const properties = typeDef.properties
    ? Object.values(typeDef.properties)
    : [];

  const relatedLinks = (links || []).filter(
    (l) => l.fromType === typeDef.id || l.toType === typeDef.id
  );

  const relatedActions = (actions || []).filter(
    (a) => a.objectTypeId === typeDef.id
  );

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h2 className="text-lg font-semibold text-gray-800">{typeDef.id}</h2>
        {typeDef.description && (
          <p className="text-sm text-gray-500 mt-1">{typeDef.description}</p>
        )}
        <p className="text-xs text-gray-400 mt-1">主鍵: {typeDef.primaryKey}</p>
      </div>

      {/* Properties */}
      <div>
        <h3 className="text-sm font-semibold text-gray-700 mb-2">
          屬性 ({properties.length})
        </h3>
        <div className="overflow-x-auto">
          <table className="w-full text-sm border border-gray-200 rounded">
            <thead className="bg-gray-50">
              <tr>
                <th className="text-left px-3 py-2 font-medium text-gray-600">名稱</th>
                <th className="text-left px-3 py-2 font-medium text-gray-600">類型</th>
                <th className="text-center px-3 py-2 font-medium text-gray-600">必填</th>
                <th className="text-center px-3 py-2 font-medium text-gray-600">唯一</th>
                <th className="text-left px-3 py-2 font-medium text-gray-600">預設值</th>
                <th className="text-left px-3 py-2 font-medium text-gray-600">列舉值</th>
              </tr>
            </thead>
            <tbody>
              {properties.map((p) => (
                <tr key={p.name} className="border-t border-gray-100">
                  <td className="px-3 py-1.5 font-mono text-xs">{p.name}</td>
                  <td className="px-3 py-1.5">
                    <span className="inline-block bg-gray-100 text-gray-600 rounded px-1.5 py-0.5 text-xs">
                      {p.type}
                    </span>
                  </td>
                  <td className="px-3 py-1.5 text-center">
                    {p.required && <span className="text-red-500">*</span>}
                  </td>
                  <td className="px-3 py-1.5 text-center">
                    {p.uniqueCol && <span className="text-indigo-500">U</span>}
                  </td>
                  <td className="px-3 py-1.5 text-xs text-gray-500">
                    {p.defaultValue || ''}
                  </td>
                  <td className="px-3 py-1.5 text-xs text-gray-500">
                    {p.enumValues ? p.enumValues.join(', ') : ''}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Related Links */}
      {relatedLinks.length > 0 && (
        <div>
          <h3 className="text-sm font-semibold text-gray-700 mb-2">
            關係 ({relatedLinks.length})
          </h3>
          <div className="space-y-1">
            {relatedLinks.map((l) => (
              <div
                key={l.id}
                className="bg-gray-50 rounded px-3 py-2 text-sm flex items-center gap-2"
              >
                <span className="font-mono text-xs text-gray-600">{l.id}</span>
                <span className="text-gray-400">:</span>
                <span className={l.fromType === typeDef.id ? 'font-semibold' : ''}>
                  {l.fromType}
                </span>
                <span className="text-gray-400">&rarr;</span>
                <span className={l.toType === typeDef.id ? 'font-semibold' : ''}>
                  {l.toType}
                </span>
                <span className="text-xs text-gray-400 ml-auto">
                  {l.cardinality} (FK: {l.foreignKey})
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Related Actions */}
      {relatedActions.length > 0 && (
        <div>
          <h3 className="text-sm font-semibold text-gray-700 mb-2">
            動作 ({relatedActions.length})
          </h3>
          {relatedActions.map((a) => (
            <div key={a.id} className="bg-gray-50 rounded px-3 py-2 text-sm">
              <span className="font-mono text-xs">{a.id}</span>
              {a.description && (
                <span className="text-gray-500 ml-2">— {a.description}</span>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
