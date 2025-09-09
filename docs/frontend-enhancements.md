# 前端/样式与交互增强

## 功能概述

本次更新大幅增强了前端用户体验，包括参数验证、多周期联动、图表可视化、策略管理和可靠性改进。

## 1. 参数面板与校验

### 功能特性
- **输入范围/类型校验**：实时验证参数输入，支持整数、字符串、枚举类型
- **错误提示**：清晰的错误信息显示，包括字段级和全局验证
- **常用参数一键预设**：内置多种交易策略预设（剥头皮、摆动、持仓、加密货币）
- **保存与分享**：支持自定义预设保存、导入/导出功能

### 使用方法
```javascript
// 参数验证示例
const validator = new ParamValidator();
const result = validator.validate('emaShort', '20');
if (result.valid) {
  console.log('Valid parameter:', result.value);
} else {
  console.log('Errors:', result.errors);
}

// 应用预设
paramPanel.applyPreset('scalping');
```

### 预设配置
- **剥头皮策略**：EMA(5,20), MACD(8,17,9)
- **摆动策略**：EMA(20,50), MACD(12,26,9)
- **持仓策略**：EMA(50,200), MACD(12,26,9)
- **加密货币策略**：EMA(12,26), MACD(12,26,9)

## 2. 多周期联动

### 功能特性
- **十字光标联动**：主副图十字光标同步显示
- **缩放/平移同步**：支持比例缩放和时间对齐两种模式
- **二图时间轴对齐**：自动对齐不同时间周期的数据
- **主副图指标开关**：独立的指标显示控制

### 配置选项
```javascript
// 联动控制
multiTimeframeManager.setSyncState({
  isLinked: true,           // 时间周期联动
  crosshairSync: true,      // 十字光标联动
  zoomSync: true,          // 缩放联动
  panSync: true,           // 平移联动
  timeAlignment: true      // 时间轴对齐
});
```

### 时间周期映射
- 1日 → 1小时
- 4小时 → 15分钟
- 1小时 → 15分钟
- 15分钟 → 1分钟

## 3. 图表可视化增强

### 功能特性
- **信号形状/颜色编码**：
  - 买入：绿色三角形
  - 卖出：红色倒三角形
  - 平仓：黄色圆形
  - 止损：橙色菱形
  - 止盈：绿色星形
- **滚动性能优化**：虚拟化渲染，支持大数据量
- **增强图例**：交互式信号控制面板

### 性能优化
```javascript
// 虚拟化配置
chartVisualization.maxVisiblePoints = 1000;  // 最大可见点数
chartVisualization.renderBuffer = 200;       // 渲染缓冲区
chartVisualization.virtualizationEnabled = true; // 启用虚拟化
```

### 信号类型
- **BUY**：绿色三角形，向上箭头
- **SELL**：红色倒三角形，向下箭头
- **CLOSE**：黄色圆形
- **STOP_LOSS**：橙色菱形
- **TAKE_PROFIT**：绿色星形

## 4. 策略管理

### 功能特性
- **可视化版本对比**：并排显示不同版本差异
- **激活/标注生产活跃版本**：清晰标识当前活跃策略
- **Schema校验结果直观展示**：实时验证策略配置
- **版本历史管理**：完整的版本控制功能

### 界面布局
- **列表视图**：显示所有策略概览
- **比较视图**：并排对比两个版本
- **版本视图**：按策略分组的版本历史

### 策略验证
```javascript
// Schema验证
const validation = schemaValidator.validate(strategy);
if (validation.valid) {
  console.log('策略配置有效');
} else {
  console.log('验证错误:', validation.errors);
}
```

### 版本比较
- 参数变更检测
- 配置差异高亮
- 变更历史记录

## 5. 可靠性改进

### 功能特性
- **增强重试机制**：
  - 指数退避算法
  - 抖动防止雷群效应
  - 区分4xx/5xx错误处理
- **熔断器模式**：防止级联故障
- **全局错误处理**：统一的错误处理机制
- **降级UI**：服务不可用时的备用界面

### 重试配置
```javascript
const retryConfig = {
  maxRetries: 3,              // 最大重试次数
  baseDelay: 1000,            // 基础延迟(ms)
  maxDelay: 10000,            // 最大延迟(ms)
  jitter: true,               // 启用抖动
  backoffMultiplier: 2        // 退避乘数
};
```

### 熔断器配置
```javascript
const circuitBreaker = new CircuitBreaker({
  failureThreshold: 5,        // 失败阈值
  resetTimeout: 30000,        // 重置超时(ms)
  monitoringPeriod: 60000     // 监控周期(ms)
});
```

### 错误分类
- **4xx错误**：客户端错误，通常不重试
- **5xx错误**：服务器错误，可重试
- **网络错误**：连接问题，可重试
- **超时错误**：请求超时，可重试

## 技术实现

### 文件结构
```
src/main/resources/static/
├── js/
│   ├── param-validation.js      # 参数验证
│   ├── multi-timeframe.js       # 多周期联动
│   ├── chart-visualization.js   # 图表可视化
│   ├── strategy-management.js   # 策略管理
│   └── reliability.js           # 可靠性改进
├── app.html                     # 主页面
└── styles.css                   # 样式文件
```

### 依赖关系
- **ECharts 5**：图表渲染
- **原生JavaScript**：无外部依赖
- **CSS Grid/Flexbox**：响应式布局

### 浏览器兼容性
- Chrome 80+
- Firefox 75+
- Safari 13+
- Edge 80+

## 使用指南

### 1. 参数设置
1. 在参数面板输入交易参数
2. 系统自动验证输入范围
3. 选择预设策略快速配置
4. 保存自定义预设供后续使用

### 2. 多周期分析
1. 设置主图时间周期
2. 启用时间周期联动
3. 配置同步选项（十字光标、缩放、平移）
4. 观察主副图联动效果

### 3. 策略管理
1. 创建新策略或编辑现有策略
2. 实时验证策略配置
3. 激活策略到生产环境
4. 比较不同版本差异

### 4. 错误处理
1. 系统自动处理网络错误
2. 显示友好的错误提示
3. 提供重试和降级选项
4. 记录错误日志供调试

## 性能优化

### 虚拟化渲染
- 大数据量时自动启用虚拟化
- 只渲染可见区域数据
- 动态加载/卸载数据点

### 内存管理
- 及时清理事件监听器
- 避免内存泄漏
- 优化DOM操作

### 网络优化
- 智能重试机制
- 请求去重
- 缓存策略

## 故障排除

### 常见问题
1. **参数验证失败**：检查输入范围和类型
2. **联动不工作**：确认同步选项已启用
3. **图表渲染慢**：启用虚拟化渲染
4. **策略保存失败**：检查JSON格式和必填字段

### 调试工具
- 浏览器开发者工具
- 控制台日志输出
- 网络请求监控
- 性能分析器

## 未来改进

### 计划功能
- 更多图表类型支持
- 实时数据推送
- 移动端适配
- 主题定制

### 性能优化
- Web Workers支持
- 更高效的虚拟化
- 智能预加载
- 缓存策略优化
