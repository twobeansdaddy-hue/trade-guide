package com.tradeguide.domain.broker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 증권사 자격 증명 암호화 키 로테이션 실행 한 건의 감사 기록이다
 * (docs/agent-tasks/broker-credential-encryption-key-rotation.md §8, §10).
 *
 * <p>ciphertext·평문·키 값을 담지 않는다. provider·시각·건수·상태만 남기므로 그대로
 * 인시던트 문서에 인용해도 안전하다. 이 행 자체가 {@link BrokerKeyRotationRunStatus#IN_PROGRESS}인
 * 동안 부분 유니크 인덱스로 동시 실행을 막는 상호 배제 잠금 역할도 한다(§6.3).
 *
 * <p>재시도는 이 행을 새로 만들지 않고 <b>같은 실행을 이어서</b> 진행한다(§6.2, §6.4).
 * 그래서 진행 건수 갱신 메서드는 절대값이 아니라 배치 한 건의 증분을 더한다.
 */
@Entity
@Table(name = "broker_key_rotation_runs")
public class BrokerKeyRotationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_key_version", nullable = false)
    private int targetKeyVersion;

    @Column(name = "source_key_versions_observed", length = 100)
    private String sourceKeyVersionsObserved;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BrokerKeyRotationRunStatus status;

    @Column(name = "secret_values_total", nullable = false)
    private int secretValuesTotal;

    @Column(name = "secret_values_processed", nullable = false)
    private int secretValuesProcessed;

    @Column(name = "secret_values_skipped", nullable = false)
    private int secretValuesSkipped;

    @Column(name = "secret_values_failed", nullable = false)
    private int secretValuesFailed;

    @Column(name = "accounts_total", nullable = false)
    private int accountsTotal;

    @Column(name = "accounts_processed", nullable = false)
    private int accountsProcessed;

    @Column(name = "accounts_skipped", nullable = false)
    private int accountsSkipped;

    @Column(name = "accounts_failed", nullable = false)
    private int accountsFailed;

    @Column(name = "triggered_by", nullable = false, length = 100)
    private String triggeredBy;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    protected BrokerKeyRotationRun() {
    }

    private BrokerKeyRotationRun(
            int targetKeyVersion,
            List<Integer> sourceKeyVersionsObserved,
            int secretValuesTotal,
            int accountsTotal,
            String triggeredBy,
            LocalDateTime startedAt
    ) {
        this.targetKeyVersion = targetKeyVersion;
        this.sourceKeyVersionsObserved = sourceKeyVersionsObserved.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        this.secretValuesTotal = secretValuesTotal;
        this.accountsTotal = accountsTotal;
        this.triggeredBy = triggeredBy;
        this.startedAt = startedAt;
        this.status = BrokerKeyRotationRunStatus.IN_PROGRESS;
    }

    /** 사전 점검을 모두 통과한 뒤에만 호출한다. 저장 즉시 커밋해 상호 배제 잠금 역할을 하게 한다(§6.1). */
    public static BrokerKeyRotationRun start(
            int targetKeyVersion,
            List<Integer> sourceKeyVersionsObserved,
            int secretValuesTotal,
            int accountsTotal,
            String triggeredBy,
            LocalDateTime startedAt
    ) {
        if (sourceKeyVersionsObserved == null || triggeredBy == null || triggeredBy.isBlank() || startedAt == null) {
            throw new IllegalArgumentException("키 로테이션 실행 정보가 올바르지 않습니다.");
        }
        return new BrokerKeyRotationRun(
                targetKeyVersion, sourceKeyVersionsObserved, secretValuesTotal, accountsTotal, triggeredBy, startedAt);
    }

    /** 사전 점검이 실패했을 때만 호출한다. 어떤 행도 손대지 않았음을 뜻하는 기록이다(§5). */
    public static BrokerKeyRotationRun failedPreflight(
            int targetKeyVersion,
            String triggeredBy,
            LocalDateTime startedAt,
            String failureReason
    ) {
        BrokerKeyRotationRun run = new BrokerKeyRotationRun(
                targetKeyVersion, List.of(), 0, 0, triggeredBy, startedAt);
        run.status = BrokerKeyRotationRunStatus.FAILED_PREFLIGHT;
        run.completedAt = startedAt;
        run.failureReason = failureReason;
        return run;
    }

    /** 값 단위 배치 한 건이 끝날 때마다 증분을 더한다. 절대값으로 덮어쓰지 않는다(§6.2). */
    public void recordSecretValueBatch(int processed, int skipped, int failed) {
        this.secretValuesProcessed += processed;
        this.secretValuesSkipped += skipped;
        this.secretValuesFailed += failed;
    }

    /** 계좌 단위 배치 한 건이 끝날 때마다 증분을 더한다. 절대값으로 덮어쓰지 않는다(§6.2). */
    public void recordAccountBatch(int processed, int skipped, int failed) {
        this.accountsProcessed += processed;
        this.accountsSkipped += skipped;
        this.accountsFailed += failed;
    }

    /** 두 표 모두 목표 버전이 아닌 행이 0건이 됐을 때만 호출한다(§6.1-4). */
    public void markCompleted(LocalDateTime completedAt) {
        this.status = BrokerKeyRotationRunStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    public Long getId() {
        return id;
    }

    public int getTargetKeyVersion() {
        return targetKeyVersion;
    }

    public String getSourceKeyVersionsObserved() {
        return sourceKeyVersionsObserved;
    }

    public BrokerKeyRotationRunStatus getStatus() {
        return status;
    }

    public int getSecretValuesTotal() {
        return secretValuesTotal;
    }

    public int getSecretValuesProcessed() {
        return secretValuesProcessed;
    }

    public int getSecretValuesSkipped() {
        return secretValuesSkipped;
    }

    public int getSecretValuesFailed() {
        return secretValuesFailed;
    }

    public int getAccountsTotal() {
        return accountsTotal;
    }

    public int getAccountsProcessed() {
        return accountsProcessed;
    }

    public int getAccountsSkipped() {
        return accountsSkipped;
    }

    public int getAccountsFailed() {
        return accountsFailed;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    /** 완료 보고에서 "누락 없이 다음 회차에 다시 훑어야 하는 행이 있었다"는 신호다(§6.1-4). */
    public boolean isPartiallySkipped() {
        return secretValuesSkipped > 0 || accountsSkipped > 0;
    }
}
