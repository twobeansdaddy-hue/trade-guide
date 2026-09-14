package com.tradeguide;

import com.tradeguide.config.BrokerCredentialKeyringProperties;
import com.tradeguide.config.BrokerKeyRotationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({BrokerCredentialKeyringProperties.class, BrokerKeyRotationProperties.class})
public class TradeGuideApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradeGuideApplication.class, args);
    }
}
