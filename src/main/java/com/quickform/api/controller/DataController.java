package com.quickform.api.controller;

import com.quickform.api.dto.ApiResponse;
import com.quickform.api.dto.DataQueryRequest;
import com.quickform.api.dto.DataWriteRequest;
import com.quickform.api.dto.PageResult;
import com.quickform.api.service.DataService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/data")
public class DataController {
    private final DataService dataService;

    public DataController(DataService dataService) {
        this.dataService = dataService;
    }

    @PostMapping("/{pageCode}/query")
    public ApiResponse<PageResult<Map<String, Object>>> query(
        @PathVariable String pageCode,
        @RequestBody(required = false) DataQueryRequest request
    ) {
        return ApiResponse.ok(dataService.query(pageCode, request));
    }

    @PostMapping("/{pageCode}/create")
    public ApiResponse<UUID> create(
        @PathVariable String pageCode,
        @RequestBody DataWriteRequest request
    ) {
        return ApiResponse.ok(dataService.create(pageCode, request));
    }

    @PostMapping("/{pageCode}/{id}/update")
    public ApiResponse<Integer> update(
        @PathVariable String pageCode,
        @PathVariable UUID id,
        @RequestBody DataWriteRequest request
    ) {
        return ApiResponse.ok(dataService.update(pageCode, id, request));
    }

    @PostMapping("/{pageCode}/{id}/delete")
    public ApiResponse<Integer> delete(
        @PathVariable String pageCode,
        @PathVariable UUID id
    ) {
        return ApiResponse.ok(dataService.delete(pageCode, id));
    }
}
