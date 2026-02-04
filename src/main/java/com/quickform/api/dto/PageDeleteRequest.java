package com.quickform.api.dto;

import jakarta.validation.constraints.NotBlank;

public class PageDeleteRequest {
    @NotBlank
    private String pageCode;

    public String getPageCode() {
        return pageCode;
    }

    public void setPageCode(String pageCode) {
        this.pageCode = pageCode;
    }
}
