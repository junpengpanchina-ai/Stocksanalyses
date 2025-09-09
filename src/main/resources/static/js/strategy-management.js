// 策略管理增强
class StrategyManager {
  constructor() {
    this.strategies = new Map();
    this.activeStrategy = null;
    this.schemaValidator = new SchemaValidator();
    this.versionComparator = new VersionComparator();
    
    this.init();
  }

  init() {
    this.setupStrategyUI();
    this.loadStrategies();
    this.bindEvents();
  }

  setupStrategyUI() {
    // 创建增强的策略管理界面
    const strategyContainer = document.getElementById('strats');
    if (!strategyContainer) return;

    strategyContainer.innerHTML = `
      <div class="strategy-header">
        <h3>Strategy Management</h3>
        <div class="strategy-actions">
          <button id="createStrategy" class="btn primary">Create New</button>
          <button id="importStrategy" class="btn">Import</button>
          <button id="exportStrategy" class="btn">Export</button>
        </div>
      </div>
      
      <div class="strategy-tabs">
        <button class="tab-btn active" data-tab="list">List</button>
        <button class="tab-btn" data-tab="compare">Compare</button>
        <button class="tab-btn" data-tab="versions">Versions</button>
      </div>
      
      <div class="tab-content">
        <div id="strategyList" class="tab-panel active">
          <div class="strategy-list" id="strategyListContainer"></div>
        </div>
        
        <div id="strategyCompare" class="tab-panel">
          <div class="compare-container">
            <div class="compare-side">
              <h4>Version A</h4>
              <select id="compareVersionA" class="strategy-select"></select>
              <div id="versionADetails" class="version-details"></div>
            </div>
            <div class="compare-side">
              <h4>Version B</h4>
              <select id="compareVersionB" class="strategy-select"></select>
              <div id="versionBDetails" class="version-details"></div>
            </div>
          </div>
          <div id="compareResult" class="compare-result"></div>
        </div>
        
        <div id="strategyVersions" class="tab-panel">
          <div class="version-list" id="versionListContainer"></div>
        </div>
      </div>
      
      <div class="strategy-form" id="strategyForm" style="display: none;">
        <div class="form-header">
          <h4>Strategy Editor</h4>
          <button id="closeForm" class="btn btn-sm">×</button>
        </div>
        <div class="form-content">
          <div class="form-group">
            <label>Strategy ID</label>
            <input type="text" id="strategyId" placeholder="my-strategy" />
          </div>
          <div class="form-group">
            <label>Version</label>
            <input type="text" id="strategyVersion" placeholder="v1.0.0" />
          </div>
          <div class="form-group">
            <label>Name</label>
            <input type="text" id="strategyName" placeholder="Strategy Name" />
          </div>
          <div class="form-group">
            <label>Description</label>
            <textarea id="strategyDescription" placeholder="Strategy description..."></textarea>
          </div>
          <div class="form-group">
            <label>Strategy JSON</label>
            <textarea id="strategyJson" class="mono" placeholder='{"name":"strategy","params":{...}}'></textarea>
            <div id="schemaValidation" class="validation-result"></div>
          </div>
          <div class="form-actions">
            <button id="validateStrategy" class="btn">Validate</button>
            <button id="saveStrategy" class="btn primary">Save</button>
            <button id="cancelStrategy" class="btn">Cancel</button>
          </div>
        </div>
      </div>
      
      <input type="file" id="importFile" accept=".json" style="display: none;" />
    `;
  }

  bindEvents() {
    // 标签页切换
    document.querySelectorAll('.tab-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        this.switchTab(e.target.dataset.tab);
      });
    });

    // 策略操作
    document.getElementById('createStrategy')?.addEventListener('click', () => {
      this.showStrategyForm();
    });

    document.getElementById('importStrategy')?.addEventListener('click', () => {
      document.getElementById('importFile').click();
    });

    document.getElementById('exportStrategy')?.addEventListener('click', () => {
      this.exportStrategies();
    });

    // 表单操作
    document.getElementById('closeForm')?.addEventListener('click', () => {
      this.hideStrategyForm();
    });

    document.getElementById('validateStrategy')?.addEventListener('click', () => {
      this.validateStrategy();
    });

    document.getElementById('saveStrategy')?.addEventListener('click', () => {
      this.saveStrategy();
    });

    document.getElementById('cancelStrategy')?.addEventListener('click', () => {
      this.hideStrategyForm();
    });

    // 导入文件
    document.getElementById('importFile')?.addEventListener('change', (e) => {
      this.importStrategy(e.target.files[0]);
    });

    // 版本比较
    document.getElementById('compareVersionA')?.addEventListener('change', () => {
      this.updateCompareView();
    });

    document.getElementById('compareVersionB')?.addEventListener('change', () => {
      this.updateCompareView();
    });
  }

  switchTab(tabName) {
    // 更新标签页状态
    document.querySelectorAll('.tab-btn').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.tab === tabName);
    });

    document.querySelectorAll('.tab-panel').forEach(panel => {
      panel.classList.toggle('active', panel.id === `strategy${tabName.charAt(0).toUpperCase() + tabName.slice(1)}`);
    });

    // 加载对应内容
    switch (tabName) {
      case 'list':
        this.loadStrategyList();
        break;
      case 'compare':
        this.loadCompareView();
        break;
      case 'versions':
        this.loadVersionList();
        break;
    }
  }

  async loadStrategies() {
    try {
      const response = await fetchWithRetry('/api/strategies/latest');
      const strategies = await response.json();
      
      this.strategies.clear();
      Object.entries(strategies).forEach(([id, strategy]) => {
        this.strategies.set(id, strategy);
      });
      
      this.loadStrategyList();
    } catch (error) {
      console.error('Failed to load strategies:', error);
      this.showError('Failed to load strategies');
    }
  }

  loadStrategyList() {
    const container = document.getElementById('strategyListContainer');
    if (!container) return;

    if (this.strategies.size === 0) {
      container.innerHTML = '<div class="empty">No strategies found</div>';
      return;
    }

    const strategiesHTML = Array.from(this.strategies.entries()).map(([id, strategy]) => {
      const isActive = this.activeStrategy === id;
      const status = isActive ? 'active' : 'inactive';
      
      return `
        <div class="strategy-item ${status}" data-id="${id}">
          <div class="strategy-info">
            <div class="strategy-name">
              <h4>${strategy.name || id}</h4>
              <span class="strategy-id">${id}</span>
            </div>
            <div class="strategy-meta">
              <span class="version">v${strategy.version || '1.0.0'}</span>
              <span class="status ${status}">${status}</span>
              <span class="last-modified">${this.formatDate(strategy.lastModified)}</span>
            </div>
          </div>
          <div class="strategy-actions">
            <button class="btn btn-sm" onclick="strategyManager.editStrategy('${id}')">Edit</button>
            <button class="btn btn-sm" onclick="strategyManager.activateStrategy('${id}')">${isActive ? 'Deactivate' : 'Activate'}</button>
            <button class="btn btn-sm" onclick="strategyManager.deleteStrategy('${id}')">Delete</button>
          </div>
        </div>
      `;
    }).join('');

    container.innerHTML = strategiesHTML;
  }

  loadCompareView() {
    // 加载版本比较视图
    const versionA = document.getElementById('compareVersionA');
    const versionB = document.getElementById('compareVersionB');
    
    if (versionA && versionB) {
      this.populateVersionSelects(versionA, versionB);
    }
  }

  populateVersionSelects(selectA, selectB) {
    const versions = Array.from(this.strategies.entries()).map(([id, strategy]) => ({
      id,
      version: strategy.version || '1.0.0',
      name: strategy.name || id
    }));

    selectA.innerHTML = versions.map(v => 
      `<option value="${v.id}">${v.name} (${v.version})</option>`
    ).join('');

    selectB.innerHTML = versions.map(v => 
      `<option value="${v.id}">${v.name} (${v.version})</option>`
    ).join('');
  }

  updateCompareView() {
    const versionA = document.getElementById('compareVersionA').value;
    const versionB = document.getElementById('compareVersionB').value;
    
    if (!versionA || !versionB) return;

    const strategyA = this.strategies.get(versionA);
    const strategyB = this.strategies.get(versionB);
    
    if (!strategyA || !strategyB) return;

    // 显示版本详情
    this.showVersionDetails('versionADetails', strategyA);
    this.showVersionDetails('versionBDetails', strategyB);
    
    // 比较版本
    const comparison = this.versionComparator.compare(strategyA, strategyB);
    this.showComparisonResult(comparison);
  }

  showVersionDetails(containerId, strategy) {
    const container = document.getElementById(containerId);
    if (!container) return;

    container.innerHTML = `
      <div class="version-info">
        <h5>${strategy.name || 'Unnamed Strategy'}</h5>
        <p><strong>Version:</strong> ${strategy.version || '1.0.0'}</p>
        <p><strong>Description:</strong> ${strategy.description || 'No description'}</p>
        <p><strong>Last Modified:</strong> ${this.formatDate(strategy.lastModified)}</p>
        <div class="strategy-json">
          <pre>${JSON.stringify(strategy.dsl || {}, null, 2)}</pre>
        </div>
      </div>
    `;
  }

  showComparisonResult(comparison) {
    const container = document.getElementById('compareResult');
    if (!container) return;

    const changes = comparison.changes.map(change => `
      <div class="change-item ${change.type}">
        <span class="change-type">${change.type}</span>
        <span class="change-path">${change.path}</span>
        <span class="change-description">${change.description}</span>
      </div>
    `).join('');

    container.innerHTML = `
      <div class="comparison-summary">
        <h4>Comparison Result</h4>
        <p>Found ${comparison.changes.length} differences</p>
      </div>
      <div class="changes-list">
        ${changes}
      </div>
    `;
  }

  loadVersionList() {
    const container = document.getElementById('versionListContainer');
    if (!container) return;

    // 按策略ID分组版本
    const versionGroups = new Map();
    this.strategies.forEach((strategy, id) => {
      if (!versionGroups.has(id)) {
        versionGroups.set(id, []);
      }
      versionGroups.get(id).push(strategy);
    });

    const versionsHTML = Array.from(versionGroups.entries()).map(([id, versions]) => {
      const sortedVersions = versions.sort((a, b) => 
        new Date(b.lastModified) - new Date(a.lastModified)
      );

      return `
        <div class="version-group">
          <h4>${id}</h4>
          <div class="version-items">
            ${sortedVersions.map(version => `
              <div class="version-item">
                <div class="version-info">
                  <span class="version-number">v${version.version || '1.0.0'}</span>
                  <span class="version-date">${this.formatDate(version.lastModified)}</span>
                  ${this.activeStrategy === id ? '<span class="active-badge">Active</span>' : ''}
                </div>
                <div class="version-actions">
                  <button class="btn btn-xs" onclick="strategyManager.activateStrategy('${id}')">Activate</button>
                  <button class="btn btn-xs" onclick="strategyManager.editStrategy('${id}')">Edit</button>
                </div>
              </div>
            `).join('')}
          </div>
        </div>
      `;
    }).join('');

    container.innerHTML = versionsHTML;
  }

  showStrategyForm(strategy = null) {
    const form = document.getElementById('strategyForm');
    if (!form) return;

    if (strategy) {
      // 编辑模式
      document.getElementById('strategyId').value = strategy.id || '';
      document.getElementById('strategyVersion').value = strategy.version || '';
      document.getElementById('strategyName').value = strategy.name || '';
      document.getElementById('strategyDescription').value = strategy.description || '';
      document.getElementById('strategyJson').value = JSON.stringify(strategy.dsl || {}, null, 2);
    } else {
      // 创建模式
      document.getElementById('strategyId').value = '';
      document.getElementById('strategyVersion').value = '';
      document.getElementById('strategyName').value = '';
      document.getElementById('strategyDescription').value = '';
      document.getElementById('strategyJson').value = '{}';
    }

    form.style.display = 'block';
  }

  hideStrategyForm() {
    const form = document.getElementById('strategyForm');
    if (form) {
      form.style.display = 'none';
    }
  }

  async validateStrategy() {
    const json = document.getElementById('strategyJson').value;
    const container = document.getElementById('schemaValidation');
    
    try {
      const parsed = JSON.parse(json);
      const validation = this.schemaValidator.validate(parsed);
      
      if (validation.valid) {
        container.innerHTML = '<div class="validation-success">✓ Valid strategy schema</div>';
        container.className = 'validation-result success';
      } else {
        container.innerHTML = `<div class="validation-error">✗ ${validation.errors.join(', ')}</div>`;
        container.className = 'validation-result error';
      }
    } catch (error) {
      container.innerHTML = `<div class="validation-error">✗ JSON Error: ${error.message}</div>`;
      container.className = 'validation-result error';
    }
  }

  async saveStrategy() {
    const id = document.getElementById('strategyId').value.trim();
    const version = document.getElementById('strategyVersion').value.trim();
    const name = document.getElementById('strategyName').value.trim();
    const description = document.getElementById('strategyDescription').value.trim();
    const json = document.getElementById('strategyJson').value.trim();

    if (!id || !version) {
      this.showError('ID and version are required');
      return;
    }

    try {
      const dsl = JSON.parse(json);
      const strategy = {
        id,
        version,
        name,
        description,
        dsl,
        lastModified: new Date().toISOString()
      };

      const response = await fetchWithRetry('/api/strategies', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(strategy)
      });

      if (response.ok) {
        this.strategies.set(id, strategy);
        this.loadStrategyList();
        this.hideStrategyForm();
        this.showSuccess('Strategy saved successfully');
      } else {
        const error = await response.json();
        this.showError(`Failed to save strategy: ${error.message || 'Unknown error'}`);
      }
    } catch (error) {
      this.showError(`Failed to save strategy: ${error.message}`);
    }
  }

  editStrategy(id) {
    const strategy = this.strategies.get(id);
    if (strategy) {
      this.showStrategyForm(strategy);
    }
  }

  async activateStrategy(id) {
    try {
      const response = await fetchWithRetry(`/api/strategies/${id}/activate`, {
        method: 'POST'
      });

      if (response.ok) {
        this.activeStrategy = id;
        this.loadStrategyList();
        this.showSuccess(`Strategy ${id} activated`);
      } else {
        this.showError(`Failed to activate strategy ${id}`);
      }
    } catch (error) {
      this.showError(`Failed to activate strategy: ${error.message}`);
    }
  }

  async deleteStrategy(id) {
    if (!confirm(`Delete strategy ${id}?`)) return;

    try {
      const response = await fetchWithRetry(`/api/strategies/${id}`, {
        method: 'DELETE'
      });

      if (response.ok) {
        this.strategies.delete(id);
        if (this.activeStrategy === id) {
          this.activeStrategy = null;
        }
        this.loadStrategyList();
        this.showSuccess(`Strategy ${id} deleted`);
      } else {
        this.showError(`Failed to delete strategy ${id}`);
      }
    } catch (error) {
      this.showError(`Failed to delete strategy: ${error.message}`);
    }
  }

  exportStrategies() {
    const data = JSON.stringify(Array.from(this.strategies.entries()), null, 2);
    const blob = new Blob([data], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'strategies.json';
    a.click();
    URL.revokeObjectURL(url);
  }

  async importStrategy(file) {
    if (!file) return;

    try {
      const text = await file.text();
      const strategies = JSON.parse(text);
      
      for (const [id, strategy] of strategies) {
        this.strategies.set(id, strategy);
      }
      
      this.loadStrategyList();
      this.showSuccess('Strategies imported successfully');
    } catch (error) {
      this.showError(`Failed to import strategies: ${error.message}`);
    }
  }

  formatDate(dateString) {
    if (!dateString) return 'Unknown';
    return new Date(dateString).toLocaleDateString();
  }

  showError(message) {
    this.showMessage(message, 'error');
  }

  showSuccess(message) {
    this.showMessage(message, 'success');
  }

  showMessage(message, type) {
    // 实现消息显示逻辑
    console.log(`${type.toUpperCase()}: ${message}`);
  }
}

// Schema验证器
class SchemaValidator {
  validate(strategy) {
    const errors = [];
    
    if (!strategy.name) {
      errors.push('Strategy name is required');
    }
    
    if (!strategy.params) {
      errors.push('Strategy params are required');
    }
    
    if (strategy.params) {
      const requiredParams = ['emaShort', 'emaLong', 'macdFast', 'macdSlow', 'macdSignal'];
      requiredParams.forEach(param => {
        if (typeof strategy.params[param] !== 'number') {
          errors.push(`Parameter ${param} must be a number`);
        }
      });
    }
    
    return {
      valid: errors.length === 0,
      errors
    };
  }
}

// 版本比较器
class VersionComparator {
  compare(strategyA, strategyB) {
    const changes = [];
    
    // 比较基本属性
    if (strategyA.name !== strategyB.name) {
      changes.push({
        type: 'modified',
        path: 'name',
        description: `Changed from "${strategyA.name}" to "${strategyB.name}"`
      });
    }
    
    if (strategyA.version !== strategyB.version) {
      changes.push({
        type: 'modified',
        path: 'version',
        description: `Changed from "${strategyA.version}" to "${strategyB.version}"`
      });
    }
    
    // 比较参数
    if (strategyA.dsl && strategyB.dsl) {
      const paramsA = strategyA.dsl.params || {};
      const paramsB = strategyB.dsl.params || {};
      
      Object.keys({...paramsA, ...paramsB}).forEach(key => {
        if (paramsA[key] !== paramsB[key]) {
          changes.push({
            type: 'modified',
            path: `params.${key}`,
            description: `Changed from ${paramsA[key]} to ${paramsB[key]}`
          });
        }
      });
    }
    
    return { changes };
  }
}

// 添加策略管理样式
const strategyStyle = document.createElement('style');
strategyStyle.textContent = `
  .strategy-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
  }
  
  .strategy-actions {
    display: flex;
    gap: 8px;
  }
  
  .strategy-tabs {
    display: flex;
    gap: 4px;
    margin-bottom: 16px;
  }
  
  .tab-btn {
    padding: 8px 16px;
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 6px 6px 0 0;
    color: var(--muted);
    cursor: pointer;
  }
  
  .tab-btn.active {
    background: var(--panel-alt);
    color: var(--text);
    border-bottom-color: var(--panel-alt);
  }
  
  .tab-panel {
    display: none;
  }
  
  .tab-panel.active {
    display: block;
  }
  
  .strategy-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px;
    margin-bottom: 8px;
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 6px;
  }
  
  .strategy-item.active {
    border-color: var(--accent);
    background: rgba(255, 195, 0, 0.1);
  }
  
  .strategy-info {
    flex: 1;
  }
  
  .strategy-name h4 {
    margin: 0 0 4px 0;
    font-size: 14px;
  }
  
  .strategy-id {
    font-size: 12px;
    color: var(--muted);
  }
  
  .strategy-meta {
    display: flex;
    gap: 12px;
    font-size: 12px;
    color: var(--muted);
  }
  
  .status.active {
    color: var(--accent);
    font-weight: bold;
  }
  
  .strategy-actions {
    display: flex;
    gap: 6px;
  }
  
  .strategy-form {
    position: fixed;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    width: 80%;
    max-width: 600px;
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 8px;
    z-index: 1000;
  }
  
  .form-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 16px;
    border-bottom: 1px solid var(--border);
  }
  
  .form-content {
    padding: 16px;
  }
  
  .form-group {
    margin-bottom: 12px;
  }
  
  .form-group label {
    display: block;
    margin-bottom: 4px;
    font-size: 12px;
    color: var(--muted);
  }
  
  .form-group input,
  .form-group textarea {
    width: 100%;
    padding: 6px 8px;
    background: var(--panel-alt);
    border: 1px solid var(--border);
    border-radius: 4px;
    color: var(--text);
    font-size: 12px;
  }
  
  .form-group textarea {
    height: 120px;
    resize: vertical;
  }
  
  .form-actions {
    display: flex;
    gap: 8px;
    justify-content: flex-end;
    margin-top: 16px;
  }
  
  .validation-result {
    margin-top: 4px;
    padding: 4px 8px;
    border-radius: 4px;
    font-size: 11px;
  }
  
  .validation-result.success {
    background: rgba(76, 175, 80, 0.1);
    color: #4CAF50;
  }
  
  .validation-result.error {
    background: rgba(244, 67, 54, 0.1);
    color: #F44336;
  }
  
  .compare-container {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
    margin-bottom: 16px;
  }
  
  .compare-side {
    padding: 12px;
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 6px;
  }
  
  .compare-side h4 {
    margin: 0 0 8px 0;
    font-size: 13px;
    color: var(--muted);
  }
  
  .strategy-select {
    width: 100%;
    padding: 6px 8px;
    background: var(--panel-alt);
    border: 1px solid var(--border);
    border-radius: 4px;
    color: var(--text);
    font-size: 12px;
    margin-bottom: 12px;
  }
  
  .version-details {
    font-size: 11px;
    color: var(--muted);
  }
  
  .strategy-json {
    margin-top: 8px;
    padding: 8px;
    background: var(--panel-alt);
    border-radius: 4px;
    max-height: 200px;
    overflow: auto;
  }
  
  .strategy-json pre {
    margin: 0;
    font-size: 10px;
  }
  
  .compare-result {
    padding: 12px;
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 6px;
  }
  
  .comparison-summary {
    margin-bottom: 12px;
  }
  
  .comparison-summary h4 {
    margin: 0 0 4px 0;
    font-size: 13px;
  }
  
  .changes-list {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  
  .change-item {
    display: flex;
    gap: 8px;
    padding: 4px 8px;
    background: var(--panel-alt);
    border-radius: 4px;
    font-size: 11px;
  }
  
  .change-type {
    font-weight: bold;
    min-width: 60px;
  }
  
  .change-item.modified .change-type {
    color: var(--accent);
  }
  
  .change-path {
    color: var(--muted);
    font-family: monospace;
  }
  
  .version-group {
    margin-bottom: 16px;
  }
  
  .version-group h4 {
    margin: 0 0 8px 0;
    font-size: 13px;
    color: var(--muted);
  }
  
  .version-items {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  
  .version-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px;
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 4px;
  }
  
  .version-info {
    display: flex;
    gap: 8px;
    align-items: center;
  }
  
  .version-number {
    font-weight: bold;
    font-size: 12px;
  }
  
  .version-date {
    font-size: 11px;
    color: var(--muted);
  }
  
  .active-badge {
    padding: 2px 6px;
    background: var(--accent);
    color: #111;
    border-radius: 3px;
    font-size: 10px;
    font-weight: bold;
  }
  
  .version-actions {
    display: flex;
    gap: 4px;
  }
`;

document.head.appendChild(strategyStyle);

// 初始化策略管理器
const strategyManager = new StrategyManager();
