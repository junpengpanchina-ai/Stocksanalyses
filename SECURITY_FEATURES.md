# 安全功能实现说明

本项目已集成以下安全功能：

## 1. JWT/OAuth 鉴权

### 功能特性
- JWT Token 认证
- 基于角色的访问控制 (RBAC)
- 自动Token验证和刷新

### 使用方法

#### 登录获取Token
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin123"}'
```

#### 使用Token访问受保护资源
```bash
curl -X GET http://localhost:8080/api/demo/user \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 默认用户
- **admin** / admin123 (ROLE_ADMIN)
- **user** / user123 (ROLE_USER)

## 2. Bucket4j 限流

### 功能特性
- 基于IP和用户代理的限流
- 可配置的限流规则
- 注解式限流控制

### 使用方法

#### 在Controller方法上添加限流注解
```java
@GetMapping("/api/endpoint")
@RateLimit(value = 10, windowMinutes = 1) // 每分钟10次请求
public ResponseEntity<?> endpoint() {
    // 方法实现
}
```

#### 限流配置
- 默认限流：100次/分钟
- 上传接口：10次/分钟
- 管理员接口：10次/分钟

## 3. @Audited 审计日志

### 功能特性
- 自动记录用户操作
- 支持实体变更审计
- 可查询的审计日志

### 使用方法

#### 在实体类上添加审计
```java
@Entity
@Audited
public class YourEntity extends AuditEntity {
    // 实体字段
}
```

#### 手动记录审计日志
```java
@Autowired
private AuditService auditService;

public void someMethod(HttpServletRequest request) {
    auditService.logAction("ACTION_NAME", "ENTITY_TYPE", "ENTITY_ID", request);
}
```

#### 查询审计日志
```bash
# 获取所有审计日志
curl -X GET http://localhost:8080/api/audit/logs

# 按用户查询
curl -X GET http://localhost:8080/api/audit/logs/user/admin

# 按实体查询
curl -X GET http://localhost:8080/api/audit/logs/entity/UPLOAD/filename.jpg
```

## 4. 隐私开关 (anonymize/persistImages)

### 功能特性
- 数据匿名化控制
- 图片持久化控制
- 数据保留期管理
- 自动清理过期数据

### 配置选项

```yaml
privacy:
  anonymize-data: false      # 是否匿名化数据
  persist-images: true       # 是否持久化图片
  data-retention-days: 30    # 数据保留天数
  enable-data-encryption: false  # 是否启用数据加密
  log-data-access: true      # 是否记录数据访问日志
```

### 使用方法

#### 查看隐私设置
```bash
curl -X GET http://localhost:8080/api/privacy/settings
```

#### 更新隐私设置
```bash
curl -X POST http://localhost:8080/api/privacy/settings \
  -H "Content-Type: application/json" \
  -d '{
    "anonymizeData": true,
    "persistImages": false,
    "dataRetentionDays": 7
  }'
```

#### 手动触发数据清理
```bash
curl -X POST http://localhost:8080/api/privacy/cleanup
```

## 5. 安全演示接口

### 公开接口 (无需认证)
```bash
curl -X GET http://localhost:8080/api/demo/public
```

### 用户接口 (需要USER或ADMIN角色)
```bash
curl -X GET http://localhost:8080/api/demo/user \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 管理员接口 (需要ADMIN角色)
```bash
curl -X GET http://localhost:8080/api/demo/admin \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 敏感操作接口 (需要ADMIN角色，严格限流)
```bash
curl -X POST http://localhost:8080/api/demo/sensitive-operation \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"operation": "test"}'
```

## 6. 配置说明

### JWT配置
```yaml
jwt:
  secret: mySecretKey123456789012345678901234567890
  expiration: 86400000 # 24小时
```

### 限流配置
```yaml
rate-limit:
  default-requests-per-minute: 100
  default-requests-per-hour: 1000
```

### 数据库配置
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password: 
  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        envers:
          audit_table_suffix: _aud
          store_data_at_delete: true
```

## 7. 安全最佳实践

1. **生产环境配置**
   - 修改默认JWT密钥
   - 使用强密码策略
   - 启用HTTPS
   - 配置适当的限流规则

2. **审计日志管理**
   - 定期清理过期审计日志
   - 监控异常访问模式
   - 设置审计日志告警

3. **隐私保护**
   - 根据法规要求配置数据匿名化
   - 定期清理敏感数据
   - 监控数据访问权限

4. **监控和告警**
   - 监控限流触发情况
   - 设置认证失败告警
   - 监控异常操作模式

## 8. 故障排除

### 常见问题

1. **JWT Token无效**
   - 检查Token是否过期
   - 验证Token格式是否正确
   - 确认密钥配置一致

2. **限流触发**
   - 检查请求频率
   - 调整限流配置
   - 查看限流日志

3. **审计日志不记录**
   - 检查数据库连接
   - 验证审计配置
   - 查看应用日志

4. **隐私设置不生效**
   - 重启应用使配置生效
   - 检查配置格式
   - 验证权限设置
