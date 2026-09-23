package com.tradeguide.domain.strategy;

import java.time.Instant;

/**
 * 장전 가이드가 사용한 포트폴리오 결정 입력의 다이제스트 묶음. 보유 수량·평단 등 원문은 담지 않는다.
 *
 * @param brokerSnapshotId 보유 종목이 없을 때 안내 문구 판단에 참조한 최신 증권사 보유 스냅샷 id. 없으면 {@code null}.
 */
public record PremarketGuidePortfolioStateDigests(
        int schemaVersion,
        Instant readAt,
        String ledgerSha256,
        int ledgerTransactionCount,
        String holdingsSha256,
        String strategyOverridesSha256,
        String riskSettingsSha256,
        PremarketGuideCandidateSource candidateSource,
        String candidateSetSha256,
        String assetCatalogSha256,
        Long brokerSnapshotId,
        String stateSha256
) {
    public PremarketGuidePortfolioStateDigests {
        if (schemaVersion < 1 || readAt == null || ledgerTransactionCount < 0 || candidateSource == null
                || !isSha256(ledgerSha256) || !isSha256(holdingsSha256)
                || !isSha256(strategyOverridesSha256) || !isSha256(riskSettingsSha256)
                || !isSha256(candidateSetSha256) || !isSha256(assetCatalogSha256)
                || !isSha256(stateSha256)) {
            throw new IllegalArgumentException("포트폴리오 상태 다이제스트가 올바르지 않습니다.");
        }
    }

    private static boolean isSha256(String value) {
        return value != null && value.length() == 64;
    }
}
