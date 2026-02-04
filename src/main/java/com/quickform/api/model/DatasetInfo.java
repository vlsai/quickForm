package com.quickform.api.model;

public class DatasetInfo {
    private long id;
    private String code;
    private String primaryKey;

    public DatasetInfo(long id, String code, String primaryKey) {
        this.id = id;
        this.code = code;
        this.primaryKey = primaryKey;
    }

    public long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getPrimaryKey() {
        return primaryKey;
    }
}
