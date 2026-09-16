# Task: Add held-asset stop-loss override controls

- Owner: Claude frontend
- Mode: `claude-frontend`
- Allowed write scope:
  - `frontend/src/api/portfolioAssetRiskOverrideApi.ts` (new)
  - `frontend/src/types/portfolioAssetRiskOverride.ts` (new)
  - `frontend/src/components/strategy/PortfolioAssetStrategyProfileSection.tsx`
  - `frontend/src/App.css`
- Forbidden: backend, migrations, other frontend source files, documentation, configuration, Git changes, commits, pushes

## Product requirement

Users set a review-only stop-loss percentage for an individual held asset on the strategy-guide page. That asset-specific percentage overrides the portfolio default for held-asset guide generation. Removing it returns the asset to the portfolio default. This never creates an order or enables automatic trading.

## API contract

- `GET /api/members/{memberId}/portfolios/{portfolioId}/asset-risk-overrides`
- `PUT /api/members/{memberId}/portfolios/{portfolioId}/assets/{market}/{ticker}/risk-override`
  with `{ "stopLossRatio": 0.05 }`
- `DELETE /api/members/{memberId}/portfolios/{portfolioId}/assets/{market}/{ticker}/risk-override`

## UI requirements

1. Integrate only into the existing held-asset strategy-profile cards. Rename the section copy if necessary so both track and stop-loss settings are understandable in the same card.
2. For every asset, visibly distinguish:
   - asset-specific percentage configured;
   - no asset-specific value, therefore portfolio default is used if configured;
   - no configured stop-loss guidance.
3. Accept the user-facing value as a percent (`5` means `0.05`), with client validation `0 < value < 100`; send the API decimal ratio.
4. A configured asset gets a clear `기본값 사용` action that calls DELETE. Do not show destructive styling or a confirmation modal: this is a non-destructive reset to the portfolio default.
5. Keep track-setting controls working without behavior changes.
6. Reserve vertical feedback space per control group so success/error text cannot shift adjacent fields or create unequal/overlapping cards. All fields, select controls, and buttons must have stable dimensions.
7. Desktop controls may form a compact two-column grid. At 768px and below, stack controls and make action buttons full-width. Ensure 360, 390, and 430px have no horizontal overflow, clipping, or button overlap.
8. Use Korean user-facing text. Do not reveal raw API errors when a clear Korean fallback is possible.

## Verification

Run `npm run lint`, `npm run build`, and report changed files and results in Korean. Do not click a save/delete action against a real local portfolio while verifying.
