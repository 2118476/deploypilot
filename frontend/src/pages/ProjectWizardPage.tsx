import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { projectApi } from '@/lib/api';
import {
  ChevronRight, ChevronLeft, Check, Rocket, Code2, Database,
  Globe, Server, Sparkles, Bell, Lock, Mail, Box, Github,
  Container, Palette, FileCode, Layers, Braces, Triangle
} from 'lucide-react';

const STEPS = [
  'Project Details', 'Project Type', 'Frontend', 'Backend',
  'Database', 'Hosting', 'Services', 'Review'
];

export default function ProjectWizardPage() {
  const [step, setStep] = useState(0);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [projectType, setProjectType] = useState('');
  const [frontend, setFrontend] = useState('');
  const [backend, setBackend] = useState('');
  const [database, setDatabase] = useState('');
  const [frontendHost, setFrontendHost] = useState('');
  const [backendHost, setBackendHost] = useState('');
  const [services, setServices] = useState<string[]>([]);
  const nav = useNavigate();

  const createMut = useMutation({
    mutationFn: async () => {
      const project = await projectApi.create({ name, description });
      await projectApi.generatePlan(project.id, {
        projectType, frontendTech: frontend, backendTech: backend,
        database, frontendHost, backendHost, additionalServices: services,
      });
      return project.id;
    },
    onSuccess: (id) => nav(`/projects/${id}/plan`),
  });

  const toggleService = (s: string) => {
    setServices((prev) => prev.includes(s) ? prev.filter((x) => x !== s) : [...prev, s]);
  };

  const canNext = () => {
    if (step === 0) return name.length >= 2;
    if (step === 1) return projectType !== '';
    return true;
  };

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      {/* Progress */}
      <div className="mb-8">
        <div className="flex items-center justify-between mb-3">
          {STEPS.map((s, i) => (
            <div key={s} className="flex flex-col items-center">
              <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold transition-colors ${
                i < step ? 'bg-green-500 text-white' : i === step ? 'bg-primary-600 text-white' : 'bg-slate-200 dark:bg-slate-700 text-slate-500'
              }`}>
                {i < step ? <Check className="w-4 h-4" /> : i + 1}
              </div>
              <span className="hidden sm:block text-xs mt-1 text-slate-500">{s}</span>
            </div>
          ))}
        </div>
        <div className="w-full bg-slate-200 dark:bg-slate-700 h-1.5 rounded-full">
          <div className="bg-primary-600 h-1.5 rounded-full transition-all" style={{ width: `${(step / (STEPS.length - 1)) * 100}%` }} />
        </div>
      </div>

      <div className="card p-6 md:p-8">
        {/* Step 0: Details */}
        {step === 0 && (
          <div>
            <h2 className="text-xl font-bold mb-1">Project Details</h2>
            <p className="text-slate-500 text-sm mb-6">Let's start with the basics</p>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-1.5">Project Name *</label>
                <input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="My Awesome App" />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1.5">Description</label>
                <textarea className="input min-h-[80px]" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="What does this project do?" />
              </div>
            </div>
          </div>
        )}

        {/* Step 1: Type */}
        {step === 1 && (
          <div>
            <h2 className="text-xl font-bold mb-1">Project Type</h2>
            <p className="text-slate-500 text-sm mb-6">What kind of project are you deploying?</p>
            <div className="grid grid-cols-2 gap-3">
              {[
                { val: 'fullstack', label: 'Full-Stack Website', icon: Layers },
                { val: 'frontend-only', label: 'Frontend Only', icon: Palette },
                { val: 'backend-only', label: 'Backend Only', icon: Server },
                { val: 'android', label: 'Android App', icon: Smartphone },
                { val: 'pwa', label: 'PWA', icon: Globe },
                { val: 'ai-app', label: 'AI Application', icon: Sparkles },
              ].map((t) => (
                <button key={t.val} onClick={() => setProjectType(t.val)}
                  className={`p-4 rounded-xl border-2 text-left transition-all ${
                    projectType === t.val ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20' : 'border-slate-200 dark:border-slate-700 hover:border-slate-300'
                  }`}>
                  <t.icon className={`w-6 h-6 mb-2 ${projectType === t.val ? 'text-primary-600' : 'text-slate-400'}`} />
                  <div className="font-medium text-sm">{t.label}</div>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Step 2: Frontend */}
        {step === 2 && (
          <div>
            <h2 className="text-xl font-bold mb-1">Frontend Technology</h2>
            <p className="text-slate-500 text-sm mb-6">What framework powers your UI?</p>
            <div className="grid grid-cols-2 gap-3">
              {[
                { val: 'react-vite', label: 'React + Vite', icon: Code2 },
                { val: 'nextjs', label: 'Next.js', icon: FileCode },
                { val: 'vue', label: 'Vue', icon: Triangle },
                { val: 'angular', label: 'Angular', icon: Braces },
                { val: 'plain-html', label: 'Plain HTML/CSS/JS', icon: Globe },
                { val: 'none', label: 'No Frontend', icon: Box },
              ].map((t) => (
                <button key={t.val} onClick={() => setFrontend(t.val)}
                  className={`p-4 rounded-xl border-2 text-left transition-all ${
                    frontend === t.val ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20' : 'border-slate-200 dark:border-slate-700 hover:border-slate-300'
                  }`}>
                  <t.icon className={`w-6 h-6 mb-2 ${frontend === t.val ? 'text-primary-600' : 'text-slate-400'}`} />
                  <div className="font-medium text-sm">{t.label}</div>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Step 3: Backend */}
        {step === 3 && (
          <div>
            <h2 className="text-xl font-bold mb-1">Backend Technology</h2>
            <p className="text-slate-500 text-sm mb-6">What powers your server?</p>
            <div className="grid grid-cols-2 gap-3">
              {[
                { val: 'spring-boot', label: 'Spring Boot', icon: Server },
                { val: 'nodejs-express', label: 'Node.js + Express', icon: Code2 },
                { val: 'fastapi', label: 'Python + FastAPI', icon: Rocket },
                { val: 'django', label: 'Django', icon: Lock },
                { val: 'supabase-only', label: 'Supabase Only', icon: Database },
                { val: 'none', label: 'No Backend', icon: Box },
              ].map((t) => (
                <button key={t.val} onClick={() => setBackend(t.val)}
                  className={`p-4 rounded-xl border-2 text-left transition-all ${
                    backend === t.val ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20' : 'border-slate-200 dark:border-slate-700 hover:border-slate-300'
                  }`}>
                  <t.icon className={`w-6 h-6 mb-2 ${backend === t.val ? 'text-primary-600' : 'text-slate-400'}`} />
                  <div className="font-medium text-sm">{t.label}</div>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Step 4: Database */}
        {step === 4 && (
          <div>
            <h2 className="text-xl font-bold mb-1">Database</h2>
            <p className="text-slate-500 text-sm mb-6">Where will your data live?</p>
            <div className="grid grid-cols-2 gap-3">
              {[
                { val: 'supabase-postgresql', label: 'Supabase PostgreSQL', icon: Database },
                { val: 'render-postgresql', label: 'Render PostgreSQL', icon: Database },
                { val: 'firebase-firestore', label: 'Firestore', icon: Flame },
                { val: 'firebase-realtime', label: 'Realtime DB', icon: Zap },
                { val: 'mongodb', label: 'MongoDB', icon: Layers },
                { val: 'none', label: 'No Database', icon: Box },
              ].map((t) => (
                <button key={t.val} onClick={() => setDatabase(t.val)}
                  className={`p-4 rounded-xl border-2 text-left transition-all ${
                    database === t.val ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20' : 'border-slate-200 dark:border-slate-700 hover:border-slate-300'
                  }`}>
                  <t.icon className={`w-6 h-6 mb-2 ${database === t.val ? 'text-primary-600' : 'text-slate-400'}`} />
                  <div className="font-medium text-sm">{t.label}</div>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Step 5: Hosting */}
        {step === 5 && (
          <div className="space-y-6">
            {frontend !== 'none' && (
              <div>
                <h3 className="font-semibold mb-3">Frontend Hosting</h3>
                <div className="grid grid-cols-2 gap-3">
                  {['netlify', 'vercel', 'firebase-hosting', 'other'].map((h) => (
                    <button key={h} onClick={() => setFrontendHost(h)}
                      className={`p-3 rounded-xl border-2 text-sm font-medium capitalize transition-all ${
                        frontendHost === h ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20' : 'border-slate-200 dark:border-slate-700'
                      }`}>{h.replace('-', ' ')}</button>
                  ))}
                </div>
              </div>
            )}
            {backend !== 'none' && (
              <div>
                <h3 className="font-semibold mb-3">Backend Hosting</h3>
                <div className="grid grid-cols-2 gap-3">
                  {['render', 'railway', 'fly.io', 'firebase-functions', 'supabase-edge', 'other'].map((h) => (
                    <button key={h} onClick={() => setBackendHost(h)}
                      className={`p-3 rounded-xl border-2 text-sm font-medium capitalize transition-all ${
                        backendHost === h ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20' : 'border-slate-200 dark:border-slate-700'
                      }`}>{h.replace('-', ' ')}</button>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {/* Step 6: Services */}
        {step === 6 && (
          <div>
            <h2 className="text-xl font-bold mb-1">Additional Services</h2>
            <p className="text-slate-500 text-sm mb-6">Select all that apply</p>
            <div className="grid grid-cols-2 gap-3">
              {[
                { val: 'gemini', label: 'Gemini API', icon: Sparkles },
                { val: 'openai', label: 'OpenAI API', icon: Bot },
                { val: 'firebase-cloud-messaging', label: 'Push Notifications', icon: Bell },
                { val: 'supabase-auth', label: 'Supabase Auth', icon: Lock },
                { val: 'firebase-auth', label: 'Firebase Auth', icon: Lock },
                { val: 'file-storage', label: 'File Storage', icon: Box },
                { val: 'email-service', label: 'Email Service', icon: Mail },
                { val: 'custom-domain', label: 'Custom Domain', icon: Globe },
                { val: 'github-actions', label: 'GitHub Actions CI/CD', icon: Github },
                { val: 'docker', label: 'Docker', icon: Container },
              ].map((s) => (
                <button key={s.val} onClick={() => toggleService(s.val)}
                  className={`p-4 rounded-xl border-2 text-left transition-all ${
                    services.includes(s.val) ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20' : 'border-slate-200 dark:border-slate-700 hover:border-slate-300'
                  }`}>
                  <s.icon className={`w-5 h-5 mb-1.5 ${services.includes(s.val) ? 'text-primary-600' : 'text-slate-400'}`} />
                  <div className="font-medium text-sm">{s.label}</div>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Step 7: Review */}
        {step === 7 && (
          <div>
            <h2 className="text-xl font-bold mb-1">Review & Generate</h2>
            <p className="text-slate-500 text-sm mb-6">Here's what we know about your project</p>
            <div className="space-y-3 bg-slate-50 dark:bg-slate-800/50 rounded-xl p-5">
              {[{ label: 'Name', value: name },
                { label: 'Type', value: projectType },
                { label: 'Frontend', value: frontend || 'None' },
                { label: 'Backend', value: backend || 'None' },
                { label: 'Database', value: database || 'None' },
                { label: 'Frontend Host', value: frontendHost || 'None' },
                { label: 'Backend Host', value: backendHost || 'None' },
                { label: 'Extra Services', value: services.length > 0 ? services.join(', ') : 'None' },
              ].map((item) => (
                <div key={item.label} className="flex justify-between text-sm">
                  <span className="text-slate-500">{item.label}</span>
                  <span className="font-medium capitalize">{item.value}</span>
                </div>
              ))}
            </div>
            {createMut.isError && (
              <p className="text-red-600 text-sm mt-3">{createMut.error instanceof Error ? createMut.error.message : 'Failed to create project'}</p>
            )}
          </div>
        )}

        {/* Navigation */}
        <div className="flex items-center justify-between mt-8 pt-6 border-t border-slate-200 dark:border-slate-700">
          <button onClick={() => setStep((s) => s - 1)} disabled={step === 0}
            className="btn-secondary disabled:opacity-50">
            <ChevronLeft className="w-4 h-4" />Back
          </button>
          {step < STEPS.length - 1 ? (
            <button onClick={() => setStep((s) => s + 1)} disabled={!canNext()}
              className="btn-primary disabled:opacity-50">
              Next<ChevronRight className="w-4 h-4" />
            </button>
          ) : (
            <button onClick={() => createMut.mutate()} disabled={createMut.isPending}
              className="btn-primary disabled:opacity-50">
              {createMut.isPending ? 'Generating...' : 'Generate Plan'}<Sparkles className="w-4 h-4" />
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

// Icon imports needed for wizard
function Smartphone(props: { className?: string }) { return <Box {...props} />; }
function Flame(props: { className?: string }) { return <Sparkles {...props} />; }
function Zap(props: { className?: string }) { return <Sparkles {...props} />; }
function Bot(props: { className?: string }) { return <Sparkles {...props} />; }
