package com.quickform.api.service;

import com.quickform.api.dto.*;
import com.quickform.api.exception.BadRequestException;
import com.quickform.api.exception.NotFoundException;
import com.quickform.api.model.DatasetInfo;
import com.quickform.api.model.FieldInfo;
import com.quickform.api.mapper.DataMapper;
import com.quickform.api.mapper.MetaMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DataService {
    private final DataMapper dataMapper;
    private final MetaMapper metaMapper;
    private final JsonHelper jsonHelper;

    public DataService(DataMapper dataMapper, MetaMapper metaMapper, JsonHelper jsonHelper) {
        this.dataMapper = dataMapper;
        this.metaMapper = metaMapper;
        this.jsonHelper = jsonHelper;
    }

    public PageResult<Map<String, Object>> query(String datasetCode, DataQueryRequest request) {
        DatasetInfo dataset = getDataset(datasetCode);
        List<FieldInfo> fields = metaMapper.listFieldInfos(dataset.getId());
        Map<String, String> typeMap = new HashMap<>();
        for (FieldInfo field : fields) {
            typeMap.put(field.getCode(), field.getType());
        }

        QuerySql querySql = buildQuerySql(dataset.getId(), typeMap, request);
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

    public UUID create(String datasetCode, DataWriteRequest request) {
        DatasetInfo dataset = getDataset(datasetCode);
        if (request == null || request.getData() == null) {
            throw new BadRequestException("data required");
        }
        String dataJson = jsonHelper.toJson(request.getData());
        String status = request.getStatus() == null ? "draft" : request.getStatus();
        return dataMapper.createRecord(dataset.getId(), dataJson, status, request.getOperator());
    }

    public int update(String datasetCode, UUID id, DataWriteRequest request) {
        DatasetInfo dataset = getDataset(datasetCode);
        if (request == null || request.getData() == null) {
            throw new BadRequestException("data required");
        }
        String dataJson = jsonHelper.toJson(request.getData());
        return dataMapper.updateRecord(id, dataset.getId(), dataJson, request.getStatus(), request.getOperator());
    }

    public int delete(String datasetCode, UUID id) {
        DatasetInfo dataset = getDataset(datasetCode);
        return dataMapper.deleteRecord(id, dataset.getId());
    }

    private DatasetInfo getDataset(String datasetCode) {
        if (datasetCode == null || datasetCode.isBlank()) {
            throw new BadRequestException("dataset code required");
        }
        Map<String, Object> datasetRow = metaMapper.getDatasetByCode(datasetCode);
        DatasetInfo dataset = null;
        if (datasetRow != null) {
            long id = ((Number) datasetRow.get("id")).longValue();
            String primaryKey = datasetRow.get("primary_key") == null ? "id" : datasetRow.get("primary_key").toString();
            dataset = new DatasetInfo(id, datasetCode, primaryKey);
        }
        if (dataset == null) {
            throw new NotFoundException("dataset not found");
        }
        return dataset;
    }

    private QuerySql buildQuerySql(long datasetId, Map<String, String> typeMap, DataQueryRequest request) {
        ParamBuilder paramBuilder = new ParamBuilder();
        List<String> where = new ArrayList<>();

        where.add("dataset_id = " + paramBuilder.add(datasetId));

        if (request != null && request.getKeywords() != null && !request.getKeywords().isBlank()) {
            where.add("data::text ILIKE " + paramBuilder.add("%" + request.getKeywords().trim() + "%"));
        }

        if (request != null && request.getFilters() != null) {
            for (Filter filter : request.getFilters()) {
                String condition = buildCondition(filter, typeMap, paramBuilder);
                if (condition != null) {
                    where.add(condition);
                }
            }
        }

        if (request != null && request.getOrFilters() != null && !request.getOrFilters().isEmpty()) {
            List<String> orParts = new ArrayList<>();
            for (Filter filter : request.getOrFilters()) {
                String condition = buildCondition(filter, typeMap, paramBuilder);
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
                String expr = resolveExpr(sort.getField(), typeMap, "sort");
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

    private String fieldExpr(String field, String type, String mode) {
        String safeField = field.replace("'", "");
        String base = "data ->> '" + safeField + "'";
        if (type == null) {
            return base;
        }
        String t = type.toLowerCase();
        if (t.contains("number") || t.contains("int") || t.contains("decimal")) {
            return "NULLIF(" + base + ", '')::numeric";
        }
        if (t.contains("date") || t.contains("time")) {
            return "NULLIF(" + base + ", '')::timestamp";
        }
        return base;
    }

    private String buildCondition(Filter filter, Map<String, String> typeMap, ParamBuilder paramBuilder) {
        if (filter == null || filter.getField() == null || filter.getField().isBlank()) {
            return null;
        }
        String op = filter.getOp() == null ? "eq" : filter.getOp().toLowerCase();
        String field = filter.getField();

        if ("contains".equals(op)) {
            if (!typeMap.containsKey(field)) {
                return null;
            }
            String fieldParam = paramBuilder.add(field);
            String valueParam = paramBuilder.add(jsonHelper.toJson(filter.getValue()));
            return "data -> " + fieldParam + " @> " + valueParam + "::jsonb";
        }

        String expr = resolveExpr(field, typeMap, op);
        if (expr == null) {
            return null;
        }
        switch (op) {
            case "eq":
                return expr + " = " + paramBuilder.add(filter.getValue());
            case "ne":
                return expr + " <> " + paramBuilder.add(filter.getValue());
            case "like":
                return expr + " ILIKE " + paramBuilder.add("%" + String.valueOf(filter.getValue()) + "%");
            case "gt":
                return expr + " > " + paramBuilder.add(filter.getValue());
            case "gte":
                return expr + " >= " + paramBuilder.add(filter.getValue());
            case "lt":
                return expr + " < " + paramBuilder.add(filter.getValue());
            case "lte":
                return expr + " <= " + paramBuilder.add(filter.getValue());
            case "in":
                if (filter.getValue() instanceof Collection) {
                    Collection<?> values = (Collection<?>) filter.getValue();
                    if (!values.isEmpty()) {
                        List<String> placeholders = new ArrayList<>();
                        for (Object value : values) {
                            placeholders.add(paramBuilder.add(value));
                        }
                        return expr + " IN (" + String.join(",", placeholders) + ")";
                    }
                }
                return null;
            default:
                return expr + " = " + paramBuilder.add(filter.getValue());
        }
    }

    private String resolveExpr(String field, Map<String, String> typeMap, String mode) {
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
                if (!typeMap.containsKey(f)) {
                    return null;
                }
                return fieldExpr(f, typeMap.get(f), mode);
        }
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
