## 画像上传判读设计（A+C 主线，E 为二期）

### 0. 目标
- 上传/拖拽任意 K 线图，稳定、高精度还原结构化序列与形态判断。
- A（矢量化还原）+ C（模板驱动）为主链路；失败/不确定兜底 B；二期融入 E（分割重构）。

### 1. 端到端流程（A+C）
1) 预处理：去边框/去网格/去水印、旋正/去透视（OpenCV）
2) 风格识别（C）：TV/Wind/券商/彭博/自定义 → 模板解析优先
3) 坐标/刻度识别：直线/刻度（Hough + 连通域）+ OCR（mmocr） → 像素↔价格/时间映射
4) 蜡烛检测（A）：实例分割/几何聚合（YOLOv8-seg/Mask R-CNN + 规则）→ OHLC 重建
5) 时间序列重建：等间距/非等间距对齐、缺失补齐、质量评分
6) 形态/趋势/量价：调用 Java 核心引擎 → 信号 + 解释 + 统计
7) 失败兜底：若模板/坐标失败或质量低，走 B（端到端视觉）输出“低置信+解释”

二期（E）：
- 全图语义/实例分割（SAM2/SEG-Next）：蜡烛、影线、均线、网格、文字、注释分层
- 图层重构 → 更鲁棒的 OHLC 与样式自适应；对手绘/复杂图显著提升召回

### 2. 数据与标注方案
- 样本来源：TradingView/Wind/券商终端/东方财富/同花顺/研报截图/社交平台
- A/C 标注：
  - 坐标轴/刻度框（bbox）、轴方向、两点标尺（像素-价格对）
  - 蜡烛体/影线多边形或 bbox（可弱标注：顶点/底点/收盘点）
  - 模板元数据：布局分区（图表区/指标区/轴区）
- E 标注：
  - 语义分割掩码：蜡烛/影线/均线/网格/文字/注释
- 数量：起步各风格 200~500；总计 3k~8k；半自动标注 + 人审

### 3. 结构化数据 Schema
```json
{
  "imageId": "uuid",
  "sourceStyle": "tradingview|wind|broker|unknown",
  "axes": {
    "x": {"pixelsPerBar": 6.2, "originPx": 40, "orientation": "horizontal"},
    "y": {"pricePerPx": 0.12, "originPx": 720, "orientation": "vertical", "unit": "price"}
  },
  "ohlc": [
    {"t": 1696118400, "o": 10.23, "h": 10.87, "l": 10.05, "c": 10.66, "v": null, "px": {"x": 123, "w": 5}}
  ],
  "quality": {"axisConfidence": 0.94, "ohlcRecall": 0.92, "notes": ["auto-crop"]},
  "analysis": {
    "signals": [
      {"type": "BUY", "ts": 1698710400, "strength": 0.73, "explain": "bullish_engulfing + uptrend + vol"}
    ],
    "stats": {"patternF1": 0.91}
  }
}
```

### 4. 后端 API（建议）
- POST `/api/upload/analyze` (multipart/form-data)
  - req: `file`, optional: `hintStyle`, `userCalibrate`（两点标尺）
  - rsp: `UploadAnalyzeResult`（含上面 Schema）
- GET `/api/upload/{imageId}/explain`
- POST `/api/upload/feedback`（对/错/不确定）

`UploadAnalyzeResult`（简化）：
```json
{
  "imageId": "uuid",
  "pipeline": {"path": "C->A", "fallback": false},
  "axes": {...},
  "ohlc": [...],
  "signals": [...],
  "confidence": 0.88,
  "overlays": {"polylines": [{"type": "candle", "points": [...] }]}
}
```

### 5. 组件与服务划分
- svc-preprocess：裁切/旋正/去网格（OpenCV）
- svc-style-detector：图表风格分类（轻量 CNN）
- svc-template-parser（C）：模板解析与坐标标定
- svc-ocr：刻度/文本 OCR（mmocr）
- svc-candle-detector（A）：蜡烛/影线实例分割 + 几何聚合
- svc-series-rebuilder：像素→OHLC 序列；质量评估
- svc-acl-java-core：调用 Java 策略核心（形态/趋势/量价/风控）
- svc-fallback-B：端到端视觉分类/检测（低置信提示）
- 二期 svc-segmentation-E：全图分割与图层重构

部署：Triton/FastAPI（模型服务）+ Spring Boot（编排与业务）+ Redis（缓存）+ Kafka（异步与反馈）

### 6. 评测指标与基线
- 轴标定误差：价格映射 MAPE ≤ 1.5%
- OHLC 重建：bar 级别 MAE、召回率；可视对齐评分
- 形态检测：Top-20 F1 ≥ 0.90
- 端到端：准确率（对/错）、低置信率、平均延迟 P95 ≤ 1.5s
- 覆盖率：模板命中率 ≥ 80%，失败兜底成功率 ≥ 95%

### 7. 前端交互（关键）
- 拖拽上传预览、自动裁边、风格标签
- 标尺校准（两点法）：提升 A/C 精度；可选
- 结果叠加：蜡烛/关键位/趋势线覆盖；展开解释链
- 低置信提示与重试路径（切换兜底 B 或请求校准）

### 8. 里程碑
- M0（1 周）：样本采集与标注规范、API 草案、评测脚本
- M1（2 周）：C 模板解析器（TV/Wind/券商 3 类）+ A 蜡烛检测 v1
- M2（2 周）：OHLC 重建/质量评估 + Java 核心联调 + 解释输出
- M3（1 周）：兜底 B 与低置信策略、端到端评测达标
- 二期 M4（3 周）：E 分割重构与复杂样式适配

### 9. 风险与对策
- 模板漂移：版本化签名 + 回归集 + 监控
- OCR 噪声：两点标尺 + 价格校准回归
- 蜡烛遮挡：E 分割与交互校正
- 泛化：风格识别先行 + 数据增广（主题/配色/比例）


