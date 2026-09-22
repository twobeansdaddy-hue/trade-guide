import { describe, expect, it } from "vitest";
import { PolicyEngine } from "../src/policy/policy-engine.js";
import type { PolicyConfig } from "../src/types.js";

const config: PolicyConfig = {
  hardRules: [
    { id: "force-push", keywords: ["force push"], reason: "Force push to remote" },
  ],
  riskThresholds: { requireApprovalAtOrAbove: "high" },
};

describe("PolicyEngine", () => {
  it("overrides a low-risk decision when a hard rule keyword matches the goal", () => {
    const engine = new PolicyEngine(config);
    const result = engine.evaluate({ goal: "force push main to origin", risk: "low" });
    expect(result.requiresHumanApproval).toBe(true);
    expect(result.matchedRuleId).toBe("force-push");
  });

  it("requires approval once risk meets the configured threshold, even without a keyword match", () => {
    const engine = new PolicyEngine(config);
    const result = engine.evaluate({ goal: "harmless refactor", risk: "high" });
    expect(result.requiresHumanApproval).toBe(true);
    expect(result.matchedRuleId).toBeUndefined();
  });

  it("allows a low-risk, non-matching goal through without approval", () => {
    const engine = new PolicyEngine(config);
    const result = engine.evaluate({ goal: "add a unit test", risk: "low" });
    expect(result.requiresHumanApproval).toBe(false);
  });
});
