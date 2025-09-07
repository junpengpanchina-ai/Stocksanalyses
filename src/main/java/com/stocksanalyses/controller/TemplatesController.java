package com.stocksanalyses.controller;

import com.stocksanalyses.service.TemplateSignatureBaselineService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/templates")
public class TemplatesController {
    private final TemplateSignatureBaselineService baseline;

    public TemplatesController(TemplateSignatureBaselineService baseline) {
        this.baseline = baseline;
    }

    @GetMapping("/baseline")
    public Map<String, TemplateSignatureBaselineService.Stats> baseline() {
        return baseline.snapshot();
    }

    @PostMapping("/baseline/record")
    public void record(@RequestParam String style, @RequestParam String signature, @RequestParam boolean success){
        baseline.record(style, signature, success);
    }
}


