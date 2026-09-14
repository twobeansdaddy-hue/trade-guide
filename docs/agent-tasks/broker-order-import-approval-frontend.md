# 증권사 주문 이력 승인 UI

- Owner: Antigravity UI implementation
- Work mode: scoped UI implementation
- Task ID: `broker-order-import-approval-frontend`

## Adopted backend contract

- `POST /api/members/{memberId}/portfolios/{portfolioId}/broker-order-imports/{runId}/approval`
- `DELETE /api/members/{memberId}/portfolios/{portfolioId}/broker-order-imports/{runId}/approval`
- 409 means no eligible order after the active opening-balance approval time, reconciliation mismatch, or replay validation failure. It must render as a safe blocked state, never as an unknown error.

## UI requirements

1. Keep the existing preview read-only until the user explicitly chooses approval.
2. Show the opening-balance baseline explanation and the count of eligible orders.
3. Never expose approval when reconciliation is mismatched or eligible count is zero.
4. Require an in-context confirmation before POST; state that it writes transaction records only and never sends a broker order.
5. On success, refresh the selected run/detail and show a reversible audit status. Revocation must use a separate explicit confirmation.
6. Keep loading/error/empty states stable: do not shift form controls or cause horizontal overflow at 360px, 768px, or desktop.
7. No backend, dependency, document, secret, Git, or API-contract changes. Do not commit or push.

## Verification

- `npm run lint`
- `npm run build`
- visual inspection at 360px, 768px, and desktop for no clipping, overflow, mismatched controls, or message-driven layout shifts
- report Korean summary and any backend contract gap
