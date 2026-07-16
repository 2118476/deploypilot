import { useState, useRef, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { copilotApi } from '@/lib/api';
import type { CopilotMessage } from '@/types';
import { Bot, Send, Trash2, User as UserIcon, ArrowRight, Loader2 } from 'lucide-react';

const SUGGESTED = [
  'What is happening?',
  'What has already been completed?',
  'Why did deployment fail?',
  'What do I need to do?',
  'Which variables are missing?',
  'Is Supabase connected?',
];

export default function CopilotPanel({ projectId }: { projectId: number }) {
  const qc = useQueryClient();
  const [input, setInput] = useState('');
  const listRef = useRef<HTMLDivElement>(null);

  const { data: conversation, isLoading } = useQuery({
    queryKey: ['copilot', projectId],
    queryFn: () => copilotApi.current(projectId),
    enabled: !!projectId,
  });

  const send = useMutation({
    mutationFn: (message: string) => copilotApi.send(projectId, message),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['copilot', projectId] });
      qc.invalidateQueries({ queryKey: ['project-status', projectId] });
      qc.invalidateQueries({ queryKey: ['project-activity', projectId] });
    },
  });

  const clear = useMutation({
    mutationFn: () => copilotApi.clear(projectId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['copilot', projectId] }),
  });

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight });
  }, [conversation?.messages?.length, send.isPending]);

  const submit = (text: string) => {
    const t = text.trim();
    if (!t || send.isPending) return;
    setInput('');
    send.mutate(t);
  };

  const messages = conversation?.messages ?? [];
  const aiAvailable = conversation?.aiAvailable ?? false;

  return (
    <div className="card flex flex-col h-[32rem]">
      <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200 dark:border-slate-700">
        <div className="flex items-center gap-2">
          <Bot className="w-5 h-5 text-primary-600" />
          <h3 className="font-semibold">Project Copilot</h3>
          {!aiAvailable && (
            <span className="text-xs text-amber-600 bg-amber-50 dark:bg-amber-900/20 px-2 py-0.5 rounded">
              deterministic mode
            </span>
          )}
        </div>
        <button
          onClick={() => clear.mutate()}
          disabled={clear.isPending || messages.length === 0}
          className="text-slate-400 hover:text-red-500 disabled:opacity-40"
          title="Clear conversation"
        >
          <Trash2 className="w-4 h-4" />
        </button>
      </div>

      <div ref={listRef} className="flex-1 overflow-y-auto p-4 space-y-4">
        {isLoading && <p className="text-sm text-slate-400">Loading…</p>}
        {!isLoading && messages.length === 0 && (
          <div className="text-sm text-slate-500">
            <p className="mb-3">Ask about your project. The Copilot answers from real records and never changes anything without your confirmation.</p>
            <div className="flex flex-wrap gap-2">
              {SUGGESTED.map((q) => (
                <button key={q} onClick={() => submit(q)}
                  className="text-xs px-2.5 py-1 rounded-full border border-slate-300 dark:border-slate-600 hover:bg-slate-100 dark:hover:bg-slate-800">
                  {q}
                </button>
              ))}
            </div>
          </div>
        )}
        {messages.map((m) => <MessageBubble key={m.id} message={m} projectId={projectId} />)}
        {send.isPending && (
          <div className="flex items-center gap-2 text-sm text-slate-400">
            <Loader2 className="w-4 h-4 animate-spin" /> Thinking…
          </div>
        )}
      </div>

      <div className="p-3 border-t border-slate-200 dark:border-slate-700">
        <div className="flex items-end gap-2">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); submit(input); } }}
            rows={1}
            placeholder="Ask the Copilot…"
            className="input flex-1 resize-none"
          />
          <button onClick={() => submit(input)} disabled={send.isPending || !input.trim()}
            className="btn-primary shrink-0" aria-label="Send">
            <Send className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
}

function MessageBubble({ message, projectId }: { message: CopilotMessage; projectId: number }) {
  const isUser = message.role === 'USER';
  const proposed = message.proposedAction;
  return (
    <div className={`flex gap-2 ${isUser ? 'flex-row-reverse' : ''}`}>
      <div className={`w-7 h-7 rounded-full flex items-center justify-center shrink-0 ${isUser ? 'bg-slate-200 dark:bg-slate-700' : 'bg-primary-100 dark:bg-primary-900/40'}`}>
        {isUser ? <UserIcon className="w-4 h-4" /> : <Bot className="w-4 h-4 text-primary-600" />}
      </div>
      <div className={`max-w-[85%] ${isUser ? 'text-right' : ''}`}>
        <div className={`inline-block text-left rounded-lg px-3 py-2 text-sm whitespace-pre-wrap ${isUser ? 'bg-primary-600 text-white' : 'bg-slate-100 dark:bg-slate-800'}`}>
          {message.content}
        </div>
        {proposed && proposed.type !== 'NONE' && (
          <div className="mt-2">
            <Link to={`/projects/${projectId}/automate`}
              className="inline-flex items-center gap-1 text-xs font-medium text-primary-600 hover:underline">
              Review the deployment plan <ArrowRight className="w-3 h-3" />
            </Link>
          </div>
        )}
      </div>
    </div>
  );
}
