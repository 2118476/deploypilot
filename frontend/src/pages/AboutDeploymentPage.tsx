import TerminalBlock from '@/components/TerminalBlock';
import { Rocket, Github, Globe, Server, Database, Bot, ArrowRight } from 'lucide-react';

export default function AboutDeploymentPage() {
  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold flex items-center gap-2">
          <Rocket className="w-6 h-6 text-primary-600" />About This Deployment
        </h1>
        <p className="text-slate-500 text-sm mt-1">How DeployPilot itself is hosted</p>
      </div>

      {/* Architecture diagram */}
      <div className="card p-6 mb-8">
        <h2 className="font-semibold mb-4">Architecture</h2>
        <div className="flex flex-col items-center gap-4">
          <div className="w-full max-w-sm p-4 bg-blue-50 dark:bg-blue-900/20 rounded-xl border border-blue-200 dark:border-blue-800 text-center">
            <Github className="w-6 h-6 text-blue-600 mx-auto mb-1" />
            <div className="font-medium text-sm">GitHub</div>
            <div className="text-xs text-slate-500">Source code repository</div>
          </div>
          <ArrowRight className="w-5 h-5 text-slate-400 rotate-90" />
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 w-full">
            <div className="p-4 bg-green-50 dark:bg-green-900/20 rounded-xl border border-green-200 dark:border-green-800 text-center">
              <Globe className="w-6 h-6 text-green-600 mx-auto mb-1" />
              <div className="font-medium text-sm">Netlify</div>
              <div className="text-xs text-slate-500">React Frontend</div>
              <div className="mt-2 text-xs font-mono bg-white dark:bg-slate-800 rounded p-1.5 text-left">
                VITE_API_BASE_URL<br />VITE_APP_NAME
              </div>
            </div>
            <div className="p-4 bg-purple-50 dark:bg-purple-900/20 rounded-xl border border-purple-200 dark:border-purple-800 text-center">
              <Server className="w-6 h-6 text-purple-600 mx-auto mb-1" />
              <div className="font-medium text-sm">Render</div>
              <div className="text-xs text-slate-500">Spring Boot Backend</div>
              <div className="mt-2 text-xs font-mono bg-white dark:bg-slate-800 rounded p-1.5 text-left">
                DATABASE_URL<br />JWT_SECRET<br />GEMINI_API_KEY<br />FRONTEND_URL
              </div>
            </div>
            <div className="p-4 bg-amber-50 dark:bg-amber-900/20 rounded-xl border border-amber-200 dark:border-amber-800 text-center">
              <Database className="w-6 h-6 text-amber-600 mx-auto mb-1" />
              <div className="font-medium text-sm">Supabase</div>
              <div className="text-xs text-slate-500">PostgreSQL Database</div>
              <div className="mt-2 text-xs font-mono bg-white dark:bg-slate-800 rounded p-1.5 text-left">
                connection string<br />with SSL
              </div>
            </div>
          </div>
          <div className="w-full max-w-sm p-3 bg-slate-50 dark:bg-slate-800 rounded-lg border border-slate-200 dark:border-slate-700 text-center">
            <Bot className="w-5 h-5 text-primary-600 mx-auto mb-1" />
            <div className="text-xs text-slate-500">AI troubleshooting via Gemini (backend-only)</div>
          </div>
        </div>
      </div>

      {/* Redeployment flow */}
      <div className="card p-6 mb-8">
        <h2 className="font-semibold mb-4">How Automatic Redeployment Works</h2>
        <div className="flex flex-wrap items-center justify-center gap-2 text-sm">
          {['Local change', 'git add', 'git commit', 'git push', 'GitHub receives', 'Netlify/Render detects', 'Build & Deploy'].map((step, i) => (
            <div key={step} className="flex items-center gap-2">
              <div className="px-3 py-1.5 bg-slate-100 dark:bg-slate-800 rounded-lg font-medium text-xs">{step}</div>
              {i < 6 && <ArrowRight className="w-3 h-3 text-slate-400" />}
            </div>
          ))}
        </div>
      </div>

      {/* Future update commands */}
      <div className="card p-6">
        <h2 className="font-semibold mb-4">Your Normal Update Commands</h2>
        <TerminalBlock command={`# Check what changed\ngit status\n\n# Get latest changes from remote\ngit pull --rebase\n\n# Stage your changes\ngit add .\n\n# Commit with a descriptive message\ngit commit -m "Describe what you changed"\n\n# Push to GitHub (triggers auto-deploy)\ngit push`} explanation="These are the commands you will use daily to update DeployPilot" />
      </div>
    </div>
  );
}
