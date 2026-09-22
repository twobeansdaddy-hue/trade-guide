# Antigravity UI Task: responsive guide metadata and broker action controls

## Target

- `frontend/src/App.css`
- `frontend/src/components/strategy/PremarketGuidePanel.tsx`
- `frontend/src/components/broker/BrokerConnectionSection.tsx`

## Change

Fix two directly reproduced responsive-layout defects without changing API or business behavior.

1. In the pre-market guide metadata, keep the status pill and regular metadata text visually distinct and aligned at narrow/intermediate widths. The status pill must retain its intrinsic width; adjacent dates/provider metadata may wrap as separate readable units rather than causing uneven baseline or awkward inline fragments.
2. In the registered broker connection card, make the three controls (`다시 확인`, `자격 증명 갱신`, `연결 해제`) stable across widths. At intermediate widths they must either form equally sized columns with no overflow, or deliberately wrap to a coherent full-width stack. Do not leave a partially wrapped/control-width mismatch.
3. Add only short, visible helper copy near the existing risk-override UI when appropriate: the portfolio default stop-loss ratio applies first; a per-asset override is optional. Do not alter save behavior, API calls, or default values.

## Constraints

- UI/CSS only. Do not modify `src/**`, API modules, routes, data types, backend, migrations, package files, or tests.
- Keep the existing visual language and Korean copy. No redesign, new libraries, hard-coded viewport-specific content, or unrelated CSS cleanup.
- Do not use `!important` unless it is strictly required to override an existing conflicting selector. Prefer a focused selector with stable grid/flex constraints.
- Do not alter the semantics or destructive confirmation behavior of connection removal.
- The working tree is already dirty. Touch only the three target files and preserve unrelated edits.
- Do not commit or push.

## Acceptance

- Run `npm run lint` and `npm run build`.
- Inspect `/strategy-guides` at 375px, 640px, 768px, and desktop width. No horizontal overflow; the pre-market status pill does not stretch or collide with metadata, and each metadata item wraps coherently.
- Inspect `/broker-accounts` at the same widths. Buttons are fully visible, equal-height within a row, and have no clipping, overlap, or half-column layout.
- Report exact edited files and the viewport evidence. Report failure rather than claiming success if a check cannot be run.
