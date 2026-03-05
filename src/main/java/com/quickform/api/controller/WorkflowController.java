package com.quickform.api.controller;

import com.quickform.api.dto.*;
import com.quickform.api.service.WorkflowService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/workflow")
public class WorkflowController {
    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping("/template/list")
    public ApiResponse<PageResult<Map<String, Object>>> listTemplates(@RequestBody WorkflowTemplateListRequest request) {
        return ApiResponse.ok(workflowService.listTemplates(request));
    }

    @PostMapping("/template/get")
    public ApiResponse<Map<String, Object>> getTemplate(@RequestBody WorkflowTemplateGetRequest request) {
        return ApiResponse.ok(workflowService.getTemplate(request));
    }

    @PostMapping("/template/save")
    public ApiResponse<Long> saveTemplate(@RequestBody WorkflowTemplateSaveRequest request) {
        return ApiResponse.ok(workflowService.saveTemplate(request));
    }

    @PostMapping("/template/delete")
    public ApiResponse<Integer> deleteTemplate(@RequestBody WorkflowTemplateDeleteRequest request) {
        return ApiResponse.ok(workflowService.deleteTemplate(request));
    }

    @PostMapping("/template/set-default")
    public ApiResponse<Integer> setDefaultTemplate(@RequestBody WorkflowTemplateSetDefaultRequest request) {
        return ApiResponse.ok(workflowService.setDefaultTemplate(request));
    }

    @PostMapping("/{pageCode}/{id}/submit")
    public ApiResponse<Integer> submit(
        @PathVariable String pageCode,
        @PathVariable UUID id,
        @RequestBody(required = false) WorkflowActionRequest request
    ) {
        return ApiResponse.ok(workflowService.submit(pageCode, id, request));
    }

    @PostMapping("/{pageCode}/{id}/approve")
    public ApiResponse<Integer> approve(
        @PathVariable String pageCode,
        @PathVariable UUID id,
        @RequestBody WorkflowActionRequest request
    ) {
        return ApiResponse.ok(workflowService.approve(pageCode, id, request));
    }

    @PostMapping("/{pageCode}/{id}/reject")
    public ApiResponse<Integer> reject(
        @PathVariable String pageCode,
        @PathVariable UUID id,
        @RequestBody WorkflowActionRequest request
    ) {
        return ApiResponse.ok(workflowService.reject(pageCode, id, request));
    }

    @PostMapping("/todo/query")
    public ApiResponse<PageResult<Map<String, Object>>> todo(@RequestBody WorkflowTodoQueryRequest request) {
        return ApiResponse.ok(workflowService.queryTodo(request));
    }

    @PostMapping("/done/query")
    public ApiResponse<PageResult<Map<String, Object>>> done(@RequestBody WorkflowDoneQueryRequest request) {
        return ApiResponse.ok(workflowService.queryDone(request));
    }

    @PostMapping("/my-apply/query")
    public ApiResponse<PageResult<Map<String, Object>>> myApply(@RequestBody WorkflowMyApplyQueryRequest request) {
        return ApiResponse.ok(workflowService.queryMyApply(request));
    }

    @PostMapping("/record/timeline")
    public ApiResponse<Map<String, Object>> timeline(@RequestBody WorkflowRecordTimelineRequest request) {
        return ApiResponse.ok(workflowService.timeline(request));
    }
}
