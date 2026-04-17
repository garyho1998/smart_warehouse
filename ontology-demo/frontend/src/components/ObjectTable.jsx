export default function ObjectTable({ properties, objects, onEdit }) {
  if (!properties || properties.length === 0) return null;

  if (objects.length === 0) {
    return <div className="text-gray-400 text-sm py-8 text-center">未有資料</div>;
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm border border-gray-200 rounded">
        <thead className="bg-gray-50">
          <tr>
            {properties.map((p) => (
              <th
                key={p.name}
                className="text-left px-3 py-2 font-medium text-gray-600 whitespace-nowrap"
              >
                {p.name}
              </th>
            ))}
            <th className="px-3 py-2 w-16"></th>
          </tr>
        </thead>
        <tbody>
          {objects.map((obj, i) => (
            <tr key={obj.id || i} className="border-t border-gray-100 hover:bg-gray-50">
              {properties.map((p) => (
                <td key={p.name} className="px-3 py-1.5 text-xs whitespace-nowrap max-w-48 truncate">
                  {obj[p.name] == null ? (
                    <span className="text-gray-300">-</span>
                  ) : (
                    String(obj[p.name])
                  )}
                </td>
              ))}
              <td className="px-3 py-1.5">
                <button
                  onClick={() => onEdit(obj)}
                  className="text-indigo-600 hover:text-indigo-800 text-xs"
                >
                  編輯
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
