import { useState } from 'react';
import TerminalBlock from '@/components/TerminalBlock';
import {
  Shield, FileCheck, AlertTriangle, CheckCircle, XCircle,
  Lock, Globe, Database, Key
} from 'lucide-react';

const CHECKLIST = [
  { category: 'Git & Secrets', items: [
    'Git repository created and .gitignore includes .env, node_modules, build/, target/',
    'No secrets committed to the repository',
    'Application uses environment variables for all configuration',
    'No hardcoded API keys, passwords, or tokens in source code',
  ]},
  { category: 'Build & Test', items: [
    'Frontend production build completes without errors',
    'Backend tests pass (./mvnw test or npm test)',
    'Backend packaging works (./mvnw package or npm run build)',
  ]},
  { category: 'Database', items: [
    'Database migrations are defined and tested locally',
    'Production database is created and accessible',
    'Database credentials are stored securely (environment variables only)',
  ]},
  { category: 'Backend', items: [
    'JWT secret is strong (at least 256 bits) and unique per environment',
    'CORS is configured to allow only your frontend domain',
    'Health endpoint (/api/health) responds correctly',
    'Authentication works end-to-end (register, login, access protected routes)',
    'API does not leak stack traces or internal details in error responses',
  ]},
  { category: 'Frontend', items: [
    'Frontend does not reference localhost for API calls in production',
    'No secret values are bundled into the frontend build',
    'Frontend correctly handles 401 by redirecting to login',
  ]},
  { category: 'AI & External APIs', items: [
    'Gemini/OpenAI API key is stored on backend only (never in VITE_ variables)',
    'Error logs sent to AI are redacted to remove secrets',
  ]},
  { category: 'Deployment', items: [
    'HTTPS is enabled on both frontend and backend',
    'Frontend and backend are on the same protocol (both HTTPS)',
    'SPA redirect rules are configured (Netlify _redirects or equivalent)',
    'Environment variables are set on the hosting platform',
  ]},
];

const GITIGNORE_TEMPLATE = `# Dependencies
node_modules/
.pnp
.pnp.js

# Build outputs
dist/
build/
target/
out/

# Environment files
.env
.env.local
.env.*.local
!.env.example

# Spring Boot
application-prod.properties
application-secrets.properties

# Firebase / Service accounts
service-account.json
credentials.json
*.pem
*.key
*.p12
*.jks

# IDE
.idea/
.vscode/
*.swp
*.swo

# OS
.DS_Store
Thumbs.db

# Logs
*.log
npm-debug.log*

# Testing
coverage/
.nyc_output/`;

export default function SecurityPage() {
  const [activeTab, setActiveTab] = useState<'checklist' | 'gitignore' | 'cors' | 'auth'>('checklist');
  const [checked, setChecked] = useState<Set<string>>(new Set());

  const toggle = (item: string) => {
    setChecked((prev) => {
      const next = new Set(prev);
      if (next.has(item)) next.delete(item); else next.add(item);
      return next;
    });
  };

  const totalItems = CHECKLIST.flatMap((c) => c.items).length;
  const checkedCount = checked.size;

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-bold flex items-center gap-2">
          <Shield className="w-6 h-6 text-primary-600" />Security Center
        </h1>
        <p className="text-slate-500 text-sm mt-1">Tools and checklists to deploy securely</p>
      </div>

      {/* Tabs */}
      <div className="flex gap-2 mb-6 overflow-x-auto pb-1">
        {[
          { key: 'checklist' as const, label: 'Pre-Deploy Checklist', icon: FileCheck },
          { key: 'gitignore' as const, label: '.gitignore Generator', icon: Lock },
          { key: 'cors' as const, label: 'CORS Guide', icon: Globe },
          { key: 'auth' as const, label: 'Auth Security', icon: Key },
        ].map((t) => (
          <button key={t.key} onClick={() => setActiveTab(t.key)}
            className={`flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium whitespace-nowrap transition-colors ${
              activeTab === t.key ? 'bg-primary-600 text-white' : 'bg-slate-100 dark:bg-slate-800 text-slate-600'
            }`}>
            <t.icon className="w-4 h-4" />{t.label}
          </button>
        ))}
      </div>

      {/* Checklist */}
      {activeTab === 'checklist' && (
        <div>
          <div className="card p-4 mb-6 flex items-center justify-between">
            <div>
              <div className="text-sm font-medium">Completion</div>
              <div className="text-2xl font-bold">{Math.round((checkedCount / totalItems) * 100)}%</div>
            </div>
            <div className="text-right text-sm text-slate-500">
              {checkedCount} of {totalItems} items checked
            </div>
          </div>

          <div className="space-y-6">
            {CHECKLIST.map((group) => (
              <div key={group.category}>
                <h3 className="font-semibold text-sm text-slate-700 dark:text-slate-300 mb-3 flex items-center gap-2">
                  <Database className="w-4 h-4" />{group.category}
                </h3>
                <div className="space-y-2">
                  {group.items.map((item) => {
                    const isChecked = checked.has(item);
                    return (
                      <button key={item} onClick={() => toggle(item)}
                        className={`w-full flex items-start gap-3 p-3 rounded-lg text-left transition-all border ${
                          isChecked
                            ? 'bg-green-50 dark:bg-green-900/10 border-green-200 dark:border-green-800'
                            : 'bg-white dark:bg-slate-800 border-slate-200 dark:border-slate-700 hover:border-slate-300'
                        }`}>
                        {isChecked
                          ? <CheckCircle className="w-5 h-5 text-green-600 shrink-0 mt-0.5" />
                          : <XCircle className="w-5 h-5 text-slate-300 shrink-0 mt-0.5" />
                        }
                        <span className={`text-sm ${isChecked ? 'text-green-800 dark:text-green-400 line-through' : 'text-slate-600 dark:text-slate-400'}`}>
                          {item}
                        </span>
                      </button>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* .gitignore */}
      {activeTab === 'gitignore' && (
        <div>
          <p className="text-sm text-slate-500 mb-4">Copy this into your project's root .gitignore file. It covers the most common files that should never be committed.</p>
          <TerminalBlock command={GITIGNORE_TEMPLATE} explanation="Copy this to your .gitignore file" />
          <div className="mt-4 p-4 bg-amber-50 dark:bg-amber-900/10 rounded-lg flex items-start gap-2">
            <AlertTriangle className="w-5 h-5 text-amber-600 shrink-0" />
            <p className="text-sm text-amber-700 dark:text-amber-400">
              If you have already committed secrets, rotating the secret is not enough. The old value remains in Git history.
              Use <code className="font-mono text-xs bg-amber-100 px-1 rounded">git filter-repo</code> or BFG Repo-Cleaner to remove sensitive data from history.
            </p>
          </div>
        </div>
      )}

      {/* CORS */}
      {activeTab === 'cors' && (
        <div className="card p-6 space-y-6">
          <div>
            <h3 className="font-semibold mb-2">What is CORS?</h3>
            <p className="text-sm text-slate-600 dark:text-slate-400">
              Cross-Origin Resource Sharing (CORS) is a browser security feature that prevents websites from making requests to a different domain than the one serving the website.
              When your frontend (on Netlify) tries to call your backend (on Render), the browser checks if the backend allows this.
            </p>
          </div>
          <div>
            <h3 className="font-semibold mb-2">Spring Boot CORS Configuration</h3>
            <p className="text-sm text-slate-600 dark:text-slate-400 mb-3">
              Your backend should only allow requests from your specific frontend domain:
            </p>
            <TerminalBlock command={`@Configuration\npublic class CorsConfig {\n    @Bean\n    public CorsConfigurationSource corsConfigurationSource() {\n        CorsConfiguration config = new CorsConfiguration();\n        config.setAllowedOrigins(List.of(\n            "https://your-frontend.netlify.app",\n            "http://localhost:5173"  // for local dev\n        ));\n        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));\n        config.setAllowedHeaders(List.of("*"));\n        config.setAllowCredentials(true);\n        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();\n        source.registerCorsConfiguration("/**", config);\n        return source;\n    }\n}`} />
          </div>
          <div className="p-4 bg-red-50 dark:bg-red-900/10 rounded-lg">
            <h4 className="font-semibold text-red-800 dark:text-red-400 text-sm flex items-center gap-2 mb-1">
              <AlertTriangle className="w-4 h-4" />Common Mistake
            </h4>
            <p className="text-sm text-red-700 dark:text-red-300">
              Never use <code className="font-mono">setAllowedOrigins(List.of("*"))</code> with <code className="font-mono">allowCredentials(true)</code> in production.
              This combination is rejected by browsers. Always specify exact origins.
            </p>
          </div>
        </div>
      )}

      {/* Auth */}
      {activeTab === 'auth' && (
        <div className="card p-6 space-y-6">
          <div>
            <h3 className="font-semibold mb-2">JWT Security Best Practices</h3>
            <ul className="space-y-2 text-sm text-slate-600 dark:text-slate-400">
              <li className="flex items-start gap-2"><CheckCircle className="w-4 h-4 text-green-600 shrink-0 mt-0.5" />Use a strong, random JWT secret (at least 256 bits)</li>
              <li className="flex items-start gap-2"><CheckCircle className="w-4 h-4 text-green-600 shrink-0 mt-0.5" />Set a reasonable expiration (e.g., 24 hours)</li>
              <li className="flex items-start gap-2"><CheckCircle className="w-4 h-4 text-green-600 shrink-0 mt-0.5" />Store the secret in an environment variable, never in code</li>
              <li className="flex items-start gap-2"><CheckCircle className="w-4 h-4 text-green-600 shrink-0 mt-0.5" />Use HTTPS to prevent token interception</li>
              <li className="flex items-start gap-2"><XCircle className="w-4 h-4 text-red-500 shrink-0 mt-0.5" />Never store JWTs in localStorage for sensitive apps (use httpOnly cookies)</li>
            </ul>
          </div>
          <div>
            <h3 className="font-semibold mb-2">Ownership Checks</h3>
            <p className="text-sm text-slate-600 dark:text-slate-400 mb-3">
              Every protected endpoint must verify that the authenticated user owns the resource they are accessing:
            </p>
            <TerminalBlock command={`// Example: Project ownership check\nProject project = projectRepository.findById(projectId)\n    .orElseThrow(() -> new ResourceNotFoundException("Project not found"));\n\nif (!project.getUserId().equals(currentUserId)) {\n    throw new UnauthorizedAccessException("You do not own this project");\n}`} />
          </div>
        </div>
      )}
    </div>
  );
}
