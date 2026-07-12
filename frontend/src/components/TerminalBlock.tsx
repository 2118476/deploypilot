import { useState } from 'react';
import { Copy, Check } from 'lucide-react';

interface Props {
  command: string;
  explanation?: string;
}

export default function TerminalBlock({ command, explanation }: Props) {
  const [copied, setCopied] = useState(false);

  const copy = () => {
    navigator.clipboard.writeText(command).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  return (
    <div className="rounded-lg overflow-hidden">
      <div className="flex items-center justify-between bg-slate-800 px-3 py-1.5">
        <div className="flex items-center gap-1.5">
          <div className="w-2.5 h-2.5 rounded-full bg-red-500" />
          <div className="w-2.5 h-2.5 rounded-full bg-amber-500" />
          <div className="w-2.5 h-2.5 rounded-full bg-green-500" />
        </div>
        <button onClick={copy}
          className="flex items-center gap-1 text-xs text-slate-400 hover:text-slate-200 transition-colors">
          {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
          {copied ? 'Copied' : 'Copy'}
        </button>
      </div>
      <pre className="bg-slate-900 text-slate-100 p-4 text-sm font-mono overflow-x-auto leading-relaxed">
        <code>{command}</code>
      </pre>
      {explanation && (
        <div className="bg-slate-800/50 px-4 py-2 text-xs text-slate-400 border-t border-slate-700">
          {explanation}
        </div>
      )}
    </div>
  );
}
