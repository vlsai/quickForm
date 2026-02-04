package com.quickform.api.controller;

import com.quickform.api.dto.ApiResponse;
import com.quickform.api.dto.WorkflowActionRequest;
import com.quickform.api.dto.WorkflowConfigGetRequest;
import com.quickform.api.dto.WorkflowConfigSaveRequest;
import com.quickform.api.dto.WorkflowTaskQueryRequest;
import com.quickform.api.service.WorkflowService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/workflow")
public class WorkflowController {
    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping("/{datasetCode}/{id}/submit")
    public ApiResponse<Integer> submit(
        @PathVariable String datasetCode,
        @PathVariable UUID id,
        @RequestBody(required = false) WorkflowActionRequest request
    ) {
        return ApiResponse.ok(workflowService.submit(datasetCode, id, request));
    }

    @PostMapping("/{datasetCode}/{id}/approve")
    public ApiResponse<Integer> approve(
        @PathVariable String datasetCode,
        @PathVariable UUID id,
        @RequestBody(required = false) WorkflowActionRequest request
    ) {
        return ApiResponse.ok(workflowService.approve(datasetCode, id, request));
    }

    @PostMapping("/{datasetCode}/{id}/reject")
    public ApiResponse<Integer> reject(
        @PathVariable String datasetCode,
        @PathVariable UUID id,
        @RequestBody(required = false) WorkflowActionRequest request
    ) {
        return ApiResponse.ok(workflowService.reject(datasetCode, id, request));
    }

    @PostMapping("/tasks")
    public ApiResponse<List<Map<String, Object>>> tasks(@RequestBody(required = false) WorkflowTaskQueryRequest request) {
        return ApiResponse.ok(workflowService.listTasks(request));
    }

    @PostMapping("/config/get")
    public ApiResponse<Map<String, Object>> getConfig(@RequestBody WorkflowConfigGetRequest request) {
        return ApiResponse.ok(workflowService.getConfig(request));
    }

    @PostMapping("/config/save")
    public ApiResponse<Long> saveConfig(@RequestBody WorkflowConfigSaveRequest request) {
        return ApiResponse.ok(workflowService.saveConfig(request));
    }
}
