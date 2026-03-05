package com.quickform.api.service;

import com.quickform.api.dto.ReportRunRequest;
import com.quickform.api.exception.BadRequestException;
import com.quickform.api.exception.NotFoundException;
import com.quickform.api.mapper.ReportMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReportService {
    private static final Pattern NAMED_PARAM = Pattern.compile("(?<!:):([a-zA-Z_][a-zA-Z0-9_]*)");
    private static final Pattern PAGE_CODE_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private final ReportMapper reportMapper;

    public ReportService(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    public List<Map<String, Object>> run(ReportRunRequest request) {
        if (request == null || request.getPageCode() == null || request.getPageCode().isBlank()) {
            throw new BadRequestException("pageCode required");
        }
        if (!PAGE_CODE_PATTERN.matcher(request.getPageCode()).matches()) {
            throw new BadRequestException("invalid pageCode");
        }

        Map<String, Object> report = resolveReport(request);
        if (report == null) {
            throw new NotFoundException("report config not found");
        }
        if (report.get("page_code") == null || !request.getPageCode().equals(report.get("page_code").toString())) {
            throw new BadRequestException("report does not belong to pageCode");
        }
        Object sqlObj = report.get("sql_text");
        if (sqlObj == null || sqlObj.toString().isBlank()) {
            throw new BadRequestException("report sql is empty");
        }

        Map<String, Object> params = request.getParams() == null ? Collections.emptyMap() : request.getParams();
        String sql = convertNamedParams(sqlObj.toString());
        return reportMapper.run(sql, params);
    }

    private Map<String, Object> resolveReport(ReportRunRequest request) {
        if (request.getReportId() != null) {
            return reportMapper.getReportById(request.getReportId());
        }
        if (request.getReportName() != null && !request.getReportName().isBlank()) {
            return reportMapper.getReportByPageAndName(request.getPageCode(), request.getReportName());
        }
        return reportMapper.getLatestReportByPage(request.getPageCode());
    }

    private String convertNamedParams(String sql) {
        Matcher matcher = NAMED_PARAM.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            matcher.appendReplacement(sb, "#\\{params." + name + "\\}");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
