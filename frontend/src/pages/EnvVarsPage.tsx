import { useQuery } from '@tanstack/react-query';
import { envVarApi } from '@/lib/api';
import StatusBadge from '@/components/StatusBadge';
import { Shield, AlertTriangle, Eye, EyeOff, Info } from 'lucide-react';

export default function EnvVarsPage() {
  const { data: definitions } = useQuery({ queryKey: ['env-var-definitions'], queryFn: () => envVarApi.definitions() });

  const publicVars = definitions?.filter((d) => d.category === 'PUBLIC_FRONTEND') || [];
  const privateVars = definitions?.filter((d) => d.category === 'PRIVATE_BACKEND') || [];

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-bold flex items-center gap-2">
          <Shield className="w-6 h-6 text-primary-600" />Environment Variable Center
        </h1>
        <p className="text-slate-500 text-sm mt-1">Know what goes where and what stays secret</p>
      </div>

      {/* Security notice */}
      <div className="bg-amber-50 dark:bg-amber-900/10 border border-amber-200 dark:border-amber-800 rounded-xl p-4 mb-6 flex items-start gap-3">
        <AlertTriangle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
        <div>
          <h3 className="font-semibold text-amber-800 dark:text-amber-400 text-sm">Critical Security Rule</h3>
          <p className="text-sm text-amber-700 dark:text-amber-300 mt-1">
            Frontend environment variables in Vite (starting with VITE_) are bundled into your JavaScript and visible to anyone who inspects your site.
            Never prefix secret values with VITE_. Backend secrets should only be accessed by your server code.
          </p>
        </div>
      </div>

      {/* Gemini architecture example */}
      <div className="card p-5 mb-6">
        <h3 className="font-semibold mb-3 flex items-center gap-2"><Info className="w-4 h-4 text-primary-600" />Secure Gemini Architecture</h3>
        <div className="flex flex-wrap items-center justify-center gap-2 text-sm font-mono">
          <div className="px-3 py-2 bg-blue-50 dark:bg-blue-900/20 rounded-lg border border-blue-200 dark:border-blue-800">React Frontend</div>
          <span className="text-slate-400">&rarr; VITE_API_BASE_URL &rarr;</span>
          <div className="px-3 py-2 bg-green-50 dark:bg-green-900/20 rounded-lg border border-green-200 dark:border-green-800">Spring Boot Backend</div>
          <span className="text-slate-400">&rarr; GEMINI_API_KEY &rarr;</span>
          <div className="px-3 py-2 bg-purple-50 dark:bg-purple-900/20 rounded-lg border border-purple-200 dark:border-purple-800">Gemini API</div>
        </div>
        <div className="mt-3 p-3 bg-red-50 dark:bg-red-900/10 rounded-lg text-sm text-red-700 dark:text-red-400 flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 shrink-0" />
          Never use VITE_GEMINI_API_KEY. The key must stay on the backend only.
        </div>
      </div>

      {/* Public Frontend Variables */}
      <div className="mb-8">
        <h2 className="text-lg font-semibold mb-4 flex items-center gap-2">
          <Eye className="w-5 h-5 text-blue-600" />Public Frontend Variables
        </h2>
        <p className="text-sm text-slate-500 mb-4">These values are visible in client-side code. They configure the frontend but contain no secrets.</p>
        <div className="space-y-3">
          {publicVars.map((v) => (
            <div key={v.id} className="card p-4 flex flex-col md:flex-row md:items-center justify-between gap-3">
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <code className="text-sm font-mono font-semibold text-primary-700 dark:text-primary-400">{v.name}</code>
                  <StatusBadge status="PUBLIC" />
                  {v.required && <StatusBadge status="PENDING" label="Required" />}
                </div>
                <p className="text-sm text-slate-500">{v.description}</p>
              </div>
              <div className="text-sm text-slate-500 space-y-1 md:text-right">
                <div><span className="text-xs text-slate-400">Local:</span> {v.localFileLocation}</div>
                <div><span className="text-xs text-slate-400">Production:</span> {v.productionLocation}</div>
                {v.exampleValue && <div><span className="text-xs text-slate-400">Example:</span> <code className="text-xs font-mono bg-slate-100 dark:bg-slate-800 px-1 rounded">{v.exampleValue}</code></div>}
              </div>
            </div>
          ))}
          {publicVars.length === 0 && <p className="text-slate-400 text-sm">No public variables defined.</p>}
        </div>
      </div>

      {/* Private Backend Secrets */}
      <div>
        <h2 className="text-lg font-semibold mb-4 flex items-center gap-2">
          <EyeOff className="w-5 h-5 text-red-600" />Private Backend Secrets
        </h2>
        <p className="text-sm text-slate-500 mb-4">These must NEVER be exposed to the frontend. Use only in backend code or secure CI/CD environments.</p>
        <div className="space-y-3">
          {privateVars.map((v) => (
            <div key={v.id} className="card p-4 border-red-100 dark:border-red-900/20 flex flex-col md:flex-row md:items-center justify-between gap-3">
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <code className="text-sm font-mono font-semibold text-red-700 dark:text-red-400">{v.name}</code>
                  <StatusBadge status="SECRET" />
                  {v.required && <StatusBadge status="PENDING" label="Required" />}
                </div>
                <p className="text-sm text-slate-500">{v.description}</p>
              </div>
              <div className="text-sm text-slate-500 space-y-1 md:text-right">
                <div><span className="text-xs text-slate-400">Local:</span> {v.localFileLocation}</div>
                <div><span className="text-xs text-slate-400">Production:</span> {v.productionLocation}</div>
                {v.exampleValue && <div><span className="text-xs text-slate-400">Example:</span> <code className="text-xs font-mono bg-slate-100 dark:bg-slate-800 px-1 rounded">{v.exampleValue}</code></div>}
              </div>
            </div>
          ))}
          {privateVars.length === 0 && <p className="text-slate-400 text-sm">No private variables defined.</p>}
        </div>
      </div>
    </div>
  );
}
