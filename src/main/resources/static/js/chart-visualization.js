// 图表可视化增强
class ChartVisualization {
  constructor() {
    this.signalColors = {
      BUY: '#26A69A',
      SELL: '#EF5350',
      CLOSE: '#FFC300',
      STOP_LOSS: '#FF5722',
      TAKE_PROFIT: '#4CAF50',
      NEUTRAL: '#9AA4AE'
    };
    
    this.signalShapes = {
      BUY: 'triangle',
      SELL: 'invertedTriangle',
      CLOSE: 'circle',
      STOP_LOSS: 'diamond',
      TAKE_PROFIT: 'star',
      NEUTRAL: 'rect'
    };
    
    this.virtualizationEnabled = true;
    this.maxVisiblePoints = 1000;
    this.renderBuffer = 200;
    
    this.init();
  }

  init() {
    this.setupSignalLegend();
    this.setupPerformanceOptimization();
  }

  setupSignalLegend() {
    // 创建增强的信号图例
    const legendContainer = document.createElement('div');
    legendContainer.className = 'enhanced-legend';
    legendContainer.innerHTML = `
      <div class="legend-header">
        <h4>Signals & Indicators</h4>
        <div class="legend-controls">
          <button id="toggleAllSignals" class="btn btn-sm">Toggle All</button>
          <button id="resetZoom" class="btn btn-sm">Reset Zoom</button>
        </div>
      </div>
      <div class="legend-items" id="legendItems"></div>
    `;

    // 插入到图表区域
    const chartContainer = document.querySelector('.grid2 > div:first-child');
    if (chartContainer) {
      const existingLegend = chartContainer.querySelector('.legend');
      if (existingLegend) {
        chartContainer.replaceChild(legendContainer, existingLegend);
      } else {
        chartContainer.appendChild(legendContainer);
      }
    }

    this.bindLegendEvents();
  }

  bindLegendEvents() {
    document.getElementById('toggleAllSignals')?.addEventListener('click', () => {
      this.toggleAllSignals();
    });

    document.getElementById('resetZoom')?.addEventListener('click', () => {
      this.resetZoom();
    });
  }

  createSignalLegend(signals) {
    const legendItems = document.getElementById('legendItems');
    if (!legendItems) return;

    // 统计信号类型
    const signalCounts = {};
    signals.forEach(signal => {
      signalCounts[signal.type] = (signalCounts[signal.type] || 0) + 1;
    });

    // 创建图例项
    const legendHTML = Object.entries(signalCounts).map(([type, count]) => {
      const color = this.signalColors[type] || this.signalColors.NEUTRAL;
      const shape = this.signalShapes[type] || this.signalShapes.NEUTRAL;
      
      return `
        <div class="legend-item" data-signal-type="${type}">
          <div class="legend-marker" style="background-color: ${color}">
            <div class="legend-shape ${shape}"></div>
          </div>
          <span class="legend-label">${type}</span>
          <span class="legend-count">(${count})</span>
          <button class="legend-toggle" data-type="${type}">Hide</button>
        </div>
      `;
    }).join('');

    legendItems.innerHTML = legendHTML;

    // 绑定图例项事件
    legendItems.querySelectorAll('.legend-toggle').forEach(button => {
      button.addEventListener('click', (e) => {
        const signalType = e.target.dataset.type;
        this.toggleSignalType(signalType);
      });
    });
  }

  toggleSignalType(signalType) {
    const button = document.querySelector(`[data-type="${signalType}"]`);
    const isHidden = button.textContent === 'Show';
    
    button.textContent = isHidden ? 'Hide' : 'Show';
    button.style.opacity = isHidden ? '1' : '0.5';
    
    // 更新图表显示
    this.updateChartSignals(signalType, isHidden);
  }

  toggleAllSignals() {
    const buttons = document.querySelectorAll('.legend-toggle');
    const allHidden = Array.from(buttons).every(btn => btn.textContent === 'Show');
    
    buttons.forEach(button => {
      button.textContent = allHidden ? 'Hide' : 'Show';
      button.style.opacity = allHidden ? '1' : '0.5';
    });
    
    // 更新所有信号显示
    this.updateAllSignals(!allHidden);
  }

  updateChartSignals(signalType, show) {
    // 这里需要与主图表交互来显示/隐藏特定类型的信号
    // 具体实现依赖于图表库的API
    console.log(`Toggle ${signalType} signals: ${show ? 'show' : 'hide'}`);
  }

  updateAllSignals(show) {
    // 更新所有信号的显示状态
    console.log(`Toggle all signals: ${show ? 'show' : 'hide'}`);
  }

  resetZoom() {
    // 重置图表缩放
    if (window.chart) {
      window.chart.dispatchAction({
        type: 'dataZoom',
        startValue: 0,
        endValue: 100
      });
    }
    if (window.chart2) {
      window.chart2.dispatchAction({
        type: 'dataZoom',
        startValue: 0,
        endValue: 100
      });
    }
  }

  setupPerformanceOptimization() {
    // 设置虚拟化渲染
    this.setupVirtualization();
    
    // 设置渲染优化
    this.setupRenderOptimization();
  }

  setupVirtualization() {
    if (!this.virtualizationEnabled) return;

    // 监听数据变化，实现虚拟化
    this.originalRenderChart = window.renderChart;
    window.renderChart = () => {
      this.renderChartWithVirtualization();
    };
  }

  renderChartWithVirtualization() {
    const candles = window.state?.candles || [];
    const signals = window.state?.signals || [];
    
    if (candles.length > this.maxVisiblePoints) {
      // 实现数据虚拟化
      const visibleData = this.getVisibleData(candles, signals);
      this.renderVirtualizedChart(visibleData);
    } else {
      // 使用原始渲染
      if (this.originalRenderChart) {
        this.originalRenderChart();
      }
    }
  }

  getVisibleData(candles, signals) {
    // 获取当前可见范围
    const chart = window.chart;
    if (!chart) return { candles, signals };

    const option = chart.getOption();
    const dataZoom = option.dataZoom?.[0];
    
    if (!dataZoom) return { candles, signals };

    const startIndex = Math.max(0, dataZoom.startValue || 0);
    const endIndex = Math.min(candles.length - 1, dataZoom.endValue || candles.length - 1);
    
    // 添加缓冲区
    const bufferStart = Math.max(0, startIndex - this.renderBuffer);
    const bufferEnd = Math.min(candles.length - 1, endIndex + this.renderBuffer);
    
    const visibleCandles = candles.slice(bufferStart, bufferEnd + 1);
    const visibleSignals = signals.filter(signal => {
      const signalTime = new Date(signal.timestamp).getTime();
      const startTime = new Date(candles[bufferStart].timestamp).getTime();
      const endTime = new Date(candles[bufferEnd].timestamp).getTime();
      return signalTime >= startTime && signalTime <= endTime;
    });

    return {
      candles: visibleCandles,
      signals: visibleSignals,
      startIndex: bufferStart,
      endIndex: bufferEnd
    };
  }

  renderVirtualizedChart(data) {
    const { candles, signals } = data;
    
    // 渲染虚拟化后的图表
    const k = candles.map(c => [
      new Date(c.timestamp).toISOString().slice(0, 10),
      +c.open, +c.close, +c.low, +c.high
    ]);
    
    const showSignals = document.getElementById('toggleSignals')?.checked;
    const sigPts = showSignals ? this.createSignalPoints(signals, candles) : [];
    
    const option = {
      animation: false,
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: k.map(v => v[0]) },
      yAxis: { scale: true },
      grid: { left: 40, right: 20, top: 20, bottom: 40 },
      series: [
        { type: 'candlestick', name: window.state?.symbol || 'TEST', data: k.map(v => v.slice(1)) },
        ...(showSignals ? [{ type: 'scatter', name: 'signals', data: sigPts, yAxisIndex: 0 }] : [])
      ]
    };

    if (window.chart) {
      window.chart.setOption(option);
    }
  }

  createSignalPoints(signals, candles) {
    return signals.map(signal => {
      const signalTime = new Date(signal.timestamp).toISOString().slice(0, 10);
      const color = this.signalColors[signal.type] || this.signalColors.NEUTRAL;
      const shape = this.signalShapes[signal.type] || this.signalShapes.NEUTRAL;
      
      return {
        name: signal.type,
        value: [signalTime, signal.type === 'BUY' ? 1 : -1],
        itemStyle: {
          color: color,
          borderColor: color,
          borderWidth: 2
        },
        symbol: this.getEChartsSymbol(shape),
        symbolSize: 12
      };
    });
  }

  getEChartsSymbol(shape) {
    const shapeMap = {
      'triangle': 'triangle',
      'invertedTriangle': 'triangle',
      'circle': 'circle',
      'diamond': 'diamond',
      'star': 'star',
      'rect': 'rect'
    };
    return shapeMap[shape] || 'circle';
  }

  setupRenderOptimization() {
    // 防抖渲染
    this.debouncedRender = this.debounce(() => {
      this.renderChartWithVirtualization();
    }, 100);

    // 监听窗口大小变化
    window.addEventListener('resize', this.debouncedRender);
  }

  debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
      const later = () => {
        clearTimeout(timeout);
        func(...args);
      };
      clearTimeout(timeout);
      timeout = setTimeout(later, wait);
    };
  }

  // 创建信号形状样式
  createSignalShapes() {
    const style = document.createElement('style');
    style.textContent = `
      .legend-shape {
        width: 8px;
        height: 8px;
        margin: 0 auto;
      }
      
      .legend-shape.triangle {
        width: 0;
        height: 0;
        border-left: 4px solid transparent;
        border-right: 4px solid transparent;
        border-bottom: 8px solid currentColor;
        background: none;
      }
      
      .legend-shape.invertedTriangle {
        width: 0;
        height: 0;
        border-left: 4px solid transparent;
        border-right: 4px solid transparent;
        border-top: 8px solid currentColor;
        background: none;
      }
      
      .legend-shape.circle {
        border-radius: 50%;
      }
      
      .legend-shape.diamond {
        transform: rotate(45deg);
      }
      
      .legend-shape.star {
        clip-path: polygon(50% 0%, 61% 35%, 98% 35%, 68% 57%, 79% 91%, 50% 70%, 21% 91%, 32% 57%, 2% 35%, 39% 35%);
      }
      
      .legend-shape.rect {
        border-radius: 2px;
      }
    `;
    document.head.appendChild(style);
  }
}

// 添加增强图例样式
const legendStyle = document.createElement('style');
legendStyle.textContent = `
  .enhanced-legend {
    margin-top: 12px;
    padding: 12px;
    background: var(--panel-alt);
    border: 1px solid var(--border);
    border-radius: 8px;
  }
  
  .legend-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
  }
  
  .legend-header h4 {
    margin: 0;
    font-size: 13px;
    color: var(--muted);
  }
  
  .legend-controls {
    display: flex;
    gap: 6px;
  }
  
  .legend-items {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  
  .legend-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 6px;
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 4px;
  }
  
  .legend-marker {
    width: 16px;
    height: 16px;
    border-radius: 2px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  
  .legend-label {
    flex: 1;
    font-size: 12px;
    color: var(--text);
  }
  
  .legend-count {
    font-size: 11px;
    color: var(--muted);
  }
  
  .legend-toggle {
    padding: 2px 6px;
    font-size: 10px;
    background: var(--panel-alt);
    border: 1px solid var(--border);
    border-radius: 3px;
    color: var(--muted);
    cursor: pointer;
  }
  
  .legend-toggle:hover {
    background: var(--border);
  }
`;

document.head.appendChild(legendStyle);

// 初始化图表可视化增强
const chartVisualization = new ChartVisualization();
chartVisualization.createSignalShapes();
