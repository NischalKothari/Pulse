package com.pulse.api.rateLimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "pulse.rate-limit")
public class RateLimitProperties {
    private PolicyProperties login = new PolicyProperties(5, 1, Duration.ofSeconds(12));
    private PolicyProperties registration = new PolicyProperties(3, 1, Duration.ofMinutes(20));
    private PolicyProperties authenticatedApi = new PolicyProperties(60, 1, Duration.ofSeconds(1));
    private int maxEntries = 100_000;
    private Duration idleTtl = Duration.ofMinutes(30);
    private Duration cleanupInterval = Duration.ofMinutes(1);

    public PolicyProperties getLogin() { return login; }
    public void setLogin(PolicyProperties login) { this.login = login; }
    public PolicyProperties getRegistration() { return registration; }
    public void setRegistration(PolicyProperties registration) { this.registration = registration; }
    public PolicyProperties getAuthenticatedApi() { return authenticatedApi; }
    public void setAuthenticatedApi(PolicyProperties authenticatedApi) { this.authenticatedApi = authenticatedApi; }
    public int getMaxEntries() { return maxEntries; }
    public void setMaxEntries(int maxEntries) { this.maxEntries = maxEntries; }
    public Duration getIdleTtl() { return idleTtl; }
    public void setIdleTtl(Duration idleTtl) { this.idleTtl = idleTtl; }
    public Duration getCleanupInterval() { return cleanupInterval; }
    public void setCleanupInterval(Duration cleanupInterval) { this.cleanupInterval = cleanupInterval; }

    public static class PolicyProperties {
        private int capacity;
        private int refillTokens;
        private Duration refillPeriod;

        public PolicyProperties() { }

        public PolicyProperties(int capacity, int refillTokens, Duration refillPeriod) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillPeriod = refillPeriod;
        }

        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public int getRefillTokens() { return refillTokens; }
        public void setRefillTokens(int refillTokens) { this.refillTokens = refillTokens; }
        public Duration getRefillPeriod() { return refillPeriod; }
        public void setRefillPeriod(Duration refillPeriod) { this.refillPeriod = refillPeriod; }

        public TokenBucketPolicy toPolicy() {
            return new TokenBucketPolicy(capacity, refillTokens, refillPeriod);
        }
    }
}
