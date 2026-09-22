import type { PolicyConfig, PolicyDecision, RiskLevel } from "../types.js";

const RISK_ORDER: Record<RiskLevel, number> = {
  low: 0,
  medium: 1,
  high: 2,
  critical: 3,
};

export interface PolicyEvaluationInput {
  goal: string;
  taskDescription?: string;
  risk: RiskLevel;
}

/**
 * Hard Policy Layer. Its rules always win over a Jev decision or a low
 * reported risk: the orchestrator must call this after every decision and
 * before executing any agent (see docs/design/jev-orchestrator/ARCHITECTURE.md
 * #hard-policy-layer). This is intentionally simple keyword matching, not a
 * general-purpose classifier — extend `config/policies.json`, not this code,
 * when a new destructive category needs coverage.
 */
export class PolicyEngine {
  constructor(private readonly config: PolicyConfig) {}

  evaluate(input: PolicyEvaluationInput): PolicyDecision {
    const haystack = `${input.goal} ${input.taskDescription ?? ""}`.toLowerCase();

    for (const rule of this.config.hardRules) {
      const matched = rule.keywords.some((keyword) => haystack.includes(keyword.toLowerCase()));
      if (matched) {
        return { requiresHumanApproval: true, matchedRuleId: rule.id, reason: rule.reason };
      }
    }

    const threshold = this.config.riskThresholds.requireApprovalAtOrAbove;
    if (RISK_ORDER[input.risk] >= RISK_ORDER[threshold]) {
      return {
        requiresHumanApproval: true,
        reason: `risk level '${input.risk}' meets or exceeds threshold '${threshold}'`,
      };
    }

    return { requiresHumanApproval: false };
  }
}
