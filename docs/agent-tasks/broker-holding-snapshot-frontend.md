# 증권사 보유 종목 스냅샷 화면 연동

- Task ID: pending
- Owner: Claude frontend
- Scope: `frontend/src/**`
- Branch: current `main`

## 목표

연결된 증권사 계좌의 보유 종목을 사용자가 명시적으로 갱신해 읽기 전용 스냅샷으로 저장하고,
마지막으로 저장된 결과를 다시 외부 호출 없이 조회한다.

## 확정 백엔드 계약

- `POST /api/members/{memberId}/portfolios/{portfolioId}/broker-holding-snapshots`
  - 증권사에 한 번 조회하고 새 스냅샷을 저장한 뒤 응답한다.
- `GET /api/members/{memberId}/portfolios/{portfolioId}/broker-holding-snapshots/latest`
  - 저장된 최신 스냅샷만 조회한다. 증권사 호출을 유발하지 않는다.
- 두 응답의 형태:
  - `provider`, `brokerConnectionId`, `maskedAccountNumber`, `syncedAt`,
    `unsupportedMarketCount`, `items[]`
  - 각 `items[]`: `market`, `ticker`, `displayName`, `quantity`,
    `averagePurchasePrice`
- 최신 스냅샷이 없을 때의 `404`는 정상적인 첫 사용 빈 상태다. 오류 경고로 보여서는 안 된다.
- 연결되지 않았거나 연결 검증이 필요하면 현재의 명확한 오류 처리 흐름을 유지한다.

## 구현 요구사항

1. 현재 증권사 계좌 기준 화면에서 진입할 수 있게 한다.
2. 최초 진입 시에는 최신 저장본만 불러오며, 증권사 API를 자동 호출하지 않는다.
3. 사용자가 누르는 `보유 종목 갱신` 버튼만 POST를 호출한다. 처리 중 중복 클릭을 막는다.
4. 응답에는 증권사명, 마스킹 계좌, 저장 시각, 지원하지 않는 시장 수, 종목명과 티커,
   수량과 평균 매입가를 일관된 UI로 표시한다.
5. 저장본이 없는 경우에는 빈 상태와 갱신 버튼을 표시한다.
6. 기존의 `증권사 보유 종목 미리보기`와 Trade Guide 보유 종목은 비교용으로만 남는다.
   스냅샷 저장이 매매 기록이나 Trade Guide 보유 종목을 자동 변경한다는 표현이나 동작을 추가하지 않는다.
7. 기존 디자인 토큰과 공통 버튼·입력 높이를 사용한다. 데스크톱과 360px에서 가로 스크롤,
   잘림, 겹침, 메시지로 인한 레이아웃 이동이 없어야 한다.

## 검증

- `npm run lint`
- `npm run build`
- 데스크톱과 360px 뷰포트에서 빈 상태, 갱신 중, 성공, 오류 상태를 직접 확인한다.

## 금지

- `src/**`, `docs/**` 중 이 작업 파일 이외의 파일, 환경 변수, 시크릿, API 키를 수정하지 않는다.
- 새 UI 라이브러리를 추가하지 않는다.
- 커밋하거나 푸시하지 않는다.
