# Jev Orchestrator — 실행 방법 (V0.1)

전체 설계는 [`ARCHITECTURE.md`](./ARCHITECTURE.md) 참고.

## 설치

`.agent`는 루트/`frontend`와 분리된 독립 npm 패키지다. 정책상 Claude/Antigravity는
의존성을 설치하지 않으므로 사용자가 직접 실행한다.

```bash
cd .agent
npm install
```

## 환경 변수

`.env.example`의 `JEV_*` 변수를 참고한다. `JEV_API_URL`을 설정하지 않으면
자동으로 `MockDecisionEngine`이 사용되므로, Jev 없이도 아래 명령을 모두 실행할
수 있다.

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `JEV_API_URL` | (없음 → Mock 사용) | Jev API base URL |
| `JEV_API_KEY` | (없음) | Jev API 인증 토큰 |
| `JEV_TIMEOUT_MS` | 10000 | Jev 호출 타임아웃 |
| `JEV_MAX_RETRIES` | 2 | 타임아웃/오류 시 재시도 횟수 |

## 검증

```bash
npm run typecheck
npm test
```

## Dry Run

실제 agent를 실행하지 않고 라우팅 결과만 확인한다.

```bash
npm run cli -- run "회원가입 구현" --dry-run
```

`MockDecisionEngine`의 기본 스크립트(`research -> backend -> frontend`)를
기준으로 아래와 비슷한 출력이 나온다.

```
Goal
 └─ 회원가입 구현

Planned flow (dry-run, no agent executed)
  -> research: dry-run: would route to 'research' (no agent executed)
  -> backend: dry-run: would route to 'backend' (no agent executed)
  -> frontend: dry-run: would route to 'frontend' (no agent executed)
  -> COMPLETE: mock script exhausted
```

## Project State 확인/초기화

```bash
npm run cli -- state show
npm run cli -- state reset
```

## 실제 실행(`run` without `--dry-run`)에 대한 중요 안내

V0.1은 실제 `AgentProvider` 연동을 포함하지 않는다. `--dry-run` 없이 `run`을
실행하면 Jev(or Mock)가 어떤 role을 선택하든, 그 role에 매핑된 provider가
`NotImplementedProvider`이므로 실행 시점에 명시적인 에러를 던진다. 이는
의도된 동작이다(가짜 성공 결과를 만들지 않는다).

실제 agent 실행을 붙이려면:

1. `src/providers/claude-provider.ts` / `codex-provider.ts` /
   `gemini-provider.ts` 중 필요한 것을 실제 구현으로 교체한다(`AgentProvider`
   인터페이스만 지키면 된다).
2. CLI 또는 별도 스크립트에서 `Router`를 생성할 때 provider 이름 ->
   구현체 인스턴스 맵에 새 provider를 등록한다.
3. 여전히 `docs/AI_COLLABORATION_POLICY.md`의 작업 계약·허용 경로 규칙을
   따라야 한다 — 오케스트레이터가 이 규칙을 우회하지 않는다.

## Agent 추가하는 방법

1. `src/types.ts`의 `Role` 유니언에 새 역할을 추가한다.
2. `config/agents.json`에 `{ "provider": "...", "capabilities": [...] }`
   항목을 추가한다.
3. 필요하면 새 `AgentProvider` 구현체를 `src/providers/`에 추가하고
   `Router` 생성 시 provider map에 등록한다.
4. `MockDecisionEngine`의 스크립트나 실제 Jev 라우팅 정책이 새 역할을
   반환하도록 갱신한다.

## Provider(모델) 바꾸는 방법

역할과 provider는 `config/agents.json`에서만 연결된다. 예를 들어
`backend`의 provider를 `codex`에서 다른 이름으로 바꾸려면 이 파일의 값만
바꾸고, `Router` 생성 시 그 이름에 해당하는 `AgentProvider` 구현체를
provider map에 등록하면 된다. 오케스트레이터 코드는 수정하지 않는다.

## Jev 판단 작동 방식

`Orchestrator.step()`은 매 턴마다 `DecisionEngine.decideNextAction()`으로
다음 role(or `human`/`complete`)을 받고, `evaluateRisk()`로 위험도를 받는다.
그 다음 `PolicyEngine.evaluate()`가 Hard Rule이나 위험도 임계값 위반이
있는지 확인하며, 있으면 Jev의 판단과 무관하게 `human_review`로 강제
전환한다. `decision`이 `complete`이면 `checkCompletion()`을 한 번 더 호출해
정말 끝났는지 확인한다(`complete` vs `verify_more`).

## Human Approval 작동 방식

다음 중 하나라도 해당하면 그 턴은 실행 없이 끝나고 `human_review` 결과를
반환한다.

- `PolicyEngine`이 Hard Rule 또는 위험도 임계값으로 승인을 요구할 때
- Jev(or Mock)가 직접 `decision: "human"`을 반환할 때
- 같은 task id가 `maxRetriesPerTask`만큼 반복 실패했을 때

`human_review`로 끝난 task는 `ProjectState.blocked`에
`status: "human_review"`로 남아, 다음 실행에서도 사용자가 확인하기 전까지는
같은 상태로 유지된다.

## 한계

`ARCHITECTURE.md`의 "한계"·"로드맵" 절 참고.
