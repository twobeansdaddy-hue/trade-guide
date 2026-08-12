# Claude 리서치 작업 환경

## 역할 분리

- **Codex와 사용자**: Java/Spring 구현, 테스트, 문서 통합, 커밋·푸시·PR
- **Claude Code**: 투자 전략 리서치. `research/**` 아래 결과만 작성

이 구분은 학습용 구현을 보호하고, 리서치의 근거·한계를 코드 변경과 분리하기 위한 것이다.

## Claude 작업 시작

1. 작업 트리가 깨끗한지 확인한다.
2. 리서치 전용 브랜치를 최신 `main`에서 만든다. 예: `research/entry-delay-review`
3. Claude Code 세션은 **그 리서치 브랜치가 체크아웃된 동일한 폴더**에서 시작한다.
4. Claude에게 `CLAUDE.md`, `AGENTS.md`, `docs/PROJECT_CONTEXT.md`, `docs/LEARNING_LOG.md`, `research/STRATEGY_ENGINE_POLICY.md`를 먼저 읽도록 요청한다.

중요: Claude 세션이 A 폴더에서 실행 중일 때 B 폴더의 worktree에 쓰도록 요청하지 않는다. 훅의 경로 검증과 실제 작업 폴더가 달라져 정상적인 `research/**` 수정도 차단될 수 있다.

## 리서치 종료와 개발 반영

1. Claude는 변경 파일과 결론·한계를 보고한다.
2. 사용자가 diff를 검토하고 리서치 커밋과 PR을 만든다.
3. 리서치 PR을 `main`에 병합한다.
4. Codex는 최신 `main`에서 새 `feature/...` 브랜치를 만들고, 채택된 정책만 테스트와 구현에 반영한다.

리서치 결과가 곧바로 실행 전략은 아니다. `research/STRATEGY_ENGINE_POLICY.md`의 채택 여부와 구현 테스트를 거친 항목만 엔진에 반영한다.

## 오류 대응

- Claude 훅이 정상적인 `research/**` 수정을 막으면 Bash나 다른 도구로 우회하지 않는다.
- Claude는 오류 메시지와 대상 경로를 사용자에게 보고한다.
- Codex가 훅·문서·브랜치 구성을 점검하고 수정한 뒤 새 Claude 세션에서 다시 시도한다.
