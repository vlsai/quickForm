package com.quickform.api.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface ReportMapper {
    List<Map<String, Object>> run(@Param("sql") String sql, @Param("params") Map<String, Object> params);
}
