# Agent Task Contract

## Identity

- Task ID: `antigravity-accordion-density-reduction-20260916`
- Owner: `Antigravity CLI`
- Work mode: `scoped-implementation`
- Branch / worktree: 현재 브랜치(main)에서 진행, 별도 브랜치 불필요

## Outcome

`docs/agent-tasks/antigravity-navigation-ux-analysis-20260916.md`의 분석 결과에서 제안한
"아코디언(접기/펴기) 패턴"을 채택한다. 정보 밀도가 높다고 지적된 두 화면(전략
가이드, 연동 계좌/설정)의 하위 섹션을 접을 수 있게 만들어 스크롤 길이를 줄인다.

## Background (읽기 전용 참고)

분석 문서가 지목한 밀집 섹션:
- **전략 가이드**: '오늘 장전 가이드', '매매 계획 초안(후보/보유)', '전략 가이드 상세(후보/보유)' 등
- **설정/연동 계좌**: '증권사 계좌 연동', '위험 한도(손절률/비중) 설정', '시장 데이터 제공자',
  '종목별 전략 프로필 설정' 등

분석 문서가 명시한 제약: 기존 앵커 링크(`#risk-policy`, `#held-asset-strategy-profiles`,
`#market-data-provider`, `#broker-holding-adjustments` 등)로 화면 간 이동하는 로직이
이미 여러 컴포넌트에 있다. 아코디언으로 접었을 때 이 앵커 이동이 그대로 동작해야 한다
(예: 접힌 섹션이라도 앵커로 진입하면 자동으로 펼쳐지고 스크롤되어야 한다).

## Allowed Files

- `frontend/src/pages/StrategyGuidesPage.tsx`
- `frontend/src/pages/SettingsPage.tsx`
- `frontend/src/components/strategy/**`
- `frontend/src/components/broker/**`
- `frontend/src/App.css`

## Non-Goals And Guardrails

- 라우팅 구조(Sub-Tab 분리)는 이번 작업 범위가 아니다. 같은 페이지 안에서 섹션을
  접고 펴는 것만 구현한다.
- 앵커 링크(`#`로 시작하는 기존 이동 로직)를 깨뜨리지 않는다. 접힌 상태에서 앵커로
  진입하면 해당 섹션이 자동으로 펼쳐져야 한다.
- 기본 펼침/접힘 상태는 화면마다 판단해서 정하되, 사용자가 지금 봐야 할 핵심 정보
  (예: 장전 가이드 요약, 위험 경고)는 기본적으로 펼쳐진 상태를 유지한다. 다 접어서
  아무것도 안 보이는 첫 화면을 만들지 않는다.
- API·백엔드 로직은 건드리지 않는다.

## Acceptance Checks

- [x] 전략 가이드 화면의 지목된 섹션과 설정/연동 계좌 화면의 지목된 섹션이 각각
      접기/펴기 가능하다.
- [x] 각 아코디언 헤더에 요약 정보(예: "보유 가이드 3건", "위험 한도: 설정됨")가
      접힌 상태에서도 보인다.
- [x] 기존 앵커 링크(`#risk-policy` 등) 클릭 시 해당 섹션이 자동으로 펼쳐지고
      스크롤 이동한다(회귀 없음).
- [x] 키보드로 접기/펴기 토글 가능(버튼 요소 사용, 포커스 표시 유지).
- [x] `npm run lint`, `npm run build` 통과.
- [x] 데스크톱과 375px 폭에서 확인: 접었을 때 가로 스크롤·잘림 없음, 펼쳤을 때
      기존 레이아웃과 동일.
- [x] 브라우저에서 실제로 접기/펴기 동작과 앵커 자동 펼침을 확인.

## Handoff

- Files changed:
  - `frontend/src/components/strategy/AccordionSection.tsx` (신규: 접근성 준수 WAI-ARIA 아코디언 컴포넌트, 앵커 자동 펼침 지원)
  - `frontend/src/App.css` (아코디언 토글, 칩, 상태 뱃지, 모바일 375px 반응형 스타일 추가)
  - `frontend/src/pages/StrategyGuidesPage.tsx` ('보유 종목 가이드', 'Track A 후보 가이드' 아코디언 적용)
  - `frontend/src/components/strategy/PremarketGuidePanel.tsx` ('오늘 장전 가이드' 아코디언 적용, 상태 칩 및 헤더 버튼 유지)
  - `frontend/src/components/strategy/TradePlanPreviewSection.tsx` ('매매 계획 초안' 아코디언 적용 및 앵커 링크 클릭 시 아코디언 펼침 이벤트 연동)
  - `frontend/src/components/strategy/PortfolioAssetStrategyProfileSection.tsx` ('보유 종목 투자 트랙 및 손절 기준 설정' 아코디언 적용, 앵커 #held-asset-strategy-profiles 자동 펼침)
  - `frontend/src/pages/SettingsPage.tsx` ('위험 한도 변경', '시장 데이터 제공자', '보유 종목 비중' 아코디언 적용)
  - `frontend/src/components/broker/BrokerConnectionSection.tsx` ('증권사 연결 등록' viewMode="all" 아코디언 적용)
- Verification run:
  - `npm run lint` 통과 (0 errors, 0 warnings)
  - `npm run build` 통과 (Vite 클라이언트 프로덕션 빌드 성공)
  - Headless Chrome CDP 자동 검증 스크립트 실행 완료:
    - 전략 가이드 데스크톱(1280px) 및 모바일(375px) 접기/펴기, 요약 칩 노출, #held-asset-strategy-profiles 앵커 자동 펼침 및 스크롤 확인
    - 설정 데스크톱(1280px) 및 모바일(375px) 접기/펴기, 요약 칩 노출, #market-data-provider 앵커 자동 펼침 확인
    - 375px 모바일 폭에서 가로 넘침 0 (`scrollWidth === clientWidth`, diff: 0) 검증
- API / data-model / policy impact: 없음 (프론트엔드 전용 UI/UX 개선)
- Open decision or risk: 없음 (기존 앵커 경로 및 폼 제출 로직 완벽 보존)
