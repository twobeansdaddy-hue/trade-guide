package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionStatus;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.repository.broker.BrokerConnectionRepository;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 포트폴리오와 검증된 증권사 계좌의 연결을 관리한다.
 * 이 서비스는 읽기 전용 조회 대상만 지정하며, 주문이나 매매 기록을 만들지 않는다.
 */
@Service
public class PortfolioBrokerLinkService {

    private final PortfolioRepository portfolioRepository;
    private final BrokerConnectionRepository brokerConnectionRepository;
    private final PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;
    private final Clock clock;

    public PortfolioBrokerLinkService(
            PortfolioRepository portfolioRepository,
            BrokerConnectionRepository brokerConnectionRepository,
            PortfolioBrokerLinkRepository portfolioBrokerLinkRepository,
            Clock clock
    ) {
        this.portfolioRepository = portfolioRepository;
        this.brokerConnectionRepository = brokerConnectionRepository;
        this.portfolioBrokerLinkRepository = portfolioBrokerLinkRepository;
        this.clock = clock;
    }

    /**
     * 연결 가능한 계좌 후보는 검증이 끝난({@code CONNECTED}) 연결의 계좌뿐이다.
     */
    @Transactional(readOnly = true)
    public List<BrokerLinkCandidate> getLinkCandidates(Long memberId, Long portfolioId) {
        requirePortfolio(memberId, portfolioId);

        return brokerConnectionRepository.findAllByMember_IdOrderByCreatedAtDesc(memberId).stream()
                .filter(connection -> connection.getStatus() == BrokerConnectionStatus.CONNECTED)
                .flatMap(connection -> connection.getAccounts().stream()
                        .map(account -> new BrokerLinkCandidate(connection, account)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PortfolioBrokerLink> getBrokerLinks(Long memberId, Long portfolioId) {
        Portfolio portfolio = requirePortfolio(memberId, portfolioId);

        return portfolioBrokerLinkRepository.findAllByPortfolio_IdOrderByLinkedAtAsc(portfolio.getId());
    }

    @Transactional
    public PortfolioBrokerLink linkBrokerAccount(
            Long memberId,
            Long portfolioId,
            Long brokerConnectionId,
            Long brokerAccountId
    ) {
        Portfolio portfolio = requirePortfolio(memberId, portfolioId);
        BrokerConnection connection = brokerConnectionRepository
                .findByMember_IdAndId(memberId, brokerConnectionId)
                .orElseThrow(() -> new IllegalArgumentException("증권사 연결 정보를 찾을 수 없습니다."));

        if (connection.getStatus() != BrokerConnectionStatus.CONNECTED) {
            throw new IllegalArgumentException("검증되지 않은 증권사 연결은 포트폴리오에 연결할 수 없습니다.");
        }

        BrokerAccount account = connection.getAccounts().stream()
                .filter(candidate -> candidate.getId().equals(brokerAccountId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("증권사 계좌를 찾을 수 없습니다."));

        LocalDateTime now = LocalDateTime.now(clock);

        // v1은 포트폴리오당 하나의 링크만 유지한다. 이 제약은 서비스 검증 사항이다.
        return portfolioBrokerLinkRepository.findByPortfolio_Id(portfolio.getId())
                .map(existing -> {
                    existing.changeAccount(connection, account, now);
                    return portfolioBrokerLinkRepository.save(existing);
                })
                .orElseGet(() -> portfolioBrokerLinkRepository.save(
                        new PortfolioBrokerLink(portfolio, connection, account, now)
                ));
    }

    @Transactional
    public void unlinkBrokerAccount(Long memberId, Long portfolioId, Long brokerConnectionId) {
        Portfolio portfolio = requirePortfolio(memberId, portfolioId);

        PortfolioBrokerLink link = portfolioBrokerLinkRepository
                .findByPortfolio_IdAndBrokerConnection_Id(portfolio.getId(), brokerConnectionId)
                .orElseThrow(() -> new IllegalArgumentException("연결된 증권사 계좌를 찾을 수 없습니다."));

        portfolioBrokerLinkRepository.delete(link);
    }

    private Portfolio requirePortfolio(Long memberId, Long portfolioId) {
        return portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("포트폴리오를 찾을 수 없습니다."));
    }

    /** 연결 후보 한 건이다. 마스킹된 계좌 번호만 담고 계좌 일련번호는 담지 않는다. */
    public record BrokerLinkCandidate(BrokerConnection connection, BrokerAccount account) {
    }
}
