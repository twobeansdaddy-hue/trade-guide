# Trade Guide Agent Orchestrator (V0.1)

Jev 기반 Decision Layer + Multi-Agent Orchestrator의 최소 구현. 이 디렉터리는
독립된 dev-tooling 패키지이며 Spring Boot 백엔드나 Vite 프론트엔드 빌드에
포함되지 않는다.

전체 설계와 사용법은 [`docs/design/jev-orchestrator/ARCHITECTURE.md`](../docs/design/jev-orchestrator/ARCHITECTURE.md)와
[`docs/design/jev-orchestrator/USAGE.md`](../docs/design/jev-orchestrator/USAGE.md)를 참고한다.

## 빠른 시작

```bash
cd .agent
npm install          # 이 저장소의 다른 에이전트는 의존성 설치를 하지 않으므로 직접 실행
npm run typecheck
npm test
npm run cli -- run "회원가입 구현" --dry-run
```

## 작업 계약

이 코드는 [`docs/agent-tasks/claude-jev-orchestrator-v0.1-20260922.md`](../docs/agent-tasks/claude-jev-orchestrator-v0.1-20260922.md)
작업 계약 범위(`.agent/**`, `docs/design/jev-orchestrator/**`)에서 구현되었다.
