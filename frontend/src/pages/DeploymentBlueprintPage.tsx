import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { AxiosError } from 'axios';
import { blueprintApi, projectApi } from '@/lib/api';
import type { ApiResponse, BlueprintComponent, BlueprintEnvVar, BlueprintFilePreview } from '@/types';
import {
  ShieldCheck, RefreshCw, AlertTriangle, AlertOctagon, Info, Loader2, ArrowLeft,
  Copy, Download, Check, ChevronDown, ChevronUp, Layers, Link2, KeyRound, ListOrdered,
  FileCode, Wand2, ScanSearch, Rocket
} from 'lucide-react';

const CONFIDENCE_STYLES: Record<string, string> = {
  HIGH: 'bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-400',
  MEDIUM: 'bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-400',
  LOW: 'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300',
};

const CLASSIFICATION_STYLES: Record<string, { label: string; cls: string }> = {
  SECRET_OR_SENSITIVE: { label: 'Secret', cls: 'bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400' },
  PUBLIC_PUBLISHABLE_CREDENTIAL: { label: 'Public credential', cls: 'bg-sky-50 dark:bg-sky-900/20 text-sky-700 dark:text-sky-400' },
  PUBLIC_CONFIGURATION: { label: 'Public', cls: 'bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-400' },
  CONFIGURATION: { label: 'Config', cls: 'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300' },
};

const SEVERITY_META = {
  BLOCKER: { icon: AlertOctagon, cls: 'border-red-200 dark:border-red-900 bg-red-50/50 dark:bg-red-900/10', text: 'text-red-700 dark:text-red-400' },
  WARNING: { icon: AlertTriangle, cls: 'border-amber-200 dark:border-amber-900 bg-amber-50/50 dark:bg-amber-900/10', text: 'text-amber-700 dark:text-amber-400' },
  INFORMATIONAL: { icon: Info, cls: 'border-slate-200 dark:border-slate-700', text: 'text-slate-600 dark:text-slate-300' },
} as const;

function errorMessage(err: unknown): string {
  if (err instanceof AxiosError) {
    const data = err.response?.data as ApiResponse<unknown> | undefined;
    if (data?.message) return data.message;
  }
  if (err instanceof Error && err.message) return err.message;
  return 'Something went wrong. Please try again.';
}

/** Generated locally with the Web Crypto API — never sent to or stored by DeployPilot. */
function generateSecretLocally(): string {
  const bytes = new Uint8Array(48);
  crypto.getRandomValues(bytes);
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export default function DeploymentBlueprintPage() {
  const { id } = useParams<{ id: string }>();
  const projectId = Number(id);
  const qc = useQueryClient();

  const { data: project } = useQuery({ queryKey: ['project', projectId], queryFn: () => projectApi.get(projectId) });
  const { data: blueprint, isLoading, error: loadError } = useQuery({
    queryKey: ['blueprint', projectId],
    queryFn: () => blueprintApi.latest(projectId),
    retry: false,
  });

  const regenMut = useMutation({
    mutationFn: () => blueprintApi.generate(projectId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['blueprint', projectId] }),
  });
  const overrideMut = useMutation({
    mutationFn: (overrides: Record<string, string>) => blueprintApi.override(projectId, overrides),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['blueprint', projectId] }),
  });

  const result = blueprint?.result;

  if (isLoading) {
    return <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
      <div className="animate-pulse space-y-4">
        <div className="h-8 bg-slate-200 dark:bg-slate-700 rounded w-1/3" />
        <div className="h-32 bg-slate-200 dark:bg-slate-700 rounded" />
      </div>
    </div>;
  }

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
      <Link to="/projects" className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-primary-600 mb-4">
        <ArrowLeft className="w-4 h-4" /> Back to projects
      </Link>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-2">
        <h1 className="text-2xl font-bold text-slate-900 dark:text-slate-100">
          Deployment Blueprint{project ? ` — ${project.name}` : ''}
        </h1>
        <div className="flex flex-wrap gap-2">
          <Link to={`/projects/${projectId}/automate`} className="btn-primary text-sm inline-flex items-center gap-2">
            <Rocket className="w-4 h-4" /> Automate deployment
          </Link>
          <Link to={`/projects/${projectId}/verify`} className="btn-secondary text-sm inline-flex items-center gap-2">
            <ShieldCheck className="w-4 h-4" /> Verify deployment
          </Link>
          {blueprint && (
            <button onClick={() => regenMut.mutate()} disabled={regenMut.isPending} className="btn-secondary text-sm">
              {regenMut.isPending
                ? <span className="inline-flex items-center gap-2"><Loader2 className="w-4 h-4 animate-spin" /> Regenerating…</span>
                : <span className="inline-flex items-center gap-2"><RefreshCw className="w-4 h-4" /> Regenerate blueprint</span>}
            </button>
          )}
        </div>
      </div>

      <div className="card p-4 mb-6 flex items-start gap-3 border-primary-200 dark:border-primary-800 bg-primary-50/50 dark:bg-primary-900/10">
        <ShieldCheck className="w-5 h-5 text-primary-600 shrink-0 mt-0.5" />
        <p className="text-sm text-slate-600 dark:text-slate-300">
          DeployPilot has prepared your deployment blueprint. It has not modified your repository or created any external services.
        </p>
      </div>

      {loadError && !blueprint && (
        <div className="card p-8 text-center">
          <p className="text-sm text-slate-500 mb-4">No blueprint yet for this project.</p>
          <div className="flex flex-wrap justify-center gap-3">
            <button onClick={() => regenMut.mutate()} disabled={regenMut.isPending} className="btn-primary">
              {regenMut.isPending ? 'Generating…' : 'Generate blueprint'}
            </button>
            <Link to={`/projects/${projectId}/analysis`} className="btn-secondary inline-flex items-center gap-2">
              <ScanSearch className="w-4 h-4" /> Run repository analysis first
            </Link>
          </div>
          {regenMut.isError && (
            <p className="text-sm text-red-600 dark:text-red-400 mt-4">{errorMessage(regenMut.error)}</p>
          )}
        </div>
      )}

      {blueprint?.stale && (
        <div className="card p-4 mb-6 border-amber-200 dark:border-amber-900 bg-amber-50/50 dark:bg-amber-900/10 flex flex-wrap items-center gap-3">
          <AlertTriangle className="w-5 h-5 text-amber-500 shrink-0" />
          <p className="text-sm text-amber-800 dark:text-amber-300 flex-1">
            A newer repository analysis exists — this blueprint may be out of date.
          </p>
          <button onClick={() => regenMut.mutate()} disabled={regenMut.isPending} className="btn-primary text-sm">
            Regenerate now
          </button>
        </div>
      )}

      {overrideMut.isError && (
        <div className="card p-4 mb-6 border-red-200 dark:border-red-900 bg-red-50/50 dark:bg-red-900/10 flex items-start gap-3">
          <AlertTriangle className="w-5 h-5 text-red-500 shrink-0 mt-0.5" />
          <p className="text-sm text-red-700 dark:text-red-400">{errorMessage(overrideMut.error)}</p>
        </div>
      )}

      {result && (
        <div className="space-y-6">
          {/* meta */}
          <div className="card p-4 flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-slate-500">
            <span className="font-mono">{result.repository}</span>
            <span className="px-2 py-0.5 rounded-full bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-400 font-medium">
              {result.structure === 'MONOREPO' ? 'Monorepo' : result.structure === 'SINGLE_APPLICATION' ? 'Single application' : 'Unknown structure'}
            </span>
            <span>rules v{result.rulesVersion}</span>
            <span>generated {new Date(blueprint.updatedAt).toLocaleString()}</span>
            <span>analysis #{blueprint.analysisId}</span>
          </div>

          {/* findings */}
          {result.findings.length > 0 && (
            <section>
              <SectionTitle icon={AlertTriangle} title="Readiness checks" />
              <div className="space-y-3">
                {result.findings.map((f, i) => {
                  const meta = SEVERITY_META[f.severity];
                  const Icon = meta.icon;
                  return (
                    <div key={i} className={`card p-4 ${meta.cls}`}>
                      <div className="flex items-start gap-3">
                        <Icon className={`w-5 h-5 shrink-0 mt-0.5 ${meta.text}`} />
                        <div className="min-w-0">
                          <p className={`text-sm font-semibold ${meta.text}`}>
                            <span className="text-[10px] font-bold mr-2 uppercase">{f.severity}</span>{f.title}
                          </p>
                          <p className="text-sm text-slate-600 dark:text-slate-300 mt-1">{f.detail}</p>
                          {f.evidence && <p className="text-xs text-slate-500 mt-1">Evidence: {f.evidence}</p>}
                          {f.affectedFile && <p className="text-xs font-mono text-slate-500 mt-0.5">{f.affectedFile}</p>}
                          {f.proposedFix && <p className="text-xs text-slate-600 dark:text-slate-300 mt-1"><span className="font-medium">Fix:</span> {f.proposedFix}</p>}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </section>
          )}

          {/* components */}
          <section>
            <SectionTitle icon={Layers} title="Components and recommended platforms" />
            <div className="grid md:grid-cols-2 gap-4">
              {result.components.map((c) => (
                <ComponentCard key={c.id} component={c}
                  onOverride={(platform) => overrideMut.mutate({ [c.id]: platform })}
                  overriding={overrideMut.isPending} />
              ))}
            </div>
          </section>

          {/* relationships */}
          {result.relationships.length > 0 && (
            <section>
              <SectionTitle icon={Link2} title="How the pieces connect" />
              <div className="card p-4 space-y-2">
                {result.relationships.map((r, i) => (
                  <div key={i} className="flex items-start gap-2 text-sm">
                    <Link2 className="w-4 h-4 text-slate-400 mt-0.5 shrink-0" />
                    <p className="text-slate-600 dark:text-slate-300">
                      <span className="font-mono text-xs text-slate-500">{r.fromComponent}</span>{' → '}
                      <span className="font-mono text-xs text-slate-500">{r.toComponent}</span>: {r.description}
                    </p>
                  </div>
                ))}
              </div>
            </section>
          )}

          {/* env vars */}
          {result.environmentVariables.length > 0 && (
            <section>
              <SectionTitle icon={KeyRound} title="Environment variables" />
              <p className="text-xs text-slate-500 mb-3 -mt-2">
                Names and destinations only — DeployPilot never reads or stores real values. Placeholders like{' '}
                <code className="font-mono">{'${BACKEND_PUBLIC_URL}'}</code> become real once that deployment exists.
              </p>
              <div className="card p-0 overflow-x-auto">
                <table className="w-full text-sm min-w-[640px]">
                  <thead>
                    <tr className="text-left text-xs text-slate-500 border-b border-slate-200 dark:border-slate-700">
                      <th className="p-3">Variable</th><th className="p-3">Target</th><th className="p-3">Type</th>
                      <th className="p-3">Value source</th><th className="p-3">Format</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.environmentVariables.map((v) => <EnvVarRow key={v.name} v={v} />)}
                  </tbody>
                </table>
              </div>
            </section>
          )}

          {/* steps */}
          <section>
            <SectionTitle icon={ListOrdered} title="Deployment order" />
            <div className="space-y-3">
              {result.steps.map((s) => (
                <div key={s.index} className="card p-4">
                  <div className="flex items-start gap-3">
                    <span className="w-7 h-7 rounded-full bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-400 flex items-center justify-center text-sm font-bold shrink-0">
                      {s.index}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="font-semibold text-sm text-slate-900 dark:text-slate-100">{s.title}</p>
                      <p className="text-sm text-slate-600 dark:text-slate-300 mt-1">{s.what}</p>
                      <div className="flex flex-wrap gap-x-4 gap-y-1 mt-2 text-xs text-slate-500">
                        <span><span className="font-medium">Where:</span> {s.where}</span>
                        {s.inputs.length > 0 && <span><span className="font-medium">Needs:</span> {s.inputs.join(', ')}</span>}
                        {s.produces && <span><span className="font-medium">Produces:</span> <code className="font-mono">{s.produces}</code></span>}
                        {s.unlocksVariables.length > 0 && <span><span className="font-medium">Unlocks:</span> {s.unlocksVariables.join(', ')}</span>}
                        {s.blockedBy.length > 0 && <span><span className="font-medium">After step{s.blockedBy.length > 1 ? 's' : ''}:</span> {s.blockedBy.join(', ')}</span>}
                      </div>
                      <p className="text-xs text-emerald-700 dark:text-emerald-400 mt-1.5">✓ Done when: {s.expectedResult}</p>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </section>

          {/* file previews */}
          {result.filePreviews.length > 0 && (
            <section>
              <SectionTitle icon={FileCode} title="Configuration file previews" />
              <p className="text-xs text-slate-500 mb-3 -mt-2">
                Copy or download these — DeployPilot does not write to your repository.
              </p>
              <div className="space-y-3">
                {result.filePreviews.map((p) => <FilePreviewCard key={p.path} preview={p} />)}
              </div>
            </section>
          )}
        </div>
      )}
    </div>
  );
}

function SectionTitle({ icon: Icon, title }: { icon: React.ComponentType<{ className?: string }>; title: string }) {
  return (
    <h2 className="flex items-center gap-2 text-base font-semibold text-slate-900 dark:text-slate-100 mb-3">
      <Icon className="w-4 h-4 text-primary-600" /> {title}
    </h2>
  );
}

function ComponentCard({ component: c, onOverride, overriding }: {
  component: BlueprintComponent;
  onOverride: (platform: string) => void;
  overriding: boolean;
}) {
  const allPlatforms = [c.recommendedPlatform.platform, ...c.alternatives.map((a) => a.platform)];
  const selected = c.selectedPlatform;
  const selectedOption = selected === c.recommendedPlatform.platform
    ? c.recommendedPlatform
    : c.alternatives.find((a) => a.platform === selected) || c.recommendedPlatform;
  const overridden = selected !== c.recommendedPlatform.platform;

  return (
    <div className="card p-4">
      <div className="flex items-start justify-between gap-2 mb-2">
        <div>
          <p className="text-[11px] uppercase font-semibold text-slate-400">{c.type.toLowerCase()}</p>
          <h3 className="font-semibold text-slate-900 dark:text-slate-100">
            {c.name}{c.path ? <span className="font-mono text-xs text-slate-500 ml-2">{c.path}/</span> : null}
          </h3>
        </div>
        <span className={`text-[11px] px-1.5 py-0.5 rounded font-medium shrink-0 ${CONFIDENCE_STYLES[c.recommendedPlatform.confidence] || CONFIDENCE_STYLES.LOW}`}>
          {c.recommendedPlatform.confidence}
        </span>
      </div>

      <label className="block text-xs text-slate-500 mb-1">
        Platform{overridden && <span className="text-amber-600 dark:text-amber-400"> (overridden — recommended: {c.recommendedPlatform.platform})</span>}
      </label>
      <select className="input text-sm w-full" value={selected} disabled={overriding}
        onChange={(e) => onOverride(e.target.value)}>
        {allPlatforms.map((p) => (
          <option key={p} value={p}>{p}{p === c.recommendedPlatform.platform ? ' (recommended)' : ''}</option>
        ))}
      </select>

      <p className="text-xs text-slate-600 dark:text-slate-300 mt-2">{selectedOption.reason}</p>
      <ul className="mt-1.5 space-y-0.5">
        {selectedOption.evidence.slice(0, 3).map((e, i) => (
          <li key={i} className="text-xs text-slate-500 flex items-start gap-1.5">
            <FileCode className="w-3 h-3 mt-0.5 shrink-0" /><span className="break-all">{e}</span>
          </li>
        ))}
      </ul>
      <div className="mt-2 space-y-1 text-xs text-slate-500">
        {selectedOption.freeTierNote && <p>💰 {selectedOption.freeTierNote}</p>}
        {selectedOption.coldStartNote && <p>⏱️ {selectedOption.coldStartNote}</p>}
        {selectedOption.pricingNote && <p className="text-slate-400">{selectedOption.pricingNote}</p>}
      </div>
      {(c.buildCommand || c.publishDirectory || c.rootDirectory) && (
        <div className="mt-3 pt-2 border-t border-slate-100 dark:border-slate-700 grid grid-cols-1 gap-1 text-xs">
          {c.rootDirectory !== undefined && c.rootDirectory !== null && (
            <p><span className="text-slate-400">Base directory:</span> <code className="font-mono">{c.rootDirectory || '(repository root)'}</code></p>
          )}
          {c.buildCommand && <p><span className="text-slate-400">Build:</span> <code className="font-mono">{c.buildCommand}</code></p>}
          {c.startCommand && <p><span className="text-slate-400">Start:</span> <code className="font-mono">{c.startCommand}</code></p>}
          {c.publishDirectory && <p><span className="text-slate-400">Publish:</span> <code className="font-mono">{c.publishDirectory}</code></p>}
          {c.healthCheckPath && <p><span className="text-slate-400">Health check:</span> <code className="font-mono">{c.healthCheckPath}</code></p>}
        </div>
      )}
      {c.notes.length > 0 && (
        <ul className="mt-2 text-xs text-slate-500 list-disc list-inside space-y-0.5">
          {c.notes.map((n, i) => <li key={i}>{n}</li>)}
        </ul>
      )}
    </div>
  );
}

function EnvVarRow({ v }: { v: BlueprintEnvVar }) {
  const [generated, setGenerated] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const style = CLASSIFICATION_STYLES[v.classification] || CLASSIFICATION_STYLES.CONFIGURATION;
  return (
    <tr className="border-b border-slate-100 dark:border-slate-700/50 last:border-0 align-top">
      <td className="p-3">
        <code className="font-mono text-xs text-slate-800 dark:text-slate-200">{v.name}</code>
        {v.required === true && <span className="ml-1.5 text-[10px] text-red-500 font-medium">required</span>}
      </td>
      <td className="p-3 text-xs text-slate-600 dark:text-slate-300">{v.targetPlatform || '—'}</td>
      <td className="p-3"><span className={`text-[11px] px-1.5 py-0.5 rounded font-medium ${style.cls}`}>{style.label}</span></td>
      <td className="p-3 text-xs text-slate-600 dark:text-slate-300">
        {v.valueSource}
        {v.generatable && (
          <div className="mt-1.5">
            {generated ? (
              <span className="inline-flex items-center gap-1.5">
                <code className="font-mono text-[11px] bg-slate-100 dark:bg-slate-700 px-1.5 py-0.5 rounded max-w-[180px] truncate inline-block align-middle">{generated}</code>
                <button className="text-primary-600 hover:text-primary-700" title="Copy"
                  onClick={() => { navigator.clipboard.writeText(generated); setCopied(true); setTimeout(() => setCopied(false), 1500); }}>
                  {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                </button>
              </span>
            ) : (
              <button onClick={() => setGenerated(generateSecretLocally())}
                className="inline-flex items-center gap-1 text-[11px] font-medium text-primary-600 hover:text-primary-700">
                <Wand2 className="w-3 h-3" /> Generate in browser
              </button>
            )}
          </div>
        )}
      </td>
      <td className="p-3 text-xs font-mono text-slate-500 break-all">{v.expectedFormat || '—'}</td>
    </tr>
  );
}

function FilePreviewCard({ preview: p }: { preview: BlueprintFilePreview }) {
  const [open, setOpen] = useState(false);
  const [copied, setCopied] = useState(false);
  const download = () => {
    const blob = new Blob([p.suggestedContent], { type: 'text/plain' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = p.path.split('/').pop() || p.path;
    a.click();
    URL.revokeObjectURL(a.href);
  };
  return (
    <div className="card p-4">
      <button className="w-full flex items-center justify-between gap-2 text-left" onClick={() => setOpen(!open)}>
        <div className="min-w-0">
          <p className="text-sm font-semibold text-slate-900 dark:text-slate-100 font-mono">{p.path}
            {p.exists === true && <span className="ml-2 text-[10px] font-sans font-medium text-amber-600 dark:text-amber-400">exists — review the diff</span>}
            {p.exists === false && <span className="ml-2 text-[10px] font-sans font-medium text-emerald-600 dark:text-emerald-400">new file</span>}
          </p>
          <p className="text-xs text-slate-500 mt-0.5">{p.purpose}</p>
        </div>
        {open ? <ChevronUp className="w-4 h-4 shrink-0" /> : <ChevronDown className="w-4 h-4 shrink-0" />}
      </button>
      {open && (
        <div className="mt-3 space-y-3">
          <p className="text-xs text-slate-600 dark:text-slate-300">{p.reason}</p>
          {p.diff && p.diff !== '(no changes needed)' && (
            <div>
              <p className="text-xs font-medium text-slate-500 mb-1">Suggested change (diff)</p>
              <pre className="text-xs font-mono bg-slate-900 text-slate-100 rounded-lg p-3 overflow-x-auto">
                {p.diff.split('\n').map((line, i) => (
                  <div key={i} className={line.startsWith('+') ? 'text-emerald-400' : line.startsWith('-') ? 'text-red-400' : 'text-slate-400'}>{line || ' '}</div>
                ))}
              </pre>
            </div>
          )}
          {p.diff === '(no changes needed)' && (
            <p className="text-xs text-emerald-600 dark:text-emerald-400">Current file already matches the suggestion.</p>
          )}
          <div>
            <div className="flex items-center justify-between mb-1">
              <p className="text-xs font-medium text-slate-500">Suggested content</p>
              <span className="flex gap-2">
                <button className="text-xs inline-flex items-center gap-1 text-primary-600 hover:text-primary-700"
                  onClick={() => { navigator.clipboard.writeText(p.suggestedContent); setCopied(true); setTimeout(() => setCopied(false), 1500); }}>
                  {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />} Copy
                </button>
                <button className="text-xs inline-flex items-center gap-1 text-primary-600 hover:text-primary-700" onClick={download}>
                  <Download className="w-3.5 h-3.5" /> Download
                </button>
              </span>
            </div>
            <pre className="text-xs font-mono bg-slate-50 dark:bg-slate-800 rounded-lg p-3 overflow-x-auto text-slate-700 dark:text-slate-300">{p.suggestedContent}</pre>
          </div>
        </div>
      )}
    </div>
  );
}
