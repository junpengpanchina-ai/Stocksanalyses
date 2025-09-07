package com.stocksanalyses.controller;

import com.stocksanalyses.model.UploadAnalyzeResult;
import com.stocksanalyses.service.UploadAnalyzeService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
public class UploadController {
    private final UploadAnalyzeService uploadAnalyzeService;

    public UploadController(UploadAnalyzeService uploadAnalyzeService) {
        this.uploadAnalyzeService = uploadAnalyzeService;
    }

    @PostMapping(path = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
                                       @RequestParam(value = "macdSignal", required = false) Integer macdSignal) {
        validateFile(file);
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


