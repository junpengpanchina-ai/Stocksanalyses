// 可靠性增强
class ReliabilityManager {
  constructor() {
    this.retryConfig = {
      maxRetries: 3,
      baseDelay: 1000,
      maxDelay: 10000,
      jitter: true,
      backoffMultiplier: 2
    };
    
    this.errorHandlers = new Map();
    this.circuitBreakers = new Map();
    this.fallbackUI = new FallbackUI();
    
    this.init();
  }

  init() {
    this.setupErrorHandling();
    this.setupCircuitBreakers();
    this.setupGlobalErrorHandler();
    this.enhanceFetchWithRetry();
  }

  setupErrorHandling() {
    // 注册错误处理器
    this.errorHandlers.set('4xx', this.handle4xxError.bind(this));
    this.errorHandlers.set('5xx', this.handle5xxError.bind(this));
    this.errorHandlers.set('network', this.handleNetworkError.bind(this));
    this.errorHandlers.set('timeout', this.handleTimeoutError.bind(this));
  }

  setupCircuitBreakers() {
    // 为不同服务设置熔断器
    const services = ['api', 'upload', 'strategy', 'matching'];
    services.forEach(service => {
      this.circuitBreakers.set(service, new CircuitBreaker({
        failureThreshold: 5,
        resetTimeout: 30000,
        monitoringPeriod: 60000
      }));
    });
  }

  setupGlobalErrorHandler() {
    // 全局错误处理
    window.addEventListener('error', (event) => {
      this.handleGlobalError(event.error);
    });

    window.addEventListener('unhandledrejection', (event) => {
      this.handleGlobalError(event.reason);
    });
  }

  enhanceFetchWithRetry() {
    // 增强原有的fetchWithRetry函数
    const originalFetchWithRetry = window.fetchWithRetry;
    
    window.fetchWithRetry = async (url, init = {}, retries = this.retryConfig.maxRetries, backoff = this.retryConfig.baseDelay) => {
      const service = this.getServiceFromUrl(url);
      const circuitBreaker = this.circuitBreakers.get(service);
      
      // 检查熔断器状态
      if (circuitBreaker && circuitBreaker.isOpen()) {
        throw new Error(`Service ${service} is currently unavailable (circuit breaker open)`);
      }

      try {
        const response = await this.fetchWithAdvancedRetry(url, init, retries, backoff);
        
        // 记录成功请求
        if (circuitBreaker) {
          circuitBreaker.recordSuccess();
        }
        
        return response;
      } catch (error) {
        // 记录失败请求
        if (circuitBreaker) {
          circuitBreaker.recordFailure();
        }
        
        // 根据错误类型处理
        const errorType = this.classifyError(error);
        const handler = this.errorHandlers.get(errorType);
        
        if (handler) {
          handler(error, url, init);
        }
        
        throw error;
      }
    };
  }

  async fetchWithAdvancedRetry(url, init, retries, backoff) {
    const startTime = Date.now();
    
    for (let attempt = 0; attempt <= retries; attempt++) {
      try {
        // 添加超时控制
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 30000); // 30秒超时
        
        const response = await fetch(url, {
          ...init,
          signal: controller.signal
        });
        
        clearTimeout(timeoutId);
        
        // 根据状态码决定是否重试
        if (response.ok) {
          return response;
        }
        
        const status = response.status;
        if (status >= 400 && status < 500) {
          // 4xx错误通常不需要重试
          throw new Error(`HTTP ${status}: ${response.statusText}`);
        }
        
        if (status >= 500 && attempt < retries) {
          // 5xx错误可以重试
          throw new Error(`HTTP ${status}: ${response.statusText}`);
        }
        
        return response;
        
      } catch (error) {
        if (attempt === retries) {
          throw error;
        }
        
        // 计算延迟时间（带抖动）
        const delay = this.calculateDelay(attempt, backoff);
        await this.sleep(delay);
      }
    }
  }

  calculateDelay(attempt, baseDelay) {
    let delay = baseDelay * Math.pow(this.retryConfig.backoffMultiplier, attempt);
    
    // 添加抖动
    if (this.retryConfig.jitter) {
      const jitter = Math.random() * 0.1 * delay; // 10%的抖动
      delay += jitter;
    }
    
    // 限制最大延迟
    return Math.min(delay, this.retryConfig.maxDelay);
  }

  sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  getServiceFromUrl(url) {
    if (url.includes('/api/upload')) return 'upload';
    if (url.includes('/api/strategy')) return 'strategy';
    if (url.includes('/api/matching')) return 'matching';
    return 'api';
  }

  classifyError(error) {
    if (error.name === 'AbortError') return 'timeout';
    if (error.message.includes('HTTP 4')) return '4xx';
    if (error.message.includes('HTTP 5')) return '5xx';
    if (error.message.includes('NetworkError') || error.message.includes('Failed to fetch')) return 'network';
    return 'unknown';
  }

  handle4xxError(error, url, init) {
    console.warn('4xx Error:', error.message);
    this.fallbackUI.showWarning(`Request failed: ${error.message}`);
  }

  handle5xxError(error, url, init) {
    console.error('5xx Error:', error.message);
    this.fallbackUI.showError(`Server error: ${error.message}`);
  }

  handleNetworkError(error, url, init) {
    console.error('Network Error:', error.message);
    this.fallbackUI.showError('Network connection failed. Please check your internet connection.');
  }

  handleTimeoutError(error, url, init) {
    console.error('Timeout Error:', error.message);
    this.fallbackUI.showError('Request timed out. Please try again.');
  }

  handleGlobalError(error) {
    console.error('Global Error:', error);
    this.fallbackUI.showError('An unexpected error occurred. Please refresh the page.');
  }
}

// 熔断器实现
class CircuitBreaker {
  constructor(options = {}) {
    this.failureThreshold = options.failureThreshold || 5;
    this.resetTimeout = options.resetTimeout || 30000;
    this.monitoringPeriod = options.monitoringPeriod || 60000;
    
    this.failureCount = 0;
    this.lastFailureTime = null;
    this.state = 'CLOSED'; // CLOSED, OPEN, HALF_OPEN
    this.nextAttempt = null;
  }

  isOpen() {
    if (this.state === 'OPEN') {
      if (Date.now() - this.lastFailureTime > this.resetTimeout) {
        this.state = 'HALF_OPEN';
        return false;
      }
      return true;
    }
    return false;
  }

  recordSuccess() {
    this.failureCount = 0;
    this.state = 'CLOSED';
    this.lastFailureTime = null;
  }

  recordFailure() {
    this.failureCount++;
    this.lastFailureTime = Date.now();
    
    if (this.failureCount >= this.failureThreshold) {
      this.state = 'OPEN';
    }
  }
}

// 降级UI
class FallbackUI {
  constructor() {
    this.notifications = [];
    this.fallbackMode = false;
    this.init();
  }

  init() {
    this.createNotificationContainer();
    this.createFallbackUI();
  }

  createNotificationContainer() {
    const container = document.createElement('div');
    container.id = 'notification-container';
    container.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      z-index: 10000;
      display: flex;
      flex-direction: column;
      gap: 8px;
    `;
    document.body.appendChild(container);
  }

  createFallbackUI() {
    const fallback = document.createElement('div');
    fallback.id = 'fallback-ui';
    fallback.style.cssText = `
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0, 0, 0, 0.8);
      z-index: 9999;
      display: none;
      align-items: center;
      justify-content: center;
    `;
    
    fallback.innerHTML = `
      <div class="fallback-content">
        <h2>Service Unavailable</h2>
        <p>Some services are currently unavailable. You can continue using the application with limited functionality.</p>
        <div class="fallback-actions">
          <button id="retry-connection" class="btn primary">Retry Connection</button>
          <button id="continue-offline" class="btn">Continue Offline</button>
        </div>
      </div>
    `;
    
    document.body.appendChild(fallback);
    
    // 绑定事件
    document.getElementById('retry-connection')?.addEventListener('click', () => {
      this.retryConnection();
    });
    
    document.getElementById('continue-offline')?.addEventListener('click', () => {
      this.continueOffline();
    });
  }

  showError(message) {
    this.showNotification(message, 'error');
  }

  showWarning(message) {
    this.showNotification(message, 'warning');
  }

  showSuccess(message) {
    this.showNotification(message, 'success');
  }

  showNotification(message, type = 'info') {
    const notification = document.createElement('div');
    notification.className = `notification notification-${type}`;
    notification.innerHTML = `
      <div class="notification-content">
        <span class="notification-message">${message}</span>
        <button class="notification-close">×</button>
      </div>
    `;
    
    // 添加样式
    notification.style.cssText = `
      padding: 12px 16px;
      border-radius: 6px;
      color: white;
      font-size: 14px;
      max-width: 300px;
      animation: slideIn 0.3s ease;
      ${this.getNotificationStyle(type)}
    `;
    
    // 添加到容器
    const container = document.getElementById('notification-container');
    container.appendChild(notification);
    
    // 绑定关闭事件
    notification.querySelector('.notification-close').addEventListener('click', () => {
      this.removeNotification(notification);
    });
    
    // 自动关闭
    setTimeout(() => {
      this.removeNotification(notification);
    }, 5000);
    
    this.notifications.push(notification);
  }

  getNotificationStyle(type) {
    const styles = {
      error: 'background: #F44336; border-left: 4px solid #D32F2F;',
      warning: 'background: #FF9800; border-left: 4px solid #F57C00;',
      success: 'background: #4CAF50; border-left: 4px solid #388E3C;',
      info: 'background: #2196F3; border-left: 4px solid #1976D2;'
    };
    return styles[type] || styles.info;
  }

  removeNotification(notification) {
    if (notification && notification.parentNode) {
      notification.style.animation = 'slideOut 0.3s ease';
      setTimeout(() => {
        if (notification.parentNode) {
          notification.parentNode.removeChild(notification);
        }
      }, 300);
    }
  }

  showFallbackUI() {
    const fallback = document.getElementById('fallback-ui');
    if (fallback) {
      fallback.style.display = 'flex';
      this.fallbackMode = true;
    }
  }

  hideFallbackUI() {
    const fallback = document.getElementById('fallback-ui');
    if (fallback) {
      fallback.style.display = 'none';
      this.fallbackMode = false;
    }
  }

  retryConnection() {
    // 重试连接逻辑
    this.showSuccess('Retrying connection...');
    setTimeout(() => {
      this.hideFallbackUI();
      this.showSuccess('Connection restored');
    }, 2000);
  }

  continueOffline() {
    this.hideFallbackUI();
    this.showWarning('Continuing in offline mode');
  }
}

// 添加样式
const reliabilityStyle = document.createElement('style');
reliabilityStyle.textContent = `
  .notification-content {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  
  .notification-close {
    background: none;
    border: none;
    color: white;
    font-size: 18px;
    cursor: pointer;
    padding: 0;
    margin-left: 8px;
  }
  
  .fallback-content {
    background: var(--panel);
    padding: 32px;
    border-radius: 8px;
    text-align: center;
    max-width: 400px;
  }
  
  .fallback-content h2 {
    margin: 0 0 16px 0;
    color: var(--text);
  }
  
  .fallback-content p {
    margin: 0 0 24px 0;
    color: var(--muted);
    line-height: 1.5;
  }
  
  .fallback-actions {
    display: flex;
    gap: 12px;
    justify-content: center;
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

document.head.appendChild(reliabilityStyle);

// 初始化可靠性管理器
const reliabilityManager = new ReliabilityManager();
