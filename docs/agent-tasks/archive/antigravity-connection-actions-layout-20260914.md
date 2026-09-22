# Agent Task Contract

## Identity

- Task ID: `antigravity-connection-actions-layout-20260914`
- Owner: `Antigravity CLI`
- Work mode: `scoped-implementation`
- Branch / worktree: `main / /Users/beansdaaddy/Desktop/_Study.nosync/Java/trade-guide`

## Outcome

Settings의 증권사 연결 카드에서 `다시 확인`, `자격 증명 갱신`, `연결 해제` 버튼이 화면 폭에 따라 어색하게 분리되지 않고 동일한 액션 그룹으로 정렬된다. 390px, 430px, 768px, 데스크톱 폭에서 텍스트 잘림과 가로 넘침이 없어야 한다.

## Allowed Files

- `frontend/src/App.css`

## Non-Goals And Guardrails

- React 컴포넌트, API, 데이터베이스, 인증, 증권사 연동, 전략 정책은 수정하지 않는다.
- `frontend/src/App.css` 외 파일은 수정하지 않는다.
- 커밋, 푸시, 병합, 의존성 설치를 하지 않는다.
- 768px 이하에서 위험 액션을 임의로 전체 폭에 배치하지 않는다. 세 버튼의 우선순위와 터치 가능성을 고려해 일관된 그룹으로 배치한다.

## Acceptance Checks

- [ ] 세 버튼이 768px, 430px, 390px에서 의도하지 않게 한 버튼만 별도 행으로 떨어지지 않는다.
- [ ] 긴 한국어 버튼 텍스트가 잘리지 않는다.
- [ ] 가로 스크롤, 버튼 겹침, 카드 높이 급증이 없다.
- [ ] `npm run lint`
- [ ] `npm run build`
- [ ] 데스크톱과 360px 이상 좁은 화면을 실제 렌더링으로 확인한다.

## Handoff

- Files changed:
- Verification run:
- API / data-model / policy impact: 없음
- Open decision or risk:
