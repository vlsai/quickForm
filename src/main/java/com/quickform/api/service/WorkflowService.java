package com.quickform.api.service;

import com.quickform.api.dto.WorkflowActionRequest;
import com.quickform.api.dto.WorkflowConfigGetRequest;
import com.quickform.api.dto.WorkflowConfigSaveRequest;
import com.quickform.api.dto.WorkflowTaskQueryRequest;
import com.quickform.api.exception.BadRequestException;
import com.quickform.api.exception.NotFoundException;
import com.quickform.api.mapper.PageMapper;
import com.quickform.api.mapper.WorkflowMapper;
import com.quickform.api.model.WorkflowConfig;
import com.quickform.api.model.WorkflowNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkflowService {
    private final PageMapper pageMapper;
    private final WorkflowMapper workflowMapper;
    private final JsonHelper jsonHelper;

    public WorkflowService(PageMapper pageMapper,
                           WorkflowMapper workflowMapper,
                           JsonHelper jsonHelper) {
        this.pageMapper = pageMapper;
        this.workflowMapper = workflowMapper;
        this.jsonHelper = jsonHelper;
    }

    public Map<String, Object> getConfig(WorkflowConfigGetRequest request) {
        if (request == null || request.getPageCode() == null || request.getPageCode().isBlank()) {
            throw new BadRequestException("page code required");
        }
        ensurePageExists(request.getPageCode());
        Map<String, Object> workflow = workflowMapper.getWorkflow(request.getPageCode());
        if (workflow == null) {
            throw new NotFoundException("workflow config not found");
        }
        if (workflow.containsKey("config_json")) {
            workflow.put("config", jsonHelper.toMap(workflow.get("config_json")));
            workflow.remove("config_json");
        }
        return workflow;
    }

    public long saveConfig(WorkflowConfigSaveRequest request) {
        if (request == null || request.getPageCode() == null || request.getPageCode().isBlank()) {
            throw new BadRequestException("page code required");
        }
        if (request.getConfig() == null) {
            throw new BadRequestException("config required");
        }
        ensurePageExists(request.getPageCode());
        String configJson = jsonHelper.toJson(request.getConfig());
        WorkflowConfig config = jsonHelper.toObject(configJson, WorkflowConfig.class);
        validateConfig(config);
        Long id = workflowMapper.findWorkflowIdByPageCode(request.getPageCode());
        if (id == null) {
            return workflowMapper.insertWorkflow(request.getPageCode(), request.getName(), configJson);
        }
        workflowMapper.updateWorkflow(id, request.getName(), configJson);
        return id;
    }

    public int submit(String pageCode, UUID recordId, WorkflowActionRequest request) {
        ensurePageExists(pageCode);
        int updated = workflowMapper.updateRecordStatus(recordId, pageCode, "submitted",
            request == null ? null : request.getOperator());

        WorkflowConfig config = loadConfig(pageCode);
        if (config == null || config.getNodes().isEmpty()) {
            workflowMapper.insertTask(
                recordId,
                pageCode,
                request == null ? "submit" : defaultNode(request.getNodeCode(), "submit"),
                request == null ? null : request.getAssignee(),
                "pending",
                "pending",
                request == null ? null : request.getComment()
            );
            return updated;
        }

        WorkflowNode first = config.getNodes().get(0);
        createTasks(recordId, pageCode, first, request == null ? null : request.getAssignee());
        return updated;
    }

    public int approve(String pageCode, UUID recordId, WorkflowActionRequest request) {
        ensurePageExists(pageCode);
        String operator = request == null ? null : request.getOperator();

        WorkflowConfig config = loadConfig(pageCode);
        if (config == null || config.getNodes().isEmpty()) {
            int updated = workflowMapper.updateRecordStatus(recordId, pageCode, "approved", operator);
            workflowMapper.insertTask(
                recordId,
                pageCode,
                request == null ? "approve" : defaultNode(request.getNodeCode(), "approve"),
                request == null ? null : request.getAssignee(),
                "done",
                "approve",
                request == null ? null : request.getComment()
            );
            return updated;
        }

        WorkflowNode node = resolveNode(config, recordId, request);
        if (node == null) {
            throw new BadRequestException("no active node");
        }
        Long taskId = resolveTaskId(recordId, node, request);
        if (taskId == null) {
            throw new BadRequestException("no pending task");
        }
        workflowMapper.updateTask(taskId, "done", "approve", request == null ? null : request.getComment());

        String mode = nodeMode(node);
        boolean nodeCompleted;
        if ("any".equals(mode)) {
            workflowMapper.cancelPendingTasks(recordId, node.getCode());
            nodeCompleted = true;
        } else {
            nodeCompleted = workflowMapper.countPendingTasks(recordId, node.getCode()) == 0;
        }

        workflowMapper.touchRecord(recordId, pageCode, operator);

        if (nodeCompleted) {
            WorkflowNode next = nextNode(config, node.getCode());
            if (next != null) {
                createTasks(recordId, pageCode, next, null);
            } else {
                workflowMapper.updateRecordStatus(recordId, pageCode, "approved", operator);
            }
        }
        return 1;
    }

    public int reject(String pageCode, UUID recordId, WorkflowActionRequest request) {
        ensurePageExists(pageCode);
        String operator = request == null ? null : request.getOperator();

        WorkflowConfig config = loadConfig(pageCode);
        if (config == null || config.getNodes().isEmpty()) {
            int updated = workflowMapper.updateRecordStatus(recordId, pageCode, "rejected", operator);
            workflowMapper.insertTask(
                recordId,
                pageCode,
                request == null ? "reject" : defaultNode(request.getNodeCode(), "reject"),
                request == null ? null : request.getAssignee(),
                "done",
                "reject",
                request == null ? null : request.getComment()
            );
            return updated;
        }

        WorkflowNode node = resolveNode(config, recordId, request);
        if (node == null) {
            throw new BadRequestException("no active node");
        }
        Long taskId = resolveTaskId(recordId, node, request);
        if (taskId == null) {
            throw new BadRequestException("no pending task");
        }

        workflowMapper.updateTask(taskId, "done", "reject", request == null ? null : request.getComment());
        workflowMapper.cancelAllPendingTasks(recordId);

        return workflowMapper.updateRecordStatus(recordId, pageCode, "rejected", operator);
    }

    public List<Map<String, Object>> listTasks(WorkflowTaskQueryRequest request) {
        String assignee = request == null ? null : request.getAssignee();
        String pageCode = request == null ? null : request.getPageCode();
        return workflowMapper.listTasks(assignee, pageCode);
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

    private WorkflowConfig loadConfig(String pageCode) {
        Map<String, Object> workflow = workflowMapper.getWorkflow(pageCode);
        if (workflow == null || !workflow.containsKey("config_json")) {
            return null;
        }
        return jsonHelper.toObject(workflow.get("config_json"), WorkflowConfig.class);
    }

    private void validateConfig(WorkflowConfig config) {
        if (config == null || config.getNodes() == null || config.getNodes().isEmpty()) {
            throw new BadRequestException("workflow nodes required");
        }
        List<String> codes = new ArrayList<>();
        for (WorkflowNode node : config.getNodes()) {
            if (node.getCode() == null || node.getCode().isBlank()) {
                throw new BadRequestException("node code required");
            }
            if (codes.contains(node.getCode())) {
                throw new BadRequestException("duplicate node code: " + node.getCode());
            }
            codes.add(node.getCode());
        }
    }

    private WorkflowNode resolveNode(WorkflowConfig config, UUID recordId, WorkflowActionRequest request) {
        String nodeCode = request == null ? null : request.getNodeCode();
        String operator = request == null ? null : request.getOperator();

        if (nodeCode == null || nodeCode.isBlank()) {
            if (operator != null && !operator.isBlank()) {
                Long taskId = workflowMapper.findPendingTaskIdByRecordAndAssignee(recordId, operator);
                if (taskId != null) {
                    nodeCode = workflowMapper.getTaskNodeCode(taskId);
                }
            }
        }

        if (nodeCode == null || nodeCode.isBlank()) {
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
        if (nodeCode == null || nodeCode.isBlank()) {
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
        if (nodeCode == null) {
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
        String operator = request == null ? null : request.getOperator();
        String assignee = operator == null || operator.isBlank() ? (request == null ? null : request.getAssignee()) : operator;
        if (assignee != null && !assignee.isBlank()) {
            Long id = workflowMapper.findPendingTaskIdByAssignee(recordId, node.getCode(), assignee);
            if (id != null) {
                return id;
            }
        }
        List<Long> ids = workflowMapper.findPendingTaskIds(recordId, node.getCode());
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void createTasks(UUID recordId, String pageCode, WorkflowNode node, String fallbackAssignee) {
        List<String> assignees = node.getAssignees() == null ? new ArrayList<>() : node.getAssignees();
        if (assignees.isEmpty()) {
            if (fallbackAssignee != null && !fallbackAssignee.isBlank()) {
                workflowMapper.insertTask(recordId, pageCode, node.getCode(), fallbackAssignee, "pending", "pending", null);
            } else {
                workflowMapper.insertTask(recordId, pageCode, node.getCode(), null, "pending", "pending", null);
            }
            return;
        }
        for (String assignee : assignees) {
            workflowMapper.insertTask(recordId, pageCode, node.getCode(), assignee, "pending", "pending", null);
        }
    }

    private String nodeMode(WorkflowNode node) {
        if (node.getMode() == null || node.getMode().isBlank()) {
            return "all";
        }
        return node.getMode().toLowerCase();
    }

    private String defaultNode(String nodeCode, String fallback) {
        return nodeCode == null || nodeCode.isBlank() ? fallback : nodeCode;
    }
}
