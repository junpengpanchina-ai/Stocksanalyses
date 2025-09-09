package com.stocksanalyses.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Configuration
public class HealthConfig {

    @Bean
    public LivenessHealthIndicator livenessHealthIndicator() {
        return new LivenessHealthIndicator();
    }

    @Bean
    public ReadinessHealthIndicator readinessHealthIndicator() {
        return new ReadinessHealthIndicator();
    }

    @Component
    public static class LivenessHealthIndicator implements HealthIndicator {
        private final AtomicBoolean isAlive = new AtomicBoolean(true);

        @Override
        public Health health() {
            if (isAlive.get()) {
                return Health.up()
                    .withDetail("timestamp", LocalDateTime.now())
                    .withDetail("status", "ALIVE")
                    .build();
            } else {
                return Health.down()
                    .withDetail("timestamp", LocalDateTime.now())
                    .withDetail("status", "DEAD")
                    .build();
            }
        }

        public void setAlive(boolean alive) {
            isAlive.set(alive);
        }
    }

    @Component
    public static class ReadinessHealthIndicator implements HealthIndicator {
        private final AtomicBoolean isReady = new AtomicBoolean(false);
        private LocalDateTime startupTime = LocalDateTime.now();

        @Override
        public Health health() {
            // 应用启动后30秒才认为ready
            boolean ready = isReady.get() || 
                LocalDateTime.now().isAfter(startupTime.plusSeconds(30));
            
            if (ready) {
                return Health.up()
                    .withDetail("timestamp", LocalDateTime.now())
                    .withDetail("status", "READY")
                    .withDetail("startup_time", startupTime)
                    .build();
            } else {
                return Health.down()
                    .withDetail("timestamp", LocalDateTime.now())
                    .withDetail("status", "STARTING")
                    .withDetail("startup_time", startupTime)
                    .build();
            }
        }

        public void setReady(boolean ready) {
            isReady.set(ready);
        }
    }
}
