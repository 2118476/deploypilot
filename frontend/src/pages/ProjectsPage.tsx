import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { projectApi } from '@/lib/api';
import ProjectCard from '@/components/ProjectCard';
import { Plus, FolderGit2 } from 'lucide-react';

export default function ProjectsPage() {
  const { data: projects, isLoading } = useQuery({ queryKey: ['projects'], queryFn: () => projectApi.list() });

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold">My Projects</h1>
          <p className="text-slate-500 dark:text-slate-400 text-sm mt-0.5">
            {projects?.length ?? 0} project{projects?.length !== 1 ? 's' : ''}
          </p>
        </div>
        <Link to="/projects/new" className="btn-primary">
          <Plus className="w-4 h-4" />New Project
        </Link>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => (
            <div key={i} className="card p-5 animate-pulse">
              <div className="h-4 bg-slate-200 dark:bg-slate-700 rounded w-1/2 mb-3" />
              <div className="h-3 bg-slate-200 dark:bg-slate-700 rounded w-3/4 mb-2" />
              <div className="h-2 bg-slate-200 dark:bg-slate-700 rounded w-full mt-4" />
            </div>
          ))}
        </div>
      ) : projects && projects.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {projects.map((p) => <ProjectCard key={p.id} project={p} />)}
        </div>
      ) : (
        <div className="card p-12 text-center">
          <FolderGit2 className="w-16 h-16 text-slate-300 mx-auto mb-4" />
          <h3 className="text-lg font-semibold mb-2">No projects yet</h3>
          <p className="text-slate-500 mb-6 max-w-md mx-auto">
            Create your first project to get a personalized deployment plan with step-by-step instructions.
          </p>
          <Link to="/projects/new" className="btn-primary">Create Your First Project</Link>
        </div>
      )}
    </div>
  );
}
