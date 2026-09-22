# Antigravity UI Task: broker action mobile stack spacing

## Target

- `frontend/src/App.css` only

## Reproduced defect

The tablet alignment fix is correct, but at 375px `/broker-accounts` step 1 still has an uneven vertical stack:

- `다시 확인` margin: `18px 0 0`
- `자격 증명 갱신` margin: `0`
- `연결 해제` margin: `18px 0 0`

The third button therefore has a 26px visual gap after the middle one, rather than the intended 8px stack gap.

## Change

Add one focused `max-width: 480px` override for only the direct controls in `.broker-connection-item-card .broker-connection-actions` to reset external button margins to zero. Keep the current 1-column full-width stack and 8px container gap.

## Constraints

- CSS only; do not alter TS, behavior, API, backend, docs, package files, or unrelated selectors.
- Do not use `!important`, a broad `.quiet-action` rule, or global margin reset.
- Do not alter desktop or 481px–1024px layout.
- Do not commit or push.

## Acceptance

- At 375px, all 3 controls are full-width, 42px high, zero external margins, and every adjacent top edge is exactly 50px apart (42px control + 8px gap).
- At 640px and 768px, the 3-column controls retain equal y positions.
- At desktop, inline controls remain unchanged.
- Run `npm run lint` and `npm run build`; report the actual rectangle/margin readings.
