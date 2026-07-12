import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { useTheme } from '@/hooks/useTheme';
import {
  Rocket, Sun, Moon, Monitor, Menu, X, LogOut, User,
  LayoutDashboard, FolderGit2, Terminal, BookOpen, Wrench, ShieldCheck
} from 'lucide-react';

export default function Navbar() {
  const { user, logout } = useAuth();
  const { theme, resolved, setTheme } = useTheme();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [themeOpen, setThemeOpen] = useState(false);
  const loc = useLocation();

  const navLinks = user ? [
    { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { to: '/projects', label: 'Projects', icon: FolderGit2 },
    { to: '/git-commands', label: 'Git', icon: Terminal },
    { to: '/guides', label: 'Guides', icon: BookOpen },
    { to: '/troubleshoot', label: 'Fix', icon: Wrench },
    { to: '/security', label: 'Security', icon: ShieldCheck },
  ] : [];

  const isActive = (to: string) => loc.pathname === to || loc.pathname.startsWith(to + '/');

  return (
    <nav className="sticky top-0 z-50 bg-white/80 dark:bg-slate-900/80 backdrop-blur border-b border-slate-200 dark:border-slate-700">
      <div className="max-w-7xl mx-auto px-4 sm:px-6">
        <div className="flex items-center justify-between h-14">
          {/* Logo */}
          <Link to={user ? '/dashboard' : '/'} className="flex items-center gap-2 shrink-0">
            <Rocket className="w-6 h-6 text-primary-600" />
            <span className="font-bold text-lg tracking-tight">DeployPilot</span>
          </Link>

          {/* Desktop nav */}
          <div className="hidden md:flex items-center gap-1">
            {navLinks.map((l) => (
              <Link key={l.to} to={l.to}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                  isActive(l.to)
                    ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-400'
                    : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800'
                }`}>
                <l.icon className="w-4 h-4" />
                {l.label}
              </Link>
            ))}
          </div>

          {/* Right side */}
          <div className="flex items-center gap-2">
            {/* Theme toggle */}
            <div className="relative">
              <button onClick={() => setThemeOpen(!themeOpen)}
                className="p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors">
                {resolved === 'dark' ? <Moon className="w-4 h-4" /> : <Sun className="w-4 h-4" />}
              </button>
              {themeOpen && (
                <div className="absolute right-0 mt-2 w-36 bg-white dark:bg-slate-800 rounded-lg shadow-lg border border-slate-200 dark:border-slate-700 py-1 z-50">
                  {(['light', 'dark', 'system'] as const).map((t) => (
                    <button key={t} onClick={() => { setTheme(t); setThemeOpen(false); }}
                      className={`w-full flex items-center gap-2 px-3 py-2 text-sm capitalize ${
                        theme === t ? 'text-primary-600 bg-primary-50 dark:bg-primary-900/20' : 'hover:bg-slate-50 dark:hover:bg-slate-700'
                      }`}>
                      {t === 'light' ? <Sun className="w-4 h-4" /> : t === 'dark' ? <Moon className="w-4 h-4" /> : <Monitor className="w-4 h-4" />}
                      {t}
                    </button>
                  ))}
                </div>
              )}
            </div>

            {user ? (
              <div className="hidden sm:flex items-center gap-2">
                <span className="text-sm text-slate-600 dark:text-slate-400">{user.username}</span>
                <button onClick={logout}
                  className="p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-500 transition-colors"
                  title="Logout">
                  <LogOut className="w-4 h-4" />
                </button>
              </div>
            ) : (
              <div className="hidden sm:flex items-center gap-2">
                <Link to="/login" className="text-sm font-medium text-slate-600 dark:text-slate-400 hover:text-slate-900 px-3 py-1.5">Sign In</Link>
                <Link to="/register" className="btn-primary text-sm py-1.5 px-3">Get Started</Link>
              </div>
            )}

            {/* Mobile menu */}
            <button onClick={() => setMobileOpen(!mobileOpen)} className="md:hidden p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800">
              {mobileOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile nav */}
      {mobileOpen && (
        <div className="md:hidden border-t border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-4 py-3 space-y-1">
          {navLinks.map((l) => (
            <Link key={l.to} to={l.to} onClick={() => setMobileOpen(false)}
              className={`flex items-center gap-2 px-3 py-2 rounded-lg text-sm ${
                isActive(l.to) ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-700' : 'text-slate-600 dark:text-slate-400'
              }`}>
              <l.icon className="w-4 h-4" />{l.label}
            </Link>
          ))}
          {user && (
            <button onClick={() => { logout(); setMobileOpen(false); }}
              className="flex items-center gap-2 px-3 py-2 text-sm text-red-600 w-full">
              <LogOut className="w-4 h-4" />Logout
            </button>
          )}
        </div>
      )}
    </nav>
  );
}
