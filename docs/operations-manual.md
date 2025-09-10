# KLine Analytics 运维手册

## 目录
- [1. 系统概述](#1-系统概述)
- [2. 部署指南](#2-部署指南)
- [3. 监控告警](#3-监控告警)
- [4. 数据源配置](#4-数据源配置)
- [5. 分割服务依赖](#5-分割服务依赖)
- [6. 性能基准与SLO](#6-性能基准与slo)
- [7. 故障排除](#7-故障排除)
- [8. 维护操作](#8-维护操作)

## 1. 系统概述

### 1.1 架构概览
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   前端界面      │    │   API网关       │    │   业务服务      │
│   (ECharts)     │◄──►│   (Spring Boot) │◄──►│   (Java Core)   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                │                        │
                                ▼                        ▼
                       ┌─────────────────┐    ┌─────────────────┐
                       │   数据存储      │    │   消息队列      │
                       │ (TimescaleDB)   │    │    (Kafka)      │
                       └─────────────────┘    └─────────────────┘
```

### 1.2 核心组件
- **前端**: HTML5 + ECharts + JavaScript
- **API层**: Spring Boot 3 + Java 21
- **业务核心**: 形态识别 + 策略引擎 + 回测引擎
- **数据层**: TimescaleDB + PostgreSQL + Redis
- **监控**: Prometheus + Grafana + OpenTelemetry

## 2. 部署指南

### 2.1 Helm参数配置

#### 2.1.1 基础配置
```yaml
# values.yaml
replicaCount: 3
image:
  repository: kline-analytics
  tag: "1.0.0"
  pullPolicy: IfNotPresent

# 资源配置
resources:
  limits:
    cpu: 2000m
    memory: 4Gi
  requests:
    cpu: 1000m
    memory: 2Gi

# Java堆配置
java:
  heap:
    initial: "1g"
    maximum: "2g"
  gc:
    type: "G1GC"
    options: "-XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

#### 2.1.2 环境特定配置

**开发环境 (values-development.yaml)**
```yaml
replicaCount: 1
resources:
  limits:
    cpu: 500m
    memory: 1Gi
  requests:
    cpu: 250m
    memory: 512Mi
java:
  heap:
    initial: "256m"
    maximum: "512m"
```

**生产环境 (values-production.yaml)**
```yaml
replicaCount: 3
resources:
  limits:
    cpu: 2000m
    memory: 4Gi
  requests:
    cpu: 1000m
    memory: 2Gi
java:
  heap:
    initial: "1g"
    maximum: "2g"
podAntiAffinity:
  enabled: true
  type: "hard"
```

### 2.2 部署命令
```bash
# 开发环境
ENVIRONMENT=development ./scripts/deploy.sh

# 测试环境
ENVIRONMENT=staging ./scripts/deploy.sh

# 生产环境
ENVIRONMENT=production ./scripts/deploy.sh

# 金丝雀部署
ENVIRONMENT=canary ./scripts/deploy.sh
```

### 2.3 健康检查配置
```yaml
healthChecks:
  liveness:
    enabled: true
    initialDelaySeconds: 60
    periodSeconds: 30
    timeoutSeconds: 10
    failureThreshold: 3
    path: /actuator/health/liveness
  readiness:
    enabled: true
    initialDelaySeconds: 30
    periodSeconds: 10
    timeoutSeconds: 5
    failureThreshold: 3
    path: /actuator/health/readiness
  startup:
    enabled: true
    initialDelaySeconds: 10
    periodSeconds: 5
    timeoutSeconds: 3
    failureThreshold: 12
    path: /actuator/health/readiness
```

## 3. 监控告警

### 3.1 Prometheus指标名规范

#### 3.1.1 应用指标
```yaml
# HTTP请求指标
kline_analytics_http_requests_total{method,uri,status,outcome}
kline_analytics_http_requests_duration_seconds{method,uri,status}

# 业务指标
kline_analytics_signals_generated_total{strategy,market,type}
kline_analytics_orders_processed_total{strategy,side,status}
kline_analytics_trades_executed_total{strategy,market}

# 撮合引擎指标
kline_analytics_matching_engine_duration_seconds{order_type,side}
kline_analytics_matching_engine_orders_total{order_type,side,status}

# 分割服务指标
kline_analytics_segmentation_requests_total{status,outcome}
kline_analytics_segmentation_duration_seconds{status,outcome}

# OCR服务指标
kline_analytics_ocr_requests_total{status,outcome}
kline_analytics_ocr_duration_seconds{status,outcome}

# 轴检测指标
kline_analytics_axis_detection_requests_total{status,outcome}
kline_analytics_axis_detection_duration_seconds{status,outcome}
```

#### 3.1.2 JVM指标
```yaml
# 内存指标
kline_analytics_jvm_memory_used_bytes{area}
kline_analytics_jvm_memory_max_bytes{area}
kline_analytics_jvm_memory_committed_bytes{area}

# GC指标
kline_analytics_jvm_gc_pause_seconds{gc_type}
kline_analytics_jvm_gc_collections_total{gc_type}

# 线程指标
kline_analytics_jvm_threads_live_threads
kline_analytics_jvm_threads_daemon_threads
```

#### 3.1.3 系统指标
```yaml
# 容器指标
container_cpu_usage_seconds_total{pod,container}
container_memory_usage_bytes{pod,container}
container_fs_usage_bytes{pod,container}

# 网络指标
container_network_receive_bytes_total{pod,interface}
container_network_transmit_bytes_total{pod,interface}
```

### 3.2 告警规则配置

#### 3.2.1 关键告警 (Critical)
```yaml
- alert: KlineAnalyticsDown
  expr: up{job="kline-analytics"} == 0
  for: 1m
  labels:
    severity: critical
    service: kline-analytics
  annotations:
    summary: "KLine Analytics is down"
    description: "KLine Analytics has been down for more than 1 minute."

- alert: KlineAnalyticsHighErrorRate
  expr: rate(kline_analytics_http_requests_total{status=~"5.."}[5m]) / rate(kline_analytics_http_requests_total[5m]) > 0.05
  for: 2m
  labels:
    severity: critical
    service: kline-analytics
  annotations:
    summary: "High error rate detected"
    description: "Error rate is {{ $value | humanizePercentage }} for the last 5 minutes."
```

#### 3.2.2 警告告警 (Warning)
```yaml
- alert: KlineAnalyticsHighResponseTime
  expr: histogram_quantile(0.95, rate(kline_analytics_http_requests_duration_seconds_bucket[5m])) > 1
  for: 3m
  labels:
    severity: warning
    service: kline-analytics
  annotations:
    summary: "High response time detected"
    description: "95th percentile response time is {{ $value }}s for the last 5 minutes."

- alert: KlineAnalyticsHighMemoryUsage
  expr: kline_analytics_jvm_memory_used_bytes / kline_analytics_jvm_memory_max_bytes > 0.85
  for: 5m
  labels:
    severity: warning
    service: kline-analytics
  annotations:
    summary: "High memory usage detected"
    description: "JVM memory usage is {{ $value | humanizePercentage }}."
```

### 3.3 静默规则
```yaml
# 维护模式静默
- alert: KlineAnalyticsMaintenanceMode
  expr: kline_analytics_maintenance_mode == 1
  for: 0s
  labels:
    severity: info
    service: kline-analytics
  annotations:
    summary: "Application in maintenance mode"
    description: "KLine Analytics is currently in maintenance mode."

# 金丝雀部署静默
- alert: KlineAnalyticsCanaryDeployment
  expr: kline_analytics_deployment_type == "canary"
  for: 0s
  labels:
    severity: info
    service: kline-analytics
  annotations:
    summary: "Canary deployment active"
    description: "KLine Analytics is running a canary deployment."
```

### 3.4 黑盒探测配置
```yaml
# 外部健康检查
- alert: KlineAnalyticsBlackboxProbeFailed
  expr: probe_success{job="kline-analytics-blackbox"} == 0
  for: 1m
  labels:
    severity: critical
    service: kline-analytics
  annotations:
    summary: "Blackbox probe failed"
    description: "Blackbox probe failed for {{ $labels.instance }} for more than 1 minute."

- alert: KlineAnalyticsBlackboxProbeSlow
  expr: probe_duration_seconds{job="kline-analytics-blackbox"} > 5
  for: 2m
  labels:
    severity: warning
    service: kline-analytics
  annotations:
    summary: "Blackbox probe slow"
    description: "Blackbox probe duration is {{ $value }}s for {{ $labels.instance }}."
```

## 4. 数据源配置

### 4.1 TimescaleDB配置

#### 4.1.1 连接配置
```yaml
# application.yml
spring:
  datasource:
    timescale:
      url: ${TIMESCALE_URL:jdbc:postgresql://localhost:5432/market}
      driver-class-name: org.postgresql.Driver
      username: ${TIMESCALE_USER:postgres}
      password: ${TIMESCALE_PASS:postgres}
      hikari:
        maximum-pool-size: 20
        minimum-idle: 5
        connection-timeout: 30000
        idle-timeout: 600000
        max-lifetime: 1800000
```

#### 4.1.2 表结构
```sql
-- K线数据表
CREATE TABLE candles (
    symbol VARCHAR(20) NOT NULL,
    interval VARCHAR(10) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    open DECIMAL(20,8) NOT NULL,
    high DECIMAL(20,8) NOT NULL,
    low DECIMAL(20,8) NOT NULL,
    close DECIMAL(20,8) NOT NULL,
    volume BIGINT NOT NULL,
    amount DECIMAL(20,8),
    PRIMARY KEY (symbol, interval, timestamp)
);

-- 创建时序索引
CREATE INDEX idx_candles_timestamp ON candles (timestamp DESC);
CREATE INDEX idx_candles_symbol_interval ON candles (symbol, interval, timestamp DESC);

-- 创建时序分区
SELECT create_hypertable('candles', 'timestamp', chunk_time_interval => INTERVAL '1 day');

-- 企业行动表
CREATE TABLE corporate_actions (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    ex_date DATE NOT NULL,
    record_date DATE,
    payment_date DATE,
    ratio DECIMAL(10,6),
    amount DECIMAL(10,6),
    currency VARCHAR(3),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 创建索引
CREATE INDEX idx_corporate_actions_symbol ON corporate_actions (symbol);
CREATE INDEX idx_corporate_actions_ex_date ON corporate_actions (ex_date);
CREATE INDEX idx_corporate_actions_type ON corporate_actions (action_type);
```

### 4.2 ClickHouse配置

#### 4.2.1 连接配置
```yaml
# application.yml
spring:
  datasource:
    clickhouse:
      url: ${CLICKHOUSE_URL:jdbc:clickhouse://localhost:8123/market}
      driver-class-name: com.clickhouse.jdbc.ClickHouseDriver
      username: ${CLICKHOUSE_USER:default}
      password: ${CLICKHOUSE_PASS:}
      hikari:
        maximum-pool-size: 10
        minimum-idle: 2
        connection-timeout: 30000
        idle-timeout: 600000
        max-lifetime: 1800000
```

#### 4.2.2 表结构
```sql
-- K线数据表
CREATE TABLE candles (
    symbol String,
    interval String,
    timestamp DateTime64(3),
    open Decimal64(8),
    high Decimal64(8),
    low Decimal64(8),
    close Decimal64(8),
    volume UInt64,
    amount Decimal64(8)
) ENGINE = MergeTree()
ORDER BY (symbol, interval, timestamp)
PARTITION BY toYYYYMM(timestamp)
SETTINGS index_granularity = 8192;

-- 企业行动表
CREATE TABLE corporate_actions (
    id UInt64,
    symbol String,
    action_type String,
    ex_date Date,
    record_date Nullable(Date),
    payment_date Nullable(Date),
    ratio Nullable(Decimal64(6)),
    amount Nullable(Decimal64(6)),
    currency Nullable(String(3)),
    created_at DateTime64(3),
    updated_at DateTime64(3)
) ENGINE = MergeTree()
ORDER BY (symbol, ex_date, action_type)
SETTINGS index_granularity = 8192;
```

### 4.3 数据源切换配置
```yaml
# application.yml
marketdata:
  primary-provider: timescale  # timescale, clickhouse
  reconciliation:
    enabled: true
    tolerance: 0.01
  timescale:
    table: candles
  clickhouse:
    table: candles
  corporate-actions:
    table: corporate_actions
```

## 5. 分割服务依赖

### 5.1 分割服务配置
```yaml
# application.yml
seg:
  endpoint: ${SEG_ENDPOINT:http://localhost:8088/predict}
  timeoutMs: ${SEG_TIMEOUT:4000}
  enabled: ${SEG_ENABLED:true}
  forceSegmentation: ${SEG_FORCE:false}
  forceGeometry: ${SEG_FORCE_GEOMETRY:false}
  model:
    type: ${SEG_MODEL_TYPE:yolov8-seg}
    confidenceThreshold: ${SEG_CONFIDENCE:0.5}
    nmsThreshold: ${SEG_NMS:0.4}
    maxInstances: ${SEG_MAX_INSTANCES:100}
  fusion:
    pixelsPerBar: ${SEG_PIXELS_PER_BAR:6.0}
    maxCandles: ${SEG_MAX_CANDLES:50}
    wickProximityFactor: ${SEG_WICK_FACTOR:0.6}
    minCandleHeight: ${SEG_MIN_HEIGHT:3.0}
    minWickLength: ${SEG_MIN_WICK:2.0}
```

### 5.2 分割服务健康检查
```yaml
# 分割服务探针
- alert: SegmentationServiceDown
  expr: up{job="segmentation-service"} == 0
  for: 1m
  labels:
    severity: critical
    service: segmentation
  annotations:
    summary: "Segmentation service is down"
    description: "Segmentation service has been down for more than 1 minute."

- alert: SegmentationServiceHighLatency
  expr: histogram_quantile(0.95, rate(segmentation_request_duration_seconds_bucket[5m])) > 2
  for: 3m
  labels:
    severity: warning
    service: segmentation
  annotations:
    summary: "Segmentation service high latency"
    description: "95th percentile latency is {{ $value }}s for the last 5 minutes."
```

### 5.3 分割服务依赖处理
```java
// 分割服务降级处理
@Component
public class SegmentationServiceFallback {
    
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public SegmentationResult segmentImage(ImageData image) {
        try {
            return segmentationService.segment(image);
        } catch (Exception e) {
            log.warn("Segmentation service unavailable, using fallback", e);
            return fallbackSegmentation(image);
        }
    }
    
    @Recover
    public SegmentationResult fallbackSegmentation(ImageData image) {
        // 使用几何方法作为降级方案
        return geometryBasedSegmentation(image);
    }
}
```

## 6. 性能基准与SLO

### 6.1 SLO目标
```yaml
# 可用性目标
availability: 99.9%  # 月度可用性

# 延迟目标
latency:
  p50: 100ms    # 50%请求延迟
  p95: 500ms    # 95%请求延迟
  p99: 1000ms   # 99%请求延迟

# 吞吐目标
throughput:
  signals: 1000/min     # 信号生成速率
  backtest: 5000000/min # 回测吞吐量(K线/分钟)
  api: 10000/min       # API请求速率

# 一致性目标
consistency:
  signal_accuracy: 99.5%  # 回测与实盘信号一致率
  data_accuracy: 99.99%   # 数据准确性
```

### 6.2 性能测试
```bash
# 运行K6性能测试
./scripts/run_k6.sh

# 运行JMH基准测试
mvn test -Dtest=PerformanceBenchmarkTest

# 运行压力测试
kubectl apply -f ops/benchmarks/stress-test.yaml
```

### 6.3 基准测试结果
```yaml
# 典型性能指标
performance_metrics:
  api_response_time:
    p50: 45ms
    p95: 120ms
    p99: 250ms
  
  signal_generation:
    average: 50ms
    p95: 150ms
    p99: 300ms
  
  backtest_throughput:
    single_thread: 1000000  # K线/分钟
    multi_thread: 5000000   # K线/分钟
  
  memory_usage:
    heap_used: 1.2GB
    heap_max: 2GB
    gc_pause: 50ms
```

## 7. 故障排除

### 7.1 常见问题诊断

#### 7.1.1 Pod启动失败
```bash
# 查看Pod状态
kubectl get pods -n kline-analytics

# 查看Pod事件
kubectl describe pod <pod-name> -n kline-analytics

# 查看Pod日志
kubectl logs <pod-name> -n kline-analytics

# 常见原因
# 1. 资源不足
# 2. 镜像拉取失败
# 3. 配置错误
# 4. 依赖服务不可用
```

#### 7.1.2 健康检查失败
```bash
# 检查探针配置
kubectl get deployment kline-analytics -n kline-analytics -o yaml

# 手动测试健康检查
curl http://<pod-ip>:8080/actuator/health/liveness
curl http://<pod-ip>:8080/actuator/health/readiness

# 常见原因
# 1. 应用启动时间过长
# 2. 依赖服务不可用
# 3. 数据库连接失败
# 4. 内存不足
```

#### 7.1.3 监控数据缺失
```bash
# 检查ServiceMonitor
kubectl get servicemonitor -n kline-analytics

# 检查Prometheus配置
kubectl get prometheus -n monitoring

# 检查指标端点
curl http://<pod-ip>:8080/actuator/prometheus

# 常见原因
# 1. ServiceMonitor配置错误
# 2. 网络策略阻止访问
# 3. 应用指标未正确暴露
# 4. Prometheus配置问题
```

### 7.2 日志分析
```bash
# 查看应用日志
kubectl logs -f deployment/kline-analytics -n kline-analytics

# 查看特定容器日志
kubectl logs -f deployment/kline-analytics -c kline-analytics -n kline-analytics

# 查看日志并过滤
kubectl logs deployment/kline-analytics -n kline-analytics | grep ERROR

# 查看结构化日志
kubectl logs deployment/kline-analytics -n kline-analytics | jq '.'
```

### 7.3 性能问题诊断
```bash
# 查看资源使用情况
kubectl top pods -n kline-analytics

# 查看节点资源
kubectl top nodes

# 查看详细资源信息
kubectl describe pod <pod-name> -n kline-analytics

# 查看JVM指标
curl http://<pod-ip>:8080/actuator/metrics/jvm.memory.used
curl http://<pod-ip>:8080/actuator/metrics/jvm.gc.pause
```

## 8. 维护操作

### 8.1 日常维护
```bash
# 查看系统状态
./scripts/deploy.sh status

# 检查备份状态
./scripts/backup-restore.sh list

# 清理旧备份
./scripts/backup-restore.sh cleanup

# 更新应用
helm upgrade kline-analytics ./helm/kline-analytics -n kline-analytics
```

### 8.2 数据维护
```sql
-- 清理旧数据
DELETE FROM candles WHERE timestamp < NOW() - INTERVAL '1 year';

-- 重建索引
REINDEX TABLE candles;

-- 更新统计信息
ANALYZE candles;

-- 检查数据完整性
SELECT COUNT(*) FROM candles WHERE open <= 0 OR high <= 0 OR low <= 0 OR close <= 0;
```

### 8.3 监控维护
```bash
# 检查告警规则
kubectl get prometheusrule -n kline-analytics

# 测试告警
./scripts/test_alerts.sh

# 更新Grafana仪表板
kubectl apply -f ops/grafana/dashboards/

# 检查日志索引
curl -X GET "elasticsearch:9200/_cat/indices/kline-analytics-*"
```

### 8.4 安全维护
```bash
# 更新镜像
docker pull kline-analytics:latest

# 检查安全漏洞
kubectl get pods -n kline-analytics -o jsonpath='{.items[*].spec.containers[*].image}'

# 更新证书
kubectl create secret tls kline-analytics-tls --cert=server.crt --key=server.key -n kline-analytics

# 检查网络策略
kubectl get networkpolicy -n kline-analytics
```

---

**注意**: 本运维手册会随着系统更新而持续维护，请定期检查最新版本。
