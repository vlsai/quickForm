package com.quickform.api.model;

public class FieldInfo {
    private String code;
    private String type;

    public FieldInfo() {}

    public FieldInfo(String code, String type) {
        this.code = code;
        this.type = type;
    }

    public String getCode() {
        return code;
    }

    public String getType() {
        return type;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setType(String type) {
        this.type = type;
    }
}
