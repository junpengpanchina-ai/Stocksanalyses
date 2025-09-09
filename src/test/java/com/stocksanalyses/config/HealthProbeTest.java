package com.stocksanalyses.config;

import com.stocksanalyses.config.HealthConfig.LivenessHealthIndicator;
import com.stocksanalyses.config.HealthConfig.ReadinessHealthIndicator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.*;

public class HealthProbeTest {

    @Test
    public void testLivenessHealthIndicator() {
        LivenessHealthIndicator indicator = new LivenessHealthIndicator();
        
        // 测试初始状态
        Health health = indicator.health();
        assertEquals(Status.UP, health.getStatus());
        assertTrue(health.getDetails().containsKey("timestamp"));
        assertTrue(health.getDetails().containsKey("status"));
        assertEquals("ALIVE", health.getDetails().get("status"));
        
        // 测试设置为dead状态
        indicator.setAlive(false);
        health = indicator.health();
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("DEAD", health.getDetails().get("status"));
    }

    @Test
    public void testReadinessHealthIndicator() {
        ReadinessHealthIndicator indicator = new ReadinessHealthIndicator();
        
        // 测试初始状态（应该是DOWN，因为需要30秒启动时间）
        Health health = indicator.health();
        assertEquals(Status.DOWN, health.getStatus());
        assertTrue(health.getDetails().containsKey("timestamp"));
        assertTrue(health.getDetails().containsKey("status"));
        assertEquals("STARTING", health.getDetails().get("status"));
        
        // 测试设置为ready状态
        indicator.setReady(true);
        health = indicator.health();
        assertEquals(Status.UP, health.getStatus());
        assertEquals("READY", health.getDetails().get("status"));
    }
}
