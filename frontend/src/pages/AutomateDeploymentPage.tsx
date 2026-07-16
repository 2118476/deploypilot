import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { AxiosError } from 'axios';
import { automationApi, connectionApi, projectApi, type PlanInputs } from '@/lib/api';
import type {
  ApiResponse, DeploymentActionPlan, PlannedAction, EnvVarPlanItem, AutomationRun, ExecutionStep, ProviderConnection,
  DatabaseChoice,
} from '@/types';
import {
  Rocket, ShieldCheck, ShieldAlert, CheckCircle2, XCircle, Loader2, ArrowLeft, Plug, Database,
  GitBranch, GitCommitHorizontal, Play, RefreshCw, Lock, KeyRound, Trash2, Server, Globe, Github, MinusCircle, Circle,
} from 'lucide-react';

const TYPE_BADGE: Record<string, string> = {
  READ_ONLY: 'badge-gray', CREATE: 'badge-blue', UPDATE: 'badge-amber',
  DEPLOY: 'badge-green', RESTART: 'badge-amber', DESTRUCTIVE: 'badge-red',
};
const PROVIDER_ICON: Record<string, React.ComponentType<{ className?: string }>> = {
  GITHUB: Github, NETLIFY: Globe, RENDER: Server, SUPABASE: Database, NONE: Circle,
};
const STEP_META: Record<string, { cls: string; icon: React.ComponentType<{ className?: string }>; spin?: boolean }> = {
  PENDING: { cls: 'text-slate-400', icon: Circle },
  RUNNING: { cls: 'text-primary-600', icon: Loader2, spin: true },
  SUCCEEDED: { cls: 'text-emerald-600 dark:text-emerald-400', icon: CheckCircle2 },
  FAILED: { cls: 'text-red-600 dark:text-red-400', icon: XCircle },
  SKIPPED: { cls: 'text-slate-400', icon: MinusCircle },
};
const RUN_BADGE: Record<string, string> = {
  PENDING: 'badge-gray', RUNNING: 'badge-blue', PAUSED: 'badge-amber',
  SUCCEEDED: 'badge-green', FAILED: 'badge-red', CANCELLED: 'badge-gray',
};

function errorMessage(err: unknown): string {
  if (err instanceof AxiosError) {
    const data = err.response?.data as ApiResponse<unknown> | undefined;
    if (data?.message) return data.message;
  }
  if (err instanceof Error && err.message) return err.message;
  return 'Something went wrong. Please try again.';
}

export default function AutomateDeploymentPage() {
  const { id } = useParams<{ id: string }>();
  const projectId = Number(id);
  const qc = useQueryClient();

  const [mode, setMode] = useState<'GUIDE_ME' | 'PREPARE_FOR_ME' | 'DEPLOY_FOR_ME'>('GUIDE_ME');
  const [plan, setPlan] = useState<DeploymentActionPlan | null>(null);
  const [confirmation, setConfirmation] = useState<{ runId: number; nonce: string; expiresAt: string } | null>(null);
  const [activeRunId, setActiveRunId] = useState<number | null>(null);
  const [db, setDb] = useState<{ choice: DatabaseChoice; orgId: string; projectRef: string; projectName: string; region: string; applyMigrations: boolean }>({
    choice: 'MANUAL', orgId: '', projectRef: '', projectName: '', region: 'us-east-1', applyMigrations: false,
  });

  const { data: project } = useQuery({ queryKey: ['project', projectId], queryFn: () => projectApi.get(projectId) });
  const { data: connections } = useQuery({ queryKey: ['connections'], queryFn: connectionApi.list });
  const supabaseConnected = !!connections?.find((c) => c.provider === 'SUPABASE')?.connected;

  const inputs: PlanInputs = { mode };
  if (db.choice !== 'MANUAL') {
    inputs.databaseChoice = db.choice;
    inputs.supabaseOrgId = db.orgId || undefined;
    inputs.supabaseProjectRef = db.projectRef || undefined;
    inputs.supabaseProjectName = db.projectName || undefined;
    inputs.supabaseRegion = db.region || undefined;
    inputs.applyMigrations = db.applyMigrations;
  }

  const planMut = useMutation({
    mutationFn: () => automationApi.plan(projectId, inputs),
    onSuccess: (p) => { setPlan(p); setConfirmation(null); setActiveRunId(null); },
  });
  const confirmMut = useMutation({
    mutationFn: () => automationApi.confirm(projectId, { ...inputs, planHash: plan!.planHash }),
    onSuccess: (c) => setConfirmation({ runId: c.runId, nonce: c.nonce, expiresAt: c.expiresAt }),
  });
  const executeMut = useMutation({
    mutationFn: () => automationApi.execute(projectId, confirmation!.runId, confirmation!.nonce),
    onSuccess: (run) => { setActiveRunId(run.id); setConfirmation(null); },
  });

  const { data: activeRun } = useQuery({
    queryKey: ['automationRun', projectId, activeRunId],
    queryFn: () => automationApi.run(projectId, activeRunId!),
    enabled: activeRunId != null,
    refetchInterval: (q) => {
      const run = q.state.data as AutomationRun | undefined;
      return run && (run.status === 'RUNNING' || run.status === 'PENDING') ? 1500 : false;
    },
  });

  const missing = (['GITHUB', 'NETLIFY', 'RENDER'] as const)
    .filter((p) => !connections?.find((c) => c.provider === p)?.connected);

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
      <Link to={`/projects/${projectId}/blueprint`} className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-primary-600 mb-4">
        <ArrowLeft className="w-4 h-4" /> Back to blueprint
      </Link>
      <h1 className="text-2xl font-bold text-slate-900 dark:text-slate-100 mb-2 flex items-center gap-2">
        <Rocket className="w-6 h-6 text-primary-600" /> Automate Deployment{project ? ` — ${project.name}` : ''}
      </h1>

      <div className="card p-4 mb-6 flex items-start gap-3 border-primary-200 dark:border-primary-800 bg-primary-50/50 dark:bg-primary-900/10">
        <ShieldCheck className="w-5 h-5 text-primary-600 shrink-0 mt-0.5" />
        <p className="text-sm text-slate-600 dark:text-slate-300">
          <span className="font-semibold text-slate-900 dark:text-slate-100">DeployPilot will only perform the actions
          shown below after you confirm.</span> Generating a plan changes nothing. You review every action, then confirm
          the exact plan immediately before anything runs.
        </p>
      </div>

      <ConnectionStatus connections={connections} missing={missing} />

      <SecretsSection projectId={projectId} plan={plan} />

      {/* mode selector */}
      <section className="card p-4 mb-6">
        <h2 className="text-base font-semibold text-slate-900 dark:text-slate-100 mb-3">1. Choose how much DeployPilot does</h2>
        <div className="grid sm:grid-cols-3 gap-3">
          {([
            { m: 'GUIDE_ME', t: 'Guide Me', d: 'Explain every step. You perform each action manually.' },
            { m: 'PREPARE_FOR_ME', t: 'Prepare for Me', d: 'Generate the config and plan. No external changes.' },
            { m: 'DEPLOY_FOR_ME', t: 'Deploy for Me', d: 'Perform only the actions you explicitly approve.' },
          ] as const).map((o) => (
            <button key={o.m} onClick={() => { setMode(o.m); setPlan(null); setConfirmation(null); }}
              className={`text-left p-3 rounded-lg border transition-all ${mode === o.m
                ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                : 'border-slate-200 dark:border-slate-700 hover:border-slate-300'}`}>
              <p className="text-sm font-semibold text-slate-900 dark:text-slate-100">{o.t}</p>
              <p className="text-xs text-slate-500 mt-0.5">{o.d}</p>
            </button>
          ))}
        </div>

        {mode === 'DEPLOY_FOR_ME' && (
          <DatabaseChoicePanel db={db} setDb={setDb} supabaseConnected={supabaseConnected} onChange={() => { setPlan(null); setConfirmation(null); }} />
        )}

        <button className="btn-primary mt-4" disabled={planMut.isPending} onClick={() => planMut.mutate()}>
          {planMut.isPending
            ? <span className="inline-flex items-center gap-2"><Loader2 className="w-4 h-4 animate-spin" /> Generating…</span>
            : <span className="inline-flex items-center gap-2"><RefreshCw className="w-4 h-4" /> Generate action plan</span>}
        </button>
        {planMut.isError && <p className="text-sm text-red-600 dark:text-red-400 mt-2">{errorMessage(planMut.error)}</p>}
      </section>

      {plan && !activeRun && (
        <PlanView plan={plan} mode={mode}
          confirmation={confirmation}
          onConfirm={() => confirmMut.mutate()} confirming={confirmMut.isPending} confirmError={confirmMut.error}
          onExecute={() => executeMut.mutate()} executing={executeMut.isPending} executeError={executeMut.error} />
      )}

      {activeRun && <RunView run={activeRun} projectId={projectId} onRetried={(id) => { setActiveRunId(id); qc.invalidateQueries({ queryKey: ['automationRun', projectId] }); }}
        planInputs={inputs} planHash={plan?.planHash} />}
    </div>
  );
}

function ConnectionStatus({ connections, missing }: { connections?: ProviderConnection[]; missing: readonly string[] }) {
  return (
    <section className="card p-4 mb-6">
      <div className="flex items-center justify-between">
        <h2 className="text-base font-semibold text-slate-900 dark:text-slate-100 flex items-center gap-2">
          <Plug className="w-4 h-4 text-primary-600" /> Connections
        </h2>
        <Link to="/connections" className="text-sm text-primary-600 hover:text-primary-700">Manage</Link>
      </div>
      <div className="flex flex-wrap gap-2 mt-3">
        {(['GITHUB', 'NETLIFY', 'RENDER', 'SUPABASE'] as const).map((p) => {
          const c = connections?.find((x) => x.provider === p);
          const Icon = PROVIDER_ICON[p];
          return (
            <span key={p} className={`badge ${c?.connected ? 'badge-green' : 'badge-gray'} gap-1.5`}>
              <Icon className="w-3.5 h-3.5" /> {p.charAt(0) + p.slice(1).toLowerCase()}
              {c?.connected ? (c.accountLabel ? ` · ${c.accountLabel}` : ' · connected') : ' · not connected'}
            </span>
          );
        })}
      </div>
      {missing.length > 0 && (
        <p className="text-xs text-amber-600 dark:text-amber-400 mt-2">
          Connect {missing.map((m) => m.charAt(0) + m.slice(1).toLowerCase()).join(', ')} to deploy automatically.
          Supabase is optional — connect it only to let DeployPilot prepare your database.
        </p>
      )}
    </section>
  );
}

type DbState = { choice: DatabaseChoice; orgId: string; projectRef: string; projectName: string; region: string; applyMigrations: boolean };

function DatabaseChoicePanel({ db, setDb, supabaseConnected, onChange }: {
  db: DbState; setDb: (d: DbState) => void; supabaseConnected: boolean; onChange: () => void;
}) {
  const set = (patch: Partial<DbState>) => { setDb({ ...db, ...patch }); onChange(); };
  const { data: orgs } = useQuery({
    queryKey: ['supabase-orgs'], queryFn: connectionApi.supabaseOrganizations,
    enabled: supabaseConnected && db.choice === 'CREATE_SUPABASE_PROJECT',
  });
  const { data: projects } = useQuery({
    queryKey: ['supabase-projects'], queryFn: connectionApi.supabaseProjects,
    enabled: supabaseConnected && db.choice === 'EXISTING_SUPABASE_PROJECT',
  });

  return (
    <div className="mt-4 border-t border-slate-200 dark:border-slate-700 pt-4">
      <h3 className="text-sm font-semibold flex items-center gap-2 mb-2">
        <Database className="w-4 h-4 text-primary-600" /> Database (Supabase)
      </h3>
      {!supabaseConnected ? (
        <p className="text-xs text-slate-500">
          DeployPilot will use the manual database handoff. <Link to="/connections" className="text-primary-600 hover:underline">Connect Supabase</Link> to
          let DeployPilot create or prepare your database automatically (free plan only).
        </p>
      ) : (
        <div className="space-y-3">
          <div className="grid sm:grid-cols-3 gap-2">
            {([
              { c: 'MANUAL', t: 'Manual', d: 'I will supply DB connection details.' },
              { c: 'EXISTING_SUPABASE_PROJECT', t: 'Use existing', d: 'Use a Supabase project I already have.' },
              { c: 'CREATE_SUPABASE_PROJECT', t: 'Create new', d: 'Create a new free Supabase project.' },
            ] as const).map((o) => (
              <button key={o.c} type="button" onClick={() => set({ choice: o.c })}
                className={`text-left p-2.5 rounded-lg border text-xs ${db.choice === o.c
                  ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20' : 'border-slate-200 dark:border-slate-700'}`}>
                <p className="font-semibold">{o.t}</p><p className="text-slate-500 mt-0.5">{o.d}</p>
              </button>
            ))}
          </div>

          {db.choice === 'EXISTING_SUPABASE_PROJECT' && (
            <label className="block text-sm">
              <span className="font-medium">Existing project</span>
              <select className="input w-full mt-1" value={db.projectRef} onChange={(e) => set({ projectRef: e.target.value })}>
                <option value="">Select a project…</option>
                {projects?.map((p) => <option key={p.ref} value={p.ref}>{p.name} ({p.status})</option>)}
              </select>
            </label>
          )}

          {db.choice === 'CREATE_SUPABASE_PROJECT' && (
            <div className="grid sm:grid-cols-3 gap-2">
              <label className="block text-sm">
                <span className="font-medium">Organization</span>
                <select className="input w-full mt-1" value={db.orgId} onChange={(e) => set({ orgId: e.target.value })}>
                  <option value="">Select…</option>
                  {orgs?.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
                </select>
              </label>
              <label className="block text-sm">
                <span className="font-medium">Project name</span>
                <input className="input w-full mt-1" value={db.projectName} maxLength={60}
                  onChange={(e) => set({ projectName: e.target.value })} placeholder="my-app-db" />
              </label>
              <label className="block text-sm">
                <span className="font-medium">Region</span>
                <input className="input w-full mt-1" value={db.region} maxLength={30}
                  onChange={(e) => set({ region: e.target.value })} placeholder="us-east-1" />
              </label>
            </div>
          )}

          {db.choice !== 'MANUAL' && (
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={db.applyMigrations} onChange={(e) => set({ applyMigrations: e.target.checked })} />
              Apply safe, repository-owned migrations (destructive migrations are always blocked)
            </label>
          )}
          <p className="text-xs text-slate-500">
            DeployPilot never selects a paid plan. Creating a project, applying migrations and changing variables are shown
            in the plan and only happen after you confirm.
          </p>
        </div>
      )}
    </div>
  );
}

function SecretsSection({ projectId, plan }: { projectId: number; plan: DeploymentActionPlan | null }) {
  const qc = useQueryClient();
  const [name, setName] = useState('');
  const [value, setValue] = useState('');
  const { data: secrets } = useQuery({ queryKey: ['secrets', projectId], queryFn: () => automationApi.secrets(projectId) });

  const saveMut = useMutation({
    mutationFn: () => automationApi.saveSecret(projectId, { name: name.trim(), value, destination: 'Backend service' }),
    onSuccess: () => { setName(''); setValue(''); qc.invalidateQueries({ queryKey: ['secrets', projectId] }); },
  });
  const removeMut = useMutation({
    mutationFn: (n: string) => automationApi.removeSecret(projectId, n),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['secrets', projectId] }),
  });

  const needsInput = plan?.environmentVariables.filter((v) => v.valueStatus === 'NEEDS_INPUT' && v.required) ?? [];

  return (
    <section className="card p-4 mb-6">
      <h2 className="text-base font-semibold text-slate-900 dark:text-slate-100 flex items-center gap-2 mb-2">
        <KeyRound className="w-4 h-4 text-primary-600" /> Secret values
      </h2>
      <p className="text-xs text-slate-500 mb-3">
        Supply provider-issued keys and database connection details. Values are encrypted, masked and never shown again.
        DeployPilot generates application secrets (like JWT keys) for you and never fabricates provider credentials.
      </p>
      {needsInput.length > 0 && (
        <p className="text-xs text-amber-600 dark:text-amber-400 mb-2">
          Still needed: {needsInput.map((v) => v.name).join(', ')}
        </p>
      )}
      {secrets && secrets.length > 0 && (
        <ul className="mb-3 space-y-1">
          {secrets.map((s) => (
            <li key={s.name} className="flex items-center justify-between text-sm bg-slate-50 dark:bg-slate-800 rounded px-3 py-1.5">
              <span className="font-mono text-xs flex items-center gap-2"><Lock className="w-3.5 h-3.5 text-slate-400" />{s.name}
                <span className="text-slate-400">{s.masked}</span></span>
              <button className="text-red-500 hover:text-red-600" onClick={() => removeMut.mutate(s.name)}><Trash2 className="w-4 h-4" /></button>
            </li>
          ))}
        </ul>
      )}
      <form className="flex flex-wrap gap-2 items-end" onSubmit={(e) => { e.preventDefault(); if (name.trim() && value) saveMut.mutate(); }}>
        <label className="block flex-1 min-w-[140px]">
          <span className="text-xs font-medium text-slate-600 dark:text-slate-300">Variable name</span>
          <input className="input w-full mt-1 font-mono text-xs" placeholder="DATABASE_URL" value={name}
            onChange={(e) => setName(e.target.value)} maxLength={200} />
        </label>
        <label className="block flex-1 min-w-[140px]">
          <span className="text-xs font-medium text-slate-600 dark:text-slate-300">Value</span>
          <input type="password" autoComplete="off" className="input w-full mt-1 font-mono text-xs" placeholder="value"
            value={value} onChange={(e) => setValue(e.target.value)} maxLength={8000} />
        </label>
        <button type="submit" className="btn-secondary text-sm" disabled={!name.trim() || !value || saveMut.isPending}>
          {saveMut.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : 'Save'}
        </button>
      </form>
      {saveMut.isError && <p className="text-xs text-red-600 dark:text-red-400 mt-1">{errorMessage(saveMut.error)}</p>}
    </section>
  );
}

function PlanView({ plan, mode, confirmation, onConfirm, confirming, confirmError, onExecute, executing, executeError }: {
  plan: DeploymentActionPlan; mode: string;
  confirmation: { runId: number; nonce: string; expiresAt: string } | null;
  onConfirm: () => void; confirming: boolean; confirmError: unknown;
  onExecute: () => void; executing: boolean; executeError: unknown;
}) {
  return (
    <section className="space-y-4">
      <div className="card p-4">
        <h2 className="text-base font-semibold text-slate-900 dark:text-slate-100 mb-2">2. Review the exact plan</h2>
        <div className="flex flex-wrap gap-4 text-sm text-slate-600 dark:text-slate-300">
          <span className="inline-flex items-center gap-1.5"><Github className="w-4 h-4" />{plan.repository}</span>
          {plan.branch && <span className="inline-flex items-center gap-1.5"><GitBranch className="w-4 h-4" />{plan.branch}</span>}
          {plan.commitSha && <span className="inline-flex items-center gap-1.5"><GitCommitHorizontal className="w-4 h-4" />{plan.commitSha.substring(0, 10)}</span>}
        </div>
      </div>

      {plan.blockers.length > 0 && (
        <div className="card p-4 border-red-200 dark:border-red-900 bg-red-50/50 dark:bg-red-900/10">
          <h3 className="text-sm font-semibold text-red-700 dark:text-red-400 flex items-center gap-2 mb-1"><ShieldAlert className="w-4 h-4" /> Resolve before deploying</h3>
          <ul className="text-sm text-red-700 dark:text-red-300 list-disc list-inside space-y-0.5">
            {plan.blockers.map((b, i) => <li key={i}>{b}</li>)}
          </ul>
        </div>
      )}
      {plan.warnings.length > 0 && (
        <div className="card p-4 border-amber-200 dark:border-amber-900 bg-amber-50/50 dark:bg-amber-900/10">
          <ul className="text-sm text-amber-700 dark:text-amber-300 list-disc list-inside space-y-0.5">
            {plan.warnings.map((w, i) => <li key={i}>{w}</li>)}
          </ul>
        </div>
      )}

      {plan.database?.required && (
        <div className="card p-4">
          <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100 flex items-center gap-2 mb-1"><Database className="w-4 h-4 text-primary-600" /> Database</h3>
          <p className="text-sm text-slate-600 dark:text-slate-300">{plan.database.instructions}</p>
          <p className="text-xs mt-1"><span className={`badge ${plan.database.connectionSupplied ? 'badge-green' : 'badge-amber'}`}>
            {plan.database.connectionSupplied ? 'Connection supplied' : 'Connection needed'}</span>
            <span className="text-slate-500 ml-2">Fields: {plan.database.requiredFields.join(', ')}</span></p>
        </div>
      )}

      <div className="card p-4">
        <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100 mb-3">Actions in order</h3>
        <div className="space-y-2">
          {plan.actions.map((a) => <ActionRow key={a.id} action={a} />)}
        </div>
      </div>

      {plan.environmentVariables.length > 0 && (
        <div className="card p-4 overflow-x-auto">
          <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100 mb-3">Environment variables</h3>
          <table className="w-full text-xs min-w-[560px]">
            <thead><tr className="text-left text-slate-500 border-b border-slate-200 dark:border-slate-700">
              <th className="p-2">Name</th><th className="p-2">Destination</th><th className="p-2">Source</th>
              <th className="p-2">Required</th><th className="p-2">Value</th></tr></thead>
            <tbody>
              {plan.environmentVariables.map((v) => <EnvRow key={v.name} v={v} />)}
            </tbody>
          </table>
        </div>
      )}

      {mode === 'DEPLOY_FOR_ME' && plan.executable && (
        <div className="card p-4 border-primary-200 dark:border-primary-800 bg-primary-50/40 dark:bg-primary-900/10">
          <p className="text-sm font-semibold text-slate-900 dark:text-slate-100 mb-3">{plan.consentNotice}</p>
          {!confirmation ? (
            <>
              <button className="btn-primary" onClick={onConfirm} disabled={confirming}>
                {confirming ? <span className="inline-flex items-center gap-2"><Loader2 className="w-4 h-4 animate-spin" /> Preparing…</span>
                  : <span className="inline-flex items-center gap-2"><Lock className="w-4 h-4" /> Confirm these actions</span>}
              </button>
              {confirmError != null && <p className="text-sm text-red-600 dark:text-red-400 mt-2">{errorMessage(confirmError)}</p>}
            </>
          ) : (
            <>
              <p className="text-xs text-slate-500 mb-2">
                Confirmation ready (expires {new Date(confirmation.expiresAt).toLocaleTimeString()}). Executing starts the
                real deployment now.
              </p>
              <button className="btn-primary" onClick={onExecute} disabled={executing}>
                {executing ? <span className="inline-flex items-center gap-2"><Loader2 className="w-4 h-4 animate-spin" /> Starting…</span>
                  : <span className="inline-flex items-center gap-2"><Play className="w-4 h-4" /> Execute deployment</span>}
              </button>
              {executeError != null && <p className="text-sm text-red-600 dark:text-red-400 mt-2">{errorMessage(executeError)}</p>}
            </>
          )}
        </div>
      )}
      {mode !== 'DEPLOY_FOR_ME' && (
        <div className="card p-4 text-sm text-slate-500">
          This mode does not change any external service. Switch to <span className="font-medium">Deploy for Me</span> to
          execute the plan after confirmation.
        </div>
      )}
    </section>
  );
}

function ActionRow({ action: a }: { action: PlannedAction }) {
  const Icon = PROVIDER_ICON[a.provider] ?? Circle;
  return (
    <div className="flex items-start gap-3 text-sm border-b border-slate-100 dark:border-slate-700/50 last:border-0 pb-2 last:pb-0">
      <span className="text-slate-400 font-mono text-xs mt-0.5 w-5 shrink-0">{a.order}</span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className={`badge ${TYPE_BADGE[a.type] ?? 'badge-gray'}`}>{a.type.replace('_', ' ')}</span>
          <span className="font-medium text-slate-800 dark:text-slate-200">{a.title}</span>
          {a.provider !== 'NONE' && <span className="text-xs text-slate-400 inline-flex items-center gap-1"><Icon className="w-3.5 h-3.5" />{a.account ?? a.provider}</span>}
        </div>
        <p className="text-xs text-slate-500 mt-0.5">{a.description}</p>
        <div className="flex flex-wrap gap-1.5 mt-1">
          {a.createsNewResource && <span className="badge badge-blue">Creates new resource</span>}
          {a.changesExisting && <span className="badge badge-amber">Changes existing</span>}
          {a.requiresRepositoryChange && <span className="badge badge-gray">Repository change (via PR)</span>}
          <span className={`badge ${a.reversible ? 'badge-green' : 'badge-red'}`}>{a.reversible ? 'Reversible' : 'Not easily reversible'}</span>
          {a.costNote && <span className="badge badge-gray">{a.costNote}</span>}
          {a.environmentVariableNames.length > 0 && <span className="badge badge-gray">vars: {a.environmentVariableNames.join(', ')}</span>}
        </div>
      </div>
    </div>
  );
}

function EnvRow({ v }: { v: EnvVarPlanItem }) {
  const status = { READY: 'badge-green', NEEDS_INPUT: 'badge-amber', WILL_BE_GENERATED: 'badge-blue', FROM_PREVIOUS_STEP: 'badge-gray' }[v.valueStatus] ?? 'badge-gray';
  return (
    <tr className="border-b border-slate-100 dark:border-slate-700/50 last:border-0">
      <td className="p-2 font-mono">{v.name} {v.secret && <Lock className="w-3 h-3 inline text-slate-400" />}</td>
      <td className="p-2">{v.destination}</td>
      <td className="p-2 text-slate-500">{v.source}</td>
      <td className="p-2">{v.required ? 'Required' : 'Optional'}</td>
      <td className="p-2">
        <span className={`badge ${status}`}>{v.valueStatus.replace(/_/g, ' ').toLowerCase()}</span>
        {v.secret && <span className="text-slate-400 ml-1">••••</span>}
      </td>
    </tr>
  );
}

function RunView({ run, projectId, onRetried, planInputs, planHash }: {
  run: AutomationRun; projectId: number; onRetried: (runId: number) => void;
  planInputs: PlanInputs; planHash?: string;
}) {
  const retryMut = useMutation({
    mutationFn: async () => {
      // A retry needs a fresh confirmation bound to the same plan.
      const confirmation = await automationApi.confirm(projectId, { ...planInputs, planHash: planHash! });
      return automationApi.retry(projectId, run.id, confirmation.nonce);
    },
    onSuccess: (r) => onRetried(r.id),
  });

  const steps = run.steps ?? [];
  const running = run.status === 'RUNNING' || run.status === 'PENDING';

  return (
    <section className="space-y-4 mt-6">
      <div className="card p-4">
        <div className="flex flex-wrap items-center gap-3">
          <h2 className="text-base font-semibold text-slate-900 dark:text-slate-100">3. Deployment progress</h2>
          <span className={`badge ${RUN_BADGE[run.status] ?? 'badge-gray'}`}>
            {running && <Loader2 className="w-3 h-3 animate-spin mr-1" />}{run.status}
          </span>
          {run.verificationStatus && <span className="badge badge-gray">Verification: {run.verificationStatus}</span>}
        </div>
        {run.failureReason && <p className="text-sm text-red-600 dark:text-red-400 mt-2">{run.failureReason}</p>}
        {run.status === 'SUCCEEDED' && run.outputs?.frontendUrl && (
          <p className="text-sm text-slate-600 dark:text-slate-300 mt-2">Frontend: <a className="text-primary-600" href={run.outputs.frontendUrl} target="_blank" rel="noreferrer">{run.outputs.frontendUrl}</a></p>
        )}
        {run.status === 'SUCCEEDED' && run.outputs?.backendUrl && (
          <p className="text-sm text-slate-600 dark:text-slate-300">Backend: <a className="text-primary-600" href={run.outputs.backendUrl} target="_blank" rel="noreferrer">{run.outputs.backendUrl}</a></p>
        )}
        {run.outputs?.pullRequestUrl && (
          <p className="text-sm text-slate-600 dark:text-slate-300">Pull request: <a className="text-primary-600" href={run.outputs.pullRequestUrl} target="_blank" rel="noreferrer">{run.outputs.pullRequestUrl}</a></p>
        )}
        {(run.status === 'FAILED' || run.status === 'PAUSED') && planHash && (
          <button className="btn-primary mt-3" onClick={() => retryMut.mutate()} disabled={retryMut.isPending}>
            {retryMut.isPending ? <span className="inline-flex items-center gap-2"><Loader2 className="w-4 h-4 animate-spin" /> Retrying…</span>
              : <span className="inline-flex items-center gap-2"><RefreshCw className="w-4 h-4" /> Retry from the failed step</span>}
          </button>
        )}
        {retryMut.isError && <p className="text-sm text-red-600 dark:text-red-400 mt-2">{errorMessage(retryMut.error)}</p>}
      </div>

      <div className="card p-4">
        <div className="space-y-3">
          {steps.map((s) => <StepRow key={s.id} step={s} />)}
        </div>
      </div>
    </section>
  );
}

function StepRow({ step }: { step: ExecutionStep }) {
  const meta = STEP_META[step.status] ?? STEP_META.PENDING;
  const Icon = meta.icon;
  return (
    <div className="flex items-start gap-3 text-sm">
      <Icon className={`w-4 h-4 mt-0.5 shrink-0 ${meta.cls} ${meta.spin ? 'animate-spin' : ''}`} />
      <div className="min-w-0 flex-1">
        <p className="text-slate-800 dark:text-slate-200 font-medium">{step.title}
          <span className={`ml-2 text-[11px] ${meta.cls}`}>{step.status}</span></p>
        {step.detail && <p className="text-xs text-slate-500 break-words">{step.detail}</p>}
        {step.sanitizedLog && (
          <pre className="text-[11px] font-mono bg-slate-50 dark:bg-slate-800 rounded p-2 mt-1 overflow-x-auto whitespace-pre-wrap">{step.sanitizedLog}</pre>
        )}
      </div>
    </div>
  );
}
