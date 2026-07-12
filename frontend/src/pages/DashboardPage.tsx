import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { dashboardApi, projectApi } from '@/lib/api';
import ProjectCard from '@/components/ProjectCard';
import ProgressBar from '@/components/ProgressBar';
import {
  FolderGit2, CheckCircle, Bookmark, ArrowRight, Plus,
  Terminal, BookOpen, Wrench, ShieldCheck
} from 'lucide-react';

export default function DashboardPage() {
  const { data: dash } = useQuery({ queryKey: ['dashboard'], queryFn: () => dashboardApi.get() });
  const { data: projects } = useQuery({ queryKey: ['projects'], queryFn: () => projectApi.list() });

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
        <div>
          <h1 className="text-2xl font-bold">Dashboard</h1>
          <p className="text-slate-500 dark:text-slate-400 mt-1">Track your deployments and next steps</p>
        </div>
        <Link to="/projects/new" className="btn-primary shrink-0">
          <Plus className="w-4 h-4" />New Project
        </Link>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
        {[
          { icon: FolderGit2, label: 'Projects', value: dash?.totalProjects ?? 0, color: 'text-primary-600' },
          { icon: CheckCircle, label: 'Steps Done', value: `${dash?.completedSteps ?? 0}/${dash?.totalSteps ?? 0}`, color: 'text-green-600' },
          { icon: Bookmark, label: 'Bookmarks', value: dash?.bookmarkCount ?? 0, color: 'text-amber-600' },
          { icon: CheckCircle, label: 'Progress', value: dash && dash.totalSteps > 0 ? `${Math.round((dash.completedSteps / dash.totalSteps) * 100)}%` : '0%', color: 'text-purple-600' },
        ].map((s) => (
          <div key={s.label} className="card p-4">
            <div className="flex items-center gap-3">
              <s.icon className={`w-5 h-5 ${s.color}`} />
              <div>
                <div className="text-2xl font-bold">{s.value}</div>
                <div className="text-xs text-slate-500">{s.label}</div>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Next Step CTA */}
      {dash?.nextStepTitle && (
        <div className="bg-primary-600 text-white rounded-xl p-6 mb-8">
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div>
              <p className="text-primary-200 text-sm font-medium mb-1">YOUR NEXT STEP</p>
              <h2 className="text-xl font-bold">{dash.nextStepTitle}</h2>
              <p className="text-primary-200 text-sm mt-1">{dash.nextStepAction}</p>
            </div>
            {projects && projects.length > 0 && (
              <Link to={`/projects/${projects[0].id}/plan`}
                className="btn bg-white text-primary-600 hover:bg-primary-50 shrink-0">
                Show My Next Step <ArrowRight className="w-4 h-4" />
              </Link>
            )}
          </div>
          {dash.totalSteps > 0 && (
            <div className="mt-4">
              <ProgressBar current={dash.completedSteps} total={dash.totalSteps} />
            </div>
          )}
        </div>
      )}

      {/* Projects */}
      <div className="mb-8">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold">Your Projects</h2>
          <Link to="/projects" className="text-sm text-primary-600 hover:underline">View all</Link>
        </div>
        {projects && projects.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {projects.slice(0, 6).map((p) => (
              <ProjectCard key={p.id} project={p} />
            ))}
          </div>
        ) : (
          <div className="card p-8 text-center">
            <FolderGit2 className="w-12 h-12 text-slate-300 mx-auto mb-3" />
            <p className="text-slate-500 mb-4">No projects yet. Create your first project to get a deployment plan.</p>
            <Link to="/projects/new" className="btn-primary">Create Project</Link>
          </div>
        )}
      </div>

      {/* Quick Links */}
      <div>
        <h2 className="text-lg font-semibold mb-4">Quick Links</h2>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {[
            { to: '/git-commands', icon: Terminal, label: 'Git Commands', desc: 'Searchable reference' },
            { to: '/guides', icon: BookOpen, label: 'Guides', desc: 'Platform guides' },
            { to: '/troubleshoot', icon: Wrench, label: 'Troubleshoot', desc: 'AI error solver' },
            { to: '/security', icon: ShieldCheck, label: 'Security', desc: 'Checklists & tools' },
          ].map((l) => (
            <Link key={l.to} to={l.to} className="card-hover p-4 flex items-center gap-3">
              <l.icon className="w-5 h-5 text-primary-600 shrink-0" />
              <div>
                <div className="font-medium text-sm">{l.label}</div>
                <div className="text-xs text-slate-500">{l.desc}</div>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}
