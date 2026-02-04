package com.quickform.api.service;

import com.quickform.api.dto.*;
import com.quickform.api.exception.BadRequestException;
import com.quickform.api.exception.NotFoundException;
import com.quickform.api.mapper.DataMapper;
import com.quickform.api.mapper.PageMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class DataService {
    private final DataMapper dataMapper;
    private final PageMapper pageMapper;
    private final JsonHelper jsonHelper;
    private static final Pattern FIELD_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    public DataService(DataMapper dataMapper, PageMapper pageMapper, JsonHelper jsonHelper) {
        this.dataMapper = dataMapper;
        this.pageMapper = pageMapper;
        this.jsonHelper = jsonHelper;
    }

    public PageResult<Map<String, Object>> query(String pageCode, DataQueryRequest request) {
        ensurePageExists(pageCode);
        QuerySql querySql = buildQuerySql(pageCode, request);
        long total = dataMapper.count(querySql.countSql, querySql.params);
        List<Map<String, Object>> rows = dataMapper.query(querySql.pageSql, querySql.params);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("status", row.get("status"));
            item.put("createdAt", row.get("created_at"));
            item.put("updatedAt", row.get("updated_at"));
            item.put("createdBy", row.get("created_by"));
            item.put("updatedBy", row.get("updated_by"));
            item.put("data", jsonHelper.toMap(row.get("data")));
            items.add(item);
        }

        int page = querySql.page;
        int pageSize = querySql.pageSize;
        return new PageResult<>(items, total, page, pageSize);
    }

    public UUID create(String pageCode, DataWriteRequest request) {
        ensurePageExists(pageCode);
        if (request == null || request.getData() == null) {
            throw new BadRequestException("data required");
        }
        String dataJson = jsonHelper.toJson(request.getData());
        String status = request.getStatus() == null ? "draft" : request.getStatus();
        return dataMapper.createRecord(pageCode, dataJson, status, request.getOperator());
    }

    public int update(String pageCode, UUID id, DataWriteRequest request) {
        ensurePageExists(pageCode);
        if (request == null || request.getData() == null) {
            throw new BadRequestException("data required");
        }
        String dataJson = jsonHelper.toJson(request.getData());
        return dataMapper.updateRecord(id, pageCode, dataJson, request.getStatus(), request.getOperator());
    }

    public int delete(String pageCode, UUID id) {
        ensurePageExists(pageCode);
        return dataMapper.deleteRecord(id, pageCode);
    }

    private void ensurePageExists(String pageCode) {
        if (pageCode == null || pageCode.isBlank()) {
            throw new BadRequestException("page code required");
        }
        Map<String, Object> page = pageMapper.getPageByCode(pageCode);
        if (page == null) {
            throw new NotFoundException("page not found");
        }
    }

    private QuerySql buildQuerySql(String pageCode, DataQueryRequest request) {
        ParamBuilder paramBuilder = new ParamBuilder();
        List<String> where = new ArrayList<>();

        where.add("page_code = " + paramBuilder.add(pageCode));

        if (request != null && request.getKeywords() != null && !request.getKeywords().isBlank()) {
            where.add("data::text ILIKE " + paramBuilder.add("%" + request.getKeywords().trim() + "%"));
        }

        if (request != null && request.getFilters() != null) {
            for (Filter filter : request.getFilters()) {
                String condition = buildCondition(filter, paramBuilder);
                if (condition != null) {
                    where.add(condition);
                }
            }
        }

        if (request != null && request.getOrFilters() != null && !request.getOrFilters().isEmpty()) {
            List<String> orParts = new ArrayList<>();
            for (Filter filter : request.getOrFilters()) {
                String condition = buildCondition(filter, paramBuilder);
                if (condition != null) {
                    orParts.add(condition);
                }
            }
            if (!orParts.isEmpty()) {
                where.add("(" + String.join(" OR ", orParts) + ")");
            }
        }

        String whereSql = String.join(" AND ", where);
        String baseSql = "FROM data_record WHERE " + whereSql;

        StringBuilder orderSql = new StringBuilder();
        if (request != null && request.getSorts() != null && !request.getSorts().isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Sort sort : request.getSorts()) {
                if (sort.getField() == null || sort.getField().isBlank()) {
                    continue;
                }
                String order = sort.getOrder() == null ? "ASC" : sort.getOrder().toUpperCase();
                if (!order.equals("ASC") && !order.equals("DESC")) {
                    order = "ASC";
                }
                String expr = resolveExpr(sort.getField());
                if (expr == null) {
                    continue;
                }
                parts.add(expr + " " + order);
            }
            if (!parts.isEmpty()) {
                orderSql.append(" ORDER BY ").append(String.join(", ", parts));
            }
        } else {
            orderSql.append(" ORDER BY updated_at DESC");
        }

        int page = request != null && request.getPage() != null ? request.getPage() : 1;
        int pageSize = request != null && request.getPageSize() != null ? request.getPageSize() : 20;
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 20;
        int offset = (page - 1) * pageSize;

        String countSql = "SELECT COUNT(1) " + baseSql;
        String pageSql = "SELECT id, status, created_at, updated_at, created_by, updated_by, data " + baseSql + orderSql +
            " LIMIT " + paramBuilder.add(pageSize) + " OFFSET " + paramBuilder.add(offset);

        return new QuerySql(countSql, pageSql, paramBuilder.params, page, pageSize);
    }

    private String buildCondition(Filter filter, ParamBuilder paramBuilder) {
        if (filter == null || filter.getField() == null || filter.getField().isBlank()) {
            return null;
        }
        String op = filter.getOp() == null ? "eq" : filter.getOp().toLowerCase();
        String field = filter.getField();

        if ("contains".equals(op)) {
            if (!isSafeField(field)) {
                return null;
            }
            String fieldParam = paramBuilder.add(field);
            String valueParam = paramBuilder.add(jsonHelper.toJson(filter.getValue()));
            return "data -> " + fieldParam + " @> " + valueParam + "::jsonb";
        }

        String expr = resolveExpr(field);
        if (expr == null) {
            return null;
        }

        boolean numeric = filter.getValue() instanceof Number;
        boolean boolVal = filter.getValue() instanceof Boolean;
        String typedExpr = expr;
        if (numeric) {
            typedExpr = "NULLIF(" + expr + ", '')::numeric";
        } else if (boolVal) {
            typedExpr = "NULLIF(" + expr + ", '')::boolean";
        }

        switch (op) {
            case "eq":
                return typedExpr + " = " + paramBuilder.add(filter.getValue());
            case "ne":
                return typedExpr + " <> " + paramBuilder.add(filter.getValue());
            case "like":
                return expr + " ILIKE " + paramBuilder.add("%" + String.valueOf(filter.getValue()) + "%");
            case "gt":
                return typedExpr + " > " + paramBuilder.add(filter.getValue());
            case "gte":
                return typedExpr + " >= " + paramBuilder.add(filter.getValue());
            case "lt":
                return typedExpr + " < " + paramBuilder.add(filter.getValue());
            case "lte":
                return typedExpr + " <= " + paramBuilder.add(filter.getValue());
            case "in":
                if (filter.getValue() instanceof Collection) {
                    Collection<?> values = (Collection<?>) filter.getValue();
                    if (!values.isEmpty()) {
                        List<String> placeholders = new ArrayList<>();
                        for (Object value : values) {
                            placeholders.add(paramBuilder.add(value));
                        }
                        return typedExpr + " IN (" + String.join(",", placeholders) + ")";
                    }
                }
                return null;
            default:
                return typedExpr + " = " + paramBuilder.add(filter.getValue());
        }
    }

    private String resolveExpr(String field) {
        if (field == null) {
            return null;
        }
        String f = field.trim();
        if (f.isEmpty()) {
            return null;
        }
        switch (f) {
            case "status":
                return "status";
            case "createdAt":
            case "created_at":
                return "created_at";
            case "updatedAt":
            case "updated_at":
                return "updated_at";
            case "createdBy":
            case "created_by":
                return "created_by";
            case "updatedBy":
            case "updated_by":
                return "updated_by";
            default:
                if (!isSafeField(f)) {
                    return null;
                }
                return "data ->> '" + f + "'";
        }
    }

    private boolean isSafeField(String field) {
        return FIELD_PATTERN.matcher(field).matches();
    }

    private static class ParamBuilder {
        private final Map<String, Object> params = new LinkedHashMap<>();
        private int index = 0;

        private String add(Object value) {
            String key = "p" + index++;
            params.put(key, value);
            return "#{params." + key + "}";
        }
    }

    private static class QuerySql {
        private final String countSql;
        private final String pageSql;
        private final Map<String, Object> params;
        private final int page;
        private final int pageSize;

        private QuerySql(String countSql, String pageSql, Map<String, Object> params, int page, int pageSize) {
            this.countSql = countSql;
            this.pageSql = pageSql;
            this.params = params;
            this.page = page;
            this.pageSize = pageSize;
        }
    }
}
