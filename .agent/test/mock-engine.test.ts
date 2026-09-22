import { describe, expect, it } from "vitest";
import { MockDecisionEngine } from "../src/decision/mock-engine.js";
import { createInitialState } from "../src/state/project-state-store.js";

describe("MockDecisionEngine", () => {
  it("routes through the scripted roles in order", async () => {
    const engine = new MockDecisionEngine(["research", "backend", "frontend"]);
    const state = createInitialState("goal");

    const first = await engine.decideNextAction({ goal: "goal", state });
    expect(first.decision).toBe("research");
  });

  it("reports complete once every scripted step is in `completed`", async () => {
    const engine = new MockDecisionEngine(["research", "backend"]);
    const state = createInitialState("goal");
    state.completed.push(
      { id: "1", goal: "goal", role: "research", status: "completed", retryCount: 0, createdAt: "", updatedAt: "" },
      { id: "2", goal: "goal", role: "backend", status: "completed", retryCount: 0, createdAt: "", updatedAt: "" },
    );

    const decision = await engine.decideNextAction({ goal: "goal", state });
    expect(decision.decision).toBe("complete");

    const completion = await engine.checkCompletion({ goal: "goal", state });
    expect(completion.status).toBe("complete");
  });

  it("reports human_review when a task is blocked, even mid-script", async () => {
    const engine = new MockDecisionEngine(["research", "backend"]);
    const state = createInitialState("goal");
    state.blocked.push({
      id: "1",
      goal: "goal",
      role: "research",
      status: "blocked",
      retryCount: 3,
      createdAt: "",
      updatedAt: "",
    });

    const completion = await engine.checkCompletion({ goal: "goal", state });
    expect(completion.status).toBe("human_review");
  });
});
