import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";

export type ArtifactCategory = "research" | "architecture" | "ui" | "reviews" | "summaries";

/**
 * Agents communicate through Artifacts, not through shared conversation
 * history (see docs/design/jev-orchestrator/ARCHITECTURE.md#artifact-centric
 * -communication). Each artifact is a plain file under artifacts/<category>/.
 */
export class ArtifactManager {
  constructor(private readonly artifactsRoot: string) {}

  async write(category: ArtifactCategory, fileName: string, content: string): Promise<string> {
    const relativePath = join(category, fileName);
    const absolutePath = join(this.artifactsRoot, relativePath);
    await mkdir(dirname(absolutePath), { recursive: true });
    await writeFile(absolutePath, content, "utf-8");
    return relativePath;
  }

  async read(relativePath: string): Promise<string> {
    return readFile(join(this.artifactsRoot, relativePath), "utf-8");
  }
}
