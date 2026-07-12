import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { guideApi } from '@/lib/api';
import type { GuideCategory, Guide } from '@/types';
import { BookOpen, Search, ChevronRight } from 'lucide-react';

export default function GuidesPage() {
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [search, setSearch] = useState('');

  const { data: categories } = useQuery({ queryKey: ['guide-categories'], queryFn: () => guideApi.categories() });

  const { data: guides } = useQuery({
    queryKey: ['guides', selectedCategory],
    queryFn: () => selectedCategory ? guideApi.list(selectedCategory) : Promise.resolve([]),
    enabled: !!selectedCategory,
  });

  const { data: searchResults } = useQuery({
    queryKey: ['guides-search', search],
    queryFn: () => search.length >= 2 ? guideApi.search(search) : Promise.resolve([]),
    enabled: search.length >= 2,
  });

  const displayGuides = search.length >= 2 ? searchResults : guides;

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-bold flex items-center gap-2">
          <BookOpen className="w-6 h-6 text-primary-600" />Guides
        </h1>
        <p className="text-slate-500 text-sm mt-1">Step-by-step guides for every platform</p>
      </div>

      {/* Search */}
      <div className="relative mb-6">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
        <input type="text" value={search} onChange={(e) => setSearch(e.target.value)}
          className="input pl-10" placeholder="Search guides..." />
      </div>

      {/* Categories */}
      {!search && (
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3 mb-8">
          {categories?.map((cat: GuideCategory) => (
            <button key={cat.id} onClick={() => setSelectedCategory(cat.slug)}
              className={`card p-4 text-left transition-all hover:shadow-md ${
                selectedCategory === cat.slug ? 'ring-2 ring-primary-500 bg-primary-50 dark:bg-primary-900/10' : ''
              }`}>
              <h3 className="font-semibold text-sm">{cat.name}</h3>
              <p className="text-xs text-slate-500 mt-1 line-clamp-2">{cat.description}</p>
            </button>
          ))}
        </div>
      )}

      {/* Guides list */}
      {displayGuides && displayGuides.length > 0 && (
        <div className="space-y-3">
          {displayGuides.map((guide: Guide) => (
            <Link key={guide.id} to={`/guides/${guide.slug}`}
              className="card-hover p-4 flex items-center justify-between group">
              <div>
                <h3 className="font-semibold group-hover:text-primary-600 transition-colors">{guide.title}</h3>
                <p className="text-sm text-slate-500 mt-0.5 line-clamp-1">{guide.description}</p>
                {guide.difficulty && (
                  <span className="inline-block mt-1.5 text-xs px-2 py-0.5 rounded-full bg-slate-100 dark:bg-slate-700 text-slate-600">
                    {guide.difficulty}
                  </span>
                )}
              </div>
              <ChevronRight className="w-5 h-5 text-slate-400 group-hover:text-primary-600 transition-colors" />
            </Link>
          ))}
        </div>
      )}

      {displayGuides && displayGuides.length === 0 && selectedCategory && (
        <div className="card p-8 text-center text-slate-500">
          <p>No guides in this category yet.</p>
        </div>
      )}
    </div>
  );
}
