package com.tradeguide.domain.broker;

/**
 * 한 증권사 제공자가 연결 시 요구하는 자격 증명 입력 칸 하나의 명세다.
 *
 * <p>이 명세는 "무엇을 입력받아야 하는가"만 설명한다. 저장된 자격 증명 값이나
 * 마스킹된 값, 실제 예시 키를 담지 않는다. {@code placeholder}는 형식 안내 문구이며
 * 실제 자격 증명이어서는 안 된다.
 *
 * @param key         연결 생성 요청 본문의 속성 이름과 같은 안정적인 키다.
 *                    브라우저는 이 키로 요청 본문을 구성하므로 임의로 바꾸지 않는다.
 * @param label       화면에 표시할 한국어 라벨이다.
 * @param type        입력 칸의 형태다. 비밀값은 {@link BrokerCredentialFieldType#SECRET}이다.
 * @param required    입력이 필수인지 여부다.
 * @param placeholder 형식 안내 문구다. 실제 자격 증명을 담지 않는다.
 * @param hint        입력 값을 어디서 얻는지 등 보조 설명이다. 실제 자격 증명을 담지 않는다.
 */
public record BrokerCredentialField(
        String key,
        String label,
        BrokerCredentialFieldType type,
        boolean required,
        String placeholder,
        String hint
) {
    public BrokerCredentialField {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("자격 증명 입력 칸 키는 필수입니다.");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("자격 증명 입력 칸 라벨은 필수입니다.");
        }
        if (type == null) {
            throw new IllegalArgumentException("자격 증명 입력 칸 유형은 필수입니다.");
        }
    }
}
