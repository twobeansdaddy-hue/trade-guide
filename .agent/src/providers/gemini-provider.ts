import { NotImplementedProvider } from "./not-implemented-provider.js";

/**
 * Placeholder for a real Gemini integration. Intentionally throws until
 * wired up — see not-implemented-provider.ts. Note: docs/AI_COLLABORATION_
 * POLICY.md keeps direct Gemini CLI out of the human delivery path; a real
 * GeminiProvider here would still need a task contract before it can write
 * files.
 */
export class GeminiProvider extends NotImplementedProvider {
  constructor() {
    super("gemini");
  }
}
