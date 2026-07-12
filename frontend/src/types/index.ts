export interface User {
  id: number;
  username: string;
  email: string;
  role: string;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  user: User;
}

export interface Project {
  id: number;
  name: string;
  description: string;
  githubUrl: string;
  localFolderPath: string;
  status: string;
  userId: number;
  technologies: Technology[];
  services: string[];
  createdAt: string;
  updatedAt: string;
}

export interface Technology {
  id: number;
  category: string;
  technology: string;
  version: string;
}

export interface ProjectSummary {
  id: number;
  name: string;
  description: string;
  status: string;
  techSummary: string;
  currentStep: number;
  totalSteps: number;
  completedSteps: number;
  nextAction: string;
  createdAt: string;
}

export interface DeploymentPlan {
  id: number;
  projectId: number;
  steps: DeploymentStep[];
  currentStepIndex: number;
  status: string;
  totalSteps: number;
  completedSteps: number;
  generatedAt: string;
}

export interface DeploymentStep {
  orderIndex: number;
  title: string;
  description: string;
  category: string;
  whatToDo: string;
  whyNecessary: string;
  whereToDoIt: string;
  commandOrValue: string;
  whatCommandDoes: string;
  expectedResult: string;
  commonErrors: string[];
  securityWarning: string;
  completionControls: string;
  nextAction: string;
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'BLOCKED' | 'SKIPPED';
  bookmarked: boolean;
  personalNote: string;
  completedAt: string;
}

export interface GuideCategory {
  id: number;
  name: string;
  slug: string;
  description: string;
  icon: string;
  sortOrder: number;
}

export interface Guide {
  id: number;
  categoryId: number;
  title: string;
  slug: string;
  description: string;
  content: string;
  difficulty: string;
  sections: GuideSection[];
  createdAt: string;
}

export interface GuideSection {
  id: number;
  guideId: number;
  title: string;
  content: string;
  sortOrder: number;
}

export interface CommandSnippet {
  id: number;
  category: string;
  title: string;
  command: string;
  description: string;
  explanation: string;
  warning: string;
  destructive: boolean;
  beginnerMode: boolean;
}

export interface EnvVarDefinition {
  id: number;
  name: string;
  description: string;
  category: string;
  platform: string;
  localFileLocation: string;
  productionLocation: string;
  required: boolean;
  exampleValue: string;
}

export interface ProjectEnvVar {
  id: number;
  projectId: number;
  variableName: string;
  description: string;
  classification: 'PUBLIC' | 'SECRET';
  localLocation: string;
  productionLocation: string;
  required: boolean;
  configured: boolean;
  lastVerifiedAt: string;
  notes: string;
}

export interface GlossaryTerm {
  id: number;
  term: string;
  slug: string;
  definition: string;
  example: string;
  category: string;
  relatedTerms: string;
}

export interface DashboardData {
  projects: ProjectSummary[];
  totalProjects: number;
  completedSteps: number;
  totalSteps: number;
  bookmarkCount: number;
  nextStepTitle: string;
  nextStepAction: string;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface ErrorReportRequest {
  errorType: string;
  content: string;
  projectId?: number;
}

export interface TechnologySelection {
  projectType: string;
  frontendTech: string;
  backendTech: string;
  database: string;
  frontendHost: string;
  backendHost: string;
  additionalServices: string[];
}
