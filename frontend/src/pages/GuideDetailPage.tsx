import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { guideApi } from '@/lib/api';
import TerminalBlock from '@/components/TerminalBlock';
import { ArrowLeft, BookOpen, AlertTriangle } from 'lucide-react';

export default function GuideDetailPage() {
  const { slug } = useParams<{ slug: string }>();
  const { data: guide, isLoading } = useQuery({
    queryKey: ['guide', slug],
    queryFn: () => guideApi.get(slug!),
  });

  if (isLoading) {
    return <div className="max-w-4xl mx-auto px-4 py-8 animate-pulse"><div className="h-8 bg-slate-200 rounded w-1/2 mb-4" /><div className="h-4 bg-slate-200 rounded w-full mb-2" /><div className="h-4 bg-slate-200 rounded w-3/4" /></div>;
  }

  if (!guide) {
    return <div className="max-w-4xl mx-auto px-4 py-8 text-center"><h2 className="text-xl font-bold">Guide not found</h2></div>;
  }

  // Simple markdown-like parser
  const renderContent = (content: string) => {
    if (!content) return null;
    const lines = content.split('\n');
    const elements: JSX.Element[] = [];
    let key = 0;

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];

      if (line.startsWith('## ')) {
        elements.push(<h2 key={key++} className="text-xl font-bold mt-8 mb-3">{line.slice(3)}</h2>);
      } else if (line.startsWith('### ')) {
        elements.push(<h3 key={key++} className="text-lg font-semibold mt-6 mb-2">{line.slice(4)}</h3>);
      } else if (line.startsWith('> ')) {
        elements.push(
          <div key={key++} className="my-4 p-4 bg-amber-50 dark:bg-amber-900/10 border-l-4 border-amber-400 rounded-r-lg">
            <p className="text-sm text-amber-800 dark:text-amber-300">{line.slice(2)}</p>
          </div>
        );
      } else if (line.startsWith('```')) {
        const lang = line.slice(3).trim();
        let code = '';
        i++;
        while (i < lines.length && !lines[i].startsWith('```')) {
          code += lines[i] + '\n';
          i++;
        }
        elements.push(<div key={key++} className="my-4"><TerminalBlock command={code.trim()} /></div>);
      } else if (line.startsWith('- ')) {
        const items: string[] = [];
        while (i < lines.length && lines[i].startsWith('- ')) {
          items.push(lines[i].slice(2));
          i++;
        }
        i--;
        elements.push(
          <ul key={key++} className="my-3 space-y-1.5">
            {items.map((item, idx) => (
              <li key={idx} className="flex items-start gap-2 text-sm text-slate-600 dark:text-slate-400">
                <span className="text-primary-500 mt-1">-</span>{item}
              </li>
            ))}
          </ul>
        );
      } else if (line.startsWith('1. ')) {
        const items: string[] = [];
        let num = 1;
        while (i < lines.length && lines[i].startsWith(`${num}. `)) {
          items.push(lines[i].slice(`${num}. `.length));
          num++;
          i++;
        }
        i--;
        elements.push(
          <ol key={key++} className="my-3 space-y-2">
            {items.map((item, idx) => (
              <li key={idx} className="flex items-start gap-2 text-sm text-slate-600 dark:text-slate-400">
                <span className="font-semibold text-primary-600 min-w-[1.5rem]">{idx + 1}.</span>{item}
              </li>
            ))}
          </ol>
        );
      } else if (line.trim()) {
        elements.push(<p key={key++} className="my-2 text-sm text-slate-600 dark:text-slate-400 leading-relaxed">{line}</p>);
      }
    }

    return elements;
  };

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 py-8">
      <Link to="/guides" className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-primary-600 mb-4">
        <ArrowLeft className="w-4 h-4" />Back to Guides
      </Link>

      <div className="card p-6 md:p-8">
        <div className="flex items-center gap-2 mb-3">
          <BookOpen className="w-5 h-5 text-primary-600" />
          {guide.difficulty && (
            <span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 dark:bg-slate-700 text-slate-600">
              {guide.difficulty}
            </span>
          )}
        </div>
        <h1 className="text-2xl md:text-3xl font-bold mb-3">{guide.title}</h1>
        <p className="text-slate-500 mb-6">{guide.description}</p>

        {guide.content ? (
          <div>{renderContent(guide.content)}</div>
        ) : guide.sections && guide.sections.length > 0 ? (
          <div className="space-y-6">
            {guide.sections.map((section) => (
              <div key={section.id}>
                <h3 className="text-lg font-semibold mb-2">{section.title}</h3>
                <div className="text-sm text-slate-600 dark:text-slate-400 leading-relaxed whitespace-pre-line">
                  {section.content}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="flex items-start gap-3 p-4 bg-amber-50 dark:bg-amber-900/10 rounded-lg">
            <AlertTriangle className="w-5 h-5 text-amber-600 shrink-0" />
            <p className="text-sm text-amber-700 dark:text-amber-300">This guide is a placeholder. Detailed content will be added.</p>
          </div>
        )}
      </div>
    </div>
  );
}
