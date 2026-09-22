# Task: Review held-asset stop-loss UI

- Owner: Antigravity UI reviewer
- Mode: `antigravity-review`
- Write access: none

## Review scope

Review only `http://127.0.0.1:5174/strategy-guides#held-asset-strategy-profiles` after the new per-asset stop-loss controls were added.

Check 1280px, 768px, 430px, 390px, and 360px widths. Report only concrete, visible issues with a severity and exact selector or component reference.

## Must check

1. No horizontal overflow, clipping, overlap, or off-card buttons.
2. Field, select, and button dimensions are stable within a card.
3. The three states are understandable: asset override, portfolio default, and no stop-loss configured.
4. Error/success text has reserved space and cannot distort adjacent controls.
5. The long section title and explanatory copy remain readable without looking like a dense settings wall.
6. Do not click save, delete, or any interaction that changes local portfolio data.

## Output

Return a concise Korean audit. Do not modify files, run Git commands, create commits, or push.
