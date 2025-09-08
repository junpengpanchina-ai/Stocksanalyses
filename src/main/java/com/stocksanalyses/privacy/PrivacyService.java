package com.stocksanalyses.privacy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PrivacyService {

    @Autowired
    private DataAnonymizer dataAnonymizer;

    @Autowired
    private ImagePersistenceService imagePersistenceService;

    @Autowired
    private PrivacyConfig privacyConfig;

    public Map<String, Object> processDataForStorage(Map<String, Object> data) {
        Map<String, Object> processedData = dataAnonymizer.anonymizeData(data);
        
        if (privacyConfig.isLogDataAccess()) {
            logDataAccess("DATA_STORAGE", processedData);
        }
        
        return processedData;
    }

    public Map<String, Object> processDataForRetrieval(Map<String, Object> data) {
        if (privacyConfig.isLogDataAccess()) {
            logDataAccess("DATA_RETRIEVAL", data);
        }
        
        return data;
    }

    public boolean shouldPersistImage() {
        return imagePersistenceService.shouldPersistImage();
    }

    public String saveImage(org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        return imagePersistenceService.saveImage(file);
    }

    public void deleteImage(String fileName) {
        imagePersistenceService.deleteImage(fileName);
    }

    @Scheduled(cron = "0 0 2 * * ?") // Run daily at 2 AM
    public void cleanupExpiredData() {
        if (privacyConfig.isLogDataAccess()) {
            System.out.println("Starting data cleanup process...");
        }
        
        imagePersistenceService.cleanupExpiredImages();
        
        if (privacyConfig.isLogDataAccess()) {
            System.out.println("Data cleanup process completed.");
        }
    }

    private void logDataAccess(String action, Map<String, Object> data) {
        // This would typically log to a secure audit system
        System.out.println("Privacy Log - Action: " + action + ", Data: " + data.keySet());
    }

    public PrivacyConfig getPrivacyConfig() {
        return privacyConfig;
    }
}
