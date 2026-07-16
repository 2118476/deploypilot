import { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { dashboardApi, projectApi } from '@/lib/api';
import ProjectCard from '@/components/ProjectCard';
import ProjectStatusPanel from '@/components/ProjectStatusPanel';
import CopilotPanel from '@/components/CopilotPanel';
import {
  FolderGit2, Bookmark, Plus, Terminal, BookOpen, Wrench, ShieldCheck,
} from 'lucide-react';

export default function DashboardPage() {
  const { data: dash } = useQuery({ queryKey: ['dashboard'], queryFn: () => dashboardApi.get() });
  const { data: projects } = useQuery({ queryKey: ['projects'], queryFn: () => projectApi.list() });

  const [selectedId, setSelectedId] = useState<number | null>(null);
  useEffect(() => {
    if (projects && projects.length > 0 && selectedId === null) setSelectedId(projects[0].id);
  }, [projects, selectedId]);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
        <div>
          <h1 className="text-2xl font-bold">Dashboard</h1>
          <p className="text-slate-500 dark:text-slate-400 mt-1">Your projects, live status and Copilot</p>
        </div>
        <Link to="/projects/new" className="btn-primary shrink-0">
          <Plus className="w-4 h-4" />New Project
        </Link>
      </div>

      {/* Stats (no fake progress percentage) */}
      <div className="grid grid-cols-2 gap-4 mb-8 max-w-md">
        {[
          { icon: FolderGit2, label: 'Projects', value: dash?.totalProjects ?? 0, color: 'text-primary-600' },
          { icon: Bookmark, label: 'Bookmarks', value: dash?.bookmarkCount ?? 0, color: 'text-amber-600' },
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

      {/* Intelligent status + Copilot for the selected project */}
      {projects && projects.length > 0 && selectedId !== null ? (
        <div className="mb-10">
          <div className="flex items-center justify-between mb-4 gap-4">
            <h2 className="text-lg font-semibold">Project status</h2>
            {projects.length > 1 && (
              <select
                value={selectedId}
                onChange={(e) => setSelectedId(Number(e.target.value))}
                className="input max-w-xs"
                aria-label="Select project"
              >
                {projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select>
            )}
          </div>
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-2">
              <ProjectStatusPanel projectId={selectedId} />
            </div>
            <div className="lg:col-span-1">
              <CopilotPanel projectId={selectedId} />
            </div>
          </div>
        </div>
      ) : (
        <div className="card p-8 text-center mb-10">
          <FolderGit2 className="w-12 h-12 text-slate-300 mx-auto mb-3" />
          <p className="text-slate-500 mb-4">No projects yet. Create your first project to get a deployment plan.</p>
          <Link to="/projects/new" className="btn-primary">Create Project</Link>
        </div>
      )}

      {/* Projects grid (existing access preserved) */}
      {projects && projects.length > 0 && (
        <div className="mb-8">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold">Your Projects</h2>
            <Link to="/projects" className="text-sm text-primary-600 hover:underline">View all</Link>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {projects.slice(0, 6).map((p) => (
              <ProjectCard key={p.id} project={p} />
            ))}
          </div>
        </div>
      )}

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
