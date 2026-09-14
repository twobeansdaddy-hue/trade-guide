package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerHistoryPage;
import com.tradeguide.domain.broker.BrokerHistoryPageRequest;
import com.tradeguide.domain.broker.BrokerOrderImportItemOverride;
import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderOverrideDecision;
import com.tradeguide.exception.BrokerOrderImportNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.BrokerOrderImportItemOverrideRepository;
import com.tradeguide.repository.broker.BrokerOrderImportRunRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 증권사 주문 이력 실행의 의심 항목 재판정을 다룬다. 재판정은 원장을 바꾸지 않으며, 반영 여부는
 * 기존 실행 승인 경로가 유효 상태를 기준으로 결정한다.
 */
@Service
public class BrokerOrderImportItemOverrideService {

    /** 최신 재판정부터 보여 준다. 기록 시각이 같을 수 있으므로 id를 동점 기준으로 둔다. */
    private static final Sort OVERRIDE_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private final PortfolioRepository portfolioRepository;
    private final BrokerOrderImportRunRepository brokerOrderImportRunRepository;
    private final BrokerOrderImportItemOverrideRepository brokerOrderImportItemOverrideRepository;
    private final BrokerOrderImportItemOverrideWriter brokerOrderImportItemOverrideWriter;

    public BrokerOrderImportItemOverrideService(
            PortfolioRepository portfolioRepository,
            BrokerOrderImportRunRepository brokerOrderImportRunRepository,
            BrokerOrderImportItemOverrideRepository brokerOrderImportItemOverrideRepository,
            BrokerOrderImportItemOverrideWriter brokerOrderImportItemOverrideWriter
    ) {
        this.portfolioRepository = portfolioRepository;
        this.brokerOrderImportRunRepository = brokerOrderImportRunRepository;
        this.brokerOrderImportItemOverrideRepository = brokerOrderImportItemOverrideRepository;
        this.brokerOrderImportItemOverrideWriter = brokerOrderImportItemOverrideWriter;
    }

    /**
     * 재판정을 기록한다. 같은 항목에 대한 동시 요청이 유니크 제약에서 경합하면 진 쪽 트랜잭션은
     * 전부 롤백되므로, 이긴 쪽이 남긴 기록을 기준으로 한 번만 다시 판단한다. 같은 결정이면
     * 기존 기록을 돌려주고, 다른 결정이면 충돌로 알린다.
     */
    public BrokerOrderImportItemOverrideWriter.OverrideResult createOverride(
            Long memberId,
            Long portfolioId,
            Long runId,
            Long itemId,
            BrokerOrderOverrideDecision decision,
            String reason
    ) {
        try {
            return brokerOrderImportItemOverrideWriter.create(memberId, portfolioId, runId, itemId, decision, reason);
        } catch (DataIntegrityViolationException exception) {
            return brokerOrderImportItemOverrideWriter.create(memberId, portfolioId, runId, itemId, decision, reason);
        }
    }

    /** 실행 한 건의 재판정 감사 이력을 최신순 한 페이지만 읽는다. */
    @Transactional(readOnly = true)
    public BrokerHistoryPage<BrokerOrderImportItemOverride> getOverrides(
            Long memberId,
            Long portfolioId,
            Long runId,
            BrokerHistoryPageRequest pageRequest
    ) {
        portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));
        BrokerOrderImportRun run = brokerOrderImportRunRepository.findByPortfolio_IdAndId(portfolioId, runId)
                .orElseThrow(() -> new BrokerOrderImportNotFoundException(
                        "요청한 주문 이력 가져오기 실행을 찾을 수 없습니다."));

        Page<BrokerOrderImportItemOverride> page = brokerOrderImportItemOverrideRepository.findAllByRun_Id(
                run.getId(),
                PageRequest.of(pageRequest.page(), pageRequest.size(), OVERRIDE_SORT)
        );

        return new BrokerHistoryPage<>(
                page.getContent(),
                pageRequest.page(),
                pageRequest.size(),
                page.getTotalElements(),
                page.hasNext()
        );
    }
}
