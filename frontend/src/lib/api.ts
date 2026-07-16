import axios, { AxiosError } from 'axios';
import type { ApiResponse, AuthResponse, User, Project, ProjectSummary, DeploymentPlan, DeploymentStep, GuideCategory, Guide, CommandSnippet, ProjectEnvVar, EnvVarDefinition, GlossaryTerm, DashboardData, LoginRequest, RegisterRequest, TechnologySelection, ErrorReportRequest, RepositoryAnalysis, StackDetectionResult, BlueprintResponse, ImportRepositoryResponse, VerificationRun, AssistResponse } from '@/types';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (err: AxiosError<ApiResponse<unknown>>) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

function wrap<T>(p: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  return p.then((r) => {
    if (!r.data.success) throw new Error(r.data.message);
    return r.data.data;
  });
}

export const authApi = {
  login: (data: LoginRequest) => wrap(api.post<ApiResponse<AuthResponse>>('/auth/login', data)),
  register: (data: RegisterRequest) => wrap(api.post<ApiResponse<AuthResponse>>('/auth/register', data)),
  me: () => wrap(api.get<ApiResponse<User>>('/auth/me')),
};

export const dashboardApi = {
  get: () => wrap(api.get<ApiResponse<DashboardData>>('/dashboard')),
};

export const projectApi = {
  list: () => wrap(api.get<ApiResponse<ProjectSummary[]>>('/projects')),
  get: (id: number) => wrap(api.get<ApiResponse<Project>>(`/projects/${id}`)),
  create: (data: { name: string; description?: string; githubUrl?: string }) =>
    wrap(api.post<ApiResponse<Project>>('/projects', data)),
  delete: (id: number) => wrap(api.delete<ApiResponse<void>>(`/projects/${id}`)),
  generatePlan: (id: number, tech: TechnologySelection) =>
    wrap(api.post<ApiResponse<DeploymentPlan>>(`/projects/${id}/generate-plan`, tech)),
  nextStep: (id: number) => wrap(api.get<ApiResponse<DeploymentStep>>(`/projects/${id}/next-step`)),
};

export const planApi = {
  get: (projectId: number) => wrap(api.get<ApiResponse<DeploymentPlan>>(`/projects/${projectId}/plan`)),
  updateStep: (projectId: number, stepIndex: number, data: { status: string; note?: string }) =>
    wrap(api.post<ApiResponse<DeploymentStep>>(`/projects/${projectId}/steps/${stepIndex}/progress`, data)),
};

export const guideApi = {
  categories: () => wrap(api.get<ApiResponse<GuideCategory[]>>('/guides/categories')),
  list: (category: string) => wrap(api.get<ApiResponse<Guide[]>>(`/guides?category=${category}`)),
  get: (slug: string) => wrap(api.get<ApiResponse<Guide>>(`/guides/${slug}`)),
  search: (q: string) => wrap(api.get<ApiResponse<Guide[]>>(`/guides/search?q=${q}`)),
};

export const commandApi = {
  list: (category?: string) => wrap(api.get<ApiResponse<CommandSnippet[]>>(`/commands${category ? `?category=${category}` : ''}`)),
  search: (q: string) => wrap(api.get<ApiResponse<CommandSnippet[]>>(`/commands/search?q=${q}`)),
};

export const envVarApi = {
  list: (projectId: number) => wrap(api.get<ApiResponse<ProjectEnvVar[]>>(`/projects/${projectId}/env-vars`)),
  definitions: () => wrap(api.get<ApiResponse<EnvVarDefinition[]>>('/env-var-definitions')),
};

export const glossaryApi = {
  list: () => wrap(api.get<ApiResponse<GlossaryTerm[]>>('/glossary')),
  search: (q: string) => wrap(api.get<ApiResponse<GlossaryTerm[]>>(`/glossary/search?q=${q}`)),
  get: (slug: string) => wrap(api.get<ApiResponse<GlossaryTerm>>(`/glossary/${slug}`)),
};

export const analysisApi = {
  latest: (projectId: number) => wrap(api.get<ApiResponse<RepositoryAnalysis>>(`/projects/${projectId}/analysis`)),
  run: (projectId: number, repository: string) =>
    wrap(api.post<ApiResponse<RepositoryAnalysis>>(`/projects/${projectId}/analysis`, { repository })),
};

export const blueprintApi = {
  latest: (projectId: number) => wrap(api.get<ApiResponse<BlueprintResponse>>(`/projects/${projectId}/blueprint`)),
  generate: (projectId: number) => wrap(api.post<ApiResponse<BlueprintResponse>>(`/projects/${projectId}/blueprint`)),
  override: (projectId: number, overrides: Record<string, string>) =>
    wrap(api.put<ApiResponse<BlueprintResponse>>(`/projects/${projectId}/blueprint/overrides`, overrides)),
};

export const importApi = {
  preview: (repository: string) =>
    wrap(api.post<ApiResponse<StackDetectionResult>>('/repositories/preview-analysis', { repository })),
  importRepository: (repository: string) =>
    wrap(api.post<ApiResponse<ImportRepositoryResponse>>('/projects/import', { repository })),
};

export const verifyApi = {
  start: (projectId: number, body: { frontendUrl?: string; backendUrl?: string; healthPath?: string; expectedCommit?: string; allowInsecureLocal?: boolean }) =>
    wrap(api.post<ApiResponse<VerificationRun>>(`/projects/${projectId}/verifications`, body)),
  get: (projectId: number, runId: number) =>
    wrap(api.get<ApiResponse<VerificationRun>>(`/projects/${projectId}/verifications/${runId}`)),
  list: (projectId: number, limit = 5) =>
    wrap(api.get<ApiResponse<VerificationRun[]>>(`/projects/${projectId}/verifications?limit=${limit}`)),
  sanitizeLog: (projectId: number, content: string) =>
    wrap(api.post<ApiResponse<{ sanitized: string; truncated: boolean; warning: string }>>(`/projects/${projectId}/logs/sanitize`, { content })),
  assist: (projectId: number, body: { question?: string; log?: string }) =>
    wrap(api.post<ApiResponse<AssistResponse>>(`/projects/${projectId}/assist`, body)),
};

export const troubleshootApi = {
  submit: (data: ErrorReportRequest) => wrap(api.post<ApiResponse<{ id: number; aiResponse: string; redactedContent: string }>>('/troubleshoot', data)),
  history: () => wrap(api.get<ApiResponse<Array<{ id: number; errorType: string; aiResponse: string; createdAt: string }>>>('/troubleshoot/history')),
};

export default api;
