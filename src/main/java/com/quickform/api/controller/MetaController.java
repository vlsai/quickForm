package com.quickform.api.controller;

import com.quickform.api.dto.*;
import com.quickform.api.service.MetaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/meta")
public class MetaController {
    private final MetaService metaService;

    public MetaController(MetaService metaService) {
        this.metaService = metaService;
    }

    @PostMapping("/datasets/list")
    public ApiResponse<List<Map<String, Object>>> listDatasets(@RequestBody(required = false) DatasetListRequest request) {
        return ApiResponse.ok(metaService.listDatasets(request));
    }

    @PostMapping("/datasets/get")
    public ApiResponse<Map<String, Object>> getDataset(@RequestBody DatasetGetRequest request) {
        return ApiResponse.ok(metaService.getDataset(request));
    }

    @PostMapping("/datasets/create")
    public ApiResponse<Long> createDataset(@Valid @RequestBody DatasetCreateRequest request) {
        return ApiResponse.ok(metaService.createDataset(request));
    }

    @PostMapping("/fields/list")
    public ApiResponse<List<Map<String, Object>>> listFields(@RequestBody FieldListRequest request) {
        return ApiResponse.ok(metaService.listFields(request.getDatasetId(), request.getDatasetCode()));
    }

    @PostMapping("/fields/create")
    public ApiResponse<Long> createField(@Valid @RequestBody FieldCreateRequest request) {
        return ApiResponse.ok(metaService.createField(request));
    }

    @PostMapping("/fields/update")
    public ApiResponse<Integer> updateField(@RequestBody FieldUpdateRequest request) {
        return ApiResponse.ok(metaService.updateField(request));
    }

    @PostMapping("/fields/delete")
    public ApiResponse<Integer> deleteField(@RequestBody FieldDeleteRequest request) {
        return ApiResponse.ok(metaService.deleteField(request));
    }
}
