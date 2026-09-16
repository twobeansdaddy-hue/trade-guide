# Task: Add portfolio asset stop-loss overrides

- Owner: Claude backend
- Mode: `claude-backend`
- Allowed write scope:
  - `src/main/java/com/tradeguide/domain/risk/`
  - `src/main/java/com/tradeguide/repository/risk/`
  - `src/main/java/com/tradeguide/dto/risk/`
  - `src/main/java/com/tradeguide/controller/portfolio/PortfolioController.java`
  - `src/main/java/com/tradeguide/service/strategy/PortfolioStrategyGuideService.java`
  - `src/main/resources/db/migration/V28__add_portfolio_asset_risk_overrides.sql` (new)
  - `src/test/java/com/tradeguide/service/strategy/PortfolioStrategyGuideServiceTest.java`
  - `src/test/java/com/tradeguide/controller/portfolio/PortfolioControllerTest.java` only if required
- Forbidden: frontend, broker integration, existing migrations, global asset profiles, strategy signal rules, configuration, documentation other than this task contract, Git changes, commits, pushes

## Product requirement

The existing portfolio-level stop-loss percentage is a default. A user must be able to set a different, review-only stop-loss percentage for one asset in one portfolio. For a holding guide, the per-asset value wins when present; otherwise the portfolio default continues unchanged. This is not an automatic stop-loss recommendation and must never send an order.

## Design requirements

1. Model this as a portfolio-scoped asset risk override keyed by `portfolio + market + ticker`; do not alter `AssetProfile` or `AssetListing`.
2. Store a nullable-equivalent optional `stopLossRatio` with the same valid interval as the existing policy: `0 < ratio < 1`.
3. Add V28 only. Never edit V26 or V27.
4. Add clear REST operations under the existing member/portfolio ownership boundary:
   - list current overrides;
   - create or replace one asset override;
   - delete one asset override to return to the portfolio default.
5. Keep response identifiers structured (`market`, `ticker`) and return the ratio without converting it to a percentage string.
6. Update only held-asset strategy-guide calculation to resolve `asset override -> portfolio default -> not configured`. Candidate guides keep their existing portfolio-default behavior.
7. Do not change Track A signal/BUY/HOLD/SELL policy, infer a recommendation, or invoke external APIs.
8. Preserve safe behavior for old portfolios with no overrides.

## Verification

Add focused tests proving fallback, per-asset precedence, and deletion/reset behavior. Run focused tests and the complete Gradle suite. Report changed files and results in Korean.
