package com.quickform.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public class ReportRunRequest {
    @NotBlank
    private String sql;
    private Map<String, Object> params;

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
