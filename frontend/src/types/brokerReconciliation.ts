import type {BrokerProvider} from "./brokerConnection";
import type {BrokerHoldingComparison} from "./portfolioBroker";

export type BrokerReconciliationOverallStatus = "MATCHED" | "DIFFERENCES_FOUND";

export type BrokerReconciliationReasonCode =
    | "UNAPPROVED_RUN_EXISTS"
    | "BASELINE_EXCLUDED_HISTORY"
    | "UNSETTLED_OR_PARTIAL_FILL"
    | "OUT_OF_PERIOD_HISTORY"
    | "LEDGER_MARKET_UNSUPPORTED"
    | "UNEXPLAINED_DIFFERENCE";

export type BrokerReconciliationRun = {
    id: number;
    provider: BrokerProvider;
    brokerConnectionId: number;
    maskedAccountNumber: string;
    snapshotId: number;
    snapshotSyncedAt: string;
    executedByMemberId: number;
    executedAt: string;
    overallStatus: BrokerReconciliationOverallStatus;
    matchedCount: number;
    quantityMismatchCount: number;
    onlyInBrokerCount: number;
    onlyInTradeGuideCount: number;
};

export type BrokerReconciliationLine = {
    market: string;
    ticker: string;
    displayName: string;
    brokerQuantity: number | null;
    tradeGuideQuantity: number | null;
    comparison: BrokerHoldingComparison;
    reasonCandidates: BrokerReconciliationReasonCode[];
};

export type BrokerReconciliationRunDetail = {
    run: BrokerReconciliationRun;
    lines: BrokerReconciliationLine[];
};
