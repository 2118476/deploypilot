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

export type AnalysisConfidence = 'HIGH' | 'MEDIUM' | 'LOW';

export interface StackDetection {
  category: string;
  name: string;
  path: string;
  confidence: AnalysisConfidence;
  evidence: string[];
}

export interface EnvVarFinding {
  name: string;
  classification: 'SECRET_OR_SENSITIVE' | 'PUBLIC_CONFIGURATION' | 'CONFIGURATION';
  source: string;
}

export interface StackDetectionResult {
  repository: string;
  structure: 'MONOREPO' | 'SINGLE_APPLICATION' | 'UNKNOWN';
  detections: StackDetection[];
  environmentVariables: EnvVarFinding[];
  buildCommands: string[];
  startCommands: string[];
  warnings: string[];
  analyzedFiles: string[];
  skippedFiles: string[];
}

export interface RepositoryAnalysis {
  id: number;
  projectId: number;
  repository: string;
  status: 'RUNNING' | 'COMPLETED' | 'FAILED';
  errorMessage?: string;
  result?: StackDetectionResult;
  createdAt: string;
}

export interface PlatformOption {
  platform: string;
  reason: string;
  evidence: string[];
  confidence: AnalysisConfidence;
  requiresConfirmation: boolean;
  freeTierNote?: string;
  coldStartNote?: string;
  pricingNote?: string;
}

export interface BlueprintComponent {
  id: string;
  type: 'FRONTEND' | 'BACKEND' | 'DATABASE' | 'EXTERNAL_SERVICE';
  name: string;
  path?: string;
  runtime?: string;
  buildTool?: string;
  recommendedPlatform: PlatformOption;
  alternatives: PlatformOption[];
  selectedPlatform: string;
  buildCommand?: string;
  startCommand?: string;
  rootDirectory?: string;
  publishDirectory?: string;
  healthCheckPath?: string;
  notes: string[];
}

export interface BlueprintRelationship {
  fromComponent: string;
  toComponent: string;
  description: string;
  viaVariable?: string;
}

export interface BlueprintEnvVar {
  name: string;
  componentId?: string;
  targetPlatform?: string;
  classification: 'SECRET_OR_SENSITIVE' | 'PUBLIC_CONFIGURATION' | 'CONFIGURATION';
  required?: boolean | null;
  valueSource: string;
  expectedFormat?: string;
  generatable: boolean;
  dependsOnOutput?: string;
  sourceEvidence?: string;
}

export interface BlueprintFinding {
  severity: 'BLOCKER' | 'WARNING' | 'INFORMATIONAL';
  title: string;
  detail: string;
  evidence?: string;
  affectedFile?: string;
  proposedFix?: string;
  requiresConfirmation: boolean;
}

export interface BlueprintStep {
  index: number;
  title: string;
  what: string;
  where: string;
  inputs: string[];
  produces?: string;
  unlocksVariables: string[];
  expectedResult: string;
  blockedBy: number[];
}

export interface BlueprintFilePreview {
  path: string;
  purpose: string;
  exists?: boolean | null;
  currentContent?: string;
  suggestedContent: string;
  diff?: string;
  reason: string;
}

export interface BlueprintResult {
  repository: string;
  structure: string;
  rulesVersion: string;
  components: BlueprintComponent[];
  relationships: BlueprintRelationship[];
  environmentVariables: BlueprintEnvVar[];
  findings: BlueprintFinding[];
  steps: BlueprintStep[];
  filePreviews: BlueprintFilePreview[];
}

export interface BlueprintResponse {
  id: number;
  projectId: number;
  analysisId: number;
  rulesVersion: string;
  stale: boolean;
  overrides: Record<string, string>;
  result?: BlueprintResult;
  createdAt: string;
  updatedAt: string;
}

export interface ImportRepositoryResponse {
  projectId: number;
  projectName: string;
  analysis: RepositoryAnalysis;
  blueprint: BlueprintResponse;
}

export type CheckStatus = 'PASS' | 'WARNING' | 'FAIL' | 'SKIPPED' | 'UNKNOWN';
export type OverallStatus = 'RUNNING' | 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY' | 'INCONCLUSIVE' | 'FAILED';

export interface VerificationCheck {
  id: string;
  category: string;
  title: string;
  status: CheckStatus;
  evidence: string;
  safeHeaders: Record<string, string>;
  timingMs: number;
}

export interface VerificationDiagnosis {
  severity: 'BLOCKER' | 'WARNING' | 'INFO';
  confidence: 'CONFIRMED' | 'LIKELY' | 'POSSIBLE' | 'USER_DEVICE_CHECK';
  affectedComponent: string;
  title: string;
  likelyCause: string;
  evidence: string;
  recommendedAction: string;
  actionType: 'CODE_CHANGE' | 'REBUILD' | 'PROVIDER_SETTINGS' | 'USER_DEVICE';
}

export interface VersionComparison {
  state: 'CURRENT' | 'OUTDATED' | 'AHEAD_OF_BLUEPRINT' | 'MISMATCHED' | 'UNKNOWN';
  expectedCommit?: string;
  liveFrontendCommit?: string;
  liveBackendCommit?: string;
  evidence?: string;
  suggestion?: string;
}

export interface VerificationResult {
  checks: VerificationCheck[];
  diagnoses: VerificationDiagnosis[];
  version?: VersionComparison;
  corsResult?: string;
  summary?: string;
  skippedChecks: string[];
}

export interface VerificationRun {
  id: number;
  projectId: number;
  blueprintId?: number;
  frontendUrl?: string;
  backendUrl?: string;
  overallStatus: OverallStatus;
  result?: VerificationResult;
  startedAt: string;
  completedAt?: string;
}

export interface AssistResponse {
  contextSummary: string;
  aiAvailable: boolean;
  answer: string;
}

// ==================== Stage 4: Controlled Deployment Automation ====================

export type ProviderName = 'GITHUB' | 'NETLIFY' | 'RENDER' | 'SUPABASE';

export interface ProviderConnection {
  provider: ProviderName;
  connected: boolean;
  connectionType?: string;
  accountLabel?: string;
  scopes?: string;
  status: string;
  lastError?: string;
  connectedAt?: string;
  lastUsedAt?: string;
}

export interface RepositorySummary {
  fullName: string;
  defaultBranch: string;
  privateRepo: boolean;
  htmlUrl?: string;
}

export interface HostingSite {
  id: string;
  name: string;
  url?: string;
  linkedRepo?: string;
}

export interface SecretView {
  name: string;
  destination?: string;
  hasValue: boolean;
  masked: string;
  updatedAt?: string;
}

export type ActionType = 'READ_ONLY' | 'CREATE' | 'UPDATE' | 'DEPLOY' | 'RESTART' | 'DESTRUCTIVE';

export interface PlannedAction {
  id: string;
  order: number;
  type: ActionType;
  provider: string;
  account?: string;
  component?: string;
  title: string;
  description: string;
  targetResource?: string;
  createsNewResource: boolean;
  changesExisting: boolean;
  reversible: boolean;
  requiresRepositoryChange: boolean;
  costNote?: string;
  environmentVariableNames: string[];
  dependsOn: string[];
}

export interface EnvVarPlanItem {
  name: string;
  destination: string;
  source: string;
  required: boolean;
  secret: boolean;
  generatable: boolean;
  valueStatus: 'READY' | 'NEEDS_INPUT' | 'WILL_BE_GENERATED' | 'FROM_PREVIOUS_STEP';
}

export interface MigrationView {
  name: string;
  checksum: string;
  order: number;
  previouslyApplied: boolean;
  destructive: boolean;
  safetyClassification: string;
  reason?: string;
}

export interface DatabaseHandoff {
  required: boolean;
  detectedProvider?: string;
  connectionSupplied: boolean;
  requiredFields: string[];
  instructions?: string;
  choice?: string;
  supabaseConnected?: boolean;
  supabaseOrgId?: string;
  supabaseProjectRef?: string;
  supabaseProjectName?: string;
  supabaseRegion?: string;
  applyMigrations?: boolean;
  migrations?: MigrationView[];
}

// ----- Supabase (Stage 5) -----
export interface SupabaseOrganization {
  id: string;
  name: string;
}

export interface SupabaseProject {
  ref: string;
  name: string;
  organizationId?: string;
  region?: string;
  status: string;
  host?: string;
  restUrl?: string;
}

export type DatabaseChoice = 'MANUAL' | 'EXISTING_SUPABASE_PROJECT' | 'CREATE_SUPABASE_PROJECT';

// ----- Intelligent project status (Stage 5) -----
export type ProjectDashboardStatus =
  | 'NOT_ANALYSED' | 'ANALYSING' | 'BLUEPRINT_READY' | 'SETUP_REQUIRED'
  | 'WAITING_FOR_CONNECTION' | 'WAITING_FOR_SECRET' | 'WAITING_FOR_CONFIRMATION'
  | 'DEPLOYING' | 'PAUSED' | 'VERIFYING' | 'HEALTHY' | 'DEGRADED' | 'FAILED' | 'UNKNOWN';

export interface StatusMilestone { key: string; label: string; done: boolean; }
export interface RequiredAction { type: string; label: string; detail?: string; }
export interface RecommendedAction { type: string; label: string; }

export interface ProjectStatus {
  projectId: number;
  projectName: string;
  status: ProjectDashboardStatus;
  summary: string;
  currentAction?: string;
  milestones: StatusMilestone[];
  requiredActions: RequiredAction[];
  recommendedNextStep?: RecommendedAction;
  latestRunId?: number;
  latestRunStatus?: string;
  mode?: string;
  verificationStatus?: string;
  frontendUrl?: string;
  backendUrl?: string;
  pullRequestUrl?: string;
  supabaseProjectUrl?: string;
  lastUpdated?: string;
  aiExplanation?: string;
}

export interface ActivityEvent {
  id: number;
  automationRunId?: number;
  eventType: string;
  provider?: string;
  actionId?: string;
  summary: string;
  status?: string;
  createdAt: string;
}

// ----- Project Copilot (Stage 5) -----
export interface ProposedAction {
  type: 'NONE' | 'DEPLOY' | 'RETRY_FAILED_STEP';
  summary?: string;
  planHash?: string;
  executable?: boolean;
  targetRunId?: number;
}

export interface CopilotMessage {
  id: number;
  role: 'USER' | 'ASSISTANT';
  content: string;
  aiAvailable: boolean;
  proposedAction?: ProposedAction;
  createdAt: string;
}

export interface CopilotConversation {
  conversationId: number;
  projectId: number;
  aiAvailable: boolean;
  messages: CopilotMessage[];
}

export interface DeploymentActionPlan {
  repository: string;
  branch?: string;
  commitSha?: string;
  mode: string;
  planHash: string;
  consentNotice: string;
  executable: boolean;
  actions: PlannedAction[];
  environmentVariables: EnvVarPlanItem[];
  database?: DatabaseHandoff;
  warnings: string[];
  blockers: string[];
}

export interface ConfirmationResponse {
  runId: number;
  nonce: string;
  planHash: string;
  mode: string;
  expiresAt: string;
  consentNotice: string;
  plan: DeploymentActionPlan;
}

export type ActionStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED';

export interface ExecutionStep {
  id: string;
  order: number;
  type: string;
  provider: string;
  title: string;
  status: ActionStatus;
  detail?: string;
  sanitizedLog?: string;
  startedAt?: string;
  finishedAt?: string;
}

export type AutomationRunStatus = 'PENDING' | 'RUNNING' | 'PAUSED' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';

export interface AutomationRun {
  id: number;
  projectId: number;
  mode: string;
  status: AutomationRunStatus;
  planHash?: string;
  repository?: string;
  branch?: string;
  commitSha?: string;
  currentStepIndex: number;
  steps?: ExecutionStep[];
  outputs: Record<string, string>;
  verificationRunId?: number;
  verificationStatus?: string;
  failureReason?: string;
  plan?: DeploymentActionPlan;
  createdAt?: string;
  updatedAt?: string;
  completedAt?: string;
}
