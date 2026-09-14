package com.tradeguide.domain.broker;

/**
 * 증권사 이력 조회의 서버 페이징 요청이다.
 *
 * <p>개시 잔고 승인 이력과 주문 이력 가져오기 실행 이력은 시간이 지날수록 계속 쌓인다.
 * 과거 전체를 한 번에 돌려주면 응답 크기와 조회 시간이 사용자 사용 기간에 비례해 늘어나므로,
 * 두 이력 모두 이 요청으로 페이지 단위로만 읽는다.
 *
 * <p>{@code size} 상한을 두는 이유는 클라이언트가 큰 값을 보내 전체 조회로 되돌리는 것을
 * 막기 위해서다. 상한을 넘으면 조용히 잘라 주지 않고 잘못된 요청으로 알린다. 조용히 자르면
 * 호출자는 자기가 받은 것이 전부라고 오해한다.
 */
public record BrokerHistoryPageRequest(int page, int size) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public BrokerHistoryPageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("페이지 크기는 1 이상 " + MAX_SIZE + " 이하여야 합니다.");
        }
    }

    /** 생략된 값에 기본값을 채워 만든다. 컨트롤러가 널 처리를 반복하지 않게 한다. */
    public static BrokerHistoryPageRequest of(Integer page, Integer size) {
        return new BrokerHistoryPageRequest(
                page == null ? DEFAULT_PAGE : page,
                size == null ? DEFAULT_SIZE : size
        );
    }
}
