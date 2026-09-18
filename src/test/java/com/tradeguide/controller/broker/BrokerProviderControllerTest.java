package com.tradeguide.controller.broker;

import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.BrokerProviderCapability;
import com.tradeguide.service.broker.BrokerProviderRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BrokerProviderController.class)
@AutoConfigureMockMvc(addFilters = false)
class BrokerProviderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrokerProviderRegistry brokerProviderRegistry;

    @Test
    void listsProviderCapabilitiesWithoutCredentialsOrInternals() throws Exception {
        when(brokerProviderRegistry.isConnectable(BrokerProvider.TOSS_SECURITIES))
                .thenReturn(true);

        mockMvc.perform(get("/api/broker-providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].provider").value("TOSS_SECURITIES"))
                .andExpect(jsonPath("$[0].displayName").value("토스증권"))
                .andExpect(jsonPath("$[0].connectable").value(true))
                .andExpect(jsonPath("$[0].supportedCapabilities")
                        .value(containsInAnyOrder(
                                "CONNECTION_VERIFICATION", "HOLDING_SNAPSHOT", "TRANSACTION_HISTORY_IMPORT", "ORDER_SUBMISSION")))
                .andExpect(jsonPath("$[0].clientId").doesNotExist())
                .andExpect(jsonPath("$[0].clientSecret").doesNotExist());
    }

    @Test
    void reportsProviderAsNotConnectableWhenNoVerifierIsWired() throws Exception {
        when(brokerProviderRegistry.isConnectable(BrokerProvider.TOSS_SECURITIES))
                .thenReturn(false);

        mockMvc.perform(get("/api/broker-providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].connectable").value(false));
    }

    @Test
    void describesCredentialFormSoTheBrowserNeedsNoProviderConditional() throws Exception {
        when(brokerProviderRegistry.isConnectable(BrokerProvider.TOSS_SECURITIES))
                .thenReturn(true);

        mockMvc.perform(get("/api/broker-providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].credentialFields.length()").value(2))
                .andExpect(jsonPath("$[0].credentialFields[0].key").value("clientId"))
                .andExpect(jsonPath("$[0].credentialFields[0].label").value("Client ID"))
                .andExpect(jsonPath("$[0].credentialFields[0].type").value("TEXT"))
                .andExpect(jsonPath("$[0].credentialFields[0].required").value(true))
                .andExpect(jsonPath("$[0].credentialFields[0].placeholder").value(not(emptyString())))
                .andExpect(jsonPath("$[0].credentialFields[0].hint").value(not(emptyString())))
                .andExpect(jsonPath("$[0].credentialFields[1].key").value("clientSecret"))
                .andExpect(jsonPath("$[0].credentialFields[1].label").value("Client Secret"))
                .andExpect(jsonPath("$[0].credentialFields[1].type").value("SECRET"))
                .andExpect(jsonPath("$[0].credentialFields[1].required").value(true));
    }

    @Test
    void credentialFieldsNeverCarryStoredOrMaskedValues() throws Exception {
        when(brokerProviderRegistry.isConnectable(BrokerProvider.TOSS_SECURITIES))
                .thenReturn(true);

        mockMvc.perform(get("/api/broker-providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].credentialFields[*].value").doesNotExist())
                .andExpect(jsonPath("$[0].credentialFields[*].currentValue").doesNotExist())
                .andExpect(jsonPath("$[0].credentialFields[*].maskedValue").doesNotExist())
                .andExpect(jsonPath("$[0].credentialFields[*].defaultValue").doesNotExist());
    }

    @Test
    void reportsTheMarketsThisServiceCanProcessForTheProvider() throws Exception {
        when(brokerProviderRegistry.isConnectable(BrokerProvider.TOSS_SECURITIES))
                .thenReturn(true);

        mockMvc.perform(get("/api/broker-providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].supportedMarkets").value(containsInAnyOrder("US", "KR")));
    }

    /**
     * 조회할 수 있는 시장과 원장에 쓸 수 있는 시장은 다른 집합이다. 화면이 둘을 구분해
     * 보여 줄 수 있어야 "왜 이 종목만 반영이 안 되나"에 답할 수 있다.
     */
    @Test
    void reportsLedgerWritableMarketsSeparatelyFromFetchableMarkets() throws Exception {
        when(brokerProviderRegistry.isConnectable(BrokerProvider.TOSS_SECURITIES))
                .thenReturn(true);

        mockMvc.perform(get("/api/broker-providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ledgerWritableMarkets").value(contains("US")));
    }

    /**
     * 화면은 선언이 아니라 실제 가용 기능으로 버튼을 켠다. 어댑터가 등록되지 않은 배포에서
     * 선언만 보고 판단하면, 눌러야 503을 받는 버튼이 활성화된 채로 나간다.
     */
    @Test
    void reportsAvailableCapabilitiesSeparatelyFromDeclaredCapabilities() throws Exception {
        when(brokerProviderRegistry.isConnectable(BrokerProvider.TOSS_SECURITIES))
                .thenReturn(true);
        when(brokerProviderRegistry.availableCapabilities(BrokerProvider.TOSS_SECURITIES))
                .thenReturn(Set.of(BrokerProviderCapability.CONNECTION_VERIFICATION));

        mockMvc.perform(get("/api/broker-providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].supportedCapabilities")
                        .value(containsInAnyOrder(
                                "CONNECTION_VERIFICATION", "HOLDING_SNAPSHOT", "TRANSACTION_HISTORY_IMPORT", "ORDER_SUBMISSION")))
                .andExpect(jsonPath("$[0].availableCapabilities").value(contains("CONNECTION_VERIFICATION")));
    }

    @Test
    void reportsNoAvailableCapabilityWhenNoAdapterIsWired() throws Exception {
        when(brokerProviderRegistry.isConnectable(BrokerProvider.TOSS_SECURITIES))
                .thenReturn(false);
        when(brokerProviderRegistry.availableCapabilities(BrokerProvider.TOSS_SECURITIES))
                .thenReturn(Set.of());

        mockMvc.perform(get("/api/broker-providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].availableCapabilities").isEmpty());
    }
}
