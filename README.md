# KLine Analytics - 智能K线分析平台

[![Build Status](https://github.com/your-org/kline-analytics/workflows/CI/badge.svg)](https://github.com/your-org/kline-analytics/actions)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green.svg)](https://spring.io/projects/spring-boot)

> 基于Java模型核心的智能K线分析平台，提供可解释的形态识别、策略回测、风险控制和实时预警功能。

## 🚀 快速开始

### 环境要求
- Java 21+
- Maven 3.8+
- Docker & Docker Compose
- Kubernetes 1.24+ (生产环境)

### 本地开发
```bash
# 克隆项目
git clone https://github.com/your-org/kline-analytics.git
cd kline-analytics

# 启动依赖服务
docker-compose up -d

# 编译并运行
mvn clean install
mvn spring-boot:run

# 访问应用
open http://localhost:8080
```

### 生产部署
```bash
# 使用Helm部署
./scripts/deploy.sh

# 或指定环境
ENVIRONMENT=production ./scripts/deploy.sh
```

## 📋 功能特性

### 核心功能
- **多周期K线分析**：支持1m/5m/15m/1h/4h/1d等多时间周期
- **智能形态识别**：20+种经典K线形态自动识别
- **策略回测引擎**：高性能事件驱动回测，支持滑点和手续费建模
- **实时信号生成**：基于形态+趋势+量价共振的智能信号
- **风险控制**：内置止损止盈、仓位管理、回撤控制
- **可解释性**：每个信号提供详细的形态证据和统计信息

### 技术特性
- **统一Java核心**：回测与实盘逻辑完全一致
- **插件化架构**：形态、指标、策略组件可插拔
- **高性能**：支持500万K线/分钟回测吞吐
- **低延迟**：实时信号延迟≤2秒
- **可观测性**：完整的监控、日志、告警体系

## 🏗️ 架构设计

### 技术栈
- **后端**：Spring Boot 3 + Java 21
- **前端**：HTML5 + ECharts + JavaScript
- **数据库**：TimescaleDB + PostgreSQL + Redis
- **消息队列**：Kafka
- **监控**：Prometheus + Grafana + OpenTelemetry
- **部署**：Kubernetes + Helm

### 核心模块
```
src/main/java/com/stocksanalyses/
├── controller/          # REST API控制器
├── service/            # 业务服务层
│   ├── matching/       # 撮合引擎
│   ├── patterns/       # 形态识别
│   └── backtest/       # 回测引擎
├── model/              # 数据模型
├── config/             # 配置类
└── web/                # Web配置
```

## 📊 监控与运维

### 健康检查
- **Liveness Probe**: `/actuator/health/liveness`
- **Readiness Probe**: `/actuator/health/readiness`
- **Metrics**: `/actuator/prometheus`

### 关键指标
- `kline_analytics_http_requests_total` - HTTP请求总数
- `kline_analytics_signals_generated_total` - 信号生成总数
- `kline_analytics_matching_engine_duration_seconds` - 撮合引擎耗时
- `kline_analytics_jvm_memory_used_bytes` - JVM内存使用

### 告警规则
- 应用宕机 (Critical)
- 高错误率 > 5% (Warning)
- 高响应时间 > 1s (Warning)
- 高内存使用 > 85% (Warning)

## 🔧 配置说明

### 环境变量
```bash
# 数据库配置
TIMESCALE_URL=jdbc:postgresql://localhost:5432/market
TIMESCALE_USER=postgres
TIMESCALE_PASS=postgres

# ClickHouse配置
CLICKHOUSE_URL=jdbc:clickhouse://localhost:8123/market
CLICKHOUSE_USER=default
CLICKHOUSE_PASS=

# 监控配置
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318
```

### Helm参数
```yaml
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

# 副本数
replicaCount: 3
```

## 📈 性能基准

### SLO目标
- **可用性**: 99.9% 月度可用性
- **延迟**: 实时信号 ≤ 2秒
- **吞吐**: 回测 ≥ 500万K线/分钟
- **一致性**: 回测与实盘信号一致率 ≥ 99.5%

### 基准测试
```bash
# 运行性能测试
./scripts/run_k6.sh

# 查看测试结果
kubectl logs -f job/k6-benchmark
```

## 🔒 安全特性

### 网络安全
- NetworkPolicy限制Pod间通信
- 只允许监控和入口流量
- 禁止不必要的出站连接

### 运行时安全
- 非root用户运行
- 只读根文件系统
- 最小权限原则
- 安全上下文配置

### 数据安全
- 敏感数据脱敏
- 传输加密
- 审计日志
- 访问控制

## 📚 API文档

### 核心端点
```http
# 获取K线数据
GET /api/candles?symbol=AAPL&interval=1d&start=2024-01-01&end=2024-01-31

# 策略回测
POST /api/strategy/backtest
Content-Type: application/json
{
  "strategyConfig": {
    "name": "ema-macd",
    "params": {
      "emaShort": 20,
      "emaLong": 50,
      "macdFast": 12,
      "macdSlow": 26,
      "macdSignal": 9
    }
  },
  "universe": ["AAPL"],
  "interval": "1d",
  "start": "2024-01-01",
  "end": "2024-01-31"
}

# 获取信号
GET /api/signals?symbol=AAPL&start=2024-01-01&end=2024-01-31

# 上传图片分析
POST /api/upload/analyze
Content-Type: multipart/form-data
```

### 错误码
- `400` - 请求参数错误
- `401` - 未授权访问
- `403` - 禁止访问
- `404` - 资源不存在
- `429` - 请求频率限制
- `500` - 服务器内部错误
- `503` - 服务不可用

## 🗄️ 数据源配置

### TimescaleDB
```sql
-- 创建K线数据表
CREATE TABLE candles (
    symbol VARCHAR(20) NOT NULL,
    interval VARCHAR(10) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    open DECIMAL(20,8) NOT NULL,
    high DECIMAL(20,8) NOT NULL,
    low DECIMAL(20,8) NOT NULL,
    close DECIMAL(20,8) NOT NULL,
    volume BIGINT NOT NULL,
    PRIMARY KEY (symbol, interval, timestamp)
);

-- 创建时序索引
CREATE INDEX idx_candles_timestamp ON candles (timestamp DESC);
CREATE INDEX idx_candles_symbol_interval ON candles (symbol, interval, timestamp DESC);
```

### ClickHouse
```sql
-- 创建K线数据表
CREATE TABLE candles (
    symbol String,
    interval String,
    timestamp DateTime64(3),
    open Decimal64(8),
    high Decimal64(8),
    low Decimal64(8),
    close Decimal64(8),
    volume UInt64
) ENGINE = MergeTree()
ORDER BY (symbol, interval, timestamp);
```

## 🔄 备份与恢复

### 自动备份
- 每日凌晨2点自动备份
- 保留30天备份数据
- S3兼容对象存储
- SHA256校验和验证

### 手动操作
```bash
# 创建备份
./scripts/backup-restore.sh backup

# 恢复备份
./scripts/backup-restore.sh restore kline_analytics_backup_20240101_020000.sql.gz

# 验证备份
./scripts/backup-restore.sh validate kline_analytics_backup_20240101_020000.sql.gz
```

## 🚨 故障排除

### 常见问题
1. **Pod启动失败**
   - 检查资源限制和安全上下文
   - 查看Pod事件和日志

2. **健康检查失败**
   - 检查应用配置和依赖服务
   - 验证探针路径和端口

3. **监控数据缺失**
   - 检查ServiceMonitor配置
   - 验证Prometheus抓取规则

4. **备份失败**
   - 检查S3权限和网络连接
   - 验证数据库连接配置

### 调试命令
```bash
# 查看Pod状态
kubectl get pods -n kline-analytics

# 查看Pod日志
kubectl logs -f deployment/kline-analytics -n kline-analytics

# 查看事件
kubectl get events -n kline-analytics --sort-by='.lastTimestamp'

# 进入Pod调试
kubectl exec -it deployment/kline-analytics -n kline-analytics -- /bin/bash
```

## 🤝 贡献指南

### 开发流程
1. Fork项目
2. 创建功能分支
3. 提交代码
4. 创建Pull Request

### 代码规范
- 遵循Google Java Style Guide
- 单元测试覆盖率 > 80%
- 所有公共API必须有文档
- 提交信息使用约定式提交格式

## 📄 许可证

本项目采用 [MIT许可证](LICENSE)。

## 📞 支持与联系

- **文档**: [项目Wiki](https://github.com/your-org/kline-analytics/wiki)
- **问题反馈**: [GitHub Issues](https://github.com/your-org/kline-analytics/issues)
- **讨论**: [GitHub Discussions](https://github.com/your-org/kline-analytics/discussions)
- **邮件**: devops@your-org.com

---

**免责声明**: 本平台仅用于研究和教育目的，不构成投资建议。使用本平台进行实际交易的风险由用户自行承担。
