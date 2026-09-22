import { NotImplementedProvider } from "./not-implemented-provider.js";

/**
 * Placeholder for a real Claude integration (e.g. Claude Agent SDK or the
 * Claude Code CLI invoked as a subprocess with a scoped task contract).
 * Intentionally throws until wired up — see not-implemented-provider.ts.
 */
export class ClaudeProvider extends NotImplementedProvider {
  constructor() {
    super("claude");
  }
}
