import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { glossaryApi } from '@/lib/api';
import type { GlossaryTerm } from '@/types';
import { Search, BookOpen, ArrowRight } from 'lucide-react';

export default function GlossaryPage() {
  const [search, setSearch] = useState('');
  const { data: terms, isLoading } = useQuery({ queryKey: ['glossary'], queryFn: () => glossaryApi.list() });

  const { data: searchResults } = useQuery({
    queryKey: ['glossary-search', search],
    queryFn: () => search.length >= 2 ? glossaryApi.search(search) : Promise.resolve([]),
    enabled: search.length >= 2,
  });

  const display = search.length >= 2 ? searchResults : terms;

  // Group by first letter
  const grouped = (display || []).reduce<Record<string, GlossaryTerm[]>>((acc, term) => {
    const letter = term.term[0].toUpperCase();
    if (!acc[letter]) acc[letter] = [];
    acc[letter].push(term);
    return acc;
  }, {});

  const letters = Object.keys(grouped).sort();

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-bold flex items-center gap-2">
          <BookOpen className="w-6 h-6 text-primary-600" />Glossary
        </h1>
        <p className="text-slate-500 text-sm mt-1">Learn the language of deployment</p>
      </div>

      {/* Search */}
      <div className="relative mb-8">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
        <input type="text" value={search} onChange={(e) => setSearch(e.target.value)}
          className="input pl-10" placeholder="Search terms..." />
      </div>

      {/* Alphabet filter */}
      {!search && (
        <div className="flex flex-wrap gap-1.5 mb-6">
          {letters.map((l) => (
            <a key={l} href={`#section-${l}`}
              className="w-8 h-8 flex items-center justify-center rounded-lg bg-slate-100 dark:bg-slate-800 text-sm font-medium text-slate-600 hover:bg-primary-100 hover:text-primary-700 transition-colors">
              {l}
            </a>
          ))}
        </div>
      )}

      {/* Terms */}
      {isLoading ? (
        <div className="space-y-4 animate-pulse">
          {[1, 2, 3].map((i) => <div key={i} className="h-16 bg-slate-200 dark:bg-slate-700 rounded-lg" />)}
        </div>
      ) : display && display.length > 0 ? (
        <div className="space-y-8">
          {letters.map((letter) => (
            <div key={letter} id={`section-${letter}`}>
              {!search && <h2 className="text-2xl font-bold text-slate-300 dark:text-slate-600 mb-3">{letter}</h2>}
              <div className="space-y-3">
                {grouped[letter].map((term) => (
                  <div key={term.id} className="card p-4">
                    <h3 className="font-semibold text-primary-700 dark:text-primary-400">{term.term}</h3>
                    <p className="text-sm text-slate-600 dark:text-slate-400 mt-1">{term.definition}</p>
                    {term.example && (
                      <p className="text-xs text-slate-500 mt-2 italic">Example: {term.example}</p>
                    )}
                    {term.relatedTerms && (
                      <div className="flex flex-wrap gap-1.5 mt-3">
                        {term.relatedTerms.split(',').map((rt) => (
                          <span key={rt} className="badge-gray text-xs flex items-center gap-1">
                            <ArrowRight className="w-3 h-3" />{rt.trim()}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="card p-8 text-center text-slate-500">
          <BookOpen className="w-10 h-10 mx-auto mb-3 text-slate-300" />
          <p>No terms found.</p>
        </div>
      )}
    </div>
  );
}
