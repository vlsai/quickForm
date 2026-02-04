package com.quickform.api.controller;

import com.quickform.api.dto.ApiResponse;
import com.quickform.api.dto.ReportRunRequest;
import com.quickform.api.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/report")
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/run")
    public ApiResponse<List<Map<String, Object>>> run(@Valid @RequestBody ReportRunRequest request) {
        return ApiResponse.ok(reportService.run(request));
    }
}
