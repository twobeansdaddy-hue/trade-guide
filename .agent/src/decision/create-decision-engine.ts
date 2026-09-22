import type { DecisionEngine } from "../types.js";
import { MockDecisionEngine } from "./mock-engine.js";
import { JevDecisionEngine } from "./jev-engine.js";

/**
 * Picks a DecisionEngine implementation from the environment so that
 * business logic never talks to a concrete engine class directly (see
 * docs/design/jev-orchestrator/ARCHITECTURE.md#jev-adapter). Falls back to
 * MockDecisionEngine when JEV_API_URL is not configured, so the orchestrator
 * always has a working engine in dev/CI without a Jev subscription.
 */
export function createDecisionEngine(env: NodeJS.ProcessEnv = process.env): DecisionEngine {
  const apiUrl = env.JEV_API_URL;
  if (!apiUrl) {
    return new MockDecisionEngine();
  }
  return new JevDecisionEngine({
    apiUrl,
    apiKey: env.JEV_API_KEY,
    timeoutMs: env.JEV_TIMEOUT_MS ? Number(env.JEV_TIMEOUT_MS) : undefined,
    maxRetries: env.JEV_MAX_RETRIES ? Number(env.JEV_MAX_RETRIES) : undefined,
  });
}
