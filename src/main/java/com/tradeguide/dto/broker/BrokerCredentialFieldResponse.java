package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerCredentialField;
import com.tradeguide.domain.broker.BrokerCredentialFieldType;

/**
 * 연결 화면이 제공자별 분기 없이 입력 칸 하나를 그릴 수 있게 하는 안전한 명세 응답이다.
 * 저장된 자격 증명 값이나 마스킹된 값을 담지 않으며, 값을 되돌려 달라고 요청하지도 않는다.
 */
public class BrokerCredentialFieldResponse {

    private final String key;
    private final String label;
    private final BrokerCredentialFieldType type;
    private final boolean required;
    private final String placeholder;
    private final String hint;

    public BrokerCredentialFieldResponse(BrokerCredentialField field) {
        this.key = field.key();
        this.label = field.label();
        this.type = field.type();
        this.required = field.required();
        this.placeholder = field.placeholder();
        this.hint = field.hint();
    }

    /** 연결 생성 요청 본문의 속성 이름과 같은 안정적인 키다. */
    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    /** {@code SECRET}인 입력은 브라우저가 마스킹 입력으로 그려야 한다. */
    public BrokerCredentialFieldType getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    /** 형식 안내 문구다. 실제 자격 증명을 담지 않는다. */
    public String getPlaceholder() {
        return placeholder;
    }

    /** 보조 설명이다. 실제 자격 증명을 담지 않는다. */
    public String getHint() {
        return hint;
    }
}
