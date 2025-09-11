package com.stocksanalyses.config;

import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API Key安全管理器
 */
@Component
public class ApiKeyManager {
    
    private final TextEncryptor textEncryptor;
    private final Path keysFile;
    private final Map<String, String> keyCache = new ConcurrentHashMap<>();
    
    public ApiKeyManager(TextEncryptor textEncryptor) {
        this.textEncryptor = textEncryptor;
        this.keysFile = Paths.get("data/security/api-keys.enc");
        loadKeys();
    }
    
    /**
     * 存储加密的API Key
     */
    public void storeApiKey(String provider, String apiKey) {
        try {
            String encrypted = textEncryptor.encrypt(apiKey);
            keyCache.put(provider, encrypted);
            saveKeys();
        } catch (Exception e) {
            throw new RuntimeException("Failed to store API key for " + provider, e);
        }
    }
    
    /**
     * 获取解密的API Key
     */
    public String getApiKey(String provider) {
        String encrypted = keyCache.get(provider);
        if (encrypted == null) {
            return null;
        }
        try {
            return textEncryptor.decrypt(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt API key for " + provider, e);
        }
    }
    
    /**
     * 删除API Key
     */
    public void removeApiKey(String provider) {
        keyCache.remove(provider);
        saveKeys();
    }
    
    /**
     * 列出所有存储的provider
     */
    public Map<String, Boolean> listProviders() {
        Map<String, Boolean> result = new HashMap<>();
        for (String provider : keyCache.keySet()) {
            result.put(provider, true);
        }
        return result;
    }
    
    private void loadKeys() {
        try {
            if (Files.exists(keysFile)) {
                String content = Files.readString(keysFile);
                if (!content.trim().isEmpty()) {
                    String[] lines = content.split("\n");
                    for (String line : lines) {
                        String[] parts = line.split("=", 2);
                        if (parts.length == 2) {
                            keyCache.put(parts[0], parts[1]);
                        }
                    }
                }
            }
        } catch (IOException e) {
            // 文件不存在或读取失败，使用空缓存
        }
    }
    
    private void saveKeys() {
        try {
            Files.createDirectories(keysFile.getParent());
            StringBuilder content = new StringBuilder();
            for (Map.Entry<String, String> entry : keyCache.entrySet()) {
                content.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
            }
            Files.writeString(keysFile, content.toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save API keys", e);
        }
    }
}
