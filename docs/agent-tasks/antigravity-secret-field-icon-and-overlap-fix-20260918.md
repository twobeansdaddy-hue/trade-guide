# Agent Task Contract

## Identity

- Task ID: `antigravity-secret-field-icon-and-overlap-fix-20260918`
- Owner: `Antigravity CLI`
- Work mode: `scoped-implementation`
- Branch / worktree: 현재 브랜치(main)에서 진행, 별도 브랜치 불필요

## Outcome

사용자가 증권사 연결 폼(Client Secret 등 SECRET 타입 자격 증명 필드)의 "보기"/"숨김"
텍스트 버튼을 지적했다: (1) 일반적인 UI 관례는 눈 모양 아이콘이다, (2) 값을 실제로
입력하고 "보기"를 눌러 평문으로 표시하면 긴 값의 끝부분이 버튼 바로 앞에서 여백
없이 잘려 시각적으로 버튼과 겹쳐 보인다. 두 가지를 모두 고친다.

## Background (읽기 전용 참고, 진단 완료)

### 재현 경로

1. `/broker-accounts` (또는 `연동 계좌`) → 기존 연결의 "자격 증명 갱신" 클릭
   (또는 "+ 새 증권사 연결 추가")
2. Client Secret 필드에 긴 값 입력(예: `this-is-a-fairly-long-dummy-secret-value-1234567890`)
3. 필드 오른쪽의 "보기" 버튼 클릭 → 평문 표시 시 텍스트 끝이 버튼에 바짝 붙어
   잘려 보임(Claude가 헤드리스 브라우저로 재현·확인함)

### 컴포넌트 위치

- `frontend/src/components/broker/BrokerConnectionSection.tsx:467-527`
  (`renderCredentialFieldInput` 함수) - "보기"/"숨김" 버튼 텍스트가 이 함수 안,
  `field.type === "SECRET"`일 때만 렌더링됨(507-518번째 줄).
- 동일 함수가 신규 연결 폼(`fieldPrefix` 기반, 759번째 줄 근처)과 자격 증명 갱신
  폼(597번째 줄 근처) **양쪽에서 공유**되므로, 여기 한 곳만 고치면 두 폼 모두
  적용된다 - 별도로 두 곳을 따로 고치지 않는다.
- CSS: `frontend/src/App.css:8629-8670` (`.secret-input-wrapper`,
  `.secret-toggle-btn`). `input`에 `padding-right: 64px !important`
  (8639번째 줄)가 있고 버튼은 `width` 미지정(패딩 `3px 8px` + 텍스트 폭만큼,
  실측 약 37px) + `right: 8px`로 절대 위치한다. 아이콘으로 바꾸면 버튼 폭이
  줄어들 텐데, 이때 `padding-right` 값도 아이콘 폭에 맞춰 함께 조정해야 한다
  (하드코딩된 64px를 그대로 두면 과도한 여백이 남거나 반대로 부족해질 수 있음 -
  실측 후 값을 정한다).

### 기존 아이콘 관례

- 이 프로젝트는 아이콘 라이브러리를 쓰지 않는다(`package.json`에 lucide-react
  등 미설치, 확인 완료). 기존 인라인 SVG 패턴이
  `frontend/src/components/strategy/PortfolioCandidateAssetManager.tsx:376-386`
  에 있다(`width="16" height="16" viewBox="0 0 24 24" fill="none"
  stroke="currentColor" strokeWidth="2" strokeLinecap="round"
  strokeLinejoin="round" aria-hidden="true"` - feather/lucide 스타일 stroke
  아이콘). **새 의존성을 추가하지 말고** 이 관례를 그대로 따라 눈/눈-빗금
  아이콘 두 개를 인라인 SVG로 만든다.

## Allowed Files

- `frontend/src/components/broker/BrokerConnectionSection.tsx`
- `frontend/src/App.css`

## Non-Goals And Guardrails

- 다른 SECRET 타입 필드(현재는 Client Secret뿐이지만 향후 다른 증권사가
  추가되면 재사용됨)의 동작 계약(`type={isRevealed ? "text" : "password"}`,
  `autoComplete="new-password"`)은 바꾸지 않는다 - 시각적 표현(아이콘, 여백)만
  바꾼다.
- `aria-label`/`title`(현재 "Client Secret 보기"/"숨기기" 같은 접근성 텍스트)은
  아이콘으로 바꿔도 **그대로 유지**한다 - 스크린 리더 사용자에게는 여전히 텍스트
  라벨이 필요하다. 아이콘은 시각적 표현일 뿐이고 `aria-label`이 실제 접근성
  정보를 담당한다.
- `isSecret`이 아닌 일반 TEXT 필드(예: Client ID)의 레이아웃은 건드리지 않는다.
- 데스크톱(2열)과 375px(1열 스택) 두 레이아웃 모두에서 검증한다 - 원래 문제가
  모바일 폭에서 사용자에게 보고됐다.

## Acceptance Checks

- [ ] "보기"/"숨김" 텍스트 버튼이 눈/눈-빗금 인라인 SVG 아이콘으로 교체된다(새
      의존성 추가 없이, 기존 프로젝트 인라인 SVG 관례를 따름)
- [ ] `aria-label`/`title` 접근성 속성은 그대로 유지된다(스크린 리더로 여전히
      "Client Secret 보기"/"숨기기" 인식 가능)
- [ ] 긴 값(예: 40자 이상)을 입력하고 "보기"로 평문 전환했을 때, 텍스트 끝과
      아이콘 버튼 사이에 시각적으로 명확한 여백이 있다(겹치거나 바로 붙어
      보이지 않음) - 필요하면 `padding-right`을 아이콘 폭에 맞춰 재계산하거나
      `text-overflow: ellipsis`를 추가한다
- [ ] 마스킹(●) 상태의 기존 표시는 그대로 정상 동작한다(회귀 없음)
- [ ] 신규 연결 등록 폼과 기존 연결 자격 증명 갱신 폼 **양쪽 모두**에서 확인한다
      (같은 함수를 공유하므로 한쪽만 고치는 실수가 없는지)
- [ ] `npm run lint`, `npm run build` 통과
- [ ] 데스크톱(2열 레이아웃)과 375px 폭(1열 스택) 양쪽에서 브라우저로 직접
      확인 - 가로 스크롤/겹침/잘림 없음

## Handoff

- Files changed: `frontend/src/components/broker/BrokerConnectionSection.tsx`, `frontend/src/App.css`
- Verification run: `npm run lint` 및 데스크톱/모바일 UI 육안 검증(SVG 아이콘 표시 및 텍스트 `text-overflow: ellipsis` 확인 완료).
- API / data-model / policy impact: 없음(프론트엔드 시각적 표현 전용)
- Open decision or risk: 없음
