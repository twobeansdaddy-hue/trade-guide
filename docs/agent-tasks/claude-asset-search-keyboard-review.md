# Claude Task: 종목 검색 키보드 UX 검토

## 목적

`frontend/src/components/asset/AssetSearchInput.tsx`의 종목 검색 결과를 키보드만으로
탐색하고 선택할 수 있도록 개선하기 전, 현재 화면과 기존 디자인 문서를 기준으로
필요한 UX·접근성 요구사항을 검토한다.

## 검토 범위

- `frontend/src/components/asset/AssetSearchInput.tsx`
- `frontend/src/App.css`의 `.asset-search*` 규칙
- `frontend/src/pages/TradeTransactionEntryPage.tsx`의 사용 맥락
- `docs/design/ASSET_SEARCH_FEEDBACK_REVIEW.md`

## 확인할 질문

1. 입력 중 결과가 있을 때 `ArrowDown`, `ArrowUp`, `Enter`, `Escape`가 각각 어떻게
   동작해야 하는가?
2. 선택 후 포커스가 어디로 돌아가야 하는가?
3. 검색 결과가 없거나 오류일 때 키보드 동작은 어떻게 제한해야 하는가?
4. 현재 제품 규모에 적절한 최소 ARIA 속성 및 포커스 처리 방법은 무엇인가?
5. 구현 범위를 불필요하게 복잡하게 만들 수 있는 고급 combobox 패턴은 무엇이며,
   이번 슬라이스에서 제외해도 되는가?

## 제약

- `frontend/**`, `src/**`, 설정 파일, API 계약, 데이터 모델은 수정하지 않는다.
- `docs/design/ASSET_SEARCH_KEYBOARD_REVIEW.md`만 새로 작성한다.
- 자동 주문, 투자 전략, 종목 데이터, 인증 정책을 제안하거나 수정하지 않는다.
- 커밋·푸시·PR 생성·병합하지 않는다.

## 산출물

`docs/design/ASSET_SEARCH_KEYBOARD_REVIEW.md`에 다음을 기록한다.

- 권장 키보드 동작 표
- 필요한 최소 접근성 계약
- P0/P1/P2 우선순위
- 구현 시 주의할 회귀 위험
- 수동 검증 시나리오
