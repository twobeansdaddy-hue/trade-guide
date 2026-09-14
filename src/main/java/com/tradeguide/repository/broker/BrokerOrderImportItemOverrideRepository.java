package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerOrderImportItemOverride;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrokerOrderImportItemOverrideRepository extends JpaRepository<BrokerOrderImportItemOverride, Long> {

    /** 항목 하나에 재판정은 한 건뿐이다. 유니크 제약이 이를 보장한다. */
    Optional<BrokerOrderImportItemOverride> findByItem_Id(Long itemId);

    /**
     * 승인 경로가 실행의 재판정 전부를 읽는다. 재판정은 의심 항목에만 붙으므로 건수 상한이
     * 의심 건수와 같다.
     *
     * <p>{@code run.id}로만 좁히므로 호출 전에 그 실행이 요청한 포트폴리오의 것인지 확인해야 한다.
     */
    List<BrokerOrderImportItemOverride> findAllByRun_Id(Long runId);

    /** 감사 이력 조회용 페이지다. 소유권 확인은 호출자 책임이다. */
    Page<BrokerOrderImportItemOverride> findAllByRun_Id(Long runId, Pageable pageable);
}
