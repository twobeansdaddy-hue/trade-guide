package com.tradeguide.domain.broker;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 한 증권사 연결의 복호화된 자격 증명 묶음이다. 필드 구성은 제공자 명세
 * ({@link BrokerProvider#getCredentialFields()})가 정하며, 이 값 객체는 그 명세를
 * 해석하지 않고 "키 → 값" 형태로만 담는다.
 *
 * <p>제공자마다 필드 수가 다르므로 어댑터 계약은 {@code (clientId, clientSecret)} 같은
 * 고정 인자를 받지 않고 이 객체를 받는다. 두 번째 증권사가 세 번째 필드를 요구해도
 * 인터페이스 시그니처는 그대로다.
 *
 * <p><b>보안 규칙.</b> 이 객체는 평문 자격 증명을 들고 있다. 요청 처리 구간과 어댑터 호출
 * 구간 밖으로 나가지 않으며, 어떤 응답에도 담기지 않는다. 값 유출의 가장 흔한 경로가 로깅과
 * 예외 메시지이므로 {@link #toString()}은 키 목록만 노출하도록 재정의한다.
 * {@code equals}/{@code hashCode}는 레코드 기본 구현을 쓴다. 값을 문자열로 드러내지 않아
 * 유출 경로가 아니고, 테스트가 자격 증명 묶음을 그대로 비교할 수 있어야 하기 때문이다.
 *
 * @param values 자격 증명 키와 평문 값. 방어적으로 복사해 불변으로 보관한다.
 */
public record BrokerCredentials(Map<String, String> values) {

    public BrokerCredentials {
        if (values == null) {
            throw new IllegalArgumentException("증권사 자격 증명이 필요합니다.");
        }
        values = Map.copyOf(values);
    }

    /**
     * 어댑터가 요구하는 자격 증명 값을 꺼낸다.
     *
     * <p>없는 키는 {@link IllegalStateException}이다. 어댑터가 제공자 명세에 없는 키를 읽었거나
     * 저장된 값이 명세와 어긋났다는 뜻이며, 둘 다 사용자 입력 오류가 아니라 계약 위반이다.
     * 예외 메시지에는 키 이름만 담고 값이나 다른 키의 값은 담지 않는다.
     */
    public String require(String fieldKey) {
        String value = values.get(fieldKey);
        if (value == null) {
            throw new IllegalStateException("증권사 자격 증명 항목이 없습니다: " + fieldKey);
        }
        return value;
    }

    /** 담고 있는 자격 증명 키 목록이다. 값은 포함하지 않는다. */
    public Set<String> keys() {
        return values.keySet();
    }

    /** 로그·예외 메시지에 값이 새지 않도록 키 목록만 노출한다. */
    @Override
    public String toString() {
        return "BrokerCredentials(keys=" + new TreeSet<>(values.keySet()) + ")";
    }
}
