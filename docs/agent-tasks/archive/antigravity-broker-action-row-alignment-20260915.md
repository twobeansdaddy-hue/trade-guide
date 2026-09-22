# Antigravity UI Task: broker action row vertical alignment

## Target

- `frontend/src/App.css` only

## Reproduced defect

On `/broker-accounts`, step 1, at viewport widths 640px and 768px, the three connection controls are in one grid row with equal dimensions but are not vertically aligned. Direct browser measurement found:

- `다시 확인`: computed margin `18px 0 0`
- `자격 증명 갱신`: computed margin `0`
- `연결 해제`: computed margin `18px 0 0`

This causes the centre button to sit 9px higher than the two outer buttons despite each being 42px high.

## Change

Add a focused responsive override for `.broker-connection-item-card .broker-connection-actions` in the 481px–1024px range so all three direct child controls have zero external margin and share the same top edge. Preserve the existing three-column layout at tablet widths and one-column stack at <=480px.

## Constraints

- CSS only. Do not edit TypeScript, APIs, backend, docs other than this task file, package files, or any unrelated styles.
- Do not use `!important`, broad `.quiet-action` changes, or global resets; target only this button group.
- Preserve the existing 42px control height, safe destructive-button semantics, and all other dirty worktree edits.
- Do not commit or push.

## Observable acceptance

- At 640px and 768px: all three controls have equal `getBoundingClientRect().y`, 42px height, fully visible text, and no horizontal overflow.
- At 375px: controls remain a full-width, vertical stack.
- At desktop: existing compact inline controls remain unchanged.
- Run `npm run lint` and `npm run build`, and report the measured y positions per viewport.
