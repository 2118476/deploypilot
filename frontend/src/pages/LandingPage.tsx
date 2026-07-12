import { Link } from 'react-router-dom';
import {
  Rocket, Shield, Terminal, BookOpen, Wrench, Globe, ArrowRight,
  Zap, Lock, BookMarked, Bot
} from 'lucide-react';

export default function LandingPage() {
  return (
    <div>
      {/* Hero */}
      <section className="relative overflow-hidden bg-slate-900 text-white">
        <div className="absolute inset-0 opacity-10">
          <div className="absolute top-20 left-10 w-72 h-72 bg-primary-500 rounded-full blur-3xl" />
          <div className="absolute bottom-20 right-10 w-96 h-96 bg-purple-500 rounded-full blur-3xl" />
        </div>
        <div className="relative max-w-7xl mx-auto px-4 sm:px-6 py-24 md:py-32">
          <div className="max-w-3xl">
            <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-primary-500/10 border border-primary-500/20 text-primary-400 text-sm mb-6">
              <Zap className="w-4 h-4" />
              Your interactive deployment assistant
            </div>
            <h1 className="text-4xl md:text-6xl font-extrabold tracking-tight leading-tight">
              Build it.<br />
              <span className="text-primary-400">Secure it.</span><br />
              Deploy it.
            </h1>
            <p className="mt-6 text-lg md:text-xl text-slate-400 max-w-xl leading-relaxed">
              Know exactly what to do next. Personalized deployment plans, step-by-step guides,
              Git command reference, and AI-powered troubleshooting.
            </p>
            <div className="mt-8 flex flex-wrap gap-4">
              <Link to="/register" className="btn-primary px-6 py-3 text-lg">
                Get Started <ArrowRight className="w-5 h-5" />
              </Link>
              <Link to="/login" className="btn border border-slate-600 hover:bg-slate-800 px-6 py-3 text-lg">
                Sign In
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* Features */}
      <section className="py-20 bg-slate-50 dark:bg-slate-950">
        <div className="max-w-7xl mx-auto px-4 sm:px-6">
          <div className="text-center mb-16">
            <h2 className="text-3xl font-bold">Everything you need to deploy</h2>
            <p className="mt-3 text-slate-500 dark:text-slate-400">A complete toolkit for confident deployments</p>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {[
              { icon: Rocket, title: 'Personalized Plans', desc: 'Answer a few questions about your stack and get a step-by-step deployment plan tailored to your project.' },
              { icon: BookOpen, title: 'Step-by-Step Guides', desc: 'Comprehensive guides for Netlify, Render, Supabase, Firebase, and more with exact commands and screenshots.' },
              { icon: Terminal, title: 'Git Command Reference', desc: 'Searchable Git commands with explanations, warnings for destructive operations, and beginner/advanced modes.' },
              { icon: Lock, title: 'Environment Variable Manager', desc: 'Track which variables are public vs secret, where they belong, and whether they are configured.' },
              { icon: Bot, title: 'AI Error Solver', desc: 'Paste build logs or error messages and get intelligent troubleshooting help powered by Gemini.' },
              { icon: Shield, title: 'Security Center', desc: 'Pre-deployment security checklists, .gitignore generator, and guides to keep your secrets safe.' },
            ].map((f) => (
              <div key={f.title} className="card-hover p-6">
                <div className="p-3 bg-primary-50 dark:bg-primary-900/20 rounded-xl w-fit mb-4">
                  <f.icon className="w-6 h-6 text-primary-600" />
                </div>
                <h3 className="font-semibold text-lg mb-2">{f.title}</h3>
                <p className="text-sm text-slate-500 dark:text-slate-400 leading-relaxed">{f.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* How it works */}
      <section className="py-20 bg-white dark:bg-slate-900">
        <div className="max-w-7xl mx-auto px-4 sm:px-6">
          <div className="text-center mb-16">
            <h2 className="text-3xl font-bold">How it works</h2>
            <p className="mt-3 text-slate-500 dark:text-slate-400">From project setup to production in three steps</p>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            {[
              { step: '01', title: 'Create your project', desc: 'Tell us about your tech stack. React? Spring Boot? Supabase? We support all major platforms.' },
              { step: '02', title: 'Get your plan', desc: 'Receive a personalized, ordered checklist of exactly what to do, where to click, and which commands to run.' },
              { step: '03', title: 'Deploy with confidence', desc: 'Follow the steps, mark them complete, and track your progress. Get help from the AI solver when stuck.' },
            ].map((s) => (
              <div key={s.step} className="relative text-center">
                <div className="text-5xl font-extrabold text-slate-200 dark:text-slate-700 mb-4">{s.step}</div>
                <h3 className="font-semibold text-lg mb-2">{s.title}</h3>
                <p className="text-sm text-slate-500 dark:text-slate-400">{s.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="py-20 bg-primary-600 text-white">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 text-center">
          <h2 className="text-3xl font-bold mb-4">Ready to deploy with confidence?</h2>
          <p className="text-primary-100 mb-8 max-w-xl mx-auto">Create a free account and get your first deployment plan in under two minutes.</p>
          <div className="flex flex-wrap justify-center gap-4">
            <Link to="/register" className="btn bg-white text-primary-600 hover:bg-primary-50 px-8 py-3 text-lg font-semibold">
              Get Started Free
            </Link>
            <Link to="/glossary" className="btn border border-primary-400 hover:bg-primary-700 px-8 py-3 text-lg">
              Explore Glossary
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}
