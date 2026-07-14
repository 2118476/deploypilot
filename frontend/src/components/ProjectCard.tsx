import { Link } from 'react-router-dom';
import type { ProjectSummary } from '@/types';
import StatusBadge from './StatusBadge';
import ProgressBar from './ProgressBar';
import { ChevronRight, FolderGit2, ScanSearch } from 'lucide-react';

interface Props {
  project: ProjectSummary;
}

export default function ProjectCard({ project }: Props) {
  return (
    <div className="card-hover p-5 group">
      <Link to={`/projects/${project.id}/plan`} className="block">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-primary-50 dark:bg-primary-900/20 rounded-lg">
              <FolderGit2 className="w-5 h-5 text-primary-600" />
            </div>
            <div>
              <h3 className="font-semibold text-slate-900 dark:text-slate-100 group-hover:text-primary-600 transition-colors">
                {project.name}
              </h3>
              <p className="text-xs text-slate-500 mt-0.5 line-clamp-1">{project.description || project.techSummary || 'No description'}</p>
            </div>
          </div>
          <StatusBadge status={project.status} />
        </div>

        {project.totalSteps > 0 && (
          <div className="mt-4">
            <ProgressBar current={project.completedSteps} total={project.totalSteps} />
          </div>
        )}

        {project.nextAction && (
          <div className="mt-3 flex items-center gap-1.5 text-xs text-primary-600">
            <ChevronRight className="w-3.5 h-3.5" />
            <span className="line-clamp-1">{project.nextAction}</span>
          </div>
        )}
      </Link>

      <div className="mt-4 pt-3 border-t border-slate-100 dark:border-slate-700">
        <Link to={`/projects/${project.id}/analysis`}
          className="inline-flex items-center gap-1.5 text-xs font-medium text-slate-500 hover:text-primary-600 transition-colors">
          <ScanSearch className="w-3.5 h-3.5" />
          Repository analysis
        </Link>
      </div>
    </div>
  );
}
