# Jev Decision Layer + Multi-Agent Orchestrator — Architecture (V0.1)

- Task Contract: `docs/agent-tasks/claude-jev-orchestrator-v0.1-20260922.md`
- Code: `.agent/**` (독립 dev-tooling 패키지, Spring Boot/Vite 런타임과 분리)
- 상태: 설계 문서 + 구현 완료(V0.1), 실제 Jev API·실제 Provider 연동은 미완료

이 문서는 제안이며, 기존 `docs/AI_COLLABORATION_POLICY.md`가 정의하는 에이전트
역할·파일 소유권·검증·Git 경계를 대체하지 않는다. 오케스트레이터는 그 정책을
보조하는 도구다. 실제로 여러 AI를 자동 디스패치하려면 여전히
`docs/agent-tasks/`의 작업 계약과 `.claude/agent-scope.json` 같은 하네스가
필요하다.

## 왜 필요한가

세션이 길어질수록 전체 대화 컨텍스트가 계속 늘어나는 문제를 피하기 위해,
Conversation 중심이 아니라 **State + Artifact + Task 중심**으로 진행 상태를
관리한다.

```
USER GOAL
   |
   v
ORCHESTRATOR (state, task, context, artifact 관리)
   |
   v
JEV DECISION LAYER (routing / completion / risk 판단만, 실행 없음)
   |
   v
AGENT EXECUTION (research/architecture/backend/frontend/ui/test/review)
   |
   v
Verification
   |
   v
JEV COMPLETION DECISION (complete / retry / verify_more / human_review)
```

## V0.1 범위

구현됨:

- Agent Registry (`config/agents.json`)
- Project State + append-only Decision Log (`state/project.json`,
  `state/decisions.jsonl`)
- `DecisionEngine` 인터페이스 + `MockDecisionEngine`(결정론적) +
  `JevDecisionEngine`(실제 API 어댑터, timeout/retry/fallback)
- Hard Policy Layer (`config/policies.json`, `PolicyEngine`)
- Context Builder (`ContextBuilder`) — 태스크가 명시한 파일/Artifact만 포함
- Artifact Manager (`ArtifactManager`)
- Orchestrator (`Orchestrator`) — 단일 스텝(`step`)과 전체 루프(`run`),
  그리고 실행 없는 `dryRun`
- Provider 추상화(`AgentProvider`) + Claude/Codex/Gemini용
  `NotImplementedProvider` 서브클래스(정직한 스텁, 가짜 성공 결과 없음)
- Dry Run CLI

구현하지 않음(의도적으로 V0.1 범위 밖):

- 실제 Claude/Codex/Gemini 실행 연동(서브프로세스, API 호출 등)
- Test Agent / Review Agent의 실질적 로직(라우팅 대상 role은 있지만 provider
  미구현)
- 병렬 실행, dependency graph
- Web 대시보드, 분산 큐, Redis, vector DB, Kubernetes

## Role -> Agent -> Provider

```
Role (research | architecture | backend | frontend | ui | test | review)
  -> Agent Registry entry (config/agents.json)
    -> Provider name (claude | codex | gemini | ...)
      -> AgentProvider 구현체 (src/providers/*)
```

`BackendAgent = Codex`처럼 역할과 provider를 코드에 고정하지 않는다. Role은
`Router`가 `config/agents.json`을 읽어 provider 이름으로 해석하고, provider
이름을 실제 `AgentProvider` 구현체에 매핑하는 것은 `Router`를 생성하는
쪽(현재는 CLI, 향후 Jev 자신)의 책임이다. Provider를 교체하려면 config만
바꾸면 된다.

`config/agents.json`의 provider 값은 현재 사람이 운영하는
`docs/AI_COLLABORATION_POLICY.md`의 역할 분담(예: backend/frontend=Codex,
architecture/ui/review=Claude)을 참고 정보로 반영한 것이며, 이 매핑 자체가
자동 실행 권한을 주지는 않는다.

## Jev Adapter

```
DecisionEngine (interface)
  decideNextAction(goal, state) -> { decision, confidence, reason }
  checkCompletion(goal, state) -> { status, confidence, reason }
  evaluateRisk(goal, action)   -> { risk, reason }

MockDecisionEngine   — 결정론적, 네트워크 없음, 테스트/CI/오프라인 개발용
JevDecisionEngine    — 실제 Jev API, timeout+retry+fallback 내장
```

Business 로직(`Orchestrator`)은 항상 `DecisionEngine` 인터페이스만 참조하고
구체 클래스를 직접 알지 못한다. `src/decision/create-decision-engine.ts`가
환경변수(`JEV_API_URL` 유무)로 어떤 엔진을 쓸지 결정한다. `JEV_API_URL`이
없으면 자동으로 `MockDecisionEngine`을 쓰므로, Jev 구독이 없어도 오케스트레이터
전체를 개발·테스트할 수 있다.

## Jev 장애 대응

```
Jev 호출
  -> timeout(JEV_TIMEOUT_MS, 기본 10s) 또는 네트워크 오류
  -> 지수 backoff로 최대 JEV_MAX_RETRIES(기본 2)회 재시도
  -> 모두 실패하면 무한 재시도하지 않고 안전한 기본값으로 폴백:
     - decideNextAction 실패 -> decision: "human"
     - checkCompletion 실패  -> status: "human_review"
     - evaluateRisk 실패     -> risk: "critical" (Policy Engine이 반드시
       human approval을 요구하도록)
```

Jev API 장애가 프로젝트 전체를 멈추거나, 반대로 위험한 자동 진행을 허용하지
않도록 항상 "더 보수적인 쪽"으로 폴백한다.

## Hard Policy Layer

`PolicyEngine`은 `config/policies.json`의 키워드 규칙과 위험도 임계값을
읽어, Jev의 판단(`decision`)이나 낮은 위험도 평가와 무관하게 다음 범주가
목표/작업 설명에 매치되면 항상 `human_review`를 강제한다.

- 프로덕션 DB 파괴적 작업, force push, 프로덕션 배포, 시크릿/자격 증명 변경,
  결제·송금, 파괴적 마이그레이션, 의존성 메이저 업그레이드, 보안 설정 변경,
  전략 정책(손절가·수량 비율 등) 채택

`Orchestrator.step()`은 Jev 판단 직후, 실제 agent 실행 이전에 항상
`PolicyEngine.evaluate()`를 호출한다. Policy가 승인을 요구하면 그 턴은
무조건 `human_review`로 끝나며, 어떤 agent도 실행되지 않는다.

## Loop Protection

`OrchestratorLimits`(기본값 `DEFAULT_LIMITS`, `src/orchestrator/orchestrator.ts`):

- `maxSteps` — 전체 루프 한 번의 `run()`이 반복할 수 있는 최대 스텝 수
- `maxDecisionCalls` — Jev(또는 Mock) 호출 총 횟수 상한
- `maxAgentCalls` — 실제 agent 실행(성공/실패 포함) 총 횟수 상한
- `maxRetriesPerTask` — 같은 task id가 실패할 수 있는 최대 횟수. 초과하면
  자동으로 `human_review`로 전환되고 해당 task는 `blocked` 목록에
  `status: "human_review"`로 남는다(같은 task가 FE -> UI -> FE -> UI로
  무한히 도는 것을 방지).

## Context Builder

각 agent에게 저장소 전체나 전체 대화 이력을 넘기지 않는다. `AgentTaskInput`이
명시적으로 나열한 `relevantFilePaths`와 `artifactRefs`만 읽어 `BuiltContext`를
구성한다. 나열되지 않은 파일/artifact는 절대 포함되지 않으며, 나열됐지만
없는 파일은 조용히 건너뛴다(전체 빌드를 막지 않기 위함). 이 보장은
`.agent/test/context-builder.test.ts`가 검증한다.

## Artifact 중심 통신

Agent 간 결과 전달은 대화가 아니라 파일로 한다. `ArtifactManager`가
`.agent/artifacts/{research,architecture,ui,reviews,summaries}/` 아래에
파일을 쓰고 읽는다. `ProjectState.artifacts`는 `taskId -> artifactPath`
맵만 보관하고, 내용 자체는 상태 파일에 넣지 않는다.

## Project State / Decision Log

- `state/project.json` — `currentGoal`, `completed`, `active`, `blocked`,
  `artifacts`, `decisions`(id 목록), `stateVersion`, `updatedAt`. 각 항목은
  `TaskRecord`(요약 결과만 저장, 대화 내용 없음).
- `state/decisions.jsonl` — append-only. 각 줄이 하나의 `DecisionLogEntry`
  (timestamp, goal, decision, confidence, reason, stateVersion, risk).
  디버깅, 판단 이력 추적, 향후 비용/라우팅 개선 분석용.

두 파일 모두 `.agent/.gitignore`로 커밋 대상에서 제외한다(에이전트 로컬 실행
상태이며, PC 간 공유해야 하는 사실은 여전히 `docs/LEARNING_LOG.md`가 담당).

## Orchestrator 루프

```
load project state
  -> ask Jev (decideNextAction)
  -> evaluateRisk
  -> apply Hard Policy
  -> (human | complete 아니면) select agent via Router
  -> build minimal context (ContextBuilder)
  -> execute agent (AgentProvider) — dry-run에서는 생략
  -> collect result, save artifact reference
  -> update + persist project state
  -> (complete 결정 시) ask Jev completion
  -> repeat / complete / human_review / limit_reached
```

Jev는 판단만 하고 실행 책임을 갖지 않는다 — 실행은 항상 `Orchestrator`가
`Router`를 통해 선택한 `AgentProvider`에게 위임한다.

## Provider Adapter

```
AgentProvider (interface)
  execute(task, context) -> AgentResult

ClaudeProvider / CodexProvider / GeminiProvider
  현재는 모두 NotImplementedProvider를 상속 — 호출 시 명시적 에러를 던진다.
```

실제 provider 연동(예: Claude Agent SDK, Codex CLI, Gemini API 호출)은 이번
V0.1 범위가 아니다. 가짜로 성공 결과를 만들어내지 않는다(Fake integration
금지) — 실제 연동 전까지는 `dryRun()`으로만 라우팅을 검증한다.

## 한계

- 실제 Jev API 스펙이 없어 `JevDecisionEngine`의 요청/응답 필드는 가정이다.
  실제 연동 시 재검증이 필요하다.
- 실제 AgentProvider가 없어 `run()`/`step()`은 라우팅과 상태 관리만
  실제로 동작을 검증할 수 있고, 진짜 agent 실행은 아직 불가능하다.
- `PolicyEngine`은 단순 키워드 매칭이다. 정교한 분류가 필요해지면
  `config/policies.json`을 확장하되, 이 계층이 여전히 Jev보다 우선해야 한다는
  원칙은 유지해야 한다.

## 로드맵

- V0.2: Test Agent/Review Agent의 실제 provider, 병렬 실행, dependency graph
- V0.3: 고급 Context Builder, Artifact 검색, 토큰/비용 추적
- V0.4: Jev의 model/provider 라우팅(Task -> Role -> Provider/Model 선택)
- V0.5: Tool Guard, 자동 위험도 평가 고도화, CI 통합
