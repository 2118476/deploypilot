import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { troubleshootApi } from '@/lib/api';
import { Wrench, Send, AlertTriangle, CheckCircle, Loader2, Sparkles } from 'lucide-react';

const CATEGORIES = ['Git', 'Build', 'Deploy', 'CORS', 'Database', 'Auth', 'Other'];

export default function TroubleshootPage() {
  const [category, setCategory] = useState('Build');
  const [content, setContent] = useState('');
  const [result, setResult] = useState<{ aiResponse: string; redactedContent: string } | null>(null);

  const mutate = useMutation({
    mutationFn: () => troubleshootApi.submit({ errorType: category, content }),
    onSuccess: (data) => setResult({ aiResponse: data.aiResponse, redactedContent: data.redactedContent }),
  });

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-bold flex items-center gap-2">
          <Wrench className="w-6 h-6 text-primary-600" />AI Error Solver
        </h1>
        <p className="text-slate-500 text-sm mt-1">Paste an error and get step-by-step help</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Input */}
        <div className="card p-5">
          <div className="flex items-center gap-2 mb-4">
            <Sparkles className="w-4 h-4 text-primary-600" />
            <span className="font-semibold text-sm">Describe your problem</span>
          </div>

          {/* Category */}
          <div className="flex flex-wrap gap-2 mb-4">
            {CATEGORIES.map((c) => (
              <button key={c} onClick={() => setCategory(c)}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                  category === c ? 'bg-primary-600 text-white' : 'bg-slate-100 dark:bg-slate-800 text-slate-600'
                }`}>{c}</button>
            ))}
          </div>

          {/* Redaction notice */}
          <div className="flex items-start gap-2 p-3 rounded-lg bg-green-50 dark:bg-green-900/10 mb-4">
            <CheckCircle className="w-4 h-4 text-green-600 shrink-0 mt-0.5" />
            <p className="text-xs text-green-700 dark:text-green-400">
              Sensitive data like API keys, passwords, and tokens will be automatically redacted before sending to the AI.
            </p>
          </div>

          {/* Textarea */}
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            className="input min-h-[200px] font-mono text-sm mb-4"
            placeholder="Paste your error message, build log, or describe what went wrong..."
          />

          <button onClick={() => mutate.mutate()} disabled={!content.trim() || mutate.isPending}
            className="btn-primary w-full disabled:opacity-50">
            {mutate.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
            {mutate.isPending ? 'Analyzing...' : 'Get Help'}
          </button>

          {mutate.isError && (
            <p className="text-red-600 text-sm mt-3 flex items-center gap-1">
              <AlertTriangle className="w-4 h-4" />Failed to analyze. Please try again.
            </p>
          )}
        </div>

        {/* Result */}
        <div className="card p-5">
          <div className="flex items-center gap-2 mb-4">
            <Sparkles className="w-4 h-4 text-primary-600" />
            <span className="font-semibold text-sm">AI Analysis</span>
          </div>

          {result ? (
            <div className="space-y-4">
              {result.redactedContent !== '[REDACTED]' && result.redactedContent !== content && (
                <div className="p-3 rounded-lg bg-amber-50 dark:bg-amber-900/10 text-xs text-amber-700 dark:text-amber-400">
                  <span className="font-semibold">Note:</span> Sensitive information was redacted from your message before analysis.
                </div>
              )}
              <div className="prose dark:prose-invert prose-sm max-w-none">
                {result.aiResponse.split('\n').map((line, i) => {
                  if (line.match(/^#{1,3}\s/)) {
                    return <h3 key={i} className="text-lg font-bold mt-4 mb-2">{line.replace(/^#{1,3}\s/, '')}</h3>;
                  }
                  if (line.match(/^\d+\./)) {
                    return <div key={i} className="flex items-start gap-2 my-1"><span className="font-semibold text-primary-600">{line.match(/^\d+/)?.[0]}.</span><span>{line.replace(/^\d+\.\s/, '')}</span></div>;
                  }
                  if (line.startsWith('```')) return null;
                  if (line.trim() === '') return <div key={i} className="h-2" />;
                  return <p key={i} className="my-1">{line}</p>;
                })}
              </div>
            </div>
          ) : (
            <div className="flex flex-col items-center justify-center h-full min-h-[200px] text-slate-400 text-center">
              <Sparkles className="w-10 h-10 mb-3 text-slate-300" />
              <p className="text-sm">Submit an error to see AI-powered analysis</p>
              <p className="text-xs mt-1">The AI will suggest likely causes and fixes</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
