package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerOrderExecutionGrant;
import com.tradeguide.domain.member.Member;
import com.tradeguide.repository.broker.BrokerConnectionRepository;
import com.tradeguide.repository.broker.BrokerOrderExecutionGrantRepository;
import com.tradeguide.repository.member.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 오토매매 opt-in 동의(grant)의 생성과 생애주기 전이를 다룬다.
 *
 * <p>이 슬라이스는 실제 브로커 주문 제출을 구현하지 않는다 - 동의를 저장하고 켜고
 * 끄는 것까지만 담당한다({@code research/reports/track-a-auto-trading-3rd-stage-
 * policy-and-architecture-proposal.md} 3절).
 *
 * <p>서킷브레이커 하드 상한(포지션 한도 20%, 일일 주문 10건)은 여기서 한 번 더
 * 검증한다 - DTO의 {@code @DecimalMax}/{@code @Max}는 조기 실패를 위한 것일 뿐,
 * 클라이언트가 검증을 우회해 보낸 값도 이 서비스 계층에서 최종적으로 걸러야 한다는
 * 것이 신뢰 경계다.
 */
@Service
public class BrokerOrderExecutionGrantService {

    static final BigDecimal MAX_POSITION_SIZE_PER_ORDER_PERCENT = new BigDecimal("0.20");
    static final int MAX_DAILY_ORDER_COUNT = 10;

    /**
     * 세후로도 수익성이 확인된 유일한 전략만 자동 주문 대상으로 허용한다
     * ({@code research/reports/track-a-real-cost-and-tax-revalidation.md}).
     * 이번 세션에서 검증한 매크로 오버레이 후보는 전부 기각됐으므로 포함하지 않는다.
     * 새 전략을 추가하려면 별도 세후 검증 리포트가 먼저 있어야 한다 - 이 목록은
     * 검증되지 않은 규칙이 슬며시 자동 주문 대상이 되는 것을 막는 마지막 방어선이다.
     */
    static final Set<String> ALLOWED_STRATEGY_IDS = Set.of("track-a-weekly-ma-crossover");

    private final MemberRepository memberRepository;
    private final BrokerConnectionRepository brokerConnectionRepository;
    private final BrokerOrderExecutionGrantRepository brokerOrderExecutionGrantRepository;

    public BrokerOrderExecutionGrantService(
            MemberRepository memberRepository,
            BrokerConnectionRepository brokerConnectionRepository,
            BrokerOrderExecutionGrantRepository brokerOrderExecutionGrantRepository
    ) {
        this.memberRepository = memberRepository;
        this.brokerConnectionRepository = brokerConnectionRepository;
        this.brokerOrderExecutionGrantRepository = brokerOrderExecutionGrantRepository;
    }

    @Transactional
    public BrokerOrderExecutionGrant createGrant(
            Long memberId,
            Long brokerConnectionId,
            String strategyId,
            BigDecimal maxPositionSizePerOrderPercent,
            int maxDailyOrderCount,
            String consentVersion
    ) {
        if (!ALLOWED_STRATEGY_IDS.contains(strategyId)) {
            throw new IllegalArgumentException("자동 주문을 허용하지 않는 전략입니다: " + strategyId);
        }
        if (maxPositionSizePerOrderPercent == null
                || maxPositionSizePerOrderPercent.compareTo(MAX_POSITION_SIZE_PER_ORDER_PERCENT) > 0) {
            throw new IllegalArgumentException(
                    "포지션 한도는 계좌 자산의 " + MAX_POSITION_SIZE_PER_ORDER_PERCENT.multiply(BigDecimal.valueOf(100))
                            + "%를 넘을 수 없습니다.");
        }
        if (maxDailyOrderCount > MAX_DAILY_ORDER_COUNT) {
            throw new IllegalArgumentException("일일 주문 한도는 " + MAX_DAILY_ORDER_COUNT + "건을 넘을 수 없습니다.");
        }

        Member member = requireMember(memberId);
        BrokerConnection connection = requireOwnedConnection(memberId, brokerConnectionId);

        if (brokerOrderExecutionGrantRepository.existsByBrokerConnection_Id(brokerConnectionId)) {
            throw new IllegalArgumentException("이 증권사 연결에는 이미 자동 주문 동의가 존재합니다.");
        }

        BrokerOrderExecutionGrant grant = new BrokerOrderExecutionGrant(
                member, connection, strategyId, maxPositionSizePerOrderPercent, maxDailyOrderCount, consentVersion);
        return brokerOrderExecutionGrantRepository.save(grant);
    }

    public BrokerOrderExecutionGrant getGrant(Long memberId, Long brokerConnectionId) {
        return brokerOrderExecutionGrantRepository.findByMember_IdAndBrokerConnection_Id(memberId, brokerConnectionId)
                .orElseThrow(() -> new IllegalArgumentException("자동 주문 동의를 찾을 수 없습니다."));
    }

    /** 킬스위치. 이미 PAUSED/ACTIVE 어느 쪽이든 안전하게 호출할 수 있다. */
    @Transactional
    public BrokerOrderExecutionGrant pauseGrant(Long memberId, Long grantId) {
        BrokerOrderExecutionGrant grant = requireOwnedGrant(memberId, grantId);
        grant.pause();
        return grant;
    }

    @Transactional
    public BrokerOrderExecutionGrant revokeGrant(Long memberId, Long grantId) {
        BrokerOrderExecutionGrant grant = requireOwnedGrant(memberId, grantId);
        grant.revoke();
        return grant;
    }

    @Transactional
    public BrokerOrderExecutionGrant reactivateGrant(Long memberId, Long grantId) {
        BrokerOrderExecutionGrant grant = requireOwnedGrant(memberId, grantId);
        grant.reactivate();
        return grant;
    }

    private Member requireMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
    }

    private BrokerConnection requireOwnedConnection(Long memberId, Long brokerConnectionId) {
        return brokerConnectionRepository.findByMember_IdAndId(memberId, brokerConnectionId)
                .orElseThrow(() -> new IllegalArgumentException("증권사 연결 정보를 찾을 수 없습니다."));
    }

    private BrokerOrderExecutionGrant requireOwnedGrant(Long memberId, Long grantId) {
        return brokerOrderExecutionGrantRepository.findByMember_IdAndId(memberId, grantId)
                .orElseThrow(() -> new IllegalArgumentException("자동 주문 동의를 찾을 수 없습니다."));
    }
}
