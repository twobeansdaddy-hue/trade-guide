# Task: Persist premarket guide market-data provider provenance

- Owner: Claude backend
- Mode: `claude-backend`
- Allowed write scope:
  - `src/main/java/com/tradeguide/domain/strategy/PremarketGuideSnapshot.java`
  - `src/main/java/com/tradeguide/dto/strategy/PremarketGuideResponse.java`
  - `src/main/java/com/tradeguide/service/strategy/PremarketGuideService.java`
  - `src/main/resources/db/migration/V27__add_premarket_guide_market_data_provider.sql` (new)
  - `src/test/java/com/tradeguide/service/strategy/PremarketGuideServiceTest.java`
  - `src/test/java/com/tradeguide/controller/strategy/PremarketGuideControllerTest.java` only if required by response serialization coverage
- Forbidden: frontend, other production code, existing migrations, configuration, documentation, Git changes, commits, pushes

## Product requirement

A daily premarket guide must disclose the market-data provider actually selected for that portfolio at generation time. A later provider preference change must not rewrite the provenance of an existing snapshot.

## Implementation requirements

1. Persist the portfolio's `candleProvider` in each newly generated or force-regenerated snapshot.
2. Add a safe nullable database column through a new V27 migration. Do not alter V26.
3. Include a nullable provider identifier in `PremarketGuideResponse`. Existing snapshots created before V27 must remain readable.
4. Use the existing `MarketDataProvider` enum; do not create duplicated provider strings.
5. Add focused tests for the generated response/provenance and existing-snapshot compatibility.
6. Do not expose credentials or API keys.

## Verification

Run the focused relevant tests and the full Gradle test suite. Report changed files, test commands/results, and migration compatibility in Korean.
