/**
 * Renders a single chat message.
 * Supports: user text, AI text, tool_use status, tool_result, action_confirm.
 */
export default function ChatMessage({ msg }) {
  if (msg.role === 'user') {
    return (
      <div className="flex justify-end mb-3">
        <div className="bg-indigo-600 text-white rounded-lg px-4 py-2 max-w-[80%] text-sm whitespace-pre-wrap">
          {msg.content}
        </div>
      </div>
    );
  }

  if (msg.type === 'tool_use') {
    return (
      <div className="mb-2 text-xs text-gray-400 flex items-center gap-1">
        <span className="animate-pulse">🔍</span>
        正在呼叫 <span className="font-mono text-gray-500">{msg.content}</span>...
      </div>
    );
  }

  if (msg.type === 'tool_result') {
    const displayType = msg.data?.displayType;
    if (displayType === 'graph') {
      return (
        <div className="mb-2 text-xs text-green-600 flex items-center gap-1">
          📊 圖形數據已更新 → 右側面板
        </div>
      );
    }
    if (displayType === 'table') {
      const objects = msg.data?.displayData?.objects;
      if (objects && objects.length > 0) {
        return (
          <div className="mb-3 overflow-x-auto">
            <table className="text-xs border border-gray-200 rounded w-full">
              <thead>
                <tr className="bg-gray-50">
                  {Object.keys(objects[0]).map((key) => (
                    <th key={key} className="px-2 py-1 text-left font-medium text-gray-600 border-b">{key}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {objects.slice(0, 10).map((obj, i) => (
                  <tr key={i} className="border-b last:border-0">
                    {Object.values(obj).map((val, j) => (
                      <td key={j} className="px-2 py-1 text-gray-700">{String(val ?? '')}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
            {objects.length > 10 && (
              <div className="text-xs text-gray-400 mt-1">...顯示前 10 / {objects.length} 條</div>
            )}
          </div>
        );
      }
      return null;
    }
    // Other tool results — don't show anything (Claude will summarize)
    return null;
  }

  if (msg.type === 'action_confirm') {
    return (
      <div className="mb-3 bg-amber-50 border border-amber-200 rounded-lg p-3">
        <div className="text-sm text-amber-800 font-medium mb-2">⚡ 確認執行操作</div>
        <div className="text-sm text-amber-700 mb-3 whitespace-pre-wrap">{msg.content}</div>
        <div className="flex gap-2">
          <button
            onClick={() => msg.onConfirm?.()}
            className="px-3 py-1 bg-amber-600 text-white text-sm rounded hover:bg-amber-700"
          >
            確認執行
          </button>
          <button
            onClick={() => msg.onCancel?.()}
            className="px-3 py-1 bg-gray-200 text-gray-700 text-sm rounded hover:bg-gray-300"
          >
            取消
          </button>
        </div>
      </div>
    );
  }

  // Default: AI text message
  return (
    <div className="flex justify-start mb-3">
      <div className="bg-white border border-gray-200 rounded-lg px-4 py-2 max-w-[85%] text-sm text-gray-800 whitespace-pre-wrap">
        {msg.content}
      </div>
    </div>
  );
}
