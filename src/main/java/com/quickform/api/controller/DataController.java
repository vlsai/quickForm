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

    @PostMapping("/{datasetCode}/query")
    public ApiResponse<PageResult<Map<String, Object>>> query(
        @PathVariable String datasetCode,
        @RequestBody(required = false) DataQueryRequest request
    ) {
        return ApiResponse.ok(dataService.query(datasetCode, request));
    }

    @PostMapping("/{datasetCode}/create")
    public ApiResponse<UUID> create(
        @PathVariable String datasetCode,
        @RequestBody DataWriteRequest request
    ) {
        return ApiResponse.ok(dataService.create(datasetCode, request));
    }

    @PostMapping("/{datasetCode}/{id}/update")
    public ApiResponse<Integer> update(
        @PathVariable String datasetCode,
        @PathVariable UUID id,
        @RequestBody DataWriteRequest request
    ) {
        return ApiResponse.ok(dataService.update(datasetCode, id, request));
    }

    @PostMapping("/{datasetCode}/{id}/delete")
    public ApiResponse<Integer> delete(
        @PathVariable String datasetCode,
        @PathVariable UUID id
    ) {
        return ApiResponse.ok(dataService.delete(datasetCode, id));
    }
}
