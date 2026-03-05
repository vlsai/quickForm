package com.quickform.api.service;

import com.quickform.api.dto.*;
import com.quickform.api.exception.BadRequestException;
import com.quickform.api.exception.NotFoundException;
import com.quickform.api.mapper.WorkflowMapper;
import com.quickform.api.model.WorkflowConfig;
import com.quickform.api.model.WorkflowNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class WorkflowService {
    private static final Pattern PAGE_CODE_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern TEMPLATE_CODE_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final WorkflowMapper workflowMapper;
    private final JsonHelper jsonHelper;

    public WorkflowService(WorkflowMapper workflowMapper, JsonHelper jsonHelper) {
        this.workflowMapper = workflowMapper;
        this.jsonHelper = jsonHelper;
    }

    public PageResult<Map<String, Object>> listTemplates(WorkflowTemplateListRequest request) {
        if (request == null || isBlank(request.getPageCode())) {
            throw new BadRequestException("pageCode required");
        }
        String pageCode = request.getPageCode().trim();
        validatePageCode(pageCode);
        int page = normalizePage(request.getPage());
        int pageSize = normalizePageSize(request.getPageSize());
        int offset = (page - 1) * pageSize;
        String keyword = trimToNull(request.getKeyword());
        Long total = workflowMapper.countTemplates(pageCode, request.getEnabled(), keyword);
        List<Map<String, Object>> rows = workflowMapper.listTemplates(pageCode, request.getEnabled(), keyword, pageSize, offset);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", pick(row, "id"));
            item.put("pageCode", str(pick(row, "page_code", "pageCode")));
            item.put("templateCode", str(pick(row, "template_code", "templateCode")));
            item.put("name", str(pick(row, "name")));
            item.put("enabled", bool(pick(row, "enabled"), true));
            item.put("isDefault", bool(pick(row, "is_default", "isDefault"), false));
            item.put("updatedAt", pick(row, "updated_at", "updatedAt"));
            WorkflowConfig config = parseConfig(pick(row, "config_json", "configJson"));
            item.put("nodeCount", config.getNodes().size());
            items.add(item);
        }
        return new PageResult<>(items, total == null ? 0 : total, page, pageSize);
    }

    public Map<String, Object> getTemplate(WorkflowTemplateGetRequest request) {
        if (request == null || isBlank(request.getPageCode()) || isBlank(request.getTemplateCode())) {
            throw new BadRequestException("pageCode and templateCode required");
        }
        String pageCode = request.getPageCode().trim();
        String templateCode = request.getTemplateCode().trim();
        validatePageCode(pageCode);
        validateTemplateCode(templateCode);

        Map<String, Object> template = workflowMapper.getTemplate(pageCode, templateCode);
        if (template == null) {
            throw new NotFoundException("workflow template not found");
        }
        return toTemplateResponse(template);
    }

    @Transactional
    public long saveTemplate(WorkflowTemplateSaveRequest request) {
        if (request == null || isBlank(request.getPageCode()) || isBlank(request.getTemplateCode()) || isBlank(request.getName())) {
            throw new BadRequestException("pageCode, templateCode, name required");
        }
        if (request.getConfig() == null) {
            throw new BadRequestException("config required");
        }
        String pageCode = request.getPageCode().trim();
        String templateCode = request.getTemplateCode().trim();
        validatePageCode(pageCode);
        validateTemplateCode(templateCode);

        String configJson = jsonHelper.toJson(request.getConfig());
        WorkflowConfig config = jsonHelper.toObject(configJson, WorkflowConfig.class);
        validateConfig(config);
        configJson = jsonHelper.toJson(config);

        Map<String, Object> existed = workflowMapper.getTemplate(pageCode, templateCode);
        boolean enabled = request.getEnabled() != null ? request.getEnabled()
            : existed == null || bool(pick(existed, "enabled"), true);
        boolean isDefault = request.getIsDefault() != null ? request.getIsDefault()
            : existed != null && bool(pick(existed, "is_default", "isDefault"), false);

        if (isDefault) {
            workflowMapper.clearDefaultByPage(pageCode);
        }

        if (existed == null) {
            Long id = workflowMapper.insertTemplate(pageCode, templateCode, request.getName().trim(), configJson, enabled, isDefault);
            return id == null ? 0 : id;
        }

        long id = toLong(pick(existed, "id"), 0L);
        workflowMapper.updateTemplate(id, request.getName().trim(), configJson, enabled, isDefault);
        return id;
    }

    @Transactional
    public int deleteTemplate(WorkflowTemplateDeleteRequest request) {
        if (request == null || isBlank(request.getPageCode()) || isBlank(request.getTemplateCode())) {
            throw new BadRequestException("pageCode and templateCode required");
        }
        String pageCode = request.getPageCode().trim();
        String templateCode = request.getTemplateCode().trim();
        validatePageCode(pageCode);
        validateTemplateCode(templateCode);

        Map<String, Object> template = workflowMapper.getTemplate(pageCode, templateCode);
        if (template == null) {
            throw new NotFoundException("workflow template not found");
        }
        Long pending = workflowMapper.countPendingTasksByTemplate(pageCode, templateCode);
        if (pending != null && pending > 0) {
            throw new BadRequestException("template has pending tasks");
        }
        return workflowMapper.deleteTemplate(pageCode, templateCode);
    }

    @Transactional
    public int setDefaultTemplate(WorkflowTemplateSetDefaultRequest request) {
        if (request == null || isBlank(request.getPageCode()) || isBlank(request.getTemplateCode())) {
            throw new BadRequestException("pageCode and templateCode required");
        }
        String pageCode = request.getPageCode().trim();
        String templateCode = request.getTemplateCode().trim();
        validatePageCode(pageCode);
        validateTemplateCode(templateCode);

        Map<String, Object> template = workflowMapper.getTemplate(pageCode, templateCode);
        if (template == null) {
            throw new NotFoundException("workflow template not found");
        }
        if (!bool(pick(template, "enabled"), true)) {
            throw new BadRequestException("disabled template cannot be default");
        }
        workflowMapper.clearDefaultByPage(pageCode);
        return workflowMapper.updateTemplateDefault(toLong(pick(template, "id"), 0L), true);
    }

    @Transactional
    public int submit(String pageCode, UUID recordId, WorkflowActionRequest request) {
        validatePageCode(pageCode);
        Map<String, Object> record = requireRecord(recordId, pageCode);
        String status = str(pick(record, "status"));
        if (!"draft".equalsIgnoreCase(status) && !"rejected".equalsIgnoreCase(status)) {
            throw new BadRequestException("record status does not allow submit");
        }
        Long pendingCount = workflowMapper.countRecordPendingTasks(recordId);
        if (pendingCount != null && pendingCount > 0) {
            throw new BadRequestException("record already has pending tasks");
        }

        String operator = request == null ? null : trimToNull(request.getOperator());
        String requestedTemplateCode = request == null ? null : trimToNull(request.getTemplateCode());
        Map<String, Object> template = requestedTemplateCode == null
            ? workflowMapper.getDefaultTemplate(pageCode)
            : workflowMapper.getTemplate(pageCode, requestedTemplateCode);
        if (template == null) {
            throw new BadRequestException("workflow template not found");
        }
        if (!bool(pick(template, "enabled"), true)) {
            throw new BadRequestException("workflow template is disabled");
        }

        String templateCode = str(pick(template, "template_code", "templateCode"));
        Long templateId = toLongObj(pick(template, "id"));
        WorkflowConfig config = parseConfig(pick(template, "config_json", "configJson"));
        validateConfig(config);

        int updated = workflowMapper.updateRecordOnSubmit(recordId, pageCode, operator, templateCode);
        if (updated == 0) {
            throw new NotFoundException("record not found");
        }
        WorkflowNode first = config.getNodes().get(0);
        createTasks(recordId, pageCode, templateId, templateCode, first, request == null ? null : request.getAssignee());
        return updated;
    }

    @Transactional
    public int approve(String pageCode, UUID recordId, WorkflowActionRequest request) {
        validatePageCode(pageCode);
        String operator = requireOperator(request);
        Map<String, Object> record = requireRecord(recordId, pageCode);
        if (!"submitted".equalsIgnoreCase(str(pick(record, "status")))) {
            throw new BadRequestException("record is not in submitted status");
        }

        String templateCode = str(pick(record, "workflow_template_code", "workflowTemplateCode"));
        if (isBlank(templateCode)) {
            throw new BadRequestException("record workflow template not found");
        }
        Map<String, Object> template = workflowMapper.getTemplate(pageCode, templateCode);
        if (template == null) {
            throw new NotFoundException("workflow template not found");
        }
        WorkflowConfig config = parseConfig(pick(template, "config_json", "configJson"));
        validateConfig(config);

        WorkflowNode node = resolveNode(config, recordId, request);
        if (node == null) {
            throw new BadRequestException("no active node");
        }
        Long taskId = resolveTaskId(recordId, node, request);
        if (taskId == null) {
            throw new BadRequestException("no pending task");
        }
        int done = workflowMapper.completeTask(taskId, "approve", request == null ? null : request.getComment(), operator);
        if (done == 0) {
            throw new BadRequestException("task already processed");
        }

        boolean nodeCompleted;
        if ("any".equals(nodeMode(node))) {
            workflowMapper.cancelPendingTasks(recordId, node.getCode());
            nodeCompleted = true;
        } else {
            nodeCompleted = workflowMapper.countPendingTasks(recordId, node.getCode()) == 0;
        }

        workflowMapper.touchRecord(recordId, pageCode, operator);
        if (nodeCompleted) {
            WorkflowNode next = nextNode(config, node.getCode());
            if (next != null) {
                createTasks(recordId, pageCode, toLongObj(pick(template, "id")), templateCode, next, null);
            } else {
                workflowMapper.updateRecordStatus(recordId, pageCode, "approved", operator);
            }
        }
        return 1;
    }

    @Transactional
    public int reject(String pageCode, UUID recordId, WorkflowActionRequest request) {
        validatePageCode(pageCode);
        String operator = requireOperator(request);
        Map<String, Object> record = requireRecord(recordId, pageCode);
        if (!"submitted".equalsIgnoreCase(str(pick(record, "status")))) {
            throw new BadRequestException("record is not in submitted status");
        }

        String templateCode = str(pick(record, "workflow_template_code", "workflowTemplateCode"));
        if (isBlank(templateCode)) {
            throw new BadRequestException("record workflow template not found");
        }
        Map<String, Object> template = workflowMapper.getTemplate(pageCode, templateCode);
        if (template == null) {
            throw new NotFoundException("workflow template not found");
        }
        WorkflowConfig config = parseConfig(pick(template, "config_json", "configJson"));
        validateConfig(config);

        WorkflowNode node = resolveNode(config, recordId, request);
        if (node == null) {
            throw new BadRequestException("no active node");
        }
        Long taskId = resolveTaskId(recordId, node, request);
        if (taskId == null) {
            throw new BadRequestException("no pending task");
        }

        int done = workflowMapper.completeTask(taskId, "reject", request == null ? null : request.getComment(), operator);
        if (done == 0) {
            throw new BadRequestException("task already processed");
        }
        workflowMapper.cancelAllPendingTasks(recordId);
        return workflowMapper.updateRecordStatus(recordId, pageCode, "rejected", operator);
    }

    public PageResult<Map<String, Object>> queryTodo(WorkflowTodoQueryRequest request) {
        if (request == null || isBlank(request.getAssignee())) {
            throw new BadRequestException("assignee required");
        }
        String assignee = request.getAssignee().trim();
        String pageCode = trimToNull(request.getPageCode());
        if (pageCode != null) {
            validatePageCode(pageCode);
        }
        String keywords = trimToNull(request.getKeywords());
        int page = normalizePage(request.getPage());
        int pageSize = normalizePageSize(request.getPageSize());
        int offset = (page - 1) * pageSize;

        Long total = workflowMapper.countTodo(assignee, pageCode, keywords);
        List<Map<String, Object>> rows = workflowMapper.queryTodo(assignee, pageCode, keywords, pageSize, offset);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskId", pick(row, "task_id", "taskId"));
            item.put("recordId", pick(row, "record_id", "recordId"));
            item.put("pageCode", str(pick(row, "page_code", "pageCode")));
            item.put("templateCode", str(pick(row, "template_code", "templateCode")));
            item.put("templateName", str(pick(row, "template_name", "templateName")));
            item.put("nodeCode", str(pick(row, "node_code", "nodeCode")));
            item.put("assignee", str(pick(row, "assignee")));
            item.put("taskStatus", str(pick(row, "task_status", "taskStatus")));
            item.put("action", str(pick(row, "action")));
            item.put("comment", str(pick(row, "comment")));
            item.put("recordStatus", str(pick(row, "record_status", "recordStatus")));
            item.put("createdAt", pick(row, "task_created_at", "taskCreatedAt"));
            item.put("updatedAt", pick(row, "task_updated_at", "taskUpdatedAt"));
            item.put("createdBy", str(pick(row, "created_by", "createdBy")));
            item.put("data", jsonHelper.toMap(pick(row, "data")));
            items.add(item);
        }
        return new PageResult<>(items, total == null ? 0 : total, page, pageSize);
    }

    public PageResult<Map<String, Object>> queryDone(WorkflowDoneQueryRequest request) {
        if (request == null || isBlank(request.getOperator())) {
            throw new BadRequestException("operator required");
        }
        String operator = request.getOperator().trim();
        String pageCode = trimToNull(request.getPageCode());
        if (pageCode != null) {
            validatePageCode(pageCode);
        }
        String keywords = trimToNull(request.getKeywords());
        int page = normalizePage(request.getPage());
        int pageSize = normalizePageSize(request.getPageSize());
        int offset = (page - 1) * pageSize;

        Long total = workflowMapper.countDone(operator, pageCode, keywords);
        List<Map<String, Object>> rows = workflowMapper.queryDone(operator, pageCode, keywords, pageSize, offset);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskId", pick(row, "task_id", "taskId"));
            item.put("recordId", pick(row, "record_id", "recordId"));
            item.put("pageCode", str(pick(row, "page_code", "pageCode")));
            item.put("templateCode", str(pick(row, "template_code", "templateCode")));
            item.put("templateName", str(pick(row, "template_name", "templateName")));
            item.put("nodeCode", str(pick(row, "node_code", "nodeCode")));
            item.put("assignee", str(pick(row, "assignee")));
            item.put("taskStatus", str(pick(row, "task_status", "taskStatus")));
            item.put("action", str(pick(row, "action")));
            item.put("comment", str(pick(row, "comment")));
            item.put("operator", str(pick(row, "operated_by", "operatedBy")));
            item.put("recordStatus", str(pick(row, "record_status", "recordStatus")));
            item.put("createdAt", pick(row, "task_created_at", "taskCreatedAt"));
            item.put("updatedAt", pick(row, "task_updated_at", "taskUpdatedAt"));
            item.put("createdBy", str(pick(row, "created_by", "createdBy")));
            item.put("data", jsonHelper.toMap(pick(row, "data")));
            items.add(item);
        }
        return new PageResult<>(items, total == null ? 0 : total, page, pageSize);
    }

    public PageResult<Map<String, Object>> queryMyApply(WorkflowMyApplyQueryRequest request) {
        if (request == null || isBlank(request.getOperator())) {
            throw new BadRequestException("operator required");
        }
        String operator = request.getOperator().trim();
        String pageCode = trimToNull(request.getPageCode());
        if (pageCode != null) {
            validatePageCode(pageCode);
        }
        String status = trimToNull(request.getStatus());
        if (status != null && !isValidRecordStatus(status)) {
            throw new BadRequestException("invalid status");
        }
        String keywords = trimToNull(request.getKeywords());
        int page = normalizePage(request.getPage());
        int pageSize = normalizePageSize(request.getPageSize());
        int offset = (page - 1) * pageSize;

        Long total = workflowMapper.countMyApply(operator, pageCode, status, keywords);
        List<Map<String, Object>> rows = workflowMapper.queryMyApply(operator, pageCode, status, keywords, pageSize, offset);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("recordId", pick(row, "record_id", "recordId"));
            item.put("pageCode", str(pick(row, "page_code", "pageCode")));
            item.put("recordStatus", str(pick(row, "record_status", "recordStatus")));
            item.put("templateCode", str(pick(row, "template_code", "templateCode")));
            item.put("templateName", str(pick(row, "template_name", "templateName")));
            item.put("currentNode", str(pick(row, "pending_node_code", "pendingNodeCode")));
            item.put("pendingAssignees", splitAssignees(str(pick(row, "pending_assignees", "pendingAssignees"))));
            item.put("createdAt", pick(row, "record_created_at", "recordCreatedAt"));
            item.put("updatedAt", pick(row, "record_updated_at", "recordUpdatedAt"));
            item.put("data", jsonHelper.toMap(pick(row, "data")));
            items.add(item);
        }
        return new PageResult<>(items, total == null ? 0 : total, page, pageSize);
    }

    public Map<String, Object> timeline(WorkflowRecordTimelineRequest request) {
        if (request == null || isBlank(request.getPageCode()) || request.getRecordId() == null) {
            throw new BadRequestException("pageCode and recordId required");
        }
        String pageCode = request.getPageCode().trim();
        validatePageCode(pageCode);
        Map<String, Object> record = requireRecord(request.getRecordId(), pageCode);

        Map<String, Object> recordView = new LinkedHashMap<>();
        recordView.put("id", pick(record, "id"));
        recordView.put("pageCode", str(pick(record, "page_code", "pageCode")));
        recordView.put("status", str(pick(record, "status")));
        recordView.put("templateCode", str(pick(record, "workflow_template_code", "workflowTemplateCode")));
        recordView.put("createdAt", pick(record, "created_at", "createdAt"));
        recordView.put("updatedAt", pick(record, "updated_at", "updatedAt"));
        recordView.put("createdBy", str(pick(record, "created_by", "createdBy")));
        recordView.put("updatedBy", str(pick(record, "updated_by", "updatedBy")));
        recordView.put("data", jsonHelper.toMap(pick(record, "data")));

        List<Map<String, Object>> rows = workflowMapper.listTimeline(request.getRecordId(), pageCode);
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskId", pick(row, "task_id", "taskId"));
            item.put("recordId", pick(row, "record_id", "recordId"));
            item.put("pageCode", str(pick(row, "page_code", "pageCode")));
            item.put("templateCode", str(pick(row, "template_code", "templateCode")));
            item.put("templateName", str(pick(row, "template_name", "templateName")));
            item.put("nodeCode", str(pick(row, "node_code", "nodeCode")));
            item.put("assignee", str(pick(row, "assignee")));
            item.put("taskStatus", str(pick(row, "task_status", "taskStatus")));
            item.put("action", str(pick(row, "action")));
            item.put("comment", str(pick(row, "comment")));
            item.put("operator", str(pick(row, "operated_by", "operatedBy")));
            item.put("createdAt", pick(row, "created_at", "createdAt"));
            item.put("updatedAt", pick(row, "updated_at", "updatedAt"));
            timeline.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("record", recordView);
        data.put("timeline", timeline);
        return data;
    }

    private Map<String, Object> toTemplateResponse(Map<String, Object> template) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", pick(template, "id"));
        result.put("pageCode", str(pick(template, "page_code", "pageCode")));
        result.put("templateCode", str(pick(template, "template_code", "templateCode")));
        result.put("name", str(pick(template, "name")));
        result.put("enabled", bool(pick(template, "enabled"), true));
        result.put("isDefault", bool(pick(template, "is_default", "isDefault"), false));
        result.put("updatedAt", pick(template, "updated_at", "updatedAt"));
        WorkflowConfig config = parseConfig(pick(template, "config_json", "configJson"));
        result.put("config", jsonHelper.toMap(jsonHelper.toJson(config)));
        result.put("nodeCount", config.getNodes().size());
        return result;
    }

    private Map<String, Object> requireRecord(UUID recordId, String pageCode) {
        Map<String, Object> record = workflowMapper.getRecord(recordId, pageCode);
        if (record == null) {
            throw new NotFoundException("record not found");
        }
        return record;
    }

    private void validatePageCode(String pageCode) {
        if (isBlank(pageCode)) {
            throw new BadRequestException("page code required");
        }
        if (!PAGE_CODE_PATTERN.matcher(pageCode).matches()) {
            throw new BadRequestException("invalid page code");
        }
    }

    private void validateTemplateCode(String templateCode) {
        if (isBlank(templateCode)) {
            throw new BadRequestException("template code required");
        }
        if (!TEMPLATE_CODE_PATTERN.matcher(templateCode).matches()) {
            throw new BadRequestException("invalid template code");
        }
    }

    private WorkflowConfig parseConfig(Object value) {
        WorkflowConfig config = jsonHelper.toObject(value, WorkflowConfig.class);
        return config == null ? new WorkflowConfig() : config;
    }

    private void validateConfig(WorkflowConfig config) {
        if (config == null || config.getNodes() == null || config.getNodes().isEmpty()) {
            throw new BadRequestException("workflow nodes required");
        }
        Set<String> codes = new HashSet<>();
        for (WorkflowNode node : config.getNodes()) {
            if (node == null || isBlank(node.getCode())) {
                throw new BadRequestException("node code required");
            }
            if (!codes.add(node.getCode())) {
                throw new BadRequestException("duplicate node code: " + node.getCode());
            }
            if (isBlank(node.getMode())) {
                node.setMode("all");
            } else {
                String mode = node.getMode().toLowerCase(Locale.ROOT);
                if (!"all".equals(mode) && !"any".equals(mode)) {
                    throw new BadRequestException("invalid node mode: " + node.getMode());
                }
                node.setMode(mode);
            }
        }
    }

    private WorkflowNode resolveNode(WorkflowConfig config, UUID recordId, WorkflowActionRequest request) {
        String nodeCode = request == null ? null : trimToNull(request.getNodeCode());
        String operator = request == null ? null : trimToNull(request.getOperator());
        if (nodeCode == null && operator != null) {
            Long taskId = workflowMapper.findPendingTaskIdByRecordAndAssignee(recordId, operator);
            if (taskId != null) {
                nodeCode = workflowMapper.getTaskNodeCode(taskId);
            }
        }
        if (nodeCode == null) {
            for (WorkflowNode node : config.getNodes()) {
                if (workflowMapper.countPendingTasks(recordId, node.getCode()) > 0) {
                    nodeCode = node.getCode();
                    break;
                }
            }
        }
        return findNode(config, nodeCode);
    }

    private WorkflowNode findNode(WorkflowConfig config, String nodeCode) {
        if (isBlank(nodeCode)) {
            return null;
        }
        for (WorkflowNode node : config.getNodes()) {
            if (nodeCode.equals(node.getCode())) {
                return node;
            }
        }
        return null;
    }

    private WorkflowNode nextNode(WorkflowConfig config, String nodeCode) {
        if (isBlank(nodeCode)) {
            return null;
        }
        List<WorkflowNode> nodes = config.getNodes();
        for (int i = 0; i < nodes.size(); i++) {
            if (nodeCode.equals(nodes.get(i).getCode())) {
                return i + 1 < nodes.size() ? nodes.get(i + 1) : null;
            }
        }
        return null;
    }

    private Long resolveTaskId(UUID recordId, WorkflowNode node, WorkflowActionRequest request) {
        String operator = request == null ? null : trimToNull(request.getOperator());
        String assignee = operator != null ? operator : (request == null ? null : trimToNull(request.getAssignee()));
        if (assignee != null) {
            Long id = workflowMapper.findPendingTaskIdByAssignee(recordId, node.getCode(), assignee);
            if (id != null) {
                return id;
            }
        }
        List<Long> ids = workflowMapper.findPendingTaskIds(recordId, node.getCode());
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void createTasks(UUID recordId, String pageCode, Long templateId, String templateCode, WorkflowNode node, String fallbackAssignee) {
        List<String> assignees = node.getAssignees() == null ? new ArrayList<>() : node.getAssignees();
        if (assignees.isEmpty()) {
            String assignee = trimToNull(fallbackAssignee);
            workflowMapper.insertTask(recordId, pageCode, templateId, templateCode, node.getCode(), assignee, "pending", "pending", null, null);
            return;
        }
        for (String assignee : assignees) {
            String safeAssignee = trimToNull(assignee);
            workflowMapper.insertTask(recordId, pageCode, templateId, templateCode, node.getCode(), safeAssignee, "pending", "pending", null, null);
        }
    }

    private String nodeMode(WorkflowNode node) {
        return node == null || isBlank(node.getMode()) ? "all" : node.getMode().toLowerCase(Locale.ROOT);
    }

    private String requireOperator(WorkflowActionRequest request) {
        String operator = request == null ? null : trimToNull(request.getOperator());
        if (operator == null) {
            throw new BadRequestException("operator required");
        }
        return operator;
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        if (pageSize > 200) {
            return 200;
        }
        return pageSize;
    }

    private boolean isValidRecordStatus(String status) {
        return "draft".equals(status) || "submitted".equals(status) || "approved".equals(status) || "rejected".equals(status);
    }

    private List<String> splitAssignees(String value) {
        if (isBlank(value)) {
            return new ArrayList<>();
        }
        String[] values = value.split(",");
        List<String> result = new ArrayList<>();
        for (String v : values) {
            String item = trimToNull(v);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    private Object pick(Map<String, Object> row, String... keys) {
        if (row == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private String str(Object value) {
        return value == null ? null : value.toString();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean bool(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private long toLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private Long toLongObj(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
