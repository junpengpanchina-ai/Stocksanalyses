package com.stocksanalyses.privacy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class ImagePersistenceService {

    @Autowired
    private PrivacyConfig privacyConfig;

    @Value("${privacy.image-storage-path:./uploads}")
    private String imageStoragePath;

    public String saveImage(MultipartFile file) throws IOException {
        if (!privacyConfig.isPersistImages()) {
            return null; // Don't persist images if privacy setting is disabled
        }

        String fileName = generateFileName(file.getOriginalFilename());
        Path filePath = Paths.get(imageStoragePath, fileName);
        
        // Create directory if it doesn't exist
        Files.createDirectories(filePath.getParent());
        
        // Save file
        Files.copy(file.getInputStream(), filePath);
        
        return fileName;
    }

    public void deleteImage(String fileName) {
        if (fileName == null) return;
        
        try {
            Path filePath = Paths.get(imageStoragePath, fileName);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            System.err.println("Failed to delete image: " + e.getMessage());
        }
    }

    public void cleanupExpiredImages() {
        if (!privacyConfig.isPersistImages()) {
            return;
        }

        try {
            Path storagePath = Paths.get(imageStoragePath);
            if (!Files.exists(storagePath)) {
                return;
            }

            Instant cutoffTime = Instant.now().minus(privacyConfig.getDataRetentionDays(), ChronoUnit.DAYS);
            
            Files.walk(storagePath)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant().isBefore(cutoffTime);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            System.out.println("Deleted expired image: " + path);
                        } catch (IOException e) {
                            System.err.println("Failed to delete expired image: " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            System.err.println("Failed to cleanup expired images: " + e.getMessage());
        }
    }

    public boolean shouldPersistImage() {
        return privacyConfig.isPersistImages();
    }

    private String generateFileName(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return UUID.randomUUID().toString() + extension;
    }
}
