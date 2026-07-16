import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { AxiosError } from 'axios';
import { connectionApi } from '@/lib/api';
import type { ApiResponse, ProviderConnection, ProviderName } from '@/types';
import {
  Github, Globe, Server, Plug, PlugZap, CheckCircle2, ShieldCheck, Loader2, Trash2, ArrowLeft, ExternalLink,
} from 'lucide-react';

const PROVIDERS: { name: ProviderName; label: string; icon: React.ComponentType<{ className?: string }>;
  tokenLabel: string; help: string; url: string; permissions: string }[] = [
  {
    name: 'GITHUB', label: 'GitHub', icon: Github,
    tokenLabel: 'Fine-grained personal access token',
    help: 'GitHub → Settings → Developer settings → Fine-grained tokens. Select only the repositories you want DeployPilot to use.',
    url: 'https://github.com/settings/tokens?type=beta',
    permissions: 'Contents: Read and write · Pull requests: Read and write · Metadata: Read',
  },
  {
    name: 'NETLIFY', label: 'Netlify', icon: Globe,
    tokenLabel: 'Personal access token',
    help: 'Netlify → User settings → Applications → Personal access tokens → New access token.',
    url: 'https://app.netlify.com/user/applications#personal-access-tokens',
    permissions: 'Manage your sites, builds and environment variables',
  },
  {
    name: 'RENDER', label: 'Render', icon: Server,
    tokenLabel: 'API key',
    help: 'Render → Account Settings → API Keys → Create API Key.',
    url: 'https://dashboard.render.com/u/settings#api-keys',
    permissions: 'Manage your web services, environment variables and deploys',
  },
];

function errorMessage(err: unknown): string {
  if (err instanceof AxiosError) {
    const data = err.response?.data as ApiResponse<unknown> | undefined;
    if (data?.message) return data.message;
  }
  if (err instanceof Error && err.message) return err.message;
  return 'Something went wrong. Please try again.';
}

export default function ConnectionsPage() {
  const { data: connections, isLoading } = useQuery({ queryKey: ['connections'], queryFn: connectionApi.list });

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      <Link to="/dashboard" className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-primary-600 mb-4">
        <ArrowLeft className="w-4 h-4" /> Back to dashboard
      </Link>
      <h1 className="text-2xl font-bold text-slate-900 dark:text-slate-100 mb-2 flex items-center gap-2">
        <Plug className="w-6 h-6 text-primary-600" /> Provider connections
      </h1>

      <div className="card p-4 mb-6 flex items-start gap-3 border-primary-200 dark:border-primary-800 bg-primary-50/50 dark:bg-primary-900/10">
        <ShieldCheck className="w-5 h-5 text-primary-600 shrink-0 mt-0.5" />
        <p className="text-sm text-slate-600 dark:text-slate-300">
          Connect your own GitHub, Netlify and Render accounts. Tokens are encrypted on the server, never shown again and
          never sent to your browser. You choose exactly which repositories and services DeployPilot can use, and you can
          disconnect at any time.
        </p>
      </div>

      {isLoading ? (
        <div className="card p-8 text-center"><Loader2 className="w-6 h-6 animate-spin mx-auto text-primary-600" /></div>
      ) : (
        <div className="space-y-4">
          {PROVIDERS.map((p) => (
            <ConnectionCard key={p.name} meta={p}
              connection={connections?.find((c) => c.provider === p.name)} />
          ))}
        </div>
      )}
    </div>
  );
}

function ConnectionCard({ meta, connection }: {
  meta: typeof PROVIDERS[number];
  connection?: ProviderConnection;
}) {
  const qc = useQueryClient();
  const [token, setToken] = useState('');
  const connected = connection?.connected;
  const Icon = meta.icon;

  const connectMut = useMutation({
    mutationFn: () => connectionApi.connect(meta.name, token.trim()),
    onSuccess: () => { setToken(''); qc.invalidateQueries({ queryKey: ['connections'] }); },
  });
  const disconnectMut = useMutation({
    mutationFn: () => connectionApi.disconnect(meta.name),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['connections'] }),
  });

  return (
    <div className="card p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-3 min-w-0">
          <Icon className="w-6 h-6 text-slate-700 dark:text-slate-200 shrink-0 mt-0.5" />
          <div className="min-w-0">
            <h2 className="text-base font-semibold text-slate-900 dark:text-slate-100">{meta.label}</h2>
            {connected ? (
              <p className="text-sm text-emerald-600 dark:text-emerald-400 flex items-center gap-1.5">
                <CheckCircle2 className="w-4 h-4" /> Connected{connection?.accountLabel ? ` as ${connection.accountLabel}` : ''}
              </p>
            ) : (
              <p className="text-sm text-slate-500">Not connected</p>
            )}
            {connected && connection?.scopes && (
              <p className="text-xs text-slate-500 mt-0.5">Granted: {connection.scopes}</p>
            )}
            {connected && connection?.connectedAt && (
              <p className="text-xs text-slate-400 mt-0.5">Since {new Date(connection.connectedAt).toLocaleDateString()}</p>
            )}
          </div>
        </div>
        {connected && (
          <button className="btn-secondary text-sm inline-flex items-center gap-1.5" disabled={disconnectMut.isPending}
            onClick={() => disconnectMut.mutate()}>
            {disconnectMut.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Trash2 className="w-4 h-4" />} Disconnect
          </button>
        )}
      </div>

      {!connected && (
        <form className="mt-3 space-y-2" onSubmit={(e) => { e.preventDefault(); if (token.trim()) connectMut.mutate(); }}>
          <details className="text-xs text-slate-500">
            <summary className="cursor-pointer font-medium">How to create a {meta.tokenLabel.toLowerCase()}</summary>
            <p className="mt-1">{meta.help}</p>
            <p className="mt-1"><span className="font-medium">Minimum permissions:</span> {meta.permissions}</p>
            <a href={meta.url} target="_blank" rel="noreferrer" className="mt-1 inline-flex items-center gap-1 text-primary-600">
              Open {meta.label} <ExternalLink className="w-3 h-3" />
            </a>
          </details>
          <label className="block">
            <span className="text-sm font-medium text-slate-700 dark:text-slate-300">{meta.tokenLabel}</span>
            <input type="password" autoComplete="off" className="input w-full mt-1 font-mono text-xs"
              placeholder="Paste your token" value={token} onChange={(e) => setToken(e.target.value)} maxLength={500} />
          </label>
          <div className="flex items-center gap-3">
            <button type="submit" className="btn-primary text-sm inline-flex items-center gap-1.5"
              disabled={!token.trim() || connectMut.isPending}>
              {connectMut.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <PlugZap className="w-4 h-4" />} Connect
            </button>
            {connectMut.isError && <span className="text-sm text-red-600 dark:text-red-400">{errorMessage(connectMut.error)}</span>}
          </div>
        </form>
      )}
      {disconnectMut.isError && <p className="text-sm text-red-600 dark:text-red-400 mt-2">{errorMessage(disconnectMut.error)}</p>}
    </div>
  );
}
