import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { AxiosError } from 'axios';
import { importApi } from '@/lib/api';
import type { ApiResponse, StackDetectionResult } from '@/types';
import {
  Github, PencilRuler, ShieldCheck, Sparkles, Loader2, AlertTriangle,
  ArrowRight, ArrowLeft, FileCode, CheckCircle2
} from 'lucide-react';

const CONFIDENCE_STYLES: Record<string, string> = {
  HIGH: 'bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-400',
  MEDIUM: 'bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-400',
  LOW: 'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300',
};

function errorMessage(err: unknown): string {
  if (err instanceof AxiosError) {
    const data = err.response?.data as ApiResponse<unknown> | undefined;
    if (data?.message) return data.message;
  }
  if (err instanceof Error && err.message) return err.message;
  return 'Something went wrong. Please try again.';
}

export default function NewProjectPage() {
  const nav = useNavigate();
  const [mode, setMode] = useState<'choose' | 'import'>('choose');
  const [repository, setRepository] = useState('');
  const [preview, setPreview] = useState<StackDetectionResult | null>(null);

  const previewMut = useMutation({
    mutationFn: (repo: string) => importApi.preview(repo),
    onSuccess: (result) => setPreview(result),
  });

  const importMut = useMutation({
    mutationFn: (repo: string) => importApi.importRepository(repo),
    onSuccess: (result) => nav(`/projects/${result.projectId}/blueprint`),
  });

  const busy = previewMut.isPending || importMut.isPending;
  const error = previewMut.isError ? previewMut.error : importMut.isError ? importMut.error : null;

  if (mode === 'choose') {
    return (
      <div className="max-w-3xl mx-auto px-4 sm:px-6 py-10">
        <h1 className="text-2xl font-bold text-slate-900 dark:text-slate-100 mb-2">Create a new project</h1>
        <p className="text-sm text-slate-500 mb-8">Choose how DeployPilot should learn about your application.</p>
        <div className="grid sm:grid-cols-2 gap-4">
          <button onClick={() => setMode('import')}
            className="card-hover p-6 text-left border-2 border-primary-300 dark:border-primary-700 relative">
            <span className="absolute top-3 right-3 text-[11px] font-semibold px-2 py-0.5 rounded-full bg-primary-600 text-white">
              Recommended
            </span>
            <div className="p-2.5 bg-primary-50 dark:bg-primary-900/20 rounded-lg w-fit mb-3">
              <Github className="w-6 h-6 text-primary-600" />
            </div>
            <h2 className="font-semibold text-slate-900 dark:text-slate-100 mb-1.5">Import from GitHub</h2>
            <p className="text-sm text-slate-500">
              DeployPilot analyses your repository (read-only), detects the stack automatically and prepares
              a deployment blueprint — no manual questions.
            </p>
          </button>
          <Link to="/projects/new/manual" className="card-hover p-6 block">
            <div className="p-2.5 bg-slate-100 dark:bg-slate-700 rounded-lg w-fit mb-3">
              <PencilRuler className="w-6 h-6 text-slate-500" />
            </div>
            <h2 className="font-semibold text-slate-900 dark:text-slate-100 mb-1.5">Set up manually</h2>
            <p className="text-sm text-slate-500">
              Answer the guided wizard yourself — for projects that are not on GitHub, unsupported stacks,
              or when you prefer full manual control.
            </p>
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-10">
      <button onClick={() => { setMode('choose'); setPreview(null); }}
        className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-primary-600 mb-4">
        <ArrowLeft className="w-4 h-4" /> Back
      </button>
      <h1 className="text-2xl font-bold text-slate-900 dark:text-slate-100 mb-2">Import from GitHub</h1>

      <div className="card p-4 mb-6 flex items-start gap-3 border-primary-200 dark:border-primary-800 bg-primary-50/50 dark:bg-primary-900/10">
        <ShieldCheck className="w-5 h-5 text-primary-600 shrink-0 mt-0.5" />
        <p className="text-sm text-slate-600 dark:text-slate-300">
          <span className="font-medium text-slate-900 dark:text-slate-100">Read-only.</span>{' '}
          DeployPilot analyses your repository. It will not modify your code, create external services or deploy anything.
        </p>
      </div>

      {/* Step 1: repository input */}
      <form className="card p-4 mb-6"
        onSubmit={(e) => { e.preventDefault(); if (repository.trim() && !busy) { setPreview(null); previewMut.mutate(repository.trim()); } }}>
        <label htmlFor="repo" className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
          GitHub repository
        </label>
        <div className="flex flex-col sm:flex-row gap-2">
          <div className="relative flex-1">
            <Github className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input id="repo" className="input pl-9 w-full" maxLength={200}
              placeholder="owner/name or https://github.com/owner/name"
              value={repository} onChange={(e) => setRepository(e.target.value)} />
          </div>
          <button type="submit" className="btn-primary whitespace-nowrap" disabled={busy || !repository.trim()}>
            {previewMut.isPending
              ? <span className="inline-flex items-center gap-2"><Loader2 className="w-4 h-4 animate-spin" /> Analysing…</span>
              : <span className="inline-flex items-center gap-2"><Sparkles className="w-4 h-4" /> Analyse</span>}
          </button>
        </div>
        <p className="text-xs text-slate-500 mt-2">
          Private repositories require the backend to be configured with a read-only GitHub token.
        </p>
      </form>

      {error && !busy && (
        <div className="card p-4 mb-6 border-red-200 dark:border-red-900 bg-red-50/50 dark:bg-red-900/10 flex items-start gap-3">
          <AlertTriangle className="w-5 h-5 text-red-500 shrink-0 mt-0.5" />
          <p className="text-sm text-red-700 dark:text-red-400">{errorMessage(error)}</p>
        </div>
      )}

      {/* Step 2: review detected stack, then confirm */}
      {preview && !previewMut.isPending && (
        <div className="space-y-4">
          <div className="card p-4">
            <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100 mb-1">Detected stack — please review</h2>
            <p className="text-xs text-slate-500 mb-3">
              Items marked <span className="font-medium">MEDIUM</span> or <span className="font-medium">LOW</span> are
              less certain; you can adjust platforms after the blueprint is created.
            </p>
            <div className="space-y-2">
              {preview.detections.map((d, i) => (
                <div key={i} className="flex flex-wrap items-center gap-2 text-sm">
                  <span className="text-xs text-slate-400 w-40 shrink-0">{d.category.replace(/_/g, ' ').toLowerCase()}</span>
                  <span className="font-medium text-slate-800 dark:text-slate-200">{d.name}</span>
                  {d.path && <span className="text-xs font-mono text-slate-500">{d.path}/</span>}
                  <span className={`text-[11px] px-1.5 py-0.5 rounded font-medium ${CONFIDENCE_STYLES[d.confidence]}`}>
                    {d.confidence}
                  </span>
                </div>
              ))}
              {preview.detections.length === 0 && (
                <p className="text-sm text-slate-500">Nothing recognisable was detected — consider the manual wizard instead.</p>
              )}
            </div>
            {preview.warnings.length > 0 && (
              <ul className="mt-3 text-xs text-amber-700 dark:text-amber-400 space-y-1">
                {preview.warnings.map((w, i) => (
                  <li key={i} className="flex items-start gap-1.5"><FileCode className="w-3.5 h-3.5 mt-0.5 shrink-0" />{w}</li>
                ))}
              </ul>
            )}
          </div>
          <button onClick={() => importMut.mutate(repository.trim())} disabled={busy || preview.detections.length === 0}
            className="btn-primary w-full sm:w-auto">
            {importMut.isPending
              ? <span className="inline-flex items-center gap-2"><Loader2 className="w-4 h-4 animate-spin" /> Creating project and blueprint…</span>
              : <span className="inline-flex items-center gap-2"><CheckCircle2 className="w-4 h-4" /> Looks right — create project and blueprint <ArrowRight className="w-4 h-4" /></span>}
          </button>
        </div>
      )}
    </div>
  );
}
