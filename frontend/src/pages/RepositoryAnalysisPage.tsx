import { useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { AxiosError } from 'axios';
import { analysisApi, blueprintApi, projectApi } from '@/lib/api';
import type { ApiResponse, StackDetection, StackDetectionResult } from '@/types';
import TerminalBlock from '@/components/TerminalBlock';
import {
  Search, ShieldCheck, Github, RefreshCw, AlertTriangle, FileCode,
  CheckCircle2, ChevronDown, ChevronUp, ArrowLeft, Loader2, DraftingCompass
} from 'lucide-react';

const CATEGORY_LABELS: Record<string, string> = {
  FRONTEND_FRAMEWORK: 'Frontend framework',
  BACKEND_FRAMEWORK: 'Backend framework',
  LANGUAGE: 'Language',
  BUILD_TOOL: 'Build tool',
  PACKAGE_MANAGER: 'Package manager',
  DATABASE: 'Database',
  CONTAINER: 'Containers',
  HOSTING: 'Hosting configuration',
  EXTERNAL_SERVICE: 'External services',
};

const CONFIDENCE_STYLES: Record<string, string> = {
  HIGH: 'bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-400',
  MEDIUM: 'bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-400',
  LOW: 'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300',
};

const CLASSIFICATION_STYLES: Record<string, { label: string; cls: string }> = {
  SECRET_OR_SENSITIVE: { label: 'Secret / sensitive', cls: 'bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400' },
  PUBLIC_CONFIGURATION: { label: 'Public config', cls: 'bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-400' },
  CONFIGURATION: { label: 'Configuration', cls: 'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300' },
};

function errorMessage(err: unknown): string {
  if (err instanceof AxiosError) {
    const data = err.response?.data as ApiResponse<unknown> | undefined;
    if (data?.message) return data.message;
  }
  if (err instanceof Error && err.message) return err.message;
  return 'Something went wrong. Please try again.';
}

export default function RepositoryAnalysisPage() {
  const { id } = useParams<{ id: string }>();
  const projectId = Number(id);
  const qc = useQueryClient();
  const nav = useNavigate();
  const [repository, setRepository] = useState('');
  const [showFiles, setShowFiles] = useState(false);

  const blueprintMut = useMutation({
    mutationFn: () => blueprintApi.generate(projectId),
    onSuccess: () => nav(`/projects/${projectId}/blueprint`),
  });

  const { data: project } = useQuery({
    queryKey: ['project', projectId],
    queryFn: () => projectApi.get(projectId),
  });

  const { data: analysis, isLoading } = useQuery({
    queryKey: ['analysis', projectId],
    queryFn: () => analysisApi.latest(projectId),
    retry: false,
  });

  const runMut = useMutation({
    mutationFn: (repo: string) => analysisApi.run(projectId, repo),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['analysis', projectId] }),
    onError: () => qc.invalidateQueries({ queryKey: ['analysis', projectId] }),
  });

  const effectiveRepo = repository || analysis?.repository || project?.githubUrl || '';
  const result = analysis?.status === 'COMPLETED' ? analysis.result : undefined;

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (effectiveRepo.trim() && !runMut.isPending) runMut.mutate(effectiveRepo.trim());
  };

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
      {/* Header */}
      <Link to="/projects" className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-primary-600 mb-4">
        <ArrowLeft className="w-4 h-4" /> Back to projects
      </Link>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-2">
        <h1 className="text-2xl font-bold text-slate-900 dark:text-slate-100">
          Repository analysis{project ? ` — ${project.name}` : ''}
        </h1>
      </div>

      {/* Read-only notice */}
      <div className="card p-4 mb-6 flex items-start gap-3 border-primary-200 dark:border-primary-800 bg-primary-50/50 dark:bg-primary-900/10">
        <ShieldCheck className="w-5 h-5 text-primary-600 shrink-0 mt-0.5" />
        <div className="text-sm text-slate-600 dark:text-slate-300">
          <span className="font-medium text-slate-900 dark:text-slate-100">Read-only analysis.</span>{' '}
          DeployPilot is analysing your repository. It will not modify your code or deploy anything.
        </div>
      </div>

      {/* Repository form */}
      <form onSubmit={submit} className="card p-4 mb-6">
        <label htmlFor="repository" className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
          GitHub repository
        </label>
        <div className="flex flex-col sm:flex-row gap-2">
          <div className="relative flex-1">
            <Github className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              id="repository"
              className="input pl-9 w-full"
              placeholder="owner/name or https://github.com/owner/name"
              value={repository || effectiveRepo}
              onChange={(e) => setRepository(e.target.value)}
              maxLength={200}
            />
          </div>
          <button type="submit" className="btn-primary whitespace-nowrap" disabled={runMut.isPending || !effectiveRepo.trim()}>
            {runMut.isPending
              ? <span className="inline-flex items-center gap-2"><Loader2 className="w-4 h-4 animate-spin" /> Analysing…</span>
              : <span className="inline-flex items-center gap-2">
                  {analysis ? <RefreshCw className="w-4 h-4" /> : <Search className="w-4 h-4" />}
                  {analysis ? 'Re-run analysis' : 'Analyse repository'}
                </span>}
          </button>
        </div>
        <p className="text-xs text-slate-500 mt-2">
          Private repositories require the backend to be configured with a read-only GitHub token.
        </p>
      </form>

      {/* Progress */}
      {runMut.isPending && (
        <div className="card p-6 mb-6 text-center">
          <Loader2 className="w-6 h-6 animate-spin mx-auto text-primary-600 mb-3" />
          <p className="text-sm text-slate-600 dark:text-slate-300">
            Reading configuration files and detecting the technology stack…
          </p>
        </div>
      )}

      {/* Run error */}
      {runMut.isError && !runMut.isPending && (
        <div className="card p-4 mb-6 border-red-200 dark:border-red-900 bg-red-50/50 dark:bg-red-900/10 flex items-start gap-3">
          <AlertTriangle className="w-5 h-5 text-red-500 shrink-0 mt-0.5" />
          <p className="text-sm text-red-700 dark:text-red-400">{errorMessage(runMut.error)}</p>
        </div>
      )}

      {/* Stored failure */}
      {analysis?.status === 'FAILED' && !runMut.isPending && !runMut.isError && (
        <div className="card p-4 mb-6 border-red-200 dark:border-red-900 bg-red-50/50 dark:bg-red-900/10 flex items-start gap-3">
          <AlertTriangle className="w-5 h-5 text-red-500 shrink-0 mt-0.5" />
          <p className="text-sm text-red-700 dark:text-red-400">
            The last analysis of <span className="font-mono">{analysis.repository}</span> failed: {analysis.errorMessage}
          </p>
        </div>
      )}

      {/* Empty state */}
      {!isLoading && !analysis && !runMut.isPending && (
        <div className="card p-8 text-center text-sm text-slate-500">
          No analysis yet. Enter a repository above to detect its technology stack.
        </div>
      )}

      {/* Blueprint action after a successful analysis */}
      {analysis?.status === 'COMPLETED' && !runMut.isPending && (
        <div className="card p-4 mb-6 flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm text-slate-600 dark:text-slate-300">
            Analysis complete — turn it into a project-specific deployment plan.
          </p>
          <button onClick={() => blueprintMut.mutate()} disabled={blueprintMut.isPending} className="btn-primary text-sm">
            {blueprintMut.isPending
              ? <span className="inline-flex items-center gap-2"><Loader2 className="w-4 h-4 animate-spin" /> Generating…</span>
              : <span className="inline-flex items-center gap-2"><DraftingCompass className="w-4 h-4" /> Generate deployment blueprint</span>}
          </button>
          {blueprintMut.isError && (
            <p className="text-sm text-red-600 dark:text-red-400 w-full">{errorMessage(blueprintMut.error)}</p>
          )}
        </div>
      )}

      {/* Results */}
      {result && !runMut.isPending && <Results result={result} showFiles={showFiles} setShowFiles={setShowFiles} />}
    </div>
  );
}

function Results({ result, showFiles, setShowFiles }: {
  result: StackDetectionResult;
  showFiles: boolean;
  setShowFiles: (v: boolean) => void;
}) {
  const grouped = new Map<string, StackDetection[]>();
  for (const d of result.detections) {
    const list = grouped.get(d.category) || [];
    list.push(d);
    grouped.set(d.category, list);
  }

  return (
    <div className="space-y-6">
      {/* Summary */}
      <div className="card p-4 flex flex-wrap items-center gap-3">
        <CheckCircle2 className="w-5 h-5 text-emerald-500" />
        <span className="font-mono text-sm">{result.repository}</span>
        <span className="text-xs px-2 py-0.5 rounded-full bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-400 font-medium">
          {result.structure === 'MONOREPO' ? 'Monorepo'
            : result.structure === 'SINGLE_APPLICATION' ? 'Single application' : 'Unknown structure'}
        </span>
      </div>

      {/* Warnings */}
      {result.warnings.length > 0 && (
        <div className="card p-4 border-amber-200 dark:border-amber-900 bg-amber-50/50 dark:bg-amber-900/10">
          <div className="flex items-center gap-2 mb-2">
            <AlertTriangle className="w-4 h-4 text-amber-500" />
            <h3 className="text-sm font-semibold text-amber-800 dark:text-amber-300">Warnings</h3>
          </div>
          <ul className="text-sm text-amber-700 dark:text-amber-400 space-y-1 list-disc list-inside">
            {result.warnings.map((w, i) => <li key={i}>{w}</li>)}
          </ul>
        </div>
      )}

      {/* Detections */}
      <div className="grid sm:grid-cols-2 gap-4">
        {[...grouped.entries()].map(([category, detections]) => (
          <div key={category} className="card p-4">
            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100 mb-3">
              {CATEGORY_LABELS[category] || category}
            </h3>
            <div className="space-y-3">
              {detections.map((d, i) => (
                <div key={i}>
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium text-sm text-slate-800 dark:text-slate-200">{d.name}</span>
                    {d.path && <span className="text-xs font-mono text-slate-500">{d.path}/</span>}
                    <span className={`text-[11px] px-1.5 py-0.5 rounded font-medium ${CONFIDENCE_STYLES[d.confidence] || CONFIDENCE_STYLES.LOW}`}>
                      {d.confidence}
                    </span>
                  </div>
                  <ul className="mt-1 space-y-0.5">
                    {d.evidence.map((e, j) => (
                      <li key={j} className="text-xs text-slate-500 flex items-start gap-1.5">
                        <FileCode className="w-3 h-3 mt-0.5 shrink-0" />
                        <span className="break-all">{e}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>

      {/* Environment variables */}
      {result.environmentVariables.length > 0 && (
        <div className="card p-4">
          <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100 mb-1">Environment variables</h3>
          <p className="text-xs text-slate-500 mb-3">Names and sources only — values are never read or shown.</p>
          <div className="space-y-2">
            {result.environmentVariables.map((v, i) => {
              const style = CLASSIFICATION_STYLES[v.classification] || CLASSIFICATION_STYLES.CONFIGURATION;
              return (
                <div key={i} className="flex flex-wrap items-center gap-2 text-sm">
                  <code className="font-mono text-slate-800 dark:text-slate-200">{v.name}</code>
                  <span className={`text-[11px] px-1.5 py-0.5 rounded font-medium ${style.cls}`}>{style.label}</span>
                  <span className="text-xs text-slate-400">from {v.source}</span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Commands */}
      {(result.buildCommands.length > 0 || result.startCommands.length > 0) && (
        <div className="grid sm:grid-cols-2 gap-4">
          {result.buildCommands.length > 0 && (
            <div className="card p-4">
              <h3 className="text-sm font-semibold mb-3 text-slate-900 dark:text-slate-100">Build commands</h3>
              <div className="space-y-2">
                {result.buildCommands.map((c, i) => <TerminalBlock key={i} command={c} />)}
              </div>
            </div>
          )}
          {result.startCommands.length > 0 && (
            <div className="card p-4">
              <h3 className="text-sm font-semibold mb-3 text-slate-900 dark:text-slate-100">Start commands</h3>
              <div className="space-y-2">
                {result.startCommands.map((c, i) => <TerminalBlock key={i} command={c} />)}
              </div>
            </div>
          )}
        </div>
      )}

      {/* Files */}
      <div className="card p-4">
        <button
          type="button"
          onClick={() => setShowFiles(!showFiles)}
          className="w-full flex items-center justify-between text-sm font-semibold text-slate-900 dark:text-slate-100">
          <span>Files analysed ({result.analyzedFiles.length}) · skipped ({result.skippedFiles.length})</span>
          {showFiles ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
        </button>
        {showFiles && (
          <div className="mt-3 grid sm:grid-cols-2 gap-4 text-xs font-mono">
            <div>
              <p className="font-sans font-medium text-slate-500 mb-1.5">Analysed</p>
              <ul className="space-y-0.5 text-slate-600 dark:text-slate-300">
                {result.analyzedFiles.map((f, i) => <li key={i} className="break-all">{f}</li>)}
              </ul>
            </div>
            <div>
              <p className="font-sans font-medium text-slate-500 mb-1.5">Skipped</p>
              <ul className="space-y-0.5 text-slate-500">
                {result.skippedFiles.length === 0 && <li className="font-sans">None</li>}
                {result.skippedFiles.map((f, i) => <li key={i} className="break-all">{f}</li>)}
              </ul>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
