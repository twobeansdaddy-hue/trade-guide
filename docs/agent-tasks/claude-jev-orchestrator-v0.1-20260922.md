# Agent Task Contract

## Identity

- Task ID: `claude-jev-orchestrator-v0.1-20260922`
- Owner: `Claude`
- Work mode: `scoped-implementation`
- Branch / worktree: `main` (작업용 feature 브랜치는 Codex가 커밋 시점에 생성)

## Outcome

기존 다중 에이전트 협업 구조(`docs/AI_COLLABORATION_POLICY.md`, `docs/agent-tasks/*`,
`.claude/agent-scope.json` 하네스)를 대체하지 않고, 향후 여러 AI 에이전트가 역할별로
협업할 수 있는 최소 기반(Jev 기반 Decision Layer + Multi-Agent Orchestrator V0.1)을
프로젝트 런타임과 분리된 독립 도구 계층으로 추가한다. Dry-run 라우팅 확인, Mock
Decision Engine 기반 결정론적 워크플로, 최소 Project State/Decision Log/Artifact
관리, Hard Policy Layer(파괴적 작업 인간 승인)까지 동작하는 것이 완료 기준이다.
실제 Jev API 연동은 어댑터 인터페이스와 timeout/retry/fallback 정책까지만 구현하고,
실제 키·엔드포인트 연결은 사용자 환경설정 몫으로 남긴다.

## Allowed Files

- `.agent/**` (신규: 오케스트레이터 도구, config, state, artifacts, tests)
- `docs/design/jev-orchestrator/**` (신규: 아키텍처/사용 설명 문서)
- `docs/agent-tasks/claude-jev-orchestrator-v0.1-20260922.md` (본 문서)
- `.env.example` (Jev/오케스트레이터 관련 변수 이름만 추가, 값 없음)

기존 `src/**`(Spring Boot), `frontend/**`, `research/**`, 다른 에이전트의 활성 작업
파일은 건드리지 않는다. 필요한 경우 읽기만 한다.

## Non-Goals And Guardrails

- 기존 Codex/Claude/Antigravity 역할 분담과 `docs/AI_COLLABORATION_POLICY.md`의 결정
  권한 구조를 변경하지 않는다 — 이번 오케스트레이터는 그 구조의 보조 도구다.
- 실제 프로덕션 배포, DB 마이그레이션, 인증/보안 설정, 시크릿 변경, 강제 push 등은
  Hard Policy Layer가 항상 `HUMAN_REVIEW`로 막아야 하며, 자동 실행 경로를 만들지
  않는다.
- Jev API 키, Claude/Codex/Gemini API 키를 코드에 하드코딩하지 않는다. `.env.example`
  에는 변수명만 추가한다.
- `npm install` 등 의존성 설치는 수행하지 않는다(정책상 금지) — `package.json`만
  준비하고 설치·실행은 사용자가 한다.
- `.claude/`, `.gemini/`, `.antigravity/`의 기존 scope 파일 포맷과 하네스
  (`scripts/agent-harness.sh`)는 수정하지 않는다.
- Web dashboard, 분산 큐, Kubernetes, Redis, vector DB, 완전 자동 provider 연결 등
  V0.1 범위를 벗어나는 것은 만들지 않는다.

## Acceptance Checks

- [ ] Agent Registry(설정)와 Project State/Decision Log(JSON/JSONL) 스키마 정의
- [ ] `DecisionEngine` 인터페이스 + `MockDecisionEngine` + `JevDecisionEngine`(fetch
      기반, timeout/retry/fallback to human_review)
- [ ] `PolicyEngine`의 Hard Rule이 Jev 판단보다 우선하는지 확인하는 테스트
- [ ] `ContextBuilder`가 무관한 컨텍스트를 포함하지 않는지 확인하는 테스트
- [ ] `Orchestrator` 루프: state → jev → policy → agent 선택 → context → (mock)
      execute → artifact 저장 → state 갱신 → completion 판단
- [ ] Dry-run CLI 명령으로 실제 agent 실행 없이 routing 결과만 출력되는지 확인
- [ ] 최소 단위 테스트(routing, state transition, failure→retry→human_review,
      policy override, context builder, completion) — 이 저장소에 Node 테스트
      러너가 없으므로 `.agent` 내부에 독립 devDependency로 추가하고 실행은
      사용자가 `npm install` 이후 수행
- [ ] 문서화: 아키텍처, 실행 방법, dry-run, agent/provider 추가 방법, Jev 장애
      대응, Human Approval 흐름, 한계, 로드맵

## Handoff

- Files changed:
  - 신규: `.agent/**` (package.json, tsconfig.json, .gitignore, README.md,
    `config/agents.json`, `config/policies.json`, `src/**`(types, decision
    engine 3종, policy engine, context builder, artifact manager,
    orchestrator+router, providers 5개, cli), `test/**`(4개 스펙, 14개 테스트),
    `artifacts/*/.gitkeep`, `state/.gitkeep`, `approvals/.gitkeep`)
  - 신규: `docs/design/jev-orchestrator/ARCHITECTURE.md`,
    `docs/design/jev-orchestrator/USAGE.md`
  - 신규: 본 작업 계약 문서
  - 미변경(훅이 차단, 사용자가 직접 처리 필요): `.env.example`에 `JEV_API_URL`,
    `JEV_API_KEY`, `JEV_TIMEOUT_MS`, `JEV_MAX_RETRIES` 변수명 추가 — 정확한
    diff는 대화 로그 참고
- Verification run:
  - `npm install` — 사용자가 직접 실행, 완료 확인됨
  - `npm run typecheck` — 통과(0 errors). 1건 수정: `mock-engine.ts`에서
    `noUncheckedIndexedAccess`로 인한 `Role | undefined` 오류를 명시적
    가드로 해결
  - `npm test` (vitest) — 4개 파일, 14개 테스트 전부 통과 (routing, state
    transition, failure→retry→human_review, policy override, context
    builder 격리, completion, dry-run 비영속성)
  - `npm run cli -- run "회원가입 구현" --dry-run` — 수동 확인: research →
    backend → frontend → COMPLETE 출력, agent 미실행, `state show`로 상태
    파일 미생성 확인
  - `npm run cli -- run "실제 실행 테스트"` (dry-run 아님) — 수동 확인:
    `NotImplementedProvider`가 정직하게 실패, 3회 재시도 후
    `HUMAN_REVIEW`로 상승하는 Loop Protection 동작 확인 후 `state reset`으로
    정리
  - lint: 해당 없음(이 패키지는 루트/`frontend`의 eslint 설정에 포함되지 않음,
    tsc strict가 대체)
- API / data-model / policy impact: 없음(기존 Spring Boot API, 전략 정책
  미변경). 오케스트레이터는 `docs/AI_COLLABORATION_POLICY.md`의 역할·경계를
  대체하지 않는 보조 도구.
- Open decision or risk:
  - 실제 Jev API 스펙(요청/응답 필드)이 아직 없어 `JevDecisionEngine`은
    가정된 계약(`POST {apiUrl}/decide|/completion|/risk`, JSON body/응답)으로
    구현됨 — 실제 연동 시 재검증 필요.
  - `ClaudeProvider`/`CodexProvider`/`GeminiProvider`는 모두
    `NotImplementedProvider`를 상속한 정직한 스텁이며 실제 실행 연동은
    V0.2 이후로 남겨둠.
  - `.env.example` 갱신은 사용자 조치 대기.
