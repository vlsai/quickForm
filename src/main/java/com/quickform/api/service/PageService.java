package com.quickform.api.service;

import com.quickform.api.dto.PageDeleteRequest;
import com.quickform.api.dto.PageGetRequest;
import com.quickform.api.dto.PageListRequest;
import com.quickform.api.dto.PageSaveRequest;
import com.quickform.api.exception.BadRequestException;
import com.quickform.api.exception.NotFoundException;
import com.quickform.api.mapper.PageMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PageService {
    private final PageMapper pageMapper;
    private final JsonHelper jsonHelper;

    public PageService(PageMapper pageMapper, JsonHelper jsonHelper) {
        this.pageMapper = pageMapper;
        this.jsonHelper = jsonHelper;
    }

    public List<Map<String, Object>> list(PageListRequest request) {
        String keyword = request == null ? null : request.getKeyword();
        List<Map<String, Object>> pages = pageMapper.listPages(keyword);
        return normalizePages(pages);
    }

    public Map<String, Object> get(PageGetRequest request) {
        if (request == null || request.getPageCode() == null || request.getPageCode().isBlank()) {
            throw new BadRequestException("page code required");
        }
        Map<String, Object> page = pageMapper.getPageByCode(request.getPageCode());
        if (page == null) {
            throw new NotFoundException("page not found");
        }
        return normalizePage(page);
    }

    public long save(PageSaveRequest request) {
        if (request == null || request.getPageCode() == null || request.getPageCode().isBlank()) {
            throw new BadRequestException("page code required");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("page name required");
        }
        String schemaJson = jsonHelper.toJson(request.getSchema());
        String optionsJson = jsonHelper.toJson(request.getOptions());
        Long id = pageMapper.findPageIdByCode(request.getPageCode());
        if (id == null) {
            return pageMapper.insertPage(request.getPageCode(), request.getName(), schemaJson, optionsJson);
        }
        pageMapper.updatePage(request.getPageCode(), request.getName(), schemaJson, optionsJson);
        return id;
    }

    public int delete(PageDeleteRequest request) {
        if (request == null || request.getPageCode() == null || request.getPageCode().isBlank()) {
            throw new BadRequestException("page code required");
        }
        return pageMapper.deletePage(request.getPageCode());
    }

    private List<Map<String, Object>> normalizePages(List<Map<String, Object>> pages) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> page : pages) {
            list.add(normalizePage(page));
        }
        return list;
    }

    private Map<String, Object> normalizePage(Map<String, Object> page) {
        if (page.containsKey("schema_json")) {
            page.put("schema", jsonHelper.toObject(page.get("schema_json"), Object.class));
            page.remove("schema_json");
        }
        if (page.containsKey("options")) {
            page.put("options", jsonHelper.toObject(page.get("options"), Object.class));
        }
        return page;
    }
}
