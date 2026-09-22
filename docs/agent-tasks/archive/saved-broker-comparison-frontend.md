# 저장된 증권사 보유 종목 비교 화면 연결

- Owner: Claude frontend
- Scope: `frontend/src/**`

## 목표

설정 화면의 증권사 보유 종목 영역을 저장된 최신 스냅샷 기준으로 통합한다.
사용자가 명시적으로 갱신할 때만 증권사 API가 호출되고, 평상시 조회와 비교는 저장 데이터만 사용해야 한다.

## API 계약

- `GET /api/members/{memberId}/portfolios/{portfolioId}/broker-holding-snapshots/latest/comparison`
  - 저장된 최신 스냅샷과 Trade Guide 보유 종목 비교
  - 외부 증권사 호출 없음
  - 스냅샷이 없으면 404
- `POST /api/members/{memberId}/portfolios/{portfolioId}/broker-holding-snapshots`
  - 사용자가 `보유 종목 갱신`을 눌렀을 때만 호출
  - 저장 완료 후 비교 GET을 다시 호출해 최신 비교 결과 표시
- 기존 `POST .../broker-sync-preview`는 이번 화면 흐름에서 사용하지 않는다. 백엔드 API 자체는 삭제하지 않는다.

## 화면 계약

- `PortfolioBrokerLinkSection`의 기존 `보유 종목 미리보기` 버튼과 중복 비교 목록을 제거한다.
- `BrokerHoldingSnapshotSection`에서 저장 시각, 증권사명, 마스킹 계좌와 함께 비교 결과를 표시한다.
- 각 종목에 종목명, 시장/티커, 증권사 수량, Trade Guide 수량, 비교 상태를 표시한다.
- 404는 오류가 아니라 첫 사용 빈 상태로 표시한다.
- 갱신 실패 시 기존 비교 결과를 유지하고 오류만 안내한다.
- 포트폴리오 변경 시 이전 포트폴리오 데이터가 잠시 보이지 않아야 한다.
- `보유 종목 갱신` 버튼은 처리 중 중복 클릭을 막는다.
- 외부 API 자동 호출이나 자동 매매 기록 생성처럼 오해할 표현을 사용하지 않는다.
- 데스크톱과 360px 화면에서 넘침, 버튼 높이 불일치, 메시지로 인한 레이아웃 이동이 없어야 한다.

## 검증

- `npm run lint`
- `npm run build`
- 가능한 경우 브라우저에서 404 빈 상태, 저장 데이터 성공 상태, 갱신 오류 상태를 확인한다.

## 금지

- 백엔드, 문서, 시크릿, 커밋, 푸시를 수정하지 않는다.
