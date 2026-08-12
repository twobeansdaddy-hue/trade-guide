# Trade Guide Claude Code 리서치 협업 지침

Claude Code는 이 저장소에서 투자 전략 **리서치 전용**으로 사용한다.
애플리케이션 코드 구현과 통합은 Codex와 사용자가 담당한다.

## 시작 전 확인

다음 파일을 읽고, 현재 브랜치와 `git status`를 확인한다.

1. `AGENTS.md`
2. `docs/PROJECT_CONTEXT.md`
3. `docs/LEARNING_LOG.md`
4. `research/STRATEGY_ENGINE_POLICY.md`

## 절대 규칙

1. `research/**` 아래만 수정한다. `src/`, `docs/`, 설정 파일, Git 작업 흐름은 읽기 전용이다.
2. `Edit` 또는 `Write`가 훅에 의해 막히면 우회하지 말고 즉시 사용자에게 경로와 오류를 보고한다.
3. 커밋, 푸시, 브랜치 병합, 의존성 설치는 하지 않는다.
4. 리서치 결과에는 데이터 제공자, 주기, 가격 조정 방식, 기준일, confidence, caveats를 남긴다.
5. 기존 리서치 수치의 오류나 재현 불가를 발견하면 원본 수치를 조용히 바꾸지 않는다. 감사 기록을 추가하고 영향 범위를 설명한다.

## 산출물과 인계

- 리포트: `research/reports/<slug>.md`
- 구조화 데이터: 기존 스키마를 지키는 `research/data/*.json`
- 감사·재현 기록: `research/notes/`, `research/data/tools/`, `research/data/cache/`
- 정책 초안은 리포트에 제안할 수 있다. `STRATEGY_ENGINE_POLICY.md`의 실제 채택·구현은 사용자와 Codex의 검토 뒤에 진행한다.

작업이 끝나면 변경 파일, 핵심 결론, 한계, 구현에 필요한 결정 사항만 짧게 보고하고 다음 주제로 자동 진행하지 않는다.
