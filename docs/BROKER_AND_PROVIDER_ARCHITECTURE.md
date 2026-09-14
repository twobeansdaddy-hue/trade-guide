# Broker And Market Data Provider Architecture

## Purpose

Trade Guide must let an authenticated member choose the source of market data and,
when supported, connect a personal brokerage account for read-only data. These are
different concerns and must not share one provider setting.

- A **market-data provider** supplies prices, candles, symbol information, and market
  schedules used by valuation and strategy calculations.
- A **broker connection** represents one member's credentials for one securities
  provider and may expose accounts, holdings, and transaction history.

The product never sends orders, conditional orders, or reservation orders to a broker.

## Product Decisions

### 1. Authentication is mandatory in the public service

Broker connections, provider preferences, portfolio valuation, risk alerts, and
strategy guides are personal financial information. In a deployed service:

- `tradeguide.auth.enabled` must be `true`.
- Every member-scoped request must resolve the logged-in member and verify ownership.
- The browser derives its member context from `GET /api/auth/me`; it must not treat a
  freely editable member id as identity.
- The current disabled-auth local profile remains a development convenience only and
  must never be used for a public deployment.

Google OIDC is the initial application-login method. It authenticates the Trade Guide
member; it does not authenticate that member to a broker.

### 2. Provider selection is not a free-text API setting

The backend owns a fixed provider catalog. A member selects only from providers that
the backend advertises as available for the relevant market and capability.

Initial catalog:

| Provider | Kind | Credentials | Intended use | Initial status |
| --- | --- | --- | --- | --- |
| `TWELVE_DATA` | Market data | Service-managed key | US prices, candles, asset search | Available |
| `TOSS_SECURITIES` | Broker and market data | Member-owned broker connection | Read-only account, holdings, transactions, KR/US market data where supported | Design only |
| `YAHOO_FINANCE` | Research data | No user key in this service | Research and backtest comparison | Not selectable for production |

Yahoo Finance must not become a production fallback merely because a public endpoint is
reachable. Its terms, reliability, and commercial-use suitability require a separate
approval. It may remain a research input without being a user-facing data source.

### 3. Preferences belong to a portfolio, credentials belong to a member

One member can own several portfolios with different purposes. Therefore a market-data
choice is stored as a **portfolio preference**, while a broker credential set belongs to
the **member** who owns it.

Proposed persisted model:

```text
Member
  -> BrokerConnection (0..n)
       -> BrokerConnectionSecretValue (1..n, one row per credential field,
                                       encrypted and never returned)
       -> BrokerSyncRun (0..n, audit and status only)
  -> Portfolio (0..n)
       -> PortfolioMarketDataPreference (0..1)
       -> PortfolioBrokerConnection (0..n)
```

`PortfolioBrokerConnection` is a link, not a field on `Portfolio`, so a portfolio can
aggregate more than one linked broker account later. The first release may permit only
one active link, enforced by service validation, without making that restriction part of
the database shape.

### 4. Do not silently mix provider data

`PortfolioMarketDataPreference` records a primary provider separately for:

- `PRICE` - valuation and exposure/risk alerts
- `CANDLE` - strategy calculations and backtests
- `ASSET_REFERENCE` - name, market, currency, and listing-state lookup

The v1 UI may present these as one simple "market data provider" choice only when all
three point to the same provider. The expanded fields prevent an accidental future mix
such as Twelve Data candles with a broker price while displaying one unnamed source.

Automatic fallback is disabled in v1. A provider failure is shown with its provider name
and retry guidance. Cross-provider fallback changes data provenance and must be an
explicit future policy with timestamp and source disclosure.

## Broker Credential Security

### Stored data

`BrokerConnection` contains only non-secret operational metadata:

- provider type, connection status, display label, masked account label
- encrypted external account reference or a non-reversible lookup hash where lookup is
  needed
- last verification and last successful sync time
- created, updated, disconnected timestamps

`BrokerConnectionSecretValue` (`broker_connection_secret_values`) holds one row per
credential field and contains only encrypted values:

- `field_key` — the same key the provider declares in `BrokerCredentialField.key`
- `ciphertext`, `initialization_vector`, `encryption_key_version`
- unique on `(broker_connection_id, field_key)`

Credentials are rows, not columns, because the number of credential fields differs per
provider. A second broker that needs a third field changes no DTO, no schema, and no
adapter signature. `field_key` carries no foreign key — the provider schema is an enum,
not a table — so the service validates every incoming key against
`BrokerProvider.getCredentialFields()` before writing. Without that whitelist a client
could invent keys and use the table as a free dictionary.

The key version is stored **per value**, not per connection, so a rotation can move one
field at a time instead of requiring every field to change at once.

The account sequence/reference stays on `BrokerAccount`, not here: it identifies an
account rather than authenticating the caller, and adapters receive it as a separate
argument from `BrokerCredentials`.

Decryption happens in exactly one place, `BrokerCredentialLoader`. Plaintext exists only
between that loader and the adapter call. `BrokerCredentials.toString()` prints key names
only, because logging is the most common way credential values escape.

`broker_connection_secrets` (the earlier fixed two-column table) is superseded by V17,
which copies the existing ciphertext across without re-encrypting it — a migration that
needed the encryption key would block startup in any environment that lacks it. The old
table was meant to be left in place for one release as rollback headroom and dropped in a
later migration, but as of V18 (`broker_reconciliation_runs`, unrelated to this table) it
has not been dropped yet — no migration drops it. The application reads and writes only
the new table from V17 onward; writing both would leave no answer to which one is true.
Dropping the legacy table is tracked as a separate, still-undecided migration
(`docs/agent-tasks/broker-credential-encryption-key-rotation.md` §1 decision 4).

Raw client ids, client secrets, bearer tokens, account numbers, and decrypted account
references are never returned by an API, logged, put in exception messages, or written
to test fixtures. Access tokens are short-lived runtime values only; they are not
persisted in the initial design.

### Encryption boundary

The application uses authenticated encryption such as AES-256-GCM, with a unique nonce
per encrypted value. The encryption key is not stored in PostgreSQL or the repository.

- Local development: an explicit local-only encryption key environment variable may be
  used with non-production test credentials.
- Production: use a managed key/secret service or a separate key-management boundary,
  support key versioning, and rotate by decrypting and re-encrypting each secret.
- A database backup alone must not be sufficient to decrypt broker credentials.

**Key rotation is implemented and is deliberately offline-only.** `AesGcmBrokerCredentialCipher`
holds a version-keyed map of secret keys (`tradeguide.broker.encryption-keys`) plus a
"current" version (`tradeguide.broker.encryption-current-version`); it decrypts using the
version stored on the row (`BrokerConnectionSecretValue.encryption_key_version`,
`BrokerAccount.encryption_key_version`) and always encrypts new values with the current
version. `BrokerKeyRotationService`/`BrokerKeyRotationBatchProcessor` page through both
tables in ascending id order, decrypt each row with its stored version, re-encrypt with the
current version, and apply the result with a conditional `UPDATE ... WHERE id = ? AND
encryption_key_version = ?` so a concurrent user write (credential replacement, account
re-verification) is skipped rather than overwritten. Batches commit independently
(`REQUIRES_NEW`), and `broker_key_rotation_runs` is the sole audit record (counts and a
fixed reason code only, never ciphertext or plaintext) with a partial unique index that
allows at most one `IN_PROGRESS` run at a time. The only trigger is
`tradeguide.broker.rotation.run-on-startup` (default `false`) read by
`BrokerKeyRotationRunner`, an `ApplicationRunner` an operator enables for one offline
application start; there is no HTTP endpoint, scheduler, or frontend path to it. See
`docs/agent-tasks/broker-credential-encryption-key-rotation.md` for the full design and
`docs/BROKER_KEY_ROTATION_OPERATIONS.md` for operator preconditions and steps (Korean).

Application-level encryption is necessary even when database disk encryption exists:
database access, a copied backup, and application key access must not be the same
security boundary.

### User interface rules

The connection screen is available only after application login. It shows provider
capabilities and connection state, never saved values.

1. User selects a provider from the catalog.
2. The UI renders only the fields required by that provider.
3. A credential submission is sent once over HTTPS to a dedicated authenticated endpoint.
4. The response contains a masked account label, status, and verification time only.
5. Users can verify, disconnect, and replace a connection. Disconnect deletes encrypted
   secrets and invalidates in-memory tokens.

For Twelve Data, ordinary users do not enter the service key. They select the available
provider only. Toss requires a member-owned connection. Yahoo is shown as unavailable
for production until separately approved, with an explanation rather than a broken form.

## Toss Securities Boundary

The current [official Toss Securities OpenAPI specification](https://openapi.tossinvest.com/openapi-docs/latest/openapi.json)
uses client-credentials authentication, issues an access token, and requires an account
header for account and asset data. It also documents an allowed-IP restriction. This
means a single application-wide Toss credential represents one brokerage client/account
context; it must not be presented as multi-user account access.

For a personal beta, one authenticated member may connect their own Toss credential set
after explicit setup. For a public multi-user service, do not launch broker connection
until the following are approved and available:

- per-user credential lifecycle and explicit user consent
- production key-management service and key rotation process
- allowed-IP strategy for the chosen hosting environment
- audit trail, credential deletion, and incident response process
- broker terms and product approval for this connection model

This is a release gate, not a reason to store all users' secrets in one shared
`application.yml` value.

## Synchronization And Data Ownership

Broker data starts as a read-only preview. A broker holding must not silently overwrite
manual `TradeTransaction` records because a holding snapshot does not preserve complete
trade history or cost-basis rules.

The first broker slice is:

1. Verify connection and list eligible broker accounts.
2. Link one account to a portfolio.
3. Fetch a read-only holding snapshot with source and synchronization timestamp.
4. Let the user review differences from manual holdings.
5. Require an explicit import decision before creating any Trade Guide transaction or
   opening-balance record.

The order-history import path now adds a source/audit model before it writes to the
transaction ledger. It stages provider orders, shows reconciliation and exclusion
reasons, and requires an explicit approval. Sync runs record provider, start/end time,
result, item counts, and sanitized failure code; they never contain secrets or full
broker responses. The path is read-only toward the broker: it never submits, modifies,
or cancels an order.

## API And UI Contract Direction

The API should expose capabilities and safe connection state, not provider internals.

```text
GET    /api/me/provider-capabilities
GET    /api/me/broker-connections
POST   /api/me/broker-connections
POST   /api/me/broker-connections/{connectionId}/verify
DELETE /api/me/broker-connections/{connectionId}

GET    /api/me/portfolios/{portfolioId}/data-preference
PUT    /api/me/portfolios/{portfolioId}/data-preference
GET    /api/me/portfolios/{portfolioId}/broker-links
PUT    /api/me/portfolios/{portfolioId}/broker-links/{connectionId}
POST   /api/me/portfolios/{portfolioId}/broker-sync-preview
```

These paths intentionally use `/api/me` for new personal settings. Existing
`/api/members/{memberId}` APIs remain compatible during migration, but new sensitive
features should not require the browser to carry a member identifier.

## Implementation Order

1. Adopt this architecture and add production-auth profile safeguards.
2. **Complete:** provider catalog and portfolio data-preference read/write APIs exist,
   without secrets. All existing portfolios migrate to `TWELVE_DATA`; `TOSS_SECURITIES`
   and `YAHOO_FINANCE` are intentionally returned as unavailable until their respective
   provider boundaries are implemented and approved.
3. Add encrypted `BrokerConnection` storage, key abstraction, ownership tests, redacted
   logs, and disconnect semantics. **Storage and API boundary complete:** AES-256-GCM
   encryption, non-secret connection responses, delete semantics, owner-bound API
   tests, and settings UI are implemented. The browser clears submitted credentials
   immediately and displays only non-secret connection details. Credential verification
   and actual provider calls remain out of scope until the next step.
4. **Complete:** implement Toss connection verification and account listing behind the
   authenticated API, using fake provider contracts in tests. Access tokens are used only
   in the request scope and are never persisted.
5. **Complete:** add portfolio-to-account linking and a read-only holdings preview. The
   preview compares broker holdings with Trade Guide holdings but does not import or alter
   the transaction ledger.
6. **Complete for the current US ledger scope:** add explicit order-history staging,
   reconciliation, source/audit links, and user approval before modifying the
   transaction ledger. Suspected duplicate/manual-overlap items require a separate
   audited reclassification decision.
7. Extend domestic-market ledger support and production operational gates only after
   the provider contract, currency model, WTS terms, and long-range history behavior
   are confirmed.

No order creation, conditional order registration, or automated trade action belongs to
any step above.

## Local Configuration

The connection API is deliberately unavailable until
`BROKER_CREDENTIAL_ENCRYPTION_KEY` is configured as a Base64-encoded 32-byte value.
Set it only in a local environment variable or a production secret manager; never place
it in a tracked YAML file. An unset key produces a `503 Service Unavailable` response
and persists no connection or credential data.
