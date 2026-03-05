package com.quickform.api.dto;

import java.util.UUID;

public class WorkflowRecordTimelineRequest {
    private String pageCode;
    private UUID recordId;

    public String getPageCode() {
        return pageCode;
    }

    public void setPageCode(String pageCode) {
        this.pageCode = pageCode;
    }

    public UUID getRecordId() {
        return recordId;
    }

    public void setRecordId(UUID recordId) {
        this.recordId = recordId;
    }
}
