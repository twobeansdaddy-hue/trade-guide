package com.tradeguide.domain.broker;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 증권사 이력 페이징의 요청·응답 계약을 검증한다. 핵심은 두 가지다.
 * 상한을 넘는 크기를 조용히 잘라 주지 않는 것, 그리고 응답만 보고 "이게 전부인지" 알 수 있는 것.
 */
class BrokerHistoryPagingTest {

    @Test
    void fillsInDefaultsWhenPageAndSizeAreOmitted() {
        BrokerHistoryPageRequest pageRequest = BrokerHistoryPageRequest.of(null, null);

        assertThat(pageRequest.page()).isEqualTo(BrokerHistoryPageRequest.DEFAULT_PAGE);
        assertThat(pageRequest.size()).isEqualTo(BrokerHistoryPageRequest.DEFAULT_SIZE);
    }

    @Test
    void keepsExplicitPageAndSize() {
        BrokerHistoryPageRequest pageRequest = BrokerHistoryPageRequest.of(3, 50);

        assertThat(pageRequest.page()).isEqualTo(3);
        assertThat(pageRequest.size()).isEqualTo(50);
    }

    @Test
    void acceptsExactlyTheMaximumSize() {
        assertThat(BrokerHistoryPageRequest.of(0, BrokerHistoryPageRequest.MAX_SIZE).size())
                .isEqualTo(BrokerHistoryPageRequest.MAX_SIZE);
    }

    /** 상한을 넘는 크기를 조용히 잘라 주면 호출자는 자기가 받은 것이 전부라고 오해한다. */
    @Test
    void rejectsASizeAboveTheMaximumInsteadOfTruncatingIt() {
        assertThatThrownBy(() -> BrokerHistoryPageRequest.of(0, BrokerHistoryPageRequest.MAX_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("페이지 크기는 1 이상 " + BrokerHistoryPageRequest.MAX_SIZE + " 이하여야 합니다.");
    }

    @Test
    void rejectsANonPositiveSize() {
        assertThatThrownBy(() -> BrokerHistoryPageRequest.of(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANegativePageNumber() {
        assertThatThrownBy(() -> BrokerHistoryPageRequest.of(-1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("페이지 번호는 0 이상이어야 합니다.");
    }

    @Test
    void carriesPagePositionTotalCountAndNextPageFlag() {
        BrokerHistoryPage<String> page = new BrokerHistoryPage<>(List.of("a", "b"), 1, 2, 7L, true);

        assertThat(page.items()).containsExactly("a", "b");
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(7);
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    void copiesItemsSoTheCallerCannotMutateAReturnedPage() {
        List<String> mutable = new ArrayList<>(List.of("a"));
        BrokerHistoryPage<String> page = new BrokerHistoryPage<>(mutable, 0, 20, 1L, false);

        mutable.add("b");

        assertThat(page.items()).containsExactly("a");
    }

    @Test
    void describesAnEmptyPageWithTheRequestedPosition() {
        BrokerHistoryPage<String> page = BrokerHistoryPage.empty(new BrokerHistoryPageRequest(2, 5));

        assertThat(page.items()).isEmpty();
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(5);
        assertThat(page.totalElements()).isZero();
        assertThat(page.hasNext()).isFalse();
    }
}
