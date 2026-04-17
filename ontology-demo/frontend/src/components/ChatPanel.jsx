import { useState, useEffect } from 'react';
import { chatStream } from '../api/client';
import ChatMessage from './ChatMessage';
import { useChatContext } from '../context/ChatContext';

const SUGGESTIONS = [
  '而家倉庫有咩問題？',
  'TSK-005 失敗嘅原因係咩？',
  '顯示所有 IN_PROGRESS 嘅 task',
  '幫我完成 TSK-001',
];

export default function ChatPanel({ onGraphData }) {
  const {
    messages, setMessages,
    conversationId, setConversationId,
    loading, setLoading,
    scrollRef,
  } = useChatContext();
  const [input, setInput] = useState('');

  // Auto-scroll to bottom
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  function sendMessage(text) {
    if (!text.trim() || loading) return;

    const userMsg = { role: 'user', content: text.trim() };
    setMessages((prev) => [...prev, userMsg]);
    setInput('');
    setLoading(true);

    chatStream(text.trim(), conversationId, (event) => {
      if (event.type === 'text' && event.content) {
        setMessages((prev) => {
          // Merge consecutive text events into one message
          const last = prev[prev.length - 1];
          if (last && last.type === 'text' && last.role === 'assistant') {
            return [...prev.slice(0, -1), { ...last, content: last.content + event.content }];
          }
          return [...prev, { role: 'assistant', type: 'text', content: event.content }];
        });
      } else if (event.type === 'tool_use') {
        setMessages((prev) => [...prev, { type: 'tool_use', content: event.content, data: event.data }]);
      } else if (event.type === 'tool_result') {
        const displayType = event.data?.displayType;

        // If graph data, send to parent for right panel
        if (displayType === 'graph') {
          const graphResult = event.data?.displayData?.graphResult;
          if (graphResult && onGraphData) {
            onGraphData(graphResult);
          }
        }

        setMessages((prev) => [...prev, { type: 'tool_result', content: event.content, data: event.data }]);
      } else if (event.type === 'done') {
        setLoading(false);
      }

      // Handle conversationId from the conversation event
      if (event.conversationId) {
        setConversationId(event.conversationId);
      }
    });
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage(input);
    }
  }

  return (
    <div className="flex flex-col h-full">
      {/* Messages area */}
      <div ref={scrollRef} className="flex-1 overflow-y-auto p-4 space-y-1">
        {messages.length === 0 && (
          <div className="flex flex-col items-center justify-center h-full text-gray-400">
            <div className="text-3xl mb-4">🤖</div>
            <div className="text-sm mb-6">倉庫 AI 助手 — 試下問我問題</div>
            <div className="flex flex-wrap gap-2 justify-center max-w-md">
              {SUGGESTIONS.map((s) => (
                <button
                  key={s}
                  onClick={() => sendMessage(s)}
                  className="px-3 py-1.5 text-xs bg-white border border-gray-200 rounded-full text-gray-600 hover:bg-indigo-50 hover:border-indigo-200 hover:text-indigo-700 transition-colors"
                >
                  {s}
                </button>
              ))}
            </div>
          </div>
        )}
        {messages.map((msg, i) => (
          <ChatMessage key={i} msg={msg} />
        ))}
        {loading && (
          <div className="text-xs text-gray-400 flex items-center gap-1">
            <span className="animate-pulse">⏳</span> AI 思考中...
          </div>
        )}
      </div>

      {/* Input area */}
      <div className="border-t border-gray-200 p-3">
        <div className="flex gap-2">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="輸入訊息..."
            disabled={loading}
            className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent disabled:bg-gray-100"
          />
          <button
            onClick={() => sendMessage(input)}
            disabled={loading || !input.trim()}
            className="px-4 py-2 bg-indigo-600 text-white text-sm rounded-lg hover:bg-indigo-700 disabled:bg-gray-300 disabled:cursor-not-allowed"
          >
            傳送
          </button>
        </div>
      </div>
    </div>
  );
}
