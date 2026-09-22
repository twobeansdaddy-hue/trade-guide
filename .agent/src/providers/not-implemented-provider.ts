import type { AgentProvider, AgentTaskInput, AgentResult, BuiltContext } from "../types.js";

/**
 * Honest placeholder for a real provider integration. It never fabricates a
 * successful result — real agent execution wiring (Claude/Codex/Gemini API
 * calls, CLI invocation, etc.) is out of scope for V0.1 per the task
 * contract's "Fake integration을 만들지 않는다" guardrail. Replace this with a
 * real AgentProvider implementation before running the orchestrator outside
 * dry-run mode.
 */
export class NotImplementedProvider implements AgentProvider {
  constructor(readonly name: string) {}

  async execute(task: AgentTaskInput, _context: BuiltContext): Promise<AgentResult> {
    throw new Error(
      `Provider '${this.name}' is not configured. Task '${task.id}' (role: ${task.role}) cannot execute ` +
        "until a real AgentProvider is wired up. Use dry-run mode to test routing without execution.",
    );
  }
}
