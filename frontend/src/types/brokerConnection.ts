export type BrokerProvider = "TOSS_SECURITIES" | (string & {})

export type BrokerCredentialFieldType = "TEXT" | "SECRET"

export type BrokerCredentialField = {
    key: string
    label: string
    type: BrokerCredentialFieldType
    required: boolean
    placeholder: string
    hint: string
}

export type BrokerProviderCapability =
    | "CONNECTION_VERIFICATION"
    | "HOLDING_SNAPSHOT"
    | "TRANSACTION_HISTORY_IMPORT"
    | "CASH_BALANCE"
    | (string & {})

export type BrokerProviderCatalogItem = {
    provider: BrokerProvider
    displayName: string
    connectable: boolean
    supportedCapabilities: BrokerProviderCapability[]
    availableCapabilities: BrokerProviderCapability[]
    supportedMarkets: string[]
    ledgerWritableMarkets?: string[]
    credentialFields: BrokerCredentialField[]
}

export type BrokerConnectionStatus = "UNVERIFIED" | "CONNECTED"

export type BrokerConnection = {
    id: number
    provider: BrokerProvider
    displayName: string
    status: BrokerConnectionStatus
    maskedAccountLabel: string | null
    lastVerifiedAt: string | null
    createdAt: string
    accounts: BrokerAccount[]
}

export type BrokerAccount = {
    id: number
    maskedAccountNumber: string
    accountType: string
}

export type BrokerConnectionCreateRequest = {
    provider: BrokerProvider
    displayName: string
    credentials?: Record<string, string>
    clientId?: string
    clientSecret?: string
}
