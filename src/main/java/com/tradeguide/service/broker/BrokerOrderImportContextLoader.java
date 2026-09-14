package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionStatus;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.exception.BrokerConnectionReverificationRequiredException;
import com.tradeguide.exception.PortfolioBrokerLinkNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 이력 조회에 필요한 연결 정보를 트랜잭션 안에서 한 번에 꺼내 온다.
 *
 * <p>조회 호출을 감싸는 서비스와 분리한 이유는 트랜잭션 경계 때문이다. 같은 빈 안에서 부른
 * {@code @Transactional} 메서드는 프록시를 거치지 않아 트랜잭션이 열리지 않고, 그러면
 * 지연 로딩이 호출 시점에 터지거나 매 쿼리가 각각의 트랜잭션으로 흩어진다.
 *
 * <p>복호화된 자격 증명과 계좌 일련번호는 반환값으로만 나가고, 저장·로그·응답 어디에도 담기지 않는다.
 */
@Component
public class BrokerOrderImportContextLoader {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;
    private final BrokerCredentialLoader brokerCredentialLoader;

    public BrokerOrderImportContextLoader(
            PortfolioRepository portfolioRepository,
            PortfolioBrokerLinkRepository portfolioBrokerLinkRepository,
            BrokerCredentialLoader brokerCredentialLoader
    ) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioBrokerLinkRepository = portfolioBrokerLinkRepository;
        this.brokerCredentialLoader = brokerCredentialLoader;
    }

    @Transactional(readOnly = true)
    public BrokerOrderImportService.ImportContext load(Long memberId, Long portfolioId) {
        portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));

        PortfolioBrokerLink link = portfolioBrokerLinkRepository.findByPortfolio_Id(portfolioId)
                .orElseThrow(() -> new PortfolioBrokerLinkNotFoundException("포트폴리오에 연결된 증권사 계좌가 없습니다."));

        BrokerConnection connection = link.getBrokerConnection();
        if (connection.getStatus() != BrokerConnectionStatus.CONNECTED) {
            throw new BrokerConnectionReverificationRequiredException("증권사 연결을 다시 검증해야 합니다.");
        }

        BrokerAccount account = link.getBrokerAccount();

        return new BrokerOrderImportService.ImportContext(
                connection.getId(),
                account.getId(),
                connection.getProvider(),
                brokerCredentialLoader.load(connection),
                brokerCredentialLoader.loadAccountSequence(account)
        );
    }
}
