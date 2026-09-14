package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionStatus;
import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerHoldingSnapshot;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.exception.BrokerConnectionReverificationRequiredException;
import com.tradeguide.exception.PortfolioBrokerLinkNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보유 종목 조회에 필요한 연결 정보를 트랜잭션 안에서 한 번에 꺼내 온다.
 *
 * <p>조회 호출을 감싸는 서비스와 분리한 이유는 트랜잭션 경계 때문이다. 같은 빈 안에서 부른
 * {@code @Transactional} 메서드는 프록시를 거치지 않아 트랜잭션이 열리지 않고, 그러면
 * 지연 로딩이 호출 시점에 터지거나 매 쿼리가 각각의 트랜잭션으로 흩어진다. 주문 이력 경로가
 * {@link BrokerOrderImportContextLoader}로 같은 형태를 이미 쓰고 있고, 보유 종목 경로도
 * 여기에 맞춘다.
 *
 * <p>기능 관문({@link BrokerProviderRegistry#requireHoldingsProvider})은 자격 증명을
 * <b>복호화하기 전에</b> 지난다. 아직 열려 있지 않은 기능 때문에 평문이 만들어지는 일이 없어야
 * 한다. 미리보기와 스냅샷 새로고침이 요구하는 관문과 검증이 같으므로 두 경로가 이 로더를 공유한다.
 *
 * <p>복호화된 자격 증명과 계좌 일련번호는 {@link HoldingContext} 안에만 머무르고,
 * 저장·로그·응답 어디에도 담기지 않는다.
 */
@Component
public class BrokerHoldingContextLoader {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;
    private final BrokerCredentialLoader brokerCredentialLoader;
    private final BrokerProviderRegistry brokerProviderRegistry;

    public BrokerHoldingContextLoader(
            PortfolioRepository portfolioRepository,
            PortfolioBrokerLinkRepository portfolioBrokerLinkRepository,
            BrokerCredentialLoader brokerCredentialLoader,
            BrokerProviderRegistry brokerProviderRegistry
    ) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioBrokerLinkRepository = portfolioBrokerLinkRepository;
        this.brokerCredentialLoader = brokerCredentialLoader;
        this.brokerProviderRegistry = brokerProviderRegistry;
    }

    @Transactional(readOnly = true)
    public HoldingContext load(Long memberId, Long portfolioId) {
        portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));

        PortfolioBrokerLink link = portfolioBrokerLinkRepository.findByPortfolio_Id(portfolioId)
                .orElseThrow(() -> new PortfolioBrokerLinkNotFoundException("포트폴리오에 연결된 증권사 계좌가 없습니다."));

        BrokerConnection connection = link.getBrokerConnection();
        if (connection.getStatus() != BrokerConnectionStatus.CONNECTED) {
            throw new BrokerConnectionReverificationRequiredException("증권사 연결을 다시 검증해야 합니다.");
        }

        BrokerHoldingsProvider holdingsProvider =
                brokerProviderRegistry.requireHoldingsProvider(connection.getProvider());

        BrokerAccount account = link.getBrokerAccount();

        // 복호화된 값은 이 컨텍스트의 어댑터 호출 구간에서만 쓰이고 저장하거나 응답에 담지 않는다.
        return new HoldingContext(
                connection.getId(),
                account.getId(),
                connection.getProvider(),
                account.getMaskedAccountNumber(),
                holdingsProvider,
                brokerCredentialLoader.load(connection),
                brokerCredentialLoader.loadAccountSequence(account)
        );
    }

    /**
     * 트랜잭션 밖에서 증권사를 호출하는 데 필요한 값 묶음이다.
     *
     * <p>어댑터와 평문 값을 함께 들고 {@link #fetchHoldings()}를 직접 제공하는 이유는,
     * 호출하는 서비스가 평문 자격 증명을 손에 쥘 필요를 없애기 위해서다. 평문이 흐르는 범위가
     * 로더와 이 레코드 안으로 닫힌다.
     */
    public record HoldingContext(
            Long brokerConnectionId,
            Long brokerAccountId,
            BrokerProvider provider,
            String maskedAccountNumber,
            BrokerHoldingsProvider holdingsProvider,
            BrokerCredentials credentials,
            String accountSequence
    ) {
        /** 증권사 보유 종목을 조회한다. 이 호출은 <b>트랜잭션 밖</b>에서 일어나야 한다. */
        public BrokerHoldingSnapshot fetchHoldings() {
            return holdingsProvider.fetchHoldings(credentials, accountSequence);
        }

        /**
         * 값이 새는 가장 흔한 경로가 로깅과 예외 메시지다. 레코드 기본 {@code toString}은
         * 계좌 일련번호 평문을 그대로 드러내므로 식별자만 남기도록 재정의한다.
         */
        @Override
        public String toString() {
            return "HoldingContext(brokerConnectionId=" + brokerConnectionId
                    + ", brokerAccountId=" + brokerAccountId
                    + ", provider=" + provider + ")";
        }
    }
}
