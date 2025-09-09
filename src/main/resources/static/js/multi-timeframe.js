// 多周期联动管理
class MultiTimeframeManager {
  constructor() {
    this.charts = {
      main: null,
      secondary: null
    };
    this.isLinked = true;
    this.crosshairSync = true;
    this.zoomSync = true;
    this.panSync = true;
    this.timeAlignment = true;
    
    this.init();
  }

  init() {
    this.setupEventListeners();
    this.setupTimeframeLinking();
  }

  setupEventListeners() {
    // 监听时间周期联动开关
    const linkToggle = document.getElementById('linkTf');
    if (linkToggle) {
      linkToggle.addEventListener('change', (e) => {
        this.isLinked = e.target.checked;
        this.updateTimeframeLinking();
      });
    }

    // 添加联动控制按钮
    this.addSyncControls();
  }

  addSyncControls() {
    const controlsContainer = document.createElement('div');
    controlsContainer.className = 'sync-controls';
    controlsContainer.innerHTML = `
      <div class="sync-options">
        <label><input type="checkbox" id="crosshairSync" checked /> Crosshair Sync</label>
        <label><input type="checkbox" id="zoomSync" checked /> Zoom Sync</label>
        <label><input type="checkbox" id="panSync" checked /> Pan Sync</label>
        <label><input type="checkbox" id="timeAlignment" checked /> Time Alignment</label>
      </div>
    `;

    // 插入到图表控制区域
    const chartContainer = document.querySelector('.grid2');
    if (chartContainer) {
      chartContainer.insertBefore(controlsContainer, chartContainer.firstChild);
    }

    // 绑定控制事件
    document.getElementById('crosshairSync')?.addEventListener('change', (e) => {
      this.crosshairSync = e.target.checked;
    });

    document.getElementById('zoomSync')?.addEventListener('change', (e) => {
      this.zoomSync = e.target.checked;
    });

    document.getElementById('panSync')?.addEventListener('change', (e) => {
      this.panSync = e.target.checked;
    });

    document.getElementById('timeAlignment')?.addEventListener('change', (e) => {
      this.timeAlignment = e.target.checked;
      this.alignTimeAxis();
    });
  }

  setupTimeframeLinking() {
    // 时间周期映射关系
    this.timeframeMap = {
      '1d': '1h',
      '4h': '15m',
      '1h': '15m',
      '15m': '1m',
      '5m': '1m',
      '1m': '1m'
    };

    // 监听主图时间周期变化
    const mainInterval = document.getElementById('interval');
    if (mainInterval) {
      mainInterval.addEventListener('change', (e) => {
        if (this.isLinked) {
          const linkedInterval = this.timeframeMap[e.target.value] || '1h';
          const secondaryInterval = document.getElementById('interval2');
          if (secondaryInterval) {
            secondaryInterval.value = linkedInterval;
            this.updateSecondaryChart();
          }
        }
      });
    }
  }

  setCharts(mainChart, secondaryChart) {
    this.charts.main = mainChart;
    this.charts.secondary = secondaryChart;
    this.setupChartSync();
  }

  setupChartSync() {
    if (!this.charts.main || !this.charts.secondary) return;

    // 十字光标联动
    this.charts.main.on('brushSelected', (params) => {
      if (this.crosshairSync) {
        this.syncCrosshair('main', 'secondary', params);
      }
    });

    this.charts.secondary.on('brushSelected', (params) => {
      if (this.crosshairSync) {
        this.syncCrosshair('secondary', 'main', params);
      }
    });

    // 缩放联动
    this.charts.main.on('dataZoom', (params) => {
      if (this.zoomSync) {
        this.syncZoom('main', 'secondary', params);
      }
    });

    this.charts.secondary.on('dataZoom', (params) => {
      if (this.zoomSync) {
        this.syncZoom('secondary', 'main', params);
      }
    });

    // 平移联动
    this.charts.main.on('brush', (params) => {
      if (this.panSync) {
        this.syncPan('main', 'secondary', params);
      }
    });

    this.charts.secondary.on('brush', (params) => {
      if (this.panSync) {
        this.syncPan('secondary', 'main', params);
      }
    });
  }

  syncCrosshair(fromChart, toChart, params) {
    if (!this.crosshairSync) return;

    const sourceChart = this.charts[fromChart];
    const targetChart = this.charts[toChart];
    
    if (!sourceChart || !targetChart) return;

    // 获取源图表的十字光标位置
    const sourceOption = sourceChart.getOption();
    const sourceData = sourceOption.series[0].data;
    
    if (params.batch && params.batch.length > 0) {
      const selectedIndex = params.batch[0].selected[0];
      if (selectedIndex >= 0 && selectedIndex < sourceData.length) {
        // 同步到目标图表
        this.highlightDataPoint(targetChart, selectedIndex);
      }
    }
  }

  syncZoom(fromChart, toChart, params) {
    if (!this.zoomSync) return;

    const sourceChart = this.charts[fromChart];
    const targetChart = this.charts[toChart];
    
    if (!sourceChart || !targetChart) return;

    // 获取缩放参数
    const zoomParams = params.batch[0];
    const startValue = zoomParams.startValue;
    const endValue = zoomParams.endValue;

    // 计算目标图表的对应范围
    const targetOption = targetChart.getOption();
    const targetData = targetOption.series[0].data;
    
    if (this.timeAlignment) {
      // 时间对齐的缩放
      this.alignZoomByTime(targetChart, startValue, endValue);
    } else {
      // 比例缩放
      this.alignZoomByRatio(targetChart, startValue, endValue, sourceData.length, targetData.length);
    }
  }

  syncPan(fromChart, toChart, params) {
    if (!this.panSync) return;

    const sourceChart = this.charts[fromChart];
    const targetChart = this.charts[toChart];
    
    if (!sourceChart || !targetChart) return;

    // 实现平移同步逻辑
    const panParams = params.batch[0];
    const startValue = panParams.startValue;
    const endValue = panParams.endValue;

    this.alignPanByTime(targetChart, startValue, endValue);
  }

  alignZoomByTime(targetChart, startTime, endTime) {
    const targetOption = targetChart.getOption();
    const targetData = targetOption.series[0].data;
    
    // 找到对应的时间范围
    let startIndex = 0;
    let endIndex = targetData.length - 1;
    
    for (let i = 0; i < targetData.length; i++) {
      const dataTime = new Date(targetData[i][0]).getTime();
      if (dataTime >= startTime) {
        startIndex = i;
        break;
      }
    }
    
    for (let i = targetData.length - 1; i >= 0; i--) {
      const dataTime = new Date(targetData[i][0]).getTime();
      if (dataTime <= endTime) {
        endIndex = i;
        break;
      }
    }

    // 应用缩放
    targetChart.dispatchAction({
      type: 'dataZoom',
      startValue: startIndex,
      endValue: endIndex
    });
  }

  alignZoomByRatio(targetChart, startValue, endValue, sourceLength, targetLength) {
    const ratio = (endValue - startValue) / sourceLength;
    const targetStart = Math.floor(startValue * targetLength / sourceLength);
    const targetEnd = Math.floor(endValue * targetLength / sourceLength);
    
    targetChart.dispatchAction({
      type: 'dataZoom',
      startValue: targetStart,
      endValue: targetEnd
    });
  }

  alignPanByTime(targetChart, startTime, endTime) {
    // 实现时间对齐的平移
    this.alignZoomByTime(targetChart, startTime, endTime);
  }

  highlightDataPoint(chart, index) {
    chart.dispatchAction({
      type: 'highlight',
      seriesIndex: 0,
      dataIndex: index
    });
  }

  alignTimeAxis() {
    if (!this.timeAlignment || !this.charts.main || !this.charts.secondary) return;

    // 获取主图的时间范围
    const mainOption = this.charts.main.getOption();
    const mainData = mainOption.series[0].data;
    
    if (mainData.length === 0) return;

    const mainStartTime = new Date(mainData[0][0]).getTime();
    const mainEndTime = new Date(mainData[mainData.length - 1][0]).getTime();

    // 对齐副图的时间轴
    this.alignZoomByTime(this.charts.secondary, mainStartTime, mainEndTime);
  }

  updateTimeframeLinking() {
    if (this.isLinked) {
      const mainInterval = document.getElementById('interval');
      const secondaryInterval = document.getElementById('interval2');
      
      if (mainInterval && secondaryInterval) {
        const linkedInterval = this.timeframeMap[mainInterval.value] || '1h';
        secondaryInterval.value = linkedInterval;
        this.updateSecondaryChart();
      }
    }
  }

  updateSecondaryChart() {
    // 触发副图更新
    const loadButton = document.getElementById('load2');
    if (loadButton) {
      loadButton.click();
    }
  }

  // 获取当前联动状态
  getSyncState() {
    return {
      isLinked: this.isLinked,
      crosshairSync: this.crosshairSync,
      zoomSync: this.zoomSync,
      panSync: this.panSync,
      timeAlignment: this.timeAlignment
    };
  }

  // 设置联动状态
  setSyncState(state) {
    this.isLinked = state.isLinked;
    this.crosshairSync = state.crosshairSync;
    this.zoomSync = state.zoomSync;
    this.panSync = state.panSync;
    this.timeAlignment = state.timeAlignment;
    
    // 更新UI
    const linkToggle = document.getElementById('linkTf');
    if (linkToggle) linkToggle.checked = this.isLinked;
    
    const crosshairToggle = document.getElementById('crosshairSync');
    if (crosshairToggle) crosshairToggle.checked = this.crosshairSync;
    
    const zoomToggle = document.getElementById('zoomSync');
    if (zoomToggle) zoomToggle.checked = this.zoomSync;
    
    const panToggle = document.getElementById('panSync');
    if (panToggle) panToggle.checked = this.panSync;
    
    const timeToggle = document.getElementById('timeAlignment');
    if (timeToggle) timeToggle.checked = this.timeAlignment;
  }
}

// 添加同步控制样式
const syncStyle = document.createElement('style');
syncStyle.textContent = `
  .sync-controls {
    margin-bottom: 12px;
    padding: 8px;
    background: var(--panel-alt);
    border: 1px solid var(--border);
    border-radius: 6px;
  }
  
  .sync-options {
    display: flex;
    gap: 16px;
    flex-wrap: wrap;
  }
  
  .sync-options label {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: var(--muted);
    cursor: pointer;
  }
  
  .sync-options input[type="checkbox"] {
    margin: 0;
  }
`;

document.head.appendChild(syncStyle);

// 初始化多周期联动管理器
const multiTimeframeManager = new MultiTimeframeManager();
