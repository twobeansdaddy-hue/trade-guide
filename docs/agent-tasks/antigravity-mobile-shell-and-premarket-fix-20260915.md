# Task: Mobile shell and premarket layout fix

- Owner: Antigravity
- Mode: `antigravity-ui`
- Allowed write scope: `frontend/src/App.css` only
- Forbidden: component/TypeScript/API/backend/documentation/configuration/Git changes; commits and pushes

## Goal

Fix only the verified mobile layout defects from `antigravity-premarket-ui-audit-20260915`.

1. At 430px, 390px, and 360px, `/strategy-guides` and `/settings` must not create page-level horizontal scrolling.
2. The header and navigation must remain reachable at those widths. Use an intentional mobile navigation treatment; do not merely hide destinations.
3. `/strategy-guides` premarket guide metadata, statistic cards, and rows must wrap or stack without clipping.
4. `/settings` broker connection action buttons must remain fully visible and evenly sized after shell overflow is fixed.

## Verification required

- Inspect live pages at 1280px, 768px, 430px, 390px, and 360px.
- Confirm `document.documentElement.scrollWidth <= window.innerWidth` for `/strategy-guides` and `/settings` on the three mobile widths.
- Do not regress the existing desktop/tablet broker action layout.
- Run `npm run lint` and `npm run build`.

## Completion report

In Korean, report changed selectors, viewport evidence, commands run, and any remaining limitation. Do not claim completion without the viewport checks.
