import { mkdtemp, rm, writeFile, mkdir } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { ContextBuilder } from "../src/context/context-builder.js";
import { ArtifactManager } from "../src/artifacts/artifact-manager.js";

describe("ContextBuilder", () => {
  let repoRoot: string;
  let artifactsRoot: string;

  beforeEach(async () => {
    repoRoot = await mkdtemp(join(tmpdir(), "ctx-repo-"));
    artifactsRoot = await mkdtemp(join(tmpdir(), "ctx-artifacts-"));
    await mkdir(join(repoRoot, "frontend", "src", "auth"), { recursive: true });
    await writeFile(join(repoRoot, "frontend", "src", "auth", "SignupForm.tsx"), "export {}\n");
    await writeFile(join(repoRoot, "frontend", "src", "auth", "Unrelated.tsx"), "export {}\n");
    await mkdir(join(artifactsRoot, "research"), { recursive: true });
    await writeFile(join(artifactsRoot, "research", "auth-options.md"), "# research\n");
    await writeFile(join(artifactsRoot, "research", "unrelated.md"), "# unrelated\n");
  });

  afterEach(async () => {
    await rm(repoRoot, { recursive: true, force: true });
    await rm(artifactsRoot, { recursive: true, force: true });
  });

  it("includes only the explicitly listed files and artifacts", async () => {
    const builder = new ContextBuilder(repoRoot, new ArtifactManager(artifactsRoot));

    const context = await builder.build({
      id: "task-1",
      goal: "Signup form 구현",
      role: "frontend",
      relevantFilePaths: ["frontend/src/auth/SignupForm.tsx"],
      artifactRefs: ["research/auth-options.md"],
    });

    expect(context.relevantFiles.map((f) => f.path)).toEqual(["frontend/src/auth/SignupForm.tsx"]);
    expect(context.relevantFiles.some((f) => f.path.includes("Unrelated"))).toBe(false);

    expect(context.referencedArtifacts.map((a) => a.path)).toEqual(["research/auth-options.md"]);
    expect(context.referencedArtifacts.some((a) => a.path.includes("unrelated"))).toBe(false);
  });

  it("skips a missing file or artifact instead of throwing", async () => {
    const builder = new ContextBuilder(repoRoot, new ArtifactManager(artifactsRoot));

    const context = await builder.build({
      id: "task-2",
      goal: "goal",
      role: "backend",
      relevantFilePaths: ["frontend/src/auth/DoesNotExist.tsx"],
      artifactRefs: ["research/does-not-exist.md"],
    });

    expect(context.relevantFiles).toEqual([]);
    expect(context.referencedArtifacts).toEqual([]);
  });
});
