package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionStatus;
import com.tradeguide.domain.broker.BrokerHoldingSnapshot;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.exception.BrokerConnectionReverificationRequiredException;
import com.tradeguide.exception.PortfolioBrokerLinkNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingSnapshotRepository;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 조회가 끝난 증권사 보유 종목을 스냅샷 한 건으로 저장한다.
 *
 * <p>트랜잭션 경계를 {@link PortfolioBrokerHoldingSnapshotService}와 나눈 이유는
 * <b>증권사 호출을 트랜잭션 안에 두지 않기 위해서다.</b> 외부 HTTP 응답을 기다리는 동안 DB
 * 커넥션과 트랜잭션을 붙잡고 있으면, 증권사가 느려질 때 커넥션 풀이 먼저 말라붙는다. 주문 이력
 * 경로가 {@link BrokerOrderImportRunWriter}로 이미 지키고 있는 경계이며 여기도 같게 맞춘다.
 *
 * <p>주문 이력 경로와 달리 <b>실패 기록을 남기지 않는다.</b> 스냅샷은 "증권사가 이렇게 보고했다"는
 * 사실 한 장이므로, 조회가 실패하면 남길 사실이 없다. 그래서 {@code REQUIRES_NEW}로 나눈
 * 실패 기록 트랜잭션도 없고, 이 트랜잭션은 <b>조회가 성공한 뒤에야</b> 열린다. 조회가 실패하면
 * 트랜잭션이 시작조차 하지 않으므로 부분 영속화가 생길 수 없다.
 *
 * <p>스냅샷과 그 항목은 한 트랜잭션에서 함께 저장된다. 항목 저장이 실패하면 스냅샷 헤더도 함께
 * 롤백되며, 항목이 비어 있거나 잘린 스냅샷이 남지 않는다.
 *
 * <p>이 클래스는 어떤 경우에도 {@code TradeTransaction}이나 파생 {@code Holding}을
 * 생성·수정하지 않는다.
 */
@Component
public class PortfolioBrokerHoldingSnapshotWriter {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;
    private final PortfolioBrokerHoldingSnapshotRepository portfolioBrokerHoldingSnapshotRepository;

    public PortfolioBrokerHoldingSnapshotWriter(
            PortfolioRepository portfolioRepository,
            PortfolioBrokerLinkRepository portfolioBrokerLinkRepository,
            PortfolioBrokerHoldingSnapshotRepository portfolioBrokerHoldingSnapshotRepository
    ) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioBrokerLinkRepository = portfolioBrokerLinkRepository;
        this.portfolioBrokerHoldingSnapshotRepository = portfolioBrokerHoldingSnapshotRepository;
    }

    /**
     * 조회 결과를 스냅샷으로 저장한다.
     *
     * <p>연결 상태와 링크 대상을 여기서 <b>다시</b> 확인한다. 컨텍스트를 읽은 시점과 저장 시점
     * 사이에는 증권사 호출이 있어 시간이 벌어지고, 그 사이에 사용자가 링크를 바꾸거나 연결을
     * 재검증했을 수 있다. 확인 없이 저장하면 A 계좌에서 가져온 보유 종목이 B 계좌의 스냅샷으로
     * 남는다. 그 오염은 개시 잔고 반영까지 번지므로 저장하지 않고 거부한다.
     */
    @Transactional
    public PortfolioBrokerHoldingSnapshot save(SaveRequest request) {
        Portfolio portfolio = portfolioRepository
                .findByMember_IdAndId(request.memberId(), request.portfolioId())
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));

        PortfolioBrokerLink link = portfolioBrokerLinkRepository
                .findByPortfolio_Id(request.portfolioId())
                .orElseThrow(() -> new PortfolioBrokerLinkNotFoundException("포트폴리오에 연결된 증권사 계좌가 없습니다."));

        BrokerConnection connection = link.getBrokerConnection();
        BrokerAccount account = link.getBrokerAccount();

        if (!connection.getId().equals(request.brokerConnectionId())
                || !account.getId().equals(request.brokerAccountId())) {
            throw new IllegalArgumentException(
                    "조회하는 동안 포트폴리오의 증권사 계좌 연결이 바뀌었습니다. 다시 시도해 주세요.");
        }
        if (connection.getStatus() != BrokerConnectionStatus.CONNECTED) {
            throw new BrokerConnectionReverificationRequiredException("증권사 연결을 다시 검증해야 합니다.");
        }

        BrokerHoldingSnapshot fetched = request.fetched();

        return portfolioBrokerHoldingSnapshotRepository.save(new PortfolioBrokerHoldingSnapshot(
                portfolio,
                connection,
                account,
                request.syncedAt(),
                fetched.unsupportedMarketCount(),
                fetched.holdings()
        ));
    }

    /**
     * 저장 요청이다. 자격 증명과 계좌 일련번호는 담지 않는다. 저장에 필요하지 않고,
     * 트랜잭션 경계를 넘겨야 할 이유도 없다.
     */
    public record SaveRequest(
            Long memberId,
            Long portfolioId,
            Long brokerConnectionId,
            Long brokerAccountId,
            LocalDateTime syncedAt,
            BrokerHoldingSnapshot fetched
    ) {
        public SaveRequest {
            if (memberId == null || portfolioId == null || brokerConnectionId == null
                    || brokerAccountId == null || syncedAt == null || fetched == null) {
                throw new IllegalArgumentException("증권사 보유 종목 스냅샷 저장 요청이 올바르지 않습니다.");
            }
        }
    }
}
