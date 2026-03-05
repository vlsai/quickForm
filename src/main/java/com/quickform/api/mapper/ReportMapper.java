package com.quickform.api.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface ReportMapper {
    Map<String, Object> getReportById(@Param("reportId") Long reportId);

    Map<String, Object> getReportByPageAndName(@Param("pageCode") String pageCode, @Param("reportName") String reportName);

    Map<String, Object> getLatestReportByPage(@Param("pageCode") String pageCode);

    List<Map<String, Object>> run(@Param("sql") String sql, @Param("params") Map<String, Object> params);
}
