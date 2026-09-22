import { readFile } from "node:fs/promises";
import type { AgentTaskInput, BuiltContext } from "../types.js";
import type { ArtifactManager } from "../artifacts/artifact-manager.js";

/**
 * Builds the minimal context an agent needs, and nothing else: only the file
 * paths and artifact references the task explicitly lists. It never reads
 * the whole repository or the full conversation/task history — see
 * docs/design/jev-orchestrator/ARCHITECTURE.md#context-builder. A file or
 * artifact that fails to read is skipped rather than failing the whole
 * build, so a stale reference in an old task does not block the pipeline.
 */
export class ContextBuilder {
  constructor(
    private readonly repoRoot: string,
    private readonly artifactManager: ArtifactManager,
  ) {}

  async build(task: AgentTaskInput): Promise<BuiltContext> {
    const relevantFiles = await this.readFiles(task.relevantFilePaths ?? []);
    const referencedArtifacts = await this.readArtifacts(task.artifactRefs ?? []);

    return {
      task: task.goal,
      relevantFiles,
      apiContract: task.apiContract,
      uiSpec: task.uiSpec,
      dependencies: task.dependencies,
      acceptanceCriteria: task.acceptanceCriteria,
      referencedArtifacts,
    };
  }

  private async readFiles(paths: string[]): Promise<Array<{ path: string; content: string }>> {
    const results: Array<{ path: string; content: string }> = [];
    for (const relativePath of paths) {
      try {
        const content = await readFile(`${this.repoRoot}/${relativePath}`, "utf-8");
        results.push({ path: relativePath, content });
      } catch {
        // Missing/unreadable file: skip rather than block context building.
      }
    }
    return results;
  }

  private async readArtifacts(paths: string[]): Promise<Array<{ path: string; content: string }>> {
    const results: Array<{ path: string; content: string }> = [];
    for (const relativePath of paths) {
      try {
        const content = await this.artifactManager.read(relativePath);
        results.push({ path: relativePath, content });
      } catch {
        // Missing/unreadable artifact: skip rather than block context building.
      }
    }
    return results;
  }
}
