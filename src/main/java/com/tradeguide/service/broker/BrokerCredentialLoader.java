package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerCredentials;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 저장된 증권사 자격 증명 암호문을 복호화해 어댑터가 쓸 수 있는 형태로 돌려주는 유일한 지점이다.
 *
 * <p>복호화 코드를 여기 하나로 모으는 이유는 두 가지다. 첫째, 자격 증명 항목 수는 제공자마다
 * 다르고 앞으로 늘어난다. 복호화가 서비스마다 복사돼 있으면 항목이 하나 늘 때마다 모든 사본을
 * 고쳐야 하고, 하나를 빠뜨리면 그 경로만 조용히 옛 항목으로 호출한다. 둘째, 평문이 만들어지는
 * 지점이 한 곳이어야 "평문은 어디까지 흐르는가"를 코드로 답할 수 있다.
 *
 * <p>여기서 만들어진 평문은 어댑터 호출 인자로만 나간다. 저장·로그·응답·예외 메시지 어디에도
 * 담기지 않는다.
 */
@Component
public class BrokerCredentialLoader {

    private final BrokerCredentialCipher brokerCredentialCipher;

    public BrokerCredentialLoader(BrokerCredentialCipher brokerCredentialCipher) {
        this.brokerCredentialCipher = brokerCredentialCipher;
    }

    /**
     * 연결에 저장된 자격 증명 항목을 전부 복호화한다.
     *
     * <p>명세와의 대조는 여기서 하지 않는다. 저장 시점에 이미 화이트리스트로 걸렀고,
     * 명세가 나중에 넓어졌을 때 아직 값이 없는 항목 때문에 기존 연결의 조회 경로가 통째로
     * 막히는 편이 더 나쁘다. 어떤 항목이 없다는 사실은 그 값을 실제로 요구하는 어댑터가
     * {@link BrokerCredentials#require(String)}에서 알린다.
     */
    public BrokerCredentials load(BrokerConnection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("증권사 연결 정보가 필요합니다.");
        }

        List<BrokerConnectionSecretValue> secretValues = connection.getSecretValues();
        if (secretValues.isEmpty()) {
            throw new IllegalStateException("증권사 자격 증명이 저장되어 있지 않습니다.");
        }

        Map<String, String> values = new LinkedHashMap<>();
        secretValues.forEach(secretValue -> values.put(
                secretValue.getFieldKey(),
                brokerCredentialCipher.decrypt(new EncryptedBrokerCredential(
                        secretValue.getCiphertext(),
                        secretValue.getInitializationVector(),
                        secretValue.getEncryptionKeyVersion()
                ))
        ));

        return new BrokerCredentials(values);
    }

    /**
     * 계좌 일련번호를 복호화한다.
     *
     * <p>일련번호는 자격 증명이 아니라 계좌 식별자이므로 {@link BrokerCredentials}에 섞지 않고
     * 별도로 꺼낸다. 어댑터 계약에서도 분리된 인자로 남는다. 복호화 코드가 흩어지지 않도록
     * 이 클래스에 함께 둔다.
     */
    public String loadAccountSequence(BrokerAccount account) {
        if (account == null) {
            throw new IllegalArgumentException("증권사 계좌 정보가 필요합니다.");
        }

        return brokerCredentialCipher.decrypt(new EncryptedBrokerCredential(
                account.getEncryptedAccountSequence(),
                account.getAccountSequenceInitializationVector(),
                account.getEncryptionKeyVersion()
        ));
    }
}
