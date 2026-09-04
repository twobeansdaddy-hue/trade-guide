package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecret;
import com.tradeguide.domain.broker.BrokerConnectionStatus;
import com.tradeguide.domain.broker.BrokerHoldingPreview;
import com.tradeguide.domain.broker.BrokerHoldingSnapshot;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.service.holding.HoldingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 포트폴리오에 연결된 증권사 계좌의 보유 종목을 읽기 전용으로 조회하고
 * Trade Guide 매매 기록과의 차이를 정리한다.
 *
 * <p>이 서비스는 어떤 경우에도 {@code TradeTransaction}을 생성·수정하지 않는다.
 * 증권사 스냅샷을 매매 기록으로 가져오는 동작은 별도의 명시적 가져오기 설계 이후에 추가한다.
 */
@Service
public class BrokerHoldingPreviewService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;
    private final BrokerCredentialCipher brokerCredentialCipher;
    private final HoldingService holdingService;
    private final BrokerHoldingPreviewCalculator brokerHoldingPreviewCalculator;
    private final Map<BrokerProvider, BrokerHoldingsProvider> holdingsProviders;
    private final Clock clock;

    public BrokerHoldingPreviewService(
            PortfolioRepository portfolioRepository,
            PortfolioBrokerLinkRepository portfolioBrokerLinkRepository,
            BrokerCredentialCipher brokerCredentialCipher,
            HoldingService holdingService,
            BrokerHoldingPreviewCalculator brokerHoldingPreviewCalculator,
            List<BrokerHoldingsProvider> holdingsProviders,
            Clock clock
    ) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioBrokerLinkRepository = portfolioBrokerLinkRepository;
        this.brokerCredentialCipher = brokerCredentialCipher;
        this.holdingService = holdingService;
        this.brokerHoldingPreviewCalculator = brokerHoldingPreviewCalculator;
        this.holdingsProviders = new EnumMap<>(BrokerProvider.class);
        holdingsProviders.forEach(provider -> this.holdingsProviders.put(provider.getProvider(), provider));
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BrokerHoldingPreview getHoldingPreview(Long memberId, Long portfolioId) {
        portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("포트폴리오를 찾을 수 없습니다."));

        PortfolioBrokerLink link = portfolioBrokerLinkRepository.findByPortfolio_Id(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("포트폴리오에 연결된 증권사 계좌가 없습니다."));

        BrokerConnection connection = link.getBrokerConnection();
        if (connection.getStatus() != BrokerConnectionStatus.CONNECTED) {
            throw new IllegalArgumentException("증권사 연결을 다시 검증해야 합니다.");
        }

        BrokerHoldingsProvider holdingsProvider = holdingsProviders.get(connection.getProvider());
        if (holdingsProvider == null) {
            throw new BrokerConnectionUnavailableException("해당 증권사의 보유 종목 조회를 아직 지원하지 않습니다.");
        }

        BrokerAccount account = link.getBrokerAccount();
        BrokerConnectionSecret secret = connection.getSecret();

        // 복호화된 값은 이 호출 구간에서만 사용하고 저장하거나 응답에 담지 않는다.
        String clientId = brokerCredentialCipher.decrypt(new EncryptedBrokerCredential(
                secret.getEncryptedClientId(),
                secret.getClientIdInitializationVector(),
                secret.getEncryptionKeyVersion()
        ));
        String clientSecret = brokerCredentialCipher.decrypt(new EncryptedBrokerCredential(
                secret.getEncryptedClientSecret(),
                secret.getClientSecretInitializationVector(),
                secret.getEncryptionKeyVersion()
        ));
        String accountSequence = brokerCredentialCipher.decrypt(new EncryptedBrokerCredential(
                account.getEncryptedAccountSequence(),
                account.getAccountSequenceInitializationVector(),
                account.getEncryptionKeyVersion()
        ));

        BrokerHoldingSnapshot snapshot = holdingsProvider.fetchHoldings(clientId, clientSecret, accountSequence);
        List<Holding> tradeGuideHoldings = holdingService.getHoldings(memberId, portfolioId);

        return new BrokerHoldingPreview(
                connection.getProvider(),
                connection.getId(),
                account.getMaskedAccountNumber(),
                LocalDateTime.now(clock),
                brokerHoldingPreviewCalculator.compare(snapshot.holdings(), tradeGuideHoldings),
                snapshot.unsupportedMarketCount()
        );
    }
}
