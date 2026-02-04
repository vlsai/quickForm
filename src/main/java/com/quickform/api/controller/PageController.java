package com.quickform.api.controller;

import com.quickform.api.dto.*;
import com.quickform.api.service.PageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/page")
public class PageController {
    private final PageService pageService;

    public PageController(PageService pageService) {
        this.pageService = pageService;
    }

    @PostMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list(@RequestBody(required = false) PageListRequest request) {
        return ApiResponse.ok(pageService.list(request));
    }

    @PostMapping("/get")
    public ApiResponse<Map<String, Object>> get(@Valid @RequestBody PageGetRequest request) {
        return ApiResponse.ok(pageService.get(request));
    }

    @PostMapping("/save")
    public ApiResponse<Long> save(@Valid @RequestBody PageSaveRequest request) {
        return ApiResponse.ok(pageService.save(request));
    }

    @PostMapping("/delete")
    public ApiResponse<Integer> delete(@Valid @RequestBody PageDeleteRequest request) {
        return ApiResponse.ok(pageService.delete(request));
    }
}
