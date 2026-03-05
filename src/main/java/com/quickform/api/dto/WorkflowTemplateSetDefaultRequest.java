package com.quickform.api.dto;

public class WorkflowTemplateSetDefaultRequest {
    private String pageCode;
    private String templateCode;

    public String getPageCode() {
        return pageCode;
    }

    public void setPageCode(String pageCode) {
        this.pageCode = pageCode;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }
}
