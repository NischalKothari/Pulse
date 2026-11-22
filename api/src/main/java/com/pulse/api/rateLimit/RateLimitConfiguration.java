package com.pulse.api.rateLimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {
    @Bean
    public TokenBucketStore tokenBucketStore(RateLimitProperties properties) {
        return new InMemoryTokenBucketStore(properties);
    }
}
