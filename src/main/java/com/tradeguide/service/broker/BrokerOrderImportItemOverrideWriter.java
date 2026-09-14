package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerOrderImportItem;
import com.tradeguide.domain.broker.BrokerOrderImportItemOverride;
import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderImportRunStatus;
import com.tradeguide.domain.broker.BrokerOrderOverrideDecision;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.dto.ApiErrorCode;
import com.tradeguide.exception.BrokerOrderImportNotFoundException;
import com.tradeguide.exception.BrokerOrderOverrideConflictException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.BrokerOrderImportItemOverrideRepository;
import com.tradeguide.repository.broker.BrokerOrderImportItemRepository;
import com.tradeguide.repository.broker.BrokerOrderImportRunRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 의심 항목 재판정 한 건을 기록하는 트랜잭션 경계다.
 *
 * <p>{@link BrokerOrderImportItemOverrideService}와 빈을 나눈 이유는
 * {@link BrokerOrderImportApprovalWriter}와 같다. 동시 요청이 유니크 제약에서 경합해 실패하면
 * 이 트랜잭션은 전부 롤백되고, 호출자가 새 트랜잭션에서 이긴 쪽의 기록을 기준으로 다시 판단한다.
 *
 * <p>이 클래스는 매매 원장에 한 행도 쓰지 않고 스테이징 항목도 바꾸지 않는다. 반영은 여전히
 * 실행 승인에서만 일어난다.
 */
@Component
public class BrokerOrderImportItemOverrideWriter {

    private final PortfolioRepository portfolioRepository;
    private final BrokerOrderImportRunRepository brokerOrderImportRunRepository;
    private final BrokerOrderImportItemRepository brokerOrderImportItemRepository;
    private final BrokerOrderImportItemOverrideRepository brokerOrderImportItemOverrideRepository;
    private final Clock clock;

    public BrokerOrderImportItemOverrideWriter(
            PortfolioRepository portfolioRepository,
            BrokerOrderImportRunRepository brokerOrderImportRunRepository,
            BrokerOrderImportItemRepository brokerOrderImportItemRepository,
            BrokerOrderImportItemOverrideRepository brokerOrderImportItemOverrideRepository,
            Clock clock
    ) {
        this.portfolioRepository = portfolioRepository;
        this.brokerOrderImportRunRepository = brokerOrderImportRunRepository;
        this.brokerOrderImportItemRepository = brokerOrderImportItemRepository;
        this.brokerOrderImportItemOverrideRepository = brokerOrderImportItemOverrideRepository;
        this.clock = clock;
    }

    /**
     * 재판정을 기록한다. 같은 결정의 재요청은 새 행을 만들지 않고 최초 기록을 그대로 돌려준다
     * (사유도 최초 값을 유지한다). 다른 결정으로 바꾸려는 요청은 거부한다.
     *
     * <p>실행 행을 쓰기 잠금으로 읽는다. 같은 실행의 승인이 동시에 진행되면 둘 중 하나가 끝날
     * 때까지 기다리므로, 승인이 읽은 재판정 집합과 실제로 기록된 재판정이 어긋나지 않는다.
     */
    @Transactional
    public OverrideResult create(
            Long memberId,
            Long portfolioId,
            Long runId,
            Long itemId,
            BrokerOrderOverrideDecision decision,
            String reason
    ) {
        if (decision == null) {
            throw new IllegalArgumentException("재판정 결정은 필수입니다.");
        }
        BrokerOrderImportItemOverride.normalizeReason(reason);

        Portfolio portfolio = portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));
        BrokerOrderImportRun run = brokerOrderImportRunRepository
                .findWithLockByPortfolioIdAndId(portfolio.getId(), runId)
                .orElseThrow(() -> new BrokerOrderImportNotFoundException(
                        "요청한 주문 이력 가져오기 실행을 찾을 수 없습니다."));
        // 항목은 실행 범위 안에서만 찾는다. 항목 id만으로 찾으면 다른 회원의 항목을 재판정할 수 있다.
        BrokerOrderImportItem item = brokerOrderImportItemRepository.findByIdAndRun_Id(itemId, run.getId())
                .orElseThrow(() -> new BrokerOrderImportNotFoundException("요청한 주문 항목을 찾을 수 없습니다."));

        Optional<BrokerOrderImportItemOverride> existing =
                brokerOrderImportItemOverrideRepository.findByItem_Id(item.getId());
        if (existing.isPresent()) {
            if (existing.get().getDecision() == decision) {
                return new OverrideResult(existing.get(), false);
            }
            throw new BrokerOrderOverrideConflictException(
                    "이 주문 항목은 이미 다른 결정으로 재판정됐습니다. 결정을 바꾸려면 주문 이력을 다시 가져와 새 실행에서 판단하세요.",
                    ApiErrorCode.ORDER_IMPORT_OVERRIDE_CONFLICT);
        }

        if (run.getStatus() != BrokerOrderImportRunStatus.STAGED
                || !BrokerOrderImportItemOverride.isOverridable(item.getStagingStatus())) {
            throw new BrokerOrderOverrideConflictException(
                    "사람 판단이 필요한 의심 항목만 재판정할 수 있습니다.",
                    ApiErrorCode.ORDER_IMPORT_ITEM_NOT_OVERRIDABLE);
        }

        BrokerOrderImportItemOverride override = new BrokerOrderImportItemOverride(
                item, decision, reason, portfolio.getMember(), LocalDateTime.now(clock));
        return new OverrideResult(brokerOrderImportItemOverrideRepository.saveAndFlush(override), true);
    }

    /** 재판정 기록 결과다. {@code created}가 거짓이면 같은 결정의 기존 기록을 돌려준 것이다. */
    public record OverrideResult(BrokerOrderImportItemOverride override, boolean created) {
    }
}
