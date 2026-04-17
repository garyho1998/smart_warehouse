export default function NodeDetail({ node, onClose }) {
  if (!node) return null;

  return (
    <div className="bg-white border border-gray-200 rounded-lg shadow-sm p-4 w-80">
      <div className="flex items-center justify-between mb-3">
        <h3 className="font-semibold text-gray-800 text-sm">{node.label}</h3>
        <button
          onClick={onClose}
          className="text-gray-400 hover:text-gray-600 text-lg leading-none"
        >
          &times;
        </button>
      </div>
      <div className="text-xs text-gray-500 mb-3">
        類型: {node.type} &middot; ID: {node.id}
      </div>
      <table className="w-full text-sm">
        <tbody>
          {Object.entries(node.properties).map(([key, value]) => (
            <tr key={key} className="border-t border-gray-100">
              <td className="py-1.5 pr-3 text-gray-500 font-mono text-xs whitespace-nowrap">
                {key}
              </td>
              <td className="py-1.5 text-gray-800 text-xs break-all">
                {value == null ? <span className="text-gray-300">null</span> : String(value)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
