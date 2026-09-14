package com.tradeguide.domain.broker;

import com.tradeguide.domain.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * 의심 판정 항목 한 건에 대한 사용자 재판정의 감사 기록이다.
 *
 * <p>스테이징 항목({@link BrokerOrderImportItem})은 "증권사가 이렇게 보고했고 우리는 이렇게
 * 읽었다"는 사실이라 바꾸지 않는다. 사용자의 판단은 그 사실과 섞지 않고 이 행에 따로 남기며,
 * 승인 경로가 두 값을 합쳐 <b>유효 상태</b>를 계산한다({@link #effectiveStatusOf}).
 *
 * <p>이 행도 만든 뒤에 바꾸지 않는다. 항목 하나에 재판정은 한 번뿐이고
 * ({@code uk_broker_order_import_item_overrides_item}), 결정을 바꾸려면 같은 구간을 다시 가져와
 * 새 실행의 새 항목에서 판단한다. 기존 결정을 덮어쓰면 "무엇을 근거로 원장에 넣었는지"가 사라진다.
 * 같은 이유로 실행 승인을 취소해도 이 행은 지우지 않는다.
 */
@Entity
@Table(
        name = "broker_order_import_item_overrides",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_broker_order_import_item_overrides_item",
                columnNames = {"item_id"}
        )
)
public class BrokerOrderImportItemOverride {

    public static final int REASON_MAX_LENGTH = 500;

    /**
     * 재판정할 수 있는 상태다. 나머지 제외 사유(체결 없음, 값 누락, 이미 반영 등)는 사람이 판단할
     * 문제가 아니라 원장에 넣을 수 없는 사실이므로 열지 않는다.
     */
    private static final Set<BrokerOrderStagingStatus> OVERRIDABLE_STATUSES = EnumSet.of(
            BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED,
            BrokerOrderStagingStatus.DUPLICATE_SUSPECTED
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private BrokerOrderImportRun run;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private BrokerOrderImportItem item;

    @Column(name = "external_order_id", nullable = false, length = 255)
    private String externalOrderId;

    /** 재판정 당시 분류기가 내린 상태다. 항목 행이 이미 같은 값을 갖지만 판단의 전제를 한 행에 남긴다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "original_staging_status", nullable = false, length = 40)
    private BrokerOrderStagingStatus originalStagingStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "original_skip_reason_code", length = 40)
    private BrokerOrderSkipReason originalSkipReasonCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 30)
    private BrokerOrderOverrideDecision decision;

    @Enumerated(EnumType.STRING)
    @Column(name = "resulting_staging_status", nullable = false, length = 40)
    private BrokerOrderStagingStatus resultingStagingStatus;

    @Column(name = "reason", nullable = false, length = REASON_MAX_LENGTH)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_member_id", nullable = false)
    private Member createdByMember;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected BrokerOrderImportItemOverride() {
    }

    public BrokerOrderImportItemOverride(
            BrokerOrderImportItem item,
            BrokerOrderOverrideDecision decision,
            String reason,
            Member createdByMember,
            LocalDateTime createdAt
    ) {
        if (item == null || decision == null || createdByMember == null || createdAt == null) {
            throw new IllegalArgumentException("주문 항목 재판정 정보가 올바르지 않습니다.");
        }
        if (!isOverridable(item.getStagingStatus())) {
            throw new IllegalArgumentException("사람 판단이 필요한 의심 항목만 재판정할 수 있습니다.");
        }

        this.run = item.getRun();
        this.item = item;
        this.externalOrderId = item.getExternalOrderId();
        this.originalStagingStatus = item.getStagingStatus();
        this.originalSkipReasonCode = item.getSkipReasonCode();
        this.decision = decision;
        this.resultingStagingStatus = decision.resultingStatusFrom(item.getStagingStatus());
        this.reason = normalizeReason(reason);
        this.createdByMember = createdByMember;
        this.createdAt = createdAt;
    }

    public static boolean isOverridable(BrokerOrderStagingStatus status) {
        return status != null && OVERRIDABLE_STATUSES.contains(status);
    }

    /**
     * 사유를 앞뒤 공백을 걷어 돌려준다. 공백뿐인 사유는 사유가 아니다. 나중에 "왜 넣었지"를
     * 물었을 때 답이 비어 있으면 감사 기록을 남긴 의미가 없다.
     */
    public static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("재판정 사유는 필수입니다.");
        }
        String normalized = reason.strip();
        if (normalized.length() > REASON_MAX_LENGTH) {
            throw new IllegalArgumentException("재판정 사유는 " + REASON_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return normalized;
    }

    /**
     * 항목의 유효 상태다. 재판정이 없으면 분류기가 내린 상태 그대로다.
     *
     * <p>승인 경로와 승인 판정 집계가 모두 이 규칙을 따른다. 유효 상태가
     * {@link BrokerOrderStagingStatus#STAGED}인 항목만 반영 후보다.
     */
    public static BrokerOrderStagingStatus effectiveStatusOf(
            BrokerOrderImportItem item,
            BrokerOrderImportItemOverride override
    ) {
        return override == null ? item.getStagingStatus() : override.getResultingStagingStatus();
    }

    public Long getId() {
        return id;
    }

    public BrokerOrderImportRun getRun() {
        return run;
    }

    public BrokerOrderImportItem getItem() {
        return item;
    }

    public String getExternalOrderId() {
        return externalOrderId;
    }

    public BrokerOrderStagingStatus getOriginalStagingStatus() {
        return originalStagingStatus;
    }

    public BrokerOrderSkipReason getOriginalSkipReasonCode() {
        return originalSkipReasonCode;
    }

    public BrokerOrderOverrideDecision getDecision() {
        return decision;
    }

    public BrokerOrderStagingStatus getResultingStagingStatus() {
        return resultingStagingStatus;
    }

    public String getReason() {
        return reason;
    }

    public Long getCreatedByMemberId() {
        return createdByMember.getId();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
