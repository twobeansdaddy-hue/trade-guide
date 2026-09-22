import type {
  CheckCompletionResult,
  DecideNextActionInput,
  DecideNextActionResult,
  DecisionEngine,
  EvaluateRiskInput,
  EvaluateRiskResult,
} from "../types.js";

export interface JevDecisionEngineConfig {
  apiUrl: string;
  apiKey?: string;
  timeoutMs?: number;
  maxRetries?: number;
}

/**
 * DecisionEngine backed by a real Jev API. Never throws out of its public
 * methods: on timeout, network error, or an exhausted retry budget it falls
 * back to a safe, conservative result (human review / high risk) rather than
 * letting a Jev outage take down the orchestrator. See
 * docs/design/jev-orchestrator/ARCHITECTURE.md#jev-failure-handling.
 */
export class JevDecisionEngine implements DecisionEngine {
  readonly name = "jev";
  private readonly apiUrl: string;
  private readonly apiKey?: string;
  private readonly timeoutMs: number;
  private readonly maxRetries: number;

  constructor(config: JevDecisionEngineConfig) {
    this.apiUrl = config.apiUrl;
    this.apiKey = config.apiKey;
    this.timeoutMs = config.timeoutMs ?? 10_000;
    this.maxRetries = config.maxRetries ?? 2;
  }

  async decideNextAction(input: DecideNextActionInput): Promise<DecideNextActionResult> {
    const result = await this.callWithFallback<DecideNextActionResult>("/decide", input);
    return (
      result ?? {
        decision: "human",
        confidence: 0,
        reason: "jev_unavailable: falling back to human review",
      }
    );
  }

  async checkCompletion(input: DecideNextActionInput): Promise<CheckCompletionResult> {
    const result = await this.callWithFallback<CheckCompletionResult>("/completion", input);
    return (
      result ?? {
        status: "human_review",
        confidence: 0,
        reason: "jev_unavailable: falling back to human review",
      }
    );
  }

  async evaluateRisk(input: EvaluateRiskInput): Promise<EvaluateRiskResult> {
    const result = await this.callWithFallback<EvaluateRiskResult>("/risk", input);
    return (
      result ?? {
        risk: "critical",
        reason: "jev_unavailable: defaulting to critical risk so policy forces human review",
      }
    );
  }

  private async callWithFallback<T>(path: string, body: unknown): Promise<T | undefined> {
    let attempt = 0;
    let lastError: unknown;
    while (attempt <= this.maxRetries) {
      try {
        return await this.callOnce<T>(path, body);
      } catch (error) {
        lastError = error;
        attempt += 1;
        if (attempt <= this.maxRetries) {
          await sleep(backoffMs(attempt));
        }
      }
    }
    console.error(
      `[JevDecisionEngine] ${path} failed after ${this.maxRetries + 1} attempt(s): ${describeError(lastError)}`,
    );
    return undefined;
  }

  private async callOnce<T>(path: string, body: unknown): Promise<T> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      const response = await fetch(`${this.apiUrl}${path}`, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          ...(this.apiKey ? { authorization: `Bearer ${this.apiKey}` } : {}),
        },
        body: JSON.stringify(body),
        signal: controller.signal,
      });
      if (!response.ok) {
        throw new Error(`Jev API responded with HTTP ${response.status}`);
      }
      return (await response.json()) as T;
    } finally {
      clearTimeout(timer);
    }
  }
}

function backoffMs(attempt: number): number {
  return 250 * 2 ** (attempt - 1);
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function describeError(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
