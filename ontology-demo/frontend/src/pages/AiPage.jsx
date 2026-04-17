import { useCallback, useRef } from 'react';
import ChatPanel from '../components/ChatPanel';
import GraphView from '../components/GraphView';
import NodeDetail from '../components/NodeDetail';
import { useChatContext } from '../context/ChatContext';

export default function AiPage() {
  const { graphResult, setGraphResult, selectedNode, setSelectedNode, chatWidth, setChatWidth } = useChatContext();
  const containerRef = useRef(null);

  function handleGraphData(data) {
    setGraphResult(data);
    setSelectedNode(null);
  }

  function handleNodeClick(node) {
    setSelectedNode(node);
  }

  const onMouseDown = useCallback((e) => {
    e.preventDefault();
    const container = containerRef.current;
    if (!container) return;

    const startX = e.clientX;
    const startWidth = chatWidth;
    const containerWidth = container.offsetWidth;

    function onMouseMove(e) {
      const delta = e.clientX - startX;
      const newPct = ((startWidth / 100) * containerWidth + delta) / containerWidth * 100;
      setChatWidth(Math.min(70, Math.max(20, newPct)));
    }

    function onMouseUp() {
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    }

    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
  }, [chatWidth, setChatWidth]);

  return (
    <div ref={containerRef} className="flex h-full">
      {/* Left: Chat Panel */}
      <div style={{ width: `${chatWidth}%` }} className="border-r border-gray-200 bg-gray-50 flex flex-col shrink-0">
        <ChatPanel onGraphData={handleGraphData} />
      </div>

      {/* Drag handle */}
      <div
        onMouseDown={onMouseDown}
        className="w-1 hover:w-1.5 bg-gray-200 hover:bg-indigo-400 cursor-col-resize shrink-0 transition-colors"
      />

      {/* Right: Dynamic Panel */}
      <div className="flex-1 flex flex-col min-w-0">
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
