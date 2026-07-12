import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { commandApi } from '@/lib/api';
import type { CommandSnippet } from '@/types';
import TerminalBlock from '@/components/TerminalBlock';
import { Search, AlertTriangle, GitBranch, Info } from 'lucide-react';

const CATEGORIES = ['all', 'setup', 'branching', 'remote', 'undo', 'advanced'];

export default function GitCommandsPage() {
  const [category, setCategory] = useState('all');
  const [search, setSearch] = useState('');
  const [beginnerMode, setBeginnerMode] = useState(true);

  const { data: commands } = useQuery({
    queryKey: ['commands', category],
    queryFn: () => commandApi.list(category === 'all' ? undefined : category),
  });

  const filtered = commands?.filter((c: CommandSnippet) => {
    if (!search) return true;
    const q = search.toLowerCase();
    return c.title.toLowerCase().includes(q) || c.command.toLowerCase().includes(q) || c.description?.toLowerCase().includes(q);
  }) || [];

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <GitBranch className="w-6 h-6 text-primary-600" />Git Command Reference
          </h1>
          <p className="text-slate-500 text-sm mt-1">Searchable Git commands with explanations</p>
        </div>
        <button onClick={() => setBeginnerMode(!beginnerMode)} className="btn-secondary text-sm self-start">
          {beginnerMode ? 'Advanced Mode' : 'Beginner Mode'}
        </button>
      </div>

      {/* Workflow diagram */}
      <div className="card p-4 mb-6 bg-slate-50 dark:bg-slate-800/50">
        <div className="flex flex-wrap items-center justify-center gap-2 text-sm">
          <div className="px-3 py-1.5 bg-white dark:bg-slate-700 rounded-lg border border-slate-200 dark:border-slate-600 font-medium">Working Directory</div>
          <span className="text-slate-400">git add &rarr;</span>
          <div className="px-3 py-1.5 bg-amber-50 dark:bg-amber-900/20 rounded-lg border border-amber-200 dark:border-amber-800 font-medium text-amber-800 dark:text-amber-400">Staging Area</div>
          <span className="text-slate-400">git commit &rarr;</span>
          <div className="px-3 py-1.5 bg-green-50 dark:bg-green-900/20 rounded-lg border border-green-200 dark:border-green-800 font-medium text-green-800 dark:text-green-400">Local Repository</div>
          <span className="text-slate-400">git push &rarr;</span>
          <div className="px-3 py-1.5 bg-primary-50 dark:bg-primary-900/20 rounded-lg border border-primary-200 dark:border-primary-800 font-medium text-primary-800 dark:text-primary-400">GitHub</div>
          <span className="text-slate-400">&rarr;</span>
          <div className="px-3 py-1.5 bg-purple-50 dark:bg-purple-900/20 rounded-lg border border-purple-200 dark:border-purple-800 font-medium text-purple-800 dark:text-purple-400">Netlify/Render</div>
        </div>
      </div>

      {/* Search and filter */}
      <div className="flex flex-col sm:flex-row gap-3 mb-6">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
          <input type="text" value={search} onChange={(e) => setSearch(e.target.value)}
            className="input pl-10" placeholder="Search commands..." />
        </div>
        <div className="flex gap-2 overflow-x-auto pb-1">
          {CATEGORIES.map((c) => (
            <button key={c} onClick={() => setCategory(c)}
              className={`px-3 py-2 rounded-lg text-sm font-medium capitalize whitespace-nowrap transition-colors ${
                category === c ? 'bg-primary-600 text-white' : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 hover:bg-slate-200'
              }`}>{c}</button>
          ))}
        </div>
      </div>

      {/* Commands */}
      <div className="space-y-4">
        {filtered.map((cmd: CommandSnippet) => (
          <div key={cmd.id} className={`card p-5 ${cmd.destructive ? 'border-red-200 dark:border-red-900/30' : ''}`}>
            <div className="flex items-start justify-between mb-3">
              <div>
                <div className="flex items-center gap-2 mb-1">
                  <h3 className="font-semibold">{cmd.title}</h3>
                  {cmd.destructive && (
                    <span className="badge-red text-xs flex items-center gap-1">
                      <AlertTriangle className="w-3 h-3" />Destructive
                    </span>
                  )}
                </div>
                <p className="text-sm text-slate-500">{cmd.description}</p>
              </div>
              <span className="badge-gray text-xs capitalize">{cmd.category}</span>
            </div>

            <TerminalBlock command={cmd.command} explanation={beginnerMode ? cmd.explanation : undefined} />

            {beginnerMode && cmd.warning && (
              <div className="mt-3 flex items-start gap-2 p-3 rounded-lg bg-red-50 dark:bg-red-900/10 text-red-700 dark:text-red-400 text-sm">
                <Info className="w-4 h-4 shrink-0 mt-0.5" />
                {cmd.warning}
              </div>
            )}
          </div>
        ))}

        {filtered.length === 0 && (
          <div className="card p-8 text-center text-slate-500">
            <Search className="w-10 h-10 mx-auto mb-3 text-slate-300" />
            <p>No commands found matching your search.</p>
          </div>
        )}
      </div>
    </div>
  );
}
