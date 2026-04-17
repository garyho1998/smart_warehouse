import { createContext, useContext, useState, useRef } from 'react';

const ChatContext = createContext(null);

export function ChatProvider({ children }) {
  const [messages, setMessages] = useState([]);
  const [conversationId, setConversationId] = useState(null);
  const [loading, setLoading] = useState(false);
  const [graphResult, setGraphResult] = useState(null);
  const [selectedNode, setSelectedNode] = useState(null);
  const [chatWidth, setChatWidth] = useState(40);
  const scrollRef = useRef(null);

  return (
    <ChatContext.Provider
      value={{
        messages, setMessages,
        conversationId, setConversationId,
        loading, setLoading,
        graphResult, setGraphResult,
        selectedNode, setSelectedNode,
        chatWidth, setChatWidth,
        scrollRef,
      }}
    >
      {children}
    </ChatContext.Provider>
  );
}

export function useChatContext() {
  const ctx = useContext(ChatContext);
  if (!ctx) throw new Error('useChatContext must be used inside ChatProvider');
  return ctx;
}
