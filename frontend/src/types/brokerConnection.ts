export type BrokerProvider = "TOSS_SECURITIES"

export type BrokerConnectionStatus = "UNVERIFIED" | "CONNECTED"

export type BrokerConnection = {
    id: number
    provider: BrokerProvider
    displayName: string
    status: BrokerConnectionStatus
    maskedAccountLabel: string | null
    lastVerifiedAt: string | null
    createdAt: string
}

export type BrokerConnectionCreateRequest = {
    provider: BrokerProvider
    displayName: string
    clientId: string
    clientSecret: string
}
