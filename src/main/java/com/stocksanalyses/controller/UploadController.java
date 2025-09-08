package com.stocksanalyses.controller;

import com.stocksanalyses.audit.AuditService;
import com.stocksanalyses.model.UploadAnalyzeResult;
import com.stocksanalyses.privacy.PrivacyService;
import com.stocksanalyses.security.RateLimit;
import com.stocksanalyses.service.UploadAnalyzeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
public class UploadController {
    private final UploadAnalyzeService uploadAnalyzeService;
    private final AuditService auditService;
    private final PrivacyService privacyService;

    public UploadController(UploadAnalyzeService uploadAnalyzeService, 
                          @Autowired(required = false) AuditService auditService,
                          @Autowired(required = false) PrivacyService privacyService) {
        this.uploadAnalyzeService = uploadAnalyzeService;
        this.auditService = auditService;
        this.privacyService = privacyService;
    }

    @PostMapping(path = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimit(value = 10, windowMinutes = 1) // 10 requests per minute for uploads
    public UploadAnalyzeResult analyze(@RequestPart("file") MultipartFile file,
                                       @RequestParam(value = "hintStyle", required = false) String hintStyle,
                                       @RequestParam(value = "calibX1", required = false) Double calibX1,
                                       @RequestParam(value = "calibY1", required = false) Double calibY1,
                                       @RequestParam(value = "calibPrice1", required = false) Double calibPrice1,
                                       @RequestParam(value = "calibX2", required = false) Double calibX2,
                                       @RequestParam(value = "calibY2", required = false) Double calibY2,
                                       @RequestParam(value = "calibPrice2", required = false) Double calibPrice2,
                                       @RequestParam(value = "emaShort", required = false) Integer emaShort,
                                       @RequestParam(value = "emaLong", required = false) Integer emaLong,
                                       @RequestParam(value = "macdFast", required = false) Integer macdFast,
                                       @RequestParam(value = "macdSlow", required = false) Integer macdSlow,
                                       @RequestParam(value = "macdSignal", required = false) Integer macdSignal,
                                       HttpServletRequest request) {
        validateFile(file);
        
        // Audit logging
        if (auditService != null) {
            auditService.logAction("UPLOAD_ANALYZE", "UPLOAD", file.getOriginalFilename(), request);
        }
        
        // Privacy handling for image persistence
        if (privacyService != null && !privacyService.shouldPersistImage()) {
            // If privacy settings don't allow image persistence, we might want to process differently
            // For now, we'll still process but not persist
        }
        
        return uploadAnalyzeService.analyze(file, hintStyle, calibX1, calibY1, calibPrice1, calibX2, calibY2, calibPrice2,
                emaShort, emaLong, macdFast, macdSlow, macdSignal);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("file empty");
        String ct = file.getContentType();
        if (ct == null || !(ct.equals("image/png") || ct.equals("image/jpeg") || ct.equals("image/webp"))) {
            throw new IllegalArgumentException("unsupported content-type");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("file too large");
        }
    }
}


