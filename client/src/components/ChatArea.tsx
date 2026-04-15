import { useState } from "react";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { MessageBubble } from "./MessageBubble";
import { executeQuery } from "@/services/api";
import type { QueryResponse } from "@/services/api";

interface Message {
  id: number;
  role: "user" | "ai";
  content: string;
}

interface ChatAreaProps {
  onQueryResult?: (result: QueryResponse) => void;
}

export function ChatArea({ onQueryResult }: ChatAreaProps) {
  const [messages, setMessages] = useState<Message[]>([
    { id: 0, role: "ai", content: "你好！我是数据库助手，请输入你的查询。" },
  ]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSend = async () => {
    if (!input.trim() || loading) return;

    const userMsg: Message = {
      id: Date.now(),
      role: "user",
      content: input,
    };
    setMessages((prev) => [...prev, userMsg]);
    const userInput = input;
    setInput("");
    setLoading(true);

    try {
      const result = await executeQuery({
        connectionId: "demo",
        sql: userInput,
      });

      setMessages((prev) => [
        ...prev,
        {
          id: Date.now() + 1,
          role: "ai",
          content: `查询完成，返回 ${result.rowCount} 行数据，耗时 ${result.durationMs}ms。`,
        },
      ]);

      onQueryResult?.(result);
    } catch (err) {
      setMessages((prev) => [
        ...prev,
        {
          id: Date.now() + 1,
          role: "ai",
          content: `查询失败: ${err instanceof Error ? err.message : "未知错误"}`,
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {messages.map((msg) => (
          <MessageBubble
            key={msg.id}
            role={msg.role}
            content={msg.content}
          />
        ))}
      </div>
      <div className="border-t p-4">
        <div className="flex gap-2">
          <Textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                handleSend();
              }
            }}
            placeholder="输入查询，例如：查询 users 表的所有数据"
            className="min-h-[44px] resize-none"
            disabled={loading}
          />
          <Button onClick={handleSend} disabled={loading}>
            {loading ? "查询中..." : "发送"}
          </Button>
        </div>
      </div>
    </div>
  );
}
