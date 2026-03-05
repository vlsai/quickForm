package com.quickform.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public class ReportRunRequest {
    @NotBlank
    private String pageCode;
    private Map<String, Object> params;

    public String getPageCode() {
        return pageCode;
    }

    public void setPageCode(String pageCode) {
        this.pageCode = pageCode;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
