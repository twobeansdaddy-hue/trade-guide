package com.tradeguide.dto.broker;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 주문 이력 미리보기 생성 요청이다.
 *
 * <p>필드 이름에 {@code ordered}를 남긴 것은 취향이 아니다. 증권사는 <b>체결일이 아니라
 * 주문일</b> 기준으로 조회하고, 미국 시장은 KST로 보면 두 날짜가 하루 어긋나는 일이 흔하다.
 * {@code from}/{@code to}라고만 두면 프론트엔드도 사용자도 체결일 기준이라고 읽는다.
 */
public class BrokerOrderImportCreateRequest {

    @NotNull(message = "주문 조회 시작일은 필수입니다.")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private final LocalDate orderedFrom;

    @NotNull(message = "주문 조회 종료일은 필수입니다.")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private final LocalDate orderedTo;

    public BrokerOrderImportCreateRequest(LocalDate orderedFrom, LocalDate orderedTo) {
        this.orderedFrom = orderedFrom;
        this.orderedTo = orderedTo;
    }

    public LocalDate getOrderedFrom() {
        return orderedFrom;
    }

    public LocalDate getOrderedTo() {
        return orderedTo;
    }
}
