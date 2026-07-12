interface Props {
  current: number;
  total: number;
  label?: string;
}

export default function ProgressBar({ current, total, label }: Props) {
  const pct = total > 0 ? Math.round((current / total) * 100) : 0;
  return (
    <div className="w-full">
      <div className="flex items-center justify-between mb-1.5">
        <span className="text-sm text-slate-600 dark:text-slate-400">{label || 'Progress'}</span>
        <span className="text-sm font-medium">{pct}%</span>
      </div>
      <div className="w-full bg-slate-200 dark:bg-slate-700 rounded-full h-2.5">
        <div className="bg-primary-600 h-2.5 rounded-full transition-all duration-500" style={{ width: `${pct}%` }} />
      </div>
      <div className="text-xs text-slate-500 mt-1">{current} of {total} steps</div>
    </div>
  );
}
