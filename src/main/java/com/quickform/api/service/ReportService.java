package com.quickform.api.service;

import com.quickform.api.dto.ReportRunRequest;
import com.quickform.api.exception.BadRequestException;
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
    private final ReportMapper reportMapper;

    public ReportService(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    public List<Map<String, Object>> run(ReportRunRequest request) {
        if (request == null || request.getSql() == null || request.getSql().isBlank()) {
            throw new BadRequestException("sql required");
        }
        Map<String, Object> params = request.getParams() == null ? Collections.emptyMap() : request.getParams();
        String sql = convertNamedParams(request.getSql());
        return reportMapper.run(sql, params);
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
