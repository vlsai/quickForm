package com.quickform.api.dto;

import jakarta.validation.constraints.NotBlank;

public class PageSaveRequest {
    @NotBlank
    private String pageCode;
    @NotBlank
    private String name;
    private Object schema;
    private Object options;

    public String getPageCode() {
        return pageCode;
    }

    public void setPageCode(String pageCode) {
        this.pageCode = pageCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getSchema() {
        return schema;
    }

    public void setSchema(Object schema) {
        this.schema = schema;
    }

    public Object getOptions() {
        return options;
    }

    public void setOptions(Object options) {
        this.options = options;
    }
}
