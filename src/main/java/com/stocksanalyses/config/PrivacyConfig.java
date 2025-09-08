package com.stocksanalyses.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "privacy")
public class PrivacyConfig {

    private boolean anonymizeData = false;
    private boolean persistImages = true;
    private int dataRetentionDays = 30;
    private boolean enableDataEncryption = false;
    private String encryptionKey = "";
    private boolean logDataAccess = true;

    // Getters and Setters
    public boolean isAnonymizeData() {
        return anonymizeData;
    }

    public void setAnonymizeData(boolean anonymizeData) {
        this.anonymizeData = anonymizeData;
    }

    public boolean isPersistImages() {
        return persistImages;
    }

    public void setPersistImages(boolean persistImages) {
        this.persistImages = persistImages;
    }

    public int getDataRetentionDays() {
        return dataRetentionDays;
    }

    public void setDataRetentionDays(int dataRetentionDays) {
        this.dataRetentionDays = dataRetentionDays;
    }

    public boolean isEnableDataEncryption() {
        return enableDataEncryption;
    }

    public void setEnableDataEncryption(boolean enableDataEncryption) {
        this.enableDataEncryption = enableDataEncryption;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    public boolean isLogDataAccess() {
        return logDataAccess;
    }

    public void setLogDataAccess(boolean logDataAccess) {
        this.logDataAccess = logDataAccess;
    }
}
