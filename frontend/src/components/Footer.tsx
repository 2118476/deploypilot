import { Link } from 'react-router-dom';
import { Rocket } from 'lucide-react';

export default function Footer() {
  return (
    <footer className="border-t border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <Rocket className="w-5 h-5 text-primary-600" />
              <span className="font-semibold">DeployPilot</span>
            </div>
            <p className="text-sm text-slate-500 dark:text-slate-400">
              Your interactive deployment assistant. Build it. Secure it. Deploy it.
            </p>
          </div>
          <div>
            <h4 className="font-medium text-sm mb-3">Learn</h4>
            <div className="space-y-2 text-sm">
              <Link to="/guides" className="block text-slate-500 dark:text-slate-400 hover:text-primary-600">Guides</Link>
              <Link to="/git-commands" className="block text-slate-500 dark:text-slate-400 hover:text-primary-600">Git Commands</Link>
              <Link to="/glossary" className="block text-slate-500 dark:text-slate-400 hover:text-primary-600">Glossary</Link>
            </div>
          </div>
          <div>
            <h4 className="font-medium text-sm mb-3">Project</h4>
            <div className="space-y-2 text-sm">
              <Link to="/about-deployment" className="block text-slate-500 dark:text-slate-400 hover:text-primary-600">About This Deployment</Link>
              <Link to="/security" className="block text-slate-500 dark:text-slate-400 hover:text-primary-600">Security Center</Link>
            </div>
          </div>
        </div>
        <div className="mt-8 pt-4 border-t border-slate-200 dark:border-slate-700 text-center text-xs text-slate-400">
          DeployPilot 2025. Built to help developers deploy with confidence.
        </div>
      </div>
    </footer>
  );
}
