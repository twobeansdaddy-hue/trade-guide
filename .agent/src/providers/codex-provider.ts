import { NotImplementedProvider } from "./not-implemented-provider.js";

/**
 * Placeholder for a real Codex integration. Intentionally throws until
 * wired up — see not-implemented-provider.ts.
 */
export class CodexProvider extends NotImplementedProvider {
  constructor() {
    super("codex");
  }
}
