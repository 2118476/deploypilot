import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { statusApi } from '@/lib/api';
import type { ProjectDashboardStatus, RequiredAction, RecommendedAction } from '@/types';
import {
  CheckCircle2, Circle, ExternalLink, GitPullRequest, Server, Globe, ShieldCheck,
  Loader2, AlertTriangle, ArrowRight, Activity as ActivityIcon, Clock,
} from 'lucide-react';

const BADGE: Record<ProjectDashboardStatus, string> = {
  NOT_ANALYSED: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
  ANALYSING: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
  SETUP_REQUIRED: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300',
  BLUEPRINT_READY: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300',
  WAITING_FOR_CONNECTION: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300',
  WAITING_FOR_SECRET: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300',
  WAITING_FOR_CONFIRMATION: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300',
  DEPLOYING: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
  VERIFYING: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
  PAUSED: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300',
  HEALTHY: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
  DEGRADED: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-300',
  FAILED: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
  UNKNOWN: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
};

const ACTIVE: ProjectDashboardStatus[] = ['ANALYSING', 'DEPLOYING', 'VERIFYING', 'PAUSED', 'WAITING_FOR_CONFIRMATION'];

function actionLink(projectId: number, type: string, pullRequestUrl?: string): string {
  switch (type) {
    case 'CONNECT_PROVIDER': return '/connections';
    case 'GENERATE_BLUEPRINT': return `/projects/${projectId}/blueprint`;
    case 'RUN_ANALYSIS': return `/projects/${projectId}`;
    case 'MERGE_PR': return pullRequestUrl || `/projects/${projectId}/automate`;
    default: return `/projects/${projectId}/automate`; // REVIEW_PLAN, CONFIRM_DEPLOYMENT, RETRY_FAILED_STEP, ADD_SECRET, VERIFY
  }
}

export default function ProjectStatusPanel({ projectId }: { projectId: number }) {
  const { data: status } = useQuery({
    queryKey: ['project-status', projectId],
    queryFn: () => statusApi.get(projectId),
    enabled: !!projectId,
    refetchInterval: (q) => (q.state.data && ACTIVE.includes(q.state.data.status) ? 4000 : false),
  });
  const isActive = status ? ACTIVE.includes(status.status) : false;
  const { data: activity } = useQuery({
    queryKey: ['project-activity', projectId],
    queryFn: () => statusApi.activity(projectId, 12),
    enabled: !!projectId,
    refetchInterval: isActive ? 4000 : false,
  });

  if (!status) return <div className="card p-6 text-sm text-slate-400">Loading project status…</div>;

  const rec: RecommendedAction | undefined = status.recommendedNextStep;

  return (
    <div className="space-y-6">
      {/* Status card */}
      <div className="card p-5">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <span className={`text-xs font-semibold px-2.5 py-1 rounded-full ${BADGE[status.status]}`}>
                {status.status.replace(/_/g, ' ')}
              </span>
              {status.lastUpdated && (
                <span className="text-xs text-slate-400 flex items-center gap-1">
                  <Clock className="w-3 h-3" /> updated {new Date(status.lastUpdated).toLocaleString()}
                </span>
              )}
            </div>
            <p className="text-slate-700 dark:text-slate-200">{status.summary}</p>
          </div>
          {rec && (
            <Link to={actionLink(projectId, rec.type, status.pullRequestUrl)}
              className="btn-primary shrink-0 whitespace-nowrap">
              {rec.label} <ArrowRight className="w-4 h-4" />
            </Link>
          )}
        </div>

        {/* What DeployPilot is doing now */}
        {status.currentAction && (
          <div className="mt-4 flex items-center gap-2 text-sm bg-blue-50 dark:bg-blue-900/20 rounded-lg px-3 py-2">
            {isActive ? <Loader2 className="w-4 h-4 animate-spin text-blue-600" /> : <ActivityIcon className="w-4 h-4 text-blue-600" />}
            <span><span className="font-medium">Now:</span> {status.currentAction}</span>
          </div>
        )}
      </div>

      {/* What you need to do */}
      {status.requiredActions.length > 0 && (
        <div>
          <h3 className="text-sm font-semibold mb-3 text-slate-600 dark:text-slate-300">What you need to do</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {status.requiredActions.map((a: RequiredAction, i) => (
              <Link key={i} to={actionLink(projectId, a.type, status.pullRequestUrl)}
                className="card-hover p-4 flex items-start gap-3">
                <AlertTriangle className="w-5 h-5 text-amber-500 shrink-0 mt-0.5" />
                <div>
                  <div className="font-medium text-sm">{a.label}</div>
                  {a.detail && <div className="text-xs text-slate-500 mt-0.5">{a.detail}</div>}
                </div>
              </Link>
            ))}
          </div>
        </div>
      )}

      {/* Deployment outputs */}
      {(status.frontendUrl || status.backendUrl || status.pullRequestUrl || status.verificationStatus) && (
        <div>
          <h3 className="text-sm font-semibold mb-3 text-slate-600 dark:text-slate-300">Deployment</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {status.frontendUrl && <OutputCard icon={Globe} label="Frontend" value={status.frontendUrl} href={status.frontendUrl} />}
            {status.backendUrl && <OutputCard icon={Server} label="Backend" value={status.backendUrl} href={status.backendUrl} />}
            {status.pullRequestUrl && <OutputCard icon={GitPullRequest} label="Config PR" value="Open pull request" href={status.pullRequestUrl} />}
            {status.verificationStatus && (
              <div className="card p-3 flex items-center gap-3">
                <ShieldCheck className="w-4 h-4 text-primary-600" />
                <div><div className="text-xs text-slate-500">Verification</div><div className="font-medium text-sm">{status.verificationStatus}</div></div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Milestones */}
      <div>
        <h3 className="text-sm font-semibold mb-3 text-slate-600 dark:text-slate-300">What has been completed</h3>
        <ol className="space-y-2">
          {status.milestones.map((m) => (
            <li key={m.key} className="flex items-center gap-2 text-sm">
              {m.done
                ? <CheckCircle2 className="w-4 h-4 text-green-500 shrink-0" />
                : <Circle className="w-4 h-4 text-slate-300 shrink-0" />}
              <span className={m.done ? '' : 'text-slate-400'}>{m.label}</span>
            </li>
          ))}
        </ol>
      </div>

      {/* Recent activity */}
      {activity && activity.length > 0 && (
        <div>
          <h3 className="text-sm font-semibold mb-3 text-slate-600 dark:text-slate-300">Recent activity</h3>
          <ul className="space-y-2">
            {activity.map((e) => (
              <li key={e.id} className="flex items-start gap-2 text-sm">
                <ActivityIcon className="w-3.5 h-3.5 text-slate-400 mt-1 shrink-0" />
                <div>
                  <span className="text-slate-700 dark:text-slate-200">{e.summary}</span>
                  <span className="text-xs text-slate-400 ml-2">{new Date(e.createdAt).toLocaleTimeString()}</span>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function OutputCard({ icon: Icon, label, value, href }: { icon: typeof Globe; label: string; value: string; href: string }) {
  return (
    <a href={href} target="_blank" rel="noreferrer" className="card-hover p-3 flex items-center gap-3">
      <Icon className="w-4 h-4 text-primary-600 shrink-0" />
      <div className="min-w-0">
        <div className="text-xs text-slate-500">{label}</div>
        <div className="font-medium text-sm truncate flex items-center gap-1">{value} <ExternalLink className="w-3 h-3 shrink-0" /></div>
      </div>
    </a>
  );
}
