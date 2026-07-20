import { useEffect } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { copilotApi } from '@/lib/api';
import type { StructuredTroubleshooting } from '@/types';
import {
  Bot, Loader2, X, CheckCircle2, AlertTriangle, HelpCircle, ShieldAlert,
  ListChecks, Lightbulb, FileSearch, RotateCw,
} from 'lucide-react';

const QUICK_QUESTIONS = [
  'Why did this fail?',
  'What should I do next?',
  'Is it safe to retry?',
  'What information do you need from me?',
  'What have we already tried?',
  'Did the deployment actually succeed?',
  'Explain this error simply',
];

const STATUS_META: Record<string, { label: string; cls: string; Icon: typeof CheckCircle2 }> = {
  DIAGNOSED: { label: 'Diagnosed', cls: 'badge-green', Icon: CheckCircle2 },
  READY_TO_RETRY: { label: 'Ready to retry', cls: 'badge-green', Icon: RotateCw },
  NEEDS_EVIDENCE: { label: 'Needs evidence', cls: 'badge-amber', Icon: HelpCircle },
  UNKNOWN: { label: 'Unknown', cls: 'badge-gray', Icon: AlertTriangle },
};

export default function TroubleshootingPanel({
  projectId, runId, stepId, onClose,
}: {
  projectId: number;
  runId: number;
  stepId?: string;
  onClose?: () => void;
}) {
  const qc = useQueryClient();
  const ask = useMutation({
    mutationFn: (question?: string) =>
      copilotApi.troubleshoot(projectId, { runId, stepId, question }),
  });
  const report = useMutation({
    mutationFn: (event: string) => copilotApi.troubleshootEvent(projectId, { runId, event }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['project-activity', projectId] }),
  });

  // Automatically diagnose the selected failed step on open — no copy/paste needed.
  useEffect(() => {
    ask.mutate(undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId, runId, stepId]);

  const data: StructuredTroubleshooting | undefined = report.data ?? ask.data;
  const busy = ask.isPending || report.isPending;
  const status = data ? STATUS_META[data.status] ?? STATUS_META.UNKNOWN : STATUS_META.UNKNOWN;

  return (
    <div className="card mt-3 border-primary-200 dark:border-primary-900/40">
      <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200 dark:border-slate-700">
        <div className="flex items-center gap-2">
          <Bot className="w-5 h-5 text-primary-600" />
          <h4 className="font-semibold text-sm">Copilot — troubleshoot this failure</h4>
          {data && (
            <span className={`${status.cls} inline-flex items-center gap-1`}>
              <status.Icon className="w-3 h-3" /> {status.label}
            </span>
          )}
        </div>
        {onClose && (
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600" aria-label="Close">
            <X className="w-4 h-4" />
          </button>
        )}
      </div>

      <div className="p-4 space-y-4 text-sm">
        {busy && !data && (
          <div className="flex items-center gap-2 text-slate-400">
            <Loader2 className="w-4 h-4 animate-spin" /> Gathering the evidence…
          </div>
        )}

        {data && (
          <>
            {/* What failed */}
            <section>
              <p className="text-slate-800 dark:text-slate-200">{data.summary}</p>
              <p className="mt-1 text-[11px] text-slate-400">
                {data.provider ? `${data.provider} · ` : ''}{data.errorCode} · confidence {data.confidence.toLowerCase()}
                {data.aiExplained ? ' · explained by AI over verified evidence' : ' · deterministic'}
              </p>
            </section>

            {/* Retry recommendation */}
            <section className={`rounded-md px-3 py-2 flex items-start gap-2 ${
              data.retryAdvice.safeNow
                ? 'bg-green-50 dark:bg-green-900/20 text-green-800 dark:text-green-300'
                : 'bg-amber-50 dark:bg-amber-900/20 text-amber-800 dark:text-amber-300'}`}>
              {data.retryAdvice.safeNow ? <RotateCw className="w-4 h-4 mt-0.5 shrink-0" /> : <ShieldAlert className="w-4 h-4 mt-0.5 shrink-0" />}
              <p><span className="font-medium">{data.retryAdvice.safeNow ? 'Safe to retry now.' : 'Do not retry yet.'}</span>{' '}{data.retryAdvice.reason}</p>
            </section>

            {data.verifiedFacts.length > 0 && (
              <Group icon={CheckCircle2} title="What is proven">
                {data.verifiedFacts.map((f, i) => <li key={i}>{f.text}</li>)}
              </Group>
            )}

            {data.likelyCauses.length > 0 && (
              <Group icon={Lightbulb} title="Likely cause (interpretation)">
                {data.likelyCauses.map((c, i) => (
                  <li key={i}><span className="font-medium">{c.cause}</span>
                    <span className="text-[11px] text-slate-400"> ({c.confidence.toLowerCase()})</span>
                    {c.reason && <span className="text-slate-500"> — {c.reason}</span>}
                  </li>
                ))}
              </Group>
            )}

            {data.steps.length > 0 && (
              <section>
                <h5 className="flex items-center gap-1.5 font-medium mb-1.5"><ListChecks className="w-4 h-4 text-primary-600" /> Exact next steps</h5>
                <ol className="space-y-2">
                  {data.steps.map((s) => (
                    <li key={s.number} className="flex gap-2">
                      <span className="shrink-0 w-5 h-5 rounded-full bg-primary-100 dark:bg-primary-900/40 text-primary-700 dark:text-primary-300 text-xs flex items-center justify-center">{s.number}</span>
                      <div>
                        <p className="text-slate-800 dark:text-slate-200">{s.instruction}</p>
                        <p className="text-[11px] text-slate-400">In {s.location} · Expect: {s.expectedResult}
                          {s.requiresConfirmation && <span className="ml-1 text-amber-600">· needs your confirmation in DeployPilot</span>}
                        </p>
                      </div>
                    </li>
                  ))}
                </ol>
              </section>
            )}

            {data.requiredEvidence.length > 0 && (
              <Group icon={FileSearch} title="Information still needed">
                {data.requiredEvidence.map((e, i) => (
                  <li key={i}><span className="font-medium">{e.label}</span>
                    {e.reason && <span className="text-slate-500"> — {e.reason}</span>}
                    <span className="block text-[11px] text-red-500">{e.secretWarning}</span>
                  </li>
                ))}
              </Group>
            )}

            {/* Report what happened outside DeployPilot (drives host-key Case A/B, prevents loops) */}
            <section className="pt-1 border-t border-slate-100 dark:border-slate-800">
              <p className="text-[11px] text-slate-400 mb-1.5">Tell the Copilot what happened so it does not repeat itself:</p>
              <div className="flex flex-wrap gap-1.5">
                <ReportBtn onClick={() => report.mutate('USER_REPORTED_RELINK_COMPLETED')} disabled={busy}>I relinked the repo</ReportBtn>
                <ReportBtn onClick={() => report.mutate('MANUAL_DEPLOY_SUCCEEDED')} disabled={busy}>Netlify’s own deploy succeeded</ReportBtn>
                <ReportBtn onClick={() => report.mutate('MANUAL_DEPLOY_FAILED')} disabled={busy}>Netlify’s own deploy failed</ReportBtn>
                <ReportBtn onClick={() => report.mutate('RETRY_ATTEMPTED')} disabled={busy}>I clicked Retry</ReportBtn>
              </div>
            </section>
          </>
        )}

        {/* Quick questions */}
        <section className="pt-1 border-t border-slate-100 dark:border-slate-800">
          <div className="flex flex-wrap gap-1.5">
            {QUICK_QUESTIONS.map((q) => (
              <button key={q} onClick={() => ask.mutate(q)} disabled={busy}
                className="text-xs px-2.5 py-1 rounded-full border border-slate-300 dark:border-slate-600 hover:bg-slate-100 dark:hover:bg-slate-800 disabled:opacity-40">
                {q}
              </button>
            ))}
          </div>
        </section>

        {(ask.isError || report.isError) && (
          <p className="text-xs text-red-500">The Copilot is unavailable right now. Your deployment records are unchanged.</p>
        )}
      </div>
    </div>
  );
}

function Group({ icon: Icon, title, children }: { icon: typeof CheckCircle2; title: string; children: React.ReactNode }) {
  return (
    <section>
      <h5 className="flex items-center gap-1.5 font-medium mb-1"><Icon className="w-4 h-4 text-primary-600" /> {title}</h5>
      <ul className="list-disc pl-5 space-y-1 text-slate-700 dark:text-slate-300">{children}</ul>
    </section>
  );
}

function ReportBtn({ onClick, disabled, children }: { onClick: () => void; disabled?: boolean; children: React.ReactNode }) {
  return (
    <button onClick={onClick} disabled={disabled}
      className="text-xs px-2.5 py-1 rounded-md bg-slate-100 dark:bg-slate-800 hover:bg-slate-200 dark:hover:bg-slate-700 disabled:opacity-40">
      {children}
    </button>
  );
}
