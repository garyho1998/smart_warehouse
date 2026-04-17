import { useState } from 'react';
import ChatPanel from '../components/ChatPanel';
import GraphView from '../components/GraphView';
import NodeDetail from '../components/NodeDetail';

export default function AiPage() {
  const [graphResult, setGraphResult] = useState(null);
  const [selectedNode, setSelectedNode] = useState(null);

  function handleGraphData(data) {
    setGraphResult(data);
    setSelectedNode(null);
  }

  function handleNodeClick(node) {
    setSelectedNode(node);
  }

  return (
    <div className="flex h-[calc(100vh-72px)] gap-0">
      {/* Left: Chat Panel (40%) */}
      <div className="w-2/5 border-r border-gray-200 bg-gray-50 flex flex-col">
        <ChatPanel onGraphData={handleGraphData} />
      </div>

      {/* Right: Dynamic Panel (60%) */}
      <div className="w-3/5 flex flex-col">
        {graphResult ? (
          <div className="flex-1 flex">
            <div className={`flex-1 ${selectedNode ? 'w-2/3' : 'w-full'}`}>
              <GraphView graphResult={graphResult} onNodeClick={handleNodeClick} />
            </div>
            {selectedNode && (
              <div className="w-1/3 border-l border-gray-200 overflow-y-auto">
                <NodeDetail node={selectedNode} />
              </div>
            )}
          </div>
        ) : (
          <div className="flex-1 flex items-center justify-center text-gray-300">
            <div className="text-center">
              <div className="text-6xl mb-4">🔮</div>
              <div className="text-sm text-gray-400">
                AI 查詢嘅圖形結果會顯示喺呢度
              </div>
              <div className="text-xs text-gray-300 mt-2">
                試下問：「TSK-005 失敗嘅原因係咩？」
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
