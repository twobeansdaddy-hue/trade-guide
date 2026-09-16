# Task: Portfolio market-provider routing regression test

- Owner: Claude backend
- Mode: `claude-backend`
- Allowed write scope: `src/test/java/com/tradeguide/service/strategy/StrategyGuideServiceTest.java` only
- Forbidden: production Java, frontend, resources, documentation other than this task contract, Git changes, commits, pushes

## Goal

Add focused regression coverage proving that portfolio-scoped strategy signal generation uses the portfolio's configured candle provider instead of the context-free/default provider path.

The test must use `TOSS_SECURITIES` as the configured candle provider and verify:

1. `MarketHistoryService.getCandles(provider, portfolioId, market, ticker, interval, outputSize)` is called.
2. The context-free `getCandles(market, ticker, interval, outputSize)` overload is not called.
3. The provider-qualified cache key path is used.

Keep existing tests intact and do not alter runtime behavior. Run only the focused Gradle test first, then the full Gradle test suite if practical.

## Completion report

Report in Korean: test name, assertions, commands and result. Do not claim completion without an executed test.
