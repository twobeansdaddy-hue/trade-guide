# 포트폴리오 평가 응답 - 통화별 분리 반영 (프론트엔드)

> 담당: Antigravity. 허용 경로: `frontend/src/`. 백엔드 계약은 이미 변경·테스트·머지됐다.

## 배경

`docs/agent-tasks/toss-krw-order-history-ledger-design-20260916.md`에 따라 토스
국내(KRW) 주문을 원장에 반영하기 시작했다. 포트폴리오에 USD·KRW 보유 종목이 섞일
수 있게 되면서, `GET /api/members/{memberId}/portfolios/{portfolioId}/valuation`
응답의 평가 합계가 **평면 필드에서 통화별 맵으로 바뀌었다**(환율 변환 없음 - 정책
결정).

## 계약 변경 (Before → After)

Before:
```json
{
  "holdingValuations": [...],
  "totalPurchaseAmount": 1000.00,
  "totalMarketValue": 2105.00,
  "totalUnrealizedProfitLoss": 1105.00,
  "totalReturnRate": 110.50
}
```

After:
```json
{
  "holdingValuations": [...],
  "totalsByCurrency": {
    "USD": {
      "totalPurchaseAmount": 1000.00,
      "totalMarketValue": 2105.00,
      "totalUnrealizedProfitLoss": 1105.00,
      "totalReturnRate": 110.50
    },
    "KRW": {
      "totalPurchaseAmount": 350000.00,
      "totalMarketValue": 375000.00,
      "totalUnrealizedProfitLoss": 25000.00,
      "totalReturnRate": 7.14
    }
  }
}
```

- 키는 보유 종목이 실제로 있는 통화만 나타난다 - KR 종목이 하나도 없으면 `KRW` 키
  자체가 없다(빈 0 객체가 아니라 키가 없음).
- **서로 다른 통화 값을 더하거나 환율로 변환해 하나의 합계로 보여주지 않는다** - 이건
  임의 누락이 아니라 명시적 정책이다(환율 데이터 소스·변동 위험을 이번 범위에서
  다루지 않기로 결정).
- 근거: `src/main/java/com/tradeguide/dto/valuation/PortfolioValuationResponse.java`,
  `src/main/java/com/tradeguide/domain/valuation/CurrencyValuationTotals.java`.

## 변경이 필요한 파일

1. `frontend/src/types/portfolioValuation.ts` - `PortfolioValuation` 타입을
   `totalsByCurrency: Record<"USD" | "KRW", CurrencyValuationTotals>` 형태로 바꾼다
   (실제로 있는 키만 존재할 수 있으므로 `Partial<Record<...>>`가 더 정확하다).
2. `frontend/src/pages/DashboardPage.tsx` (70~72행) - `valuationResource.data.
   totalMarketValue` 등 평면 필드 참조가 전부 깨진다. USD 섹션을 지금처럼 기본
   표시하고, `totalsByCurrency.KRW`가 있으면 그 아래에 원화 합계를 별도 줄로
   추가한다(예: "USD $12,340 + KRW ₩5,200,000" 형태 - 하나의 숫자로 합치지 않음).
   `formatUsd`류 포맷 함수는 KRW에는 그대로 쓸 수 없으니 원화 포맷 함수를 새로
   만들거나 기존 통화 포맷 유틸이 있으면 재사용한다.
3. 같은 패턴을 쓰는 다른 화면이 있는지 확인한다(예: 보유 종목 화면에 총계를 표시하는
   곳이 있다면 동일하게 갱신).

## 검증 기준

- `npm run lint`, `npm run build` 통과.
- USD 보유 종목만 있는 포트폴리오: 기존과 동일하게 보이되 API 응답 파싱 경로만 바뀜
  (수동 확인: 브라우저에서 대시보드가 정상 표시되는지).
- KRW 보유 종목이 있는 포트폴리오(현재 로컬에 실제 데이터가 없다면 목/스텁 응답으로
  확인): USD·KRW 합계가 각각 별도 줄로 표시되고 서로 합쳐지지 않는지 확인.
- 이 작업은 화면 표시만 다룬다 - 전략 가이드·매매 계획 초안은 여전히 USD(미국 시장)
  종목만 대상이며 이번 작업에서 범위를 넓히지 않는다.
