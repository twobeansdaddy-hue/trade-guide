package com.tradeguide.controller.broker;

import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.dto.broker.BrokerProviderCapabilityResponse;
import com.tradeguide.service.broker.BrokerProviderRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 증권사 연결 화면이 동적으로 제공자 선택지와 지원 기능을 구성할 수 있도록
 * 안전한(자격 증명·내부 구현이 없는) 제공자 카탈로그를 노출한다.
 */
@RestController
@RequestMapping("/api/broker-providers")
public class BrokerProviderController {

    private final BrokerProviderRegistry brokerProviderRegistry;

    public BrokerProviderController(BrokerProviderRegistry brokerProviderRegistry) {
        this.brokerProviderRegistry = brokerProviderRegistry;
    }

    @GetMapping
    public List<BrokerProviderCapabilityResponse> getBrokerProviders() {
        return Arrays.stream(BrokerProvider.values())
                .map(provider -> new BrokerProviderCapabilityResponse(
                        provider,
                        brokerProviderRegistry.isConnectable(provider),
                        brokerProviderRegistry.availableCapabilities(provider)))
                .toList();
    }
}
