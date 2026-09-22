import type { AgentProvider, AgentRegistry, Role } from "../types.js";
import { NotImplementedProvider } from "../providers/not-implemented-provider.js";

/**
 * Resolves a Role to an AgentProvider using the Agent Registry
 * (config/agents.json). Providers are looked up by name so a role can be
 * repointed to a different provider by editing config, not code (see
 * docs/design/jev-orchestrator/ARCHITECTURE.md#provider-adapter).
 */
export class Router {
  constructor(
    private readonly registry: AgentRegistry,
    private readonly providers: Map<string, AgentProvider>,
  ) {}

  resolve(role: Role): AgentProvider {
    const entry = this.registry[role];
    if (!entry) {
      throw new Error(`No registry entry for role '${role}'. Check config/agents.json.`);
    }
    return this.providers.get(entry.provider) ?? new NotImplementedProvider(entry.provider);
  }
}
