export type Role =
  | "research"
  | "architecture"
  | "backend"
  | "frontend"
  | "ui"
  | "test"
  | "review";

export type DecisionAction = Role | "human" | "complete";

export type CompletionStatus =
  | "complete"
  | "incomplete"
  | "verify_more"
  | "human_review";

export type RiskLevel = "low" | "medium" | "high" | "critical";

export type TaskStatus =
  | "pending"
  | "running"
  | "completed"
  | "blocked"
  | "human_review";

export interface AgentRegistryEntry {
  provider: string;
  capabilities: string[];
}

export type AgentRegistry = Record<Role, AgentRegistryEntry>;

export interface HardPolicyRule {
  id: string;
  keywords: string[];
  reason: string;
}

export interface PolicyConfig {
  hardRules: HardPolicyRule[];
  riskThresholds: {
    requireApprovalAtOrAbove: RiskLevel;
  };
}

export interface TaskRecord {
  id: string;
  goal: string;
  role: Role;
  status: TaskStatus;
  changedFiles?: string[];
  tests?: { passed: number; failed: number };
  summary?: string;
  followups?: string[];
  artifactPath?: string;
  retryCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectState {
  currentGoal: string;
  completed: TaskRecord[];
  active: TaskRecord[];
  blocked: TaskRecord[];
  artifacts: Record<string, string>;
  decisions: string[];
  stateVersion: number;
  updatedAt: string;
}

export interface DecisionLogEntry {
  timestamp: string;
  goal: string;
  decision: DecisionAction;
  confidence: number;
  reason: string;
  stateVersion: number;
  risk?: RiskLevel;
}

export interface DecideNextActionInput {
  goal: string;
  state: ProjectState;
}

export interface DecideNextActionResult {
  decision: DecisionAction;
  confidence: number;
  reason: string;
}

export interface CheckCompletionResult {
  status: CompletionStatus;
  confidence: number;
  reason: string;
}

export interface EvaluateRiskInput {
  goal: string;
  action: DecisionAction;
  taskDescription?: string;
}

export interface EvaluateRiskResult {
  risk: RiskLevel;
  reason: string;
}

/** Jev is a decision-only layer: it never executes work, only routes and judges completion/risk. */
export interface DecisionEngine {
  readonly name: string;
  decideNextAction(input: DecideNextActionInput): Promise<DecideNextActionResult>;
  checkCompletion(input: DecideNextActionInput): Promise<CheckCompletionResult>;
  evaluateRisk(input: EvaluateRiskInput): Promise<EvaluateRiskResult>;
}

export interface BuiltContext {
  task: string;
  relevantFiles: Array<{ path: string; content: string }>;
  apiContract?: string;
  uiSpec?: string;
  dependencies?: string[];
  acceptanceCriteria?: string[];
  referencedArtifacts: Array<{ path: string; content: string }>;
}

export interface AgentTaskInput {
  id: string;
  goal: string;
  role: Role;
  relevantFilePaths?: string[];
  apiContract?: string;
  uiSpec?: string;
  dependencies?: string[];
  acceptanceCriteria?: string[];
  artifactRefs?: string[];
}

export interface AgentResult {
  status: "completed" | "failed";
  changedFiles: string[];
  tests?: { passed: number; failed: number };
  summary: string;
  followups: string[];
  artifactPath?: string;
}

export interface AgentProvider {
  readonly name: string;
  execute(task: AgentTaskInput, context: BuiltContext): Promise<AgentResult>;
}

export interface PolicyDecision {
  requiresHumanApproval: boolean;
  matchedRuleId?: string;
  reason?: string;
}

export interface OrchestratorLimits {
  maxSteps: number;
  maxRetriesPerTask: number;
  maxAgentCalls: number;
  maxDecisionCalls: number;
}

export type StepOutcome =
  | { kind: "human_review"; reason: string }
  | { kind: "complete"; reason: string }
  | { kind: "verify_more"; reason: string }
  | { kind: "agent_executed"; role: Role; result: AgentResult }
  | { kind: "agent_failed"; role: Role; error: string }
  | { kind: "limit_reached"; reason: string };
