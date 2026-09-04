package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;

import java.time.LocalDateTime;

/**
 * 포트폴리오에 연결된 증권사 계좌의 안전한 상태 표현이다.
 */
public record PortfolioBrokerLinkResponse(
        Long id,
        Long brokerConnectionId,
        BrokerProvider provider,
        String displayName,
        Long brokerAccountId,
        String maskedAccountNumber,
        String accountType,
        LocalDateTime linkedAt,
        LocalDateTime updatedAt
) {
    public static PortfolioBrokerLinkResponse from(PortfolioBrokerLink link) {
        return new PortfolioBrokerLinkResponse(
                link.getId(),
                link.getBrokerConnection().getId(),
                link.getBrokerConnection().getProvider(),
                link.getBrokerConnection().getDisplayName(),
                link.getBrokerAccount().getId(),
                link.getBrokerAccount().getMaskedAccountNumber(),
                link.getBrokerAccount().getAccountType(),
                link.getLinkedAt(),
                link.getUpdatedAt()
        );
    }
}
