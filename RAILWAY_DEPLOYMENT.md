# Railway 部署指南

## 概述
本指南将帮助您将 KLine Analytics Spring Boot 应用部署到 Railway 平台。

## 前置条件

### 1. 安装 Railway CLI
```bash
# 使用 npm 安装
npm install -g @railway/cli

# 或使用其他包管理器
# yarn global add @railway/cli
# pnpm add -g @railway/cli
```

### 2. 登录 Railway
```bash
railway login
```

## 快速部署

### 方法一：使用部署脚本（推荐）
```bash
./scripts/deploy-railway.sh
```

### 方法二：手动部署
```bash
# 1. 构建项目
mvn clean package -DskipTests

# 2. 部署到 Railway
railway up
```

## 环境配置

### 1. 数据库设置
在 Railway 仪表板中添加 PostgreSQL 数据库：
1. 进入项目设置
2. 点击 "New" → "Database" → "PostgreSQL"
3. 复制连接信息到环境变量

### 2. 环境变量配置
在 Railway 仪表板中设置以下环境变量：

#### 必需变量
```
TIMESCALE_URL=jdbc:postgresql://[host]:[port]/[database]
TIMESCALE_USER=[username]
TIMESCALE_PASS=[password]
JWT_SECRET=[your-secret-key-min-32-chars]
SPRING_PROFILES_ACTIVE=prod
```

#### 可选变量
参考 `railway.env.example` 文件中的完整配置。

## 部署后验证

### 1. 健康检查
访问：`https://your-app.railway.app/actuator/health`

### 2. API 文档
访问：`https://your-app.railway.app/swagger-ui.html`

### 3. 监控日志
```bash
railway logs
```

## 常见问题

### Q: 应用启动失败
A: 检查环境变量是否正确设置，特别是数据库连接信息。

### Q: 内存不足
A: Railway 免费计划有内存限制，可以调整 JVM 参数：
```
JAVA_OPTS=-Xms128m -Xmx256m
```

### Q: 数据库连接超时
A: 确保数据库服务已启动，连接字符串正确。

## 监控和维护

### 查看应用状态
```bash
railway status
```

### 查看实时日志
```bash
railway logs --follow
```

### 重启应用
```bash
railway redeploy
```

## 成本说明
- Railway 免费计划：每月 $5 信用额度
- 数据库：PostgreSQL 插件约 $5/月
- 建议先使用免费额度测试

## 下一步
1. 配置自定义域名（可选）
2. 设置 CI/CD 自动部署
3. 配置监控和告警
4. 优化性能和成本
