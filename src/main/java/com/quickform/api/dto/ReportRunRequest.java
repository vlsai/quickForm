package com.quickform.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public class ReportRunRequest {
    @NotBlank
    private String pageCode;
    private Long reportId;
    private String reportName;
    private Map<String, Object> params;

    public String getPageCode() {
        return pageCode;
    }

    public void setPageCode(String pageCode) {
        this.pageCode = pageCode;
    }

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public String getReportName() {
        return reportName;
    }

    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
