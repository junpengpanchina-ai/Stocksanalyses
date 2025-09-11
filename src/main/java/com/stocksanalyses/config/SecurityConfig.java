package com.stocksanalyses.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * 安全配置：API Key加密存储（ENV 注入密码/盐）
 */
@Configuration
public class SecurityConfig {

    @Value("${security.encryption.password:${ENC_PASSWORD:}}")
    private String encryptionPassword;

    @Value("${security.encryption.salt:${ENC_SALT:}}")
    private String encryptionSalt;

    @Bean
    public TextEncryptor textEncryptor() {
        if (encryptionPassword == null || encryptionPassword.isEmpty() || encryptionSalt == null || encryptionSalt.isEmpty()) {
            // 回退到不可逆 no-op（但仍返回一个加密器以避免 NPE）；强烈建议配置 ENV
            return new TextEncryptor() {
                @Override public String encrypt(String text) { return text; }
                @Override public String decrypt(String encryptedText) { return encryptedText; }
            };
        }
        return Encryptors.text(encryptionPassword, encryptionSalt);
    }

    @Bean
    public ApiKeyManager apiKeyManager(TextEncryptor textEncryptor) {
        return new ApiKeyManager(textEncryptor);
    }
}