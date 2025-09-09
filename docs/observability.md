# 可观测性配置

## 健康探针

### 端点
- **Liveness**: `/actuator/health/liveness`
- **Readiness**: `/actuator/health/readiness`
- **Health**: `/actuator/health`

### 配置说明

#### Liveness Probe
- 检查应用是否存活
- 如果失败，Kubernetes会重启容器
- 初始延迟：60秒
- 检查间隔：30秒
- 超时时间：10秒
- 失败阈值：3次

#### Readiness Probe
- 检查应用是否准备好接收流量
- 如果失败，Kubernetes会从服务中移除该Pod
- 初始延迟：30秒
- 检查间隔：10秒
- 超时时间：5秒
- 失败阈值：3次

#### Startup Probe
- 检查应用是否启动完成
- 初始延迟：10秒
- 检查间隔：5秒
- 超时时间：3秒
- 失败阈值：12次（总共60秒）

## 指标监控

### 自定义指标

#### 分割服务指标
- `segmentation.requests.total` - 分割请求总数
- `segmentation.duration` - 分割处理时间

#### OCR服务指标
- `ocr.requests.total` - OCR请求总数
- `ocr.duration` - OCR处理时间

#### 轴检测指标
- `axis.detection.requests.total` - 轴检测请求总数
- `axis.detection.duration` - 轴检测处理时间

#### 撮合引擎指标
- `matching.engine.orders.total` - 撮合引擎订单总数
- `matching.engine.duration` - 撮合引擎处理时间

#### HTTP请求指标
- `http.requests.total` - HTTP请求总数
- `http.request.duration` - HTTP请求处理时间

### 指标标签

所有指标都包含以下标签：
- `app`: 应用名称
- `version`: 应用版本
- `component`: 组件名称
- `method`: HTTP方法（仅HTTP指标）
- `uri`: 请求URI（仅HTTP指标）
- `status`: 状态码（仅HTTP指标）
- `outcome`: 结果状态（SUCCESS/FAILED）

### 使用方式

#### 1. 在服务方法上添加注解

```java
@Service
public class SegmentationService {
    
    @SegmentationMetrics
    public SegmentationResult processImage(ImageData image) {
        // 处理逻辑
        return result;
    }
}
```

#### 2. 手动记录指标

```java
@Autowired
private MetricsService metricsService;

public void someMethod() {
    Timer.Sample sample = metricsService.startSegmentationTimer();
    try {
        // 处理逻辑
        metricsService.recordSegmentationDuration(sample, "SUCCESS", "COMPLETED");
        metricsService.incrementSegmentationRequests("SUCCESS", "COMPLETED");
    } catch (Exception e) {
        metricsService.recordSegmentationDuration(sample, "ERROR", "FAILED");
        metricsService.incrementSegmentationRequests("ERROR", "FAILED");
        throw e;
    }
}
```

## 部署配置

### Docker Compose
```yaml
services:
  app:
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health/liveness"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
```

### Kubernetes
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 30
  timeoutSeconds: 10
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3
```

## 监控集成

### Prometheus
- 指标端点：`/actuator/prometheus`
- 配置示例：`ops/prometheus.yml`

### Grafana
- 仪表板：`ops/grafana-dashboard.json`
- 数据源：Prometheus

### OpenTelemetry
- 追踪端点：`http://otel-collector:4318/v1/traces`
- 配置：`ops/otel-collector-config.yaml`

## 测试

运行健康探针测试：
```bash
mvn test -Dtest=HealthProbeTest
```

运行指标服务测试：
```bash
mvn test -Dtest=MetricsServiceTest
```

## 故障排除

### 健康检查失败
1. 检查应用日志
2. 验证端点可访问性
3. 检查资源限制
4. 验证依赖服务状态

### 指标缺失
1. 检查Micrometer配置
2. 验证注解使用
3. 检查MeterRegistry配置
4. 查看应用日志中的指标错误
