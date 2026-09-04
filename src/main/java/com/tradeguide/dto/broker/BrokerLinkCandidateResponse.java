package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.service.broker.PortfolioBrokerLinkService;

import java.time.LocalDateTime;

/**
 * 포트폴리오에 연결할 수 있는 검증된 증권사 계좌 후보다.
 * 마스킹된 계좌 번호만 담고 자격 증명, 토큰, 계좌 일련번호는 담지 않는다.
 */
public record BrokerLinkCandidateResponse(
        Long brokerConnectionId,
        BrokerProvider provider,
        String displayName,
        Long brokerAccountId,
        String maskedAccountNumber,
        String accountType,
        LocalDateTime lastVerifiedAt
) {
    public static BrokerLinkCandidateResponse from(PortfolioBrokerLinkService.BrokerLinkCandidate candidate) {
        return new BrokerLinkCandidateResponse(
                candidate.connection().getId(),
                candidate.connection().getProvider(),
                candidate.connection().getDisplayName(),
                candidate.account().getId(),
                candidate.account().getMaskedAccountNumber(),
                candidate.account().getAccountType(),
                candidate.connection().getLastVerifiedAt()
        );
    }
}
