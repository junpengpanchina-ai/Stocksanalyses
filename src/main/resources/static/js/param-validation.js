// 参数验证和预设管理
class ParamValidator {
  constructor() {
    this.rules = {
      emaShort: { min: 1, max: 200, type: 'integer', default: 20 },
      emaLong: { min: 1, max: 500, type: 'integer', default: 50 },
      macdFast: { min: 1, max: 50, type: 'integer', default: 12 },
      macdSlow: { min: 1, max: 100, type: 'integer', default: 26 },
      macdSignal: { min: 1, max: 50, type: 'integer', default: 9 },
      symbol: { minLength: 1, maxLength: 10, type: 'string', pattern: /^[A-Z0-9]+$/ },
      interval: { type: 'string', enum: ['1m', '5m', '15m', '1h', '4h', '1d'] }
    };
    
    this.presets = {
      'scalping': { emaShort: 5, emaLong: 20, macdFast: 8, macdSlow: 17, macdSignal: 9 },
      'swing': { emaShort: 20, emaLong: 50, macdFast: 12, macdSlow: 26, macdSignal: 9 },
      'position': { emaShort: 50, emaLong: 200, macdFast: 12, macdSlow: 26, macdSignal: 9 },
      'crypto': { emaShort: 12, emaLong: 26, macdFast: 12, macdSlow: 26, macdSignal: 9 }
    };
  }

  validate(paramName, value) {
    const rule = this.rules[paramName];
    if (!rule) return { valid: true };

    const errors = [];

    // 类型检查
    if (rule.type === 'integer') {
      const num = parseInt(value);
      if (isNaN(num)) {
        errors.push(`${paramName} must be a number`);
      } else {
        if (rule.min !== undefined && num < rule.min) {
          errors.push(`${paramName} must be >= ${rule.min}`);
        }
        if (rule.max !== undefined && num > rule.max) {
          errors.push(`${paramName} must be <= ${rule.max}`);
        }
      }
    } else if (rule.type === 'string') {
      if (rule.minLength && value.length < rule.minLength) {
        errors.push(`${paramName} must be at least ${rule.minLength} characters`);
      }
      if (rule.maxLength && value.length > rule.maxLength) {
        errors.push(`${paramName} must be at most ${rule.maxLength} characters`);
      }
      if (rule.pattern && !rule.pattern.test(value)) {
        errors.push(`${paramName} format is invalid`);
      }
      if (rule.enum && !rule.enum.includes(value)) {
        errors.push(`${paramName} must be one of: ${rule.enum.join(', ')}`);
      }
    }

    return {
      valid: errors.length === 0,
      errors: errors,
      value: rule.type === 'integer' ? parseInt(value) : value
    };
  }

  validateAll(params) {
    const results = {};
    let allValid = true;

    for (const [key, value] of Object.entries(params)) {
      const result = this.validate(key, value);
      results[key] = result;
      if (!result.valid) allValid = false;
    }

    return { valid: allValid, results };
  }

  getPreset(name) {
    return this.presets[name] || null;
  }

  getPresetNames() {
    return Object.keys(this.presets);
  }

  savePreset(name, params) {
    this.presets[name] = { ...params };
    this.saveToStorage();
  }

  loadFromStorage() {
    try {
      const stored = localStorage.getItem('kline_presets');
      if (stored) {
        const parsed = JSON.parse(stored);
        this.presets = { ...this.presets, ...parsed };
      }
    } catch (e) {
      console.warn('Failed to load presets from storage:', e);
    }
  }

  saveToStorage() {
    try {
      localStorage.setItem('kline_presets', JSON.stringify(this.presets));
    } catch (e) {
      console.warn('Failed to save presets to storage:', e);
    }
  }

  exportPresets() {
    return JSON.stringify(this.presets, null, 2);
  }

  importPresets(jsonString) {
    try {
      const imported = JSON.parse(jsonString);
      this.presets = { ...this.presets, ...imported };
      this.saveToStorage();
      return true;
    } catch (e) {
      return false;
    }
  }
}

// 参数面板UI管理
class ParamPanel {
  constructor(validator) {
    this.validator = validator;
    this.currentParams = {};
    this.errorElements = {};
    this.init();
  }

  init() {
    this.createPresetUI();
    this.bindValidation();
    this.loadCurrentParams();
  }

  createPresetUI() {
    const presetContainer = document.createElement('div');
    presetContainer.className = 'preset-container';
    presetContainer.innerHTML = `
      <div class="preset-header">
        <h4>Parameter Presets</h4>
        <div class="preset-actions">
          <button id="savePreset" class="btn btn-sm">Save Current</button>
          <button id="exportPresets" class="btn btn-sm">Export</button>
          <button id="importPresets" class="btn btn-sm">Import</button>
        </div>
      </div>
      <div class="preset-list" id="presetList"></div>
      <div class="preset-form" id="presetForm" style="display: none;">
        <input type="text" id="presetName" placeholder="Preset name" />
        <button id="confirmSave" class="btn btn-sm primary">Save</button>
        <button id="cancelSave" class="btn btn-sm">Cancel</button>
      </div>
      <input type="file" id="importFile" accept=".json" style="display: none;" />
    `;

    // 插入到参数面板
    const paramPanel = document.querySelector('.kv');
    if (paramPanel) {
      paramPanel.parentNode.insertBefore(presetContainer, paramPanel.nextSibling);
    }

    this.bindPresetEvents();
    this.renderPresetList();
  }

  bindPresetEvents() {
    document.getElementById('savePreset')?.addEventListener('click', () => {
      document.getElementById('presetForm').style.display = 'flex';
    });

    document.getElementById('confirmSave')?.addEventListener('click', () => {
      const name = document.getElementById('presetName').value.trim();
      if (name) {
        this.saveCurrentAsPreset(name);
        document.getElementById('presetForm').style.display = 'none';
        document.getElementById('presetName').value = '';
        this.renderPresetList();
      }
    });

    document.getElementById('cancelSave')?.addEventListener('click', () => {
      document.getElementById('presetForm').style.display = 'none';
      document.getElementById('presetName').value = '';
    });

    document.getElementById('exportPresets')?.addEventListener('click', () => {
      this.exportPresets();
    });

    document.getElementById('importPresets')?.addEventListener('click', () => {
      document.getElementById('importFile').click();
    });

    document.getElementById('importFile')?.addEventListener('change', (e) => {
      const file = e.target.files[0];
      if (file) {
        const reader = new FileReader();
        reader.onload = (e) => {
          if (this.validator.importPresets(e.target.result)) {
            this.renderPresetList();
            this.showMessage('Presets imported successfully', 'success');
          } else {
            this.showMessage('Invalid preset file', 'error');
          }
        };
        reader.readAsText(file);
      }
    });
  }

  bindValidation() {
    const paramInputs = ['symbol', 'interval', 'emaShort', 'emaLong', 'macdFast', 'macdSlow', 'macdSignal'];
    
    paramInputs.forEach(paramName => {
      const input = document.getElementById(paramName);
      if (input) {
        // 创建错误显示元素
        const errorDiv = document.createElement('div');
        errorDiv.className = 'param-error';
        errorDiv.style.display = 'none';
        input.parentNode.insertBefore(errorDiv, input.nextSibling);
        this.errorElements[paramName] = errorDiv;

        // 绑定验证事件
        input.addEventListener('blur', () => {
          this.validateAndShowError(paramName, input.value);
        });

        input.addEventListener('input', () => {
          this.clearError(paramName);
        });
      }
    });
  }

  validateAndShowError(paramName, value) {
    const result = this.validator.validate(paramName, value);
    const errorDiv = this.errorElements[paramName];
    
    if (!result.valid) {
      errorDiv.textContent = result.errors.join(', ');
      errorDiv.style.display = 'block';
      errorDiv.style.color = '#ffb4b4';
      errorDiv.style.fontSize = '11px';
      errorDiv.style.marginTop = '2px';
    } else {
      this.clearError(paramName);
    }
  }

  clearError(paramName) {
    const errorDiv = this.errorElements[paramName];
    if (errorDiv) {
      errorDiv.style.display = 'none';
    }
  }

  loadCurrentParams() {
    const paramInputs = ['symbol', 'interval', 'emaShort', 'emaLong', 'macdFast', 'macdSlow', 'macdSignal'];
    this.currentParams = {};
    
    paramInputs.forEach(paramName => {
      const input = document.getElementById(paramName);
      if (input && input.value) {
        this.currentParams[paramName] = input.value;
      }
    });
  }

  saveCurrentAsPreset(name) {
    this.loadCurrentParams();
    this.validator.savePreset(name, this.currentParams);
    this.showMessage(`Preset "${name}" saved`, 'success');
  }

  applyPreset(presetName) {
    const preset = this.validator.getPreset(presetName);
    if (preset) {
      Object.entries(preset).forEach(([key, value]) => {
        const input = document.getElementById(key);
        if (input) {
          input.value = value;
          this.validateAndShowError(key, value);
        }
      });
      this.showMessage(`Applied preset "${presetName}"`, 'success');
    }
  }

  renderPresetList() {
    const list = document.getElementById('presetList');
    if (!list) return;

    const presets = this.validator.getPresetNames();
    list.innerHTML = presets.map(name => `
      <div class="preset-item">
        <span class="preset-name">${name}</span>
        <div class="preset-actions">
          <button class="btn btn-xs" onclick="paramPanel.applyPreset('${name}')">Apply</button>
          <button class="btn btn-xs" onclick="paramPanel.deletePreset('${name}')">Delete</button>
        </div>
      </div>
    `).join('');
  }

  deletePreset(name) {
    if (confirm(`Delete preset "${name}"?`)) {
      delete this.validator.presets[name];
      this.validator.saveToStorage();
      this.renderPresetList();
      this.showMessage(`Preset "${name}" deleted`, 'success');
    }
  }

  exportPresets() {
    const data = this.validator.exportPresets();
    const blob = new Blob([data], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'kline-presets.json';
    a.click();
    URL.revokeObjectURL(url);
  }

  showMessage(message, type = 'info') {
    // 创建消息提示
    const messageDiv = document.createElement('div');
    messageDiv.className = `message message-${type}`;
    messageDiv.textContent = message;
    messageDiv.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      padding: 12px 16px;
      border-radius: 6px;
      color: white;
      font-size: 14px;
      z-index: 1000;
      animation: slideIn 0.3s ease;
    `;

    if (type === 'success') {
      messageDiv.style.background = '#26A69A';
    } else if (type === 'error') {
      messageDiv.style.background = '#EF5350';
    } else {
      messageDiv.style.background = '#FFC300';
      messageDiv.style.color = '#111';
    }

    document.body.appendChild(messageDiv);

    setTimeout(() => {
      messageDiv.style.animation = 'slideOut 0.3s ease';
      setTimeout(() => {
        document.body.removeChild(messageDiv);
      }, 300);
    }, 3000);
  }
}

// 初始化
const validator = new ParamValidator();
const paramPanel = new ParamPanel(validator);

// 添加CSS动画
const style = document.createElement('style');
style.textContent = `
  .preset-container {
    margin-top: 12px;
    padding: 12px;
    background: var(--panel-alt);
    border: 1px solid var(--border);
    border-radius: 8px;
  }
  
  .preset-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
  }
  
  .preset-header h4 {
    margin: 0;
    font-size: 13px;
    color: var(--muted);
  }
  
  .preset-actions {
    display: flex;
    gap: 6px;
  }
  
  .preset-list {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  
  .preset-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 6px 8px;
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 4px;
  }
  
  .preset-name {
    font-size: 12px;
    color: var(--text);
  }
  
  .preset-form {
    display: flex;
    gap: 6px;
    align-items: center;
    margin-top: 8px;
  }
  
  .preset-form input {
    flex: 1;
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 4px;
    color: var(--text);
    padding: 4px 6px;
    font-size: 12px;
  }
  
  .btn-sm {
    padding: 4px 8px;
    font-size: 11px;
  }
  
  .btn-xs {
    padding: 2px 6px;
    font-size: 10px;
  }
  
  .param-error {
    color: #ffb4b4;
    font-size: 11px;
    margin-top: 2px;
  }
  
  @keyframes slideIn {
    from { transform: translateX(100%); opacity: 0; }
    to { transform: translateX(0); opacity: 1; }
  }
  
  @keyframes slideOut {
    from { transform: translateX(0); opacity: 1; }
    to { transform: translateX(100%); opacity: 0; }
  }
`;
document.head.appendChild(style);
