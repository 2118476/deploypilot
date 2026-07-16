import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { AxiosError } from 'axios';
import { verifyApi, projectApi } from '@/lib/api';
import type { ApiResponse, VerificationRun, VerificationCheck, VerificationDiagnosis, AssistResponse } from '@/types';
import {
  ShieldCheck, Play, RefreshCw, AlertTriangle, AlertOctagon, Info, CheckCircle2,
  XCircle, HelpCircle, MinusCircle, Loader2, ArrowLeft, Server, Globe, Link2,
  GitCommitHorizontal, ChevronDown, ChevronUp, Copy, Check, Wand2, History
} from 'lucide-react';

const OVERALL_META: Record<string, { cls: string; label: string; icon: React.ComponentType<{ className?: string }> }> = {
  HEALTHY: { cls: 'text-emerald-700 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-900/20', label: 'Healthy', icon: CheckCircle2 },
  DEGRADED: { cls: 'text-amber-700 dark:text-amber-400 bg-amber-50 dark:bg-amber-900/20', label: 'Degraded', icon: AlertTriangle },
  UNHEALTHY: { cls: 'text-red-700 dark:text-red-400 bg-red-50 dark:bg-red-900/20', label: 'Unhealthy', icon: AlertOctagon },
  INCONCLUSIVE: { cls: 'text-slate-700 dark:text-slate-300 bg-slate-100 dark:bg-slate-700', label: 'Inconclusive', icon: HelpCircle },
  RUNNING: { cls: 'text-primary-700 dark:text-primary-400 bg-primary-50 dark:bg-primary-900/20', label: 'Running', icon: Loader2 },
  FAILED: { cls: 'text-red-700 dark:text-red-400 bg-red-50 dark:bg-red-900/20', label: 'Failed', icon: XCircle },
};

const CHECK_META: Record<string, { cls: string; icon: React.ComponentType<{ className?: string }> }> = {
  PASS: { cls: 'text-emerald-600 dark:text-emerald-400', icon: CheckCircle2 },
  WARNING: { cls: 'text-amber-600 dark:text-amber-400', icon: AlertTriangle },
  FAIL: { cls: 'text-red-600 dark:text-red-400', icon: XCircle },
  SKIPPED: { cls: 'text-slate-400', icon: MinusCircle },
  UNKNOWN: { cls: 'text-slate-500', icon: HelpCircle },
};

const ACTION_LABELS: Record<string, string> = {
  CODE_CHANGE: 'Code change',
  REBUILD: 'Rebuild & redeploy',
  PROVIDER_SETTINGS: 'Provider settings',
  USER_DEVICE: 'On your device',
};

function errorMessage(err: unknown): string {
  if (err instanceof AxiosError) {
    const data = err.response?.data as ApiResponse<unknown> | undefined;
    if (data?.message) return data.message;
  }
  if (err instanceof Error && err.message) return err.message;
  return 'Something went wrong. Please try again.';
}

export default function VerifyDeploymentPage() {
  const { id } = useParams<{ id: string }>();
  const projectId = Number(id);
  const qc = useQueryClient();

  const [frontendUrl, setFrontendUrl] = useState('');
  const [backendUrl, setBackendUrl] = useState('');
  const [healthPath, setHealthPath] = useState('');
  const [expectedCommit, setExpectedCommit] = useState('');
  const [activeRunId, setActiveRunId] = useState<number | null>(null);

  const { data: project } = useQuery({ queryKey: ['project', projectId], queryFn: () => projectApi.get(projectId) });
  const { data: history } = useQuery({
    queryKey: ['verifications', projectId],
    queryFn: () => verifyApi.list(projectId, 5),
  });

  // prefill from last run
  useEffect(() => {
    if (history && history.length > 0 && !frontendUrl && !backendUrl) {
      const last = history[0];
      if (last.frontendUrl) setFrontendUrl(last.frontendUrl);
      if (last.backendUrl) setBackendUrl(last.backendUrl);
    }
  }, [history]); // eslint-disable-line react-hooks/exhaustive-deps

  // poll the active run until it completes
  const { data: activeRun } = useQuery({
    queryKey: ['verification', projectId, activeRunId],
    queryFn: () => verifyApi.get(projectId, activeRunId!),
    enabled: activeRunId != null,
    refetchInterval: (q) => {
      const run = q.state.data as VerificationRun | undefined;
      return run && run.overallStatus !== 'RUNNING' ? false : 1500;
    },
  });

  useEffect(() => {
    if (activeRun && activeRun.overallStatus !== 'RUNNING') {
      qc.invalidateQueries({ queryKey: ['verifications', projectId] });
    }
  }, [activeRun?.overallStatus]); // eslint-disable-line react-hooks/exhaustive-deps

  const startMut = useMutation({
    mutationFn: () => verifyApi.start(projectId, {
      frontendUrl: frontendUrl.trim() || undefined,
      backendUrl: backendUrl.trim() || undefined,
      healthPath: healthPath.trim() || undefined,
      expectedCommit: expectedCommit.trim() || undefined,
    }),
    onSuccess: (run) => setActiveRunId(run.id),
  });

  const displayRun = activeRun ?? (history && history[0]);
  const running = displayRun?.overallStatus === 'RUNNING' || startMut.isPending;

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
      <Link to={`/projects/${projectId}/blueprint`} className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-primary-600 mb-4">
        <ArrowLeft className="w-4 h-4" /> Back to blueprint
      </Link>
      <h1 className="text-2xl font-bold text-slate-900 dark:text-slate-100 mb-2">
        Verify Deployment{project ? ` — ${project.name}` : ''}
      </h1>

      <div className="card p-4 mb-6 flex items-start gap-3 border-primary-200 dark:border-primary-800 bg-primary-50/50 dark:bg-primary-900/10">
        <ShieldCheck className="w-5 h-5 text-primary-600 shrink-0 mt-0.5" />
        <p className="text-sm text-slate-600 dark:text-slate-300">
          <span className="font-medium text-slate-900 dark:text-slate-100">Read-only.</span>{' '}
          DeployPilot only sends safe GET/HEAD/OPTIONS requests to the URLs you provide. It never logs in, changes
          settings, deploys, or reads secrets.
        </p>
      </div>

      {/* input form */}
      <form className="card p-4 mb-6 space-y-3" onSubmit={(e) => { e.preventDefault(); if (!running) startMut.mutate(); }}>
        <div className="grid sm:grid-cols-2 gap-3">
          <label className="block">
            <span className="text-sm font-medium text-slate-700 dark:text-slate-300 flex items-center gap-1.5"><Globe className="w-4 h-4" /> Frontend URL</span>
            <input className="input w-full mt-1" placeholder="https://your-app.netlify.app" value={frontendUrl}
              onChange={(e) => setFrontendUrl(e.target.value)} maxLength={500} />
          </label>
          <label className="block">
            <span className="text-sm font-medium text-slate-700 dark:text-slate-300 flex items-center gap-1.5"><Server className="w-4 h-4" /> Backend / API URL</span>
            <input className="input w-full mt-1" placeholder="https://your-api.onrender.com" value={backendUrl}
              onChange={(e) => setBackendUrl(e.target.value)} maxLength={500} />
          </label>
          <label className="block">
            <span className="text-sm font-medium text-slate-700 dark:text-slate-300">Health path (optional)</span>
            <input className="input w-full mt-1" placeholder="/api/health" value={healthPath}
              onChange={(e) => setHealthPath(e.target.value)} maxLength={200} />
          </label>
          <label className="block">
            <span className="text-sm font-medium text-slate-700 dark:text-slate-300">Expected commit (optional)</span>
            <input className="input w-full mt-1" placeholder="short or full SHA" value={expectedCommit}
              onChange={(e) => setExpectedCommit(e.target.value)} maxLength={80} />
          </label>
        </div>

        <details className="text-xs text-slate-500">
          <summary className="cursor-pointer font-medium">What DeployPilot will check</summary>
          <ul className="mt-2 list-disc list-inside space-y-0.5">
            <li>Frontend serves HTML, assets load, SPA routes survive refresh, no provider error page</li>
            <li>Bundle isn't pointing at localhost or an unreplaced placeholder</li>
            <li>Backend responds, health endpoint works, /api prefix is consistent, no stack traces exposed</li>
            <li>CORS accepts your production frontend origin (safe OPTIONS preflight)</li>
            <li>Deployed version vs expected commit; PWA manifest, service worker and cache headers</li>
          </ul>
        </details>

        <div className="flex flex-wrap items-center gap-3">
          <button type="submit" className="btn-primary" disabled={running || (!frontendUrl.trim() && !backendUrl.trim())}>
            {running
              ? <span className="inline-flex items-center gap-2"><Loader2 className="w-4 h-4 animate-spin" /> Verifying…</span>
              : displayRun
                ? <span className="inline-flex items-center gap-2"><RefreshCw className="w-4 h-4" /> Re-run verification</span>
                : <span className="inline-flex items-center gap-2"><Play className="w-4 h-4" /> Start verification</span>}
          </button>
          {startMut.isError && <span className="text-sm text-red-600 dark:text-red-400">{errorMessage(startMut.error)}</span>}
        </div>
      </form>

      {displayRun && <RunView run={displayRun} />}

      {history && history.length > 1 && <HistoryView runs={history} onSelect={(id) => setActiveRunId(id)} />}

      <AssistPanel projectId={projectId} />
    </div>
  );
}

function RunView({ run }: { run: VerificationRun }) {
  const meta = OVERALL_META[run.overallStatus] ?? OVERALL_META.INCONCLUSIVE;
  const Icon = meta.icon;
  const result = run.result;

  return (
    <div className="space-y-6">
      {/* overall */}
      <div className="card p-4">
        <div className="flex flex-wrap items-center gap-3">
          <span className={`inline-flex items-center gap-2 px-3 py-1 rounded-full text-sm font-semibold ${meta.cls}`}>
            <Icon className={`w-4 h-4 ${run.overallStatus === 'RUNNING' ? 'animate-spin' : ''}`} /> {meta.label}
          </span>
          {run.completedAt && <span className="text-xs text-slate-400">{new Date(run.completedAt).toLocaleString()}</span>}
        </div>
        {result?.summary && <p className="text-sm text-slate-700 dark:text-slate-300 mt-3">{result.summary}</p>}
      </div>

      {run.overallStatus === 'RUNNING' && (
        <div className="card p-6 text-center">
          <Loader2 className="w-6 h-6 animate-spin mx-auto text-primary-600 mb-2" />
          <p className="text-sm text-slate-500">Running read-only checks against your URLs…</p>
        </div>
      )}

      {result && (
        <>
          {/* diagnoses / next fixes */}
          {result.diagnoses.length > 0 && (
            <section>
              <h2 className="text-base font-semibold text-slate-900 dark:text-slate-100 mb-3 flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 text-primary-600" /> What to fix next
              </h2>
              <div className="space-y-3">
                {result.diagnoses.map((d, i) => <DiagnosisCard key={i} d={d} />)}
              </div>
            </section>
          )}

          {/* version + CORS summary chips */}
          <div className="grid sm:grid-cols-2 gap-4">
            {result.version && (
              <div className="card p-4">
                <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100 flex items-center gap-2 mb-2">
                  <GitCommitHorizontal className="w-4 h-4 text-primary-600" /> Version
                </h3>
                <p className="text-sm"><span className="font-medium">{result.version.state}</span></p>
                {result.version.evidence && <p className="text-xs text-slate-500 mt-1">{result.version.evidence}</p>}
                {result.version.suggestion && (
                  <pre className="text-[11px] font-mono bg-slate-50 dark:bg-slate-800 rounded p-2 mt-2 overflow-x-auto whitespace-pre-wrap">{result.version.suggestion}</pre>
                )}
              </div>
            )}
            {result.corsResult && (
              <div className="card p-4">
                <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100 flex items-center gap-2 mb-2">
                  <Link2 className="w-4 h-4 text-primary-600" /> CORS
                </h3>
                <p className="text-sm font-medium">{result.corsResult.replace(/_/g, ' ')}</p>
              </div>
            )}
          </div>

          {/* checks grouped by category */}
          <section>
            <h2 className="text-base font-semibold text-slate-900 dark:text-slate-100 mb-3">Individual checks</h2>
            <div className="space-y-3">
              {['FRONTEND', 'BACKEND', 'CONNECTION', 'VERSION', 'PWA'].map((cat) => {
                const checks = result.checks.filter((c) => c.category === cat);
                if (checks.length === 0) return null;
                return (
                  <div key={cat} className="card p-4">
                    <h3 className="text-xs uppercase font-semibold text-slate-400 mb-2">{cat.toLowerCase()}</h3>
                    <div className="space-y-2">
                      {checks.map((c) => <CheckRow key={c.id} check={c} />)}
                    </div>
                  </div>
                );
              })}
            </div>
          </section>

          {result.skippedChecks.length > 0 && (
            <div className="card p-4">
              <h3 className="text-xs uppercase font-semibold text-slate-400 mb-2">Skipped</h3>
              <ul className="text-xs text-slate-500 space-y-0.5 list-disc list-inside">
                {result.skippedChecks.map((s, i) => <li key={i}>{s}</li>)}
              </ul>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function CheckRow({ check: c }: { check: VerificationCheck }) {
  const meta = CHECK_META[c.status] ?? CHECK_META.UNKNOWN;
  const Icon = meta.icon;
  return (
    <div className="flex items-start gap-2.5 text-sm">
      <Icon className={`w-4 h-4 mt-0.5 shrink-0 ${meta.cls}`} />
      <div className="min-w-0">
        <p className="text-slate-800 dark:text-slate-200">{c.title}
          <span className={`ml-2 text-[11px] font-medium ${meta.cls}`}>{c.status}</span>
        </p>
        {c.evidence && <p className="text-xs text-slate-500 break-words">{c.evidence}</p>}
      </div>
    </div>
  );
}

function DiagnosisCard({ d }: { d: VerificationDiagnosis }) {
  const sev = d.severity === 'BLOCKER'
    ? { cls: 'border-red-200 dark:border-red-900 bg-red-50/50 dark:bg-red-900/10', text: 'text-red-700 dark:text-red-400', Icon: AlertOctagon }
    : d.severity === 'WARNING'
      ? { cls: 'border-amber-200 dark:border-amber-900 bg-amber-50/50 dark:bg-amber-900/10', text: 'text-amber-700 dark:text-amber-400', Icon: AlertTriangle }
      : { cls: 'border-slate-200 dark:border-slate-700', text: 'text-slate-600 dark:text-slate-300', Icon: Info };
  return (
    <div className={`card p-4 ${sev.cls}`}>
      <div className="flex items-start gap-3">
        <sev.Icon className={`w-5 h-5 shrink-0 mt-0.5 ${sev.text}`} />
        <div className="min-w-0">
          <p className={`text-sm font-semibold ${sev.text}`}>
            {d.title}
            <span className="ml-2 text-[10px] font-medium uppercase px-1.5 py-0.5 rounded bg-slate-200/60 dark:bg-slate-700 text-slate-600 dark:text-slate-300">{d.confidence.replace(/_/g, ' ')}</span>
          </p>
          <p className="text-sm text-slate-600 dark:text-slate-300 mt-1">{d.likelyCause}</p>
          {d.evidence && <p className="text-xs text-slate-500 mt-1">Evidence: {d.evidence}</p>}
          <p className="text-sm text-slate-700 dark:text-slate-200 mt-2">
            <span className="font-medium">Do this:</span> {d.recommendedAction}
          </p>
          <span className="inline-block mt-1.5 text-[10px] font-medium px-1.5 py-0.5 rounded bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-400">
            {ACTION_LABELS[d.actionType] ?? d.actionType}
          </span>
        </div>
      </div>
    </div>
  );
}

function HistoryView({ runs, onSelect }: { runs: VerificationRun[]; onSelect: (id: number) => void }) {
  return (
    <section className="mt-6">
      <h2 className="text-base font-semibold text-slate-900 dark:text-slate-100 mb-3 flex items-center gap-2">
        <History className="w-4 h-4 text-primary-600" /> Recent runs
      </h2>
      <div className="card p-0 overflow-x-auto">
        <table className="w-full text-sm min-w-[480px]">
          <thead>
            <tr className="text-left text-xs text-slate-500 border-b border-slate-200 dark:border-slate-700">
              <th className="p-3">When</th><th className="p-3">Result</th><th className="p-3">Frontend</th><th className="p-3">Backend</th><th className="p-3"></th>
            </tr>
          </thead>
          <tbody>
            {runs.map((run) => {
              const meta = OVERALL_META[run.overallStatus] ?? OVERALL_META.INCONCLUSIVE;
              return (
                <tr key={run.id} className="border-b border-slate-100 dark:border-slate-700/50 last:border-0">
                  <td className="p-3 text-xs text-slate-500">{run.completedAt ? new Date(run.completedAt).toLocaleString() : '—'}</td>
                  <td className="p-3"><span className={`text-[11px] px-1.5 py-0.5 rounded font-medium ${meta.cls}`}>{meta.label}</span></td>
                  <td className="p-3 text-xs font-mono text-slate-500 truncate max-w-[140px]">{run.frontendUrl || '—'}</td>
                  <td className="p-3 text-xs font-mono text-slate-500 truncate max-w-[140px]">{run.backendUrl || '—'}</td>
                  <td className="p-3"><button className="text-xs text-primary-600 hover:text-primary-700" onClick={() => onSelect(run.id)}>View</button></td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function AssistPanel({ projectId }: { projectId: number }) {
  const [open, setOpen] = useState(false);
  const [question, setQuestion] = useState('');
  const [log, setLog] = useState('');
  const [answer, setAnswer] = useState<AssistResponse | null>(null);
  const [copied, setCopied] = useState(false);

  const assistMut = useMutation({
    mutationFn: () => verifyApi.assist(projectId, { question: question.trim() || undefined, log: log.trim() || undefined }),
    onSuccess: (res) => setAnswer(res),
  });

  return (
    <section className="mt-6">
      <button className="card-hover p-4 w-full flex items-center justify-between" onClick={() => setOpen(!open)}>
        <span className="text-base font-semibold text-slate-900 dark:text-slate-100 flex items-center gap-2">
          <Wand2 className="w-4 h-4 text-primary-600" /> Ask the project-aware troubleshooter
        </span>
        {open ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
      </button>
      {open && (
        <div className="card p-4 mt-3 space-y-3">
          <p className="text-xs text-slate-500">
            DeployPilot summarises this project's analysis, blueprint and latest verification, and (if configured) asks
            AI to explain it. Any log you paste is redacted before it leaves your browser session — but automated
            redaction can't guarantee every secret is caught, so avoid pasting real credentials.
          </p>
          <label className="block">
            <span className="text-sm font-medium text-slate-700 dark:text-slate-300">Your question</span>
            <input className="input w-full mt-1" placeholder="Why can't my frontend reach the backend?" value={question}
              onChange={(e) => setQuestion(e.target.value)} maxLength={2000} />
          </label>
          <label className="block">
            <span className="text-sm font-medium text-slate-700 dark:text-slate-300">Deployment log (optional)</span>
            <textarea className="input w-full mt-1 font-mono text-xs" rows={4} placeholder="Paste a build/deploy log…"
              value={log} onChange={(e) => setLog(e.target.value)} maxLength={100000} />
          </label>
          <button className="btn-primary" onClick={() => assistMut.mutate()} disabled={assistMut.isPending || (!question.trim() && !log.trim())}>
            {assistMut.isPending
              ? <span className="inline-flex items-center gap-2"><Loader2 className="w-4 h-4 animate-spin" /> Thinking…</span>
              : 'Get help'}
          </button>
          {assistMut.isError && <p className="text-sm text-red-600 dark:text-red-400">{errorMessage(assistMut.error)}</p>}
          {answer && (
            <div className="border-t border-slate-100 dark:border-slate-700 pt-3">
              {!answer.aiAvailable && (
                <p className="text-xs text-amber-600 dark:text-amber-400 mb-2">AI is not configured on the server — showing the deterministic project summary.</p>
              )}
              <div className="flex items-center justify-between mb-1">
                <span className="text-xs font-medium text-slate-500">{answer.aiAvailable ? 'Answer' : 'Project summary'}</span>
                <button className="text-xs inline-flex items-center gap-1 text-primary-600" onClick={() => { navigator.clipboard.writeText(answer.answer); setCopied(true); setTimeout(() => setCopied(false), 1500); }}>
                  {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />} Copy
                </button>
              </div>
              <pre className="text-xs whitespace-pre-wrap text-slate-700 dark:text-slate-300 bg-slate-50 dark:bg-slate-800 rounded-lg p-3 overflow-x-auto">{answer.answer}</pre>
            </div>
          )}
        </div>
      )}
    </section>
  );
}
