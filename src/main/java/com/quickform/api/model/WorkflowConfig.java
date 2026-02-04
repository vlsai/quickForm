package com.quickform.api.model;

import java.util.ArrayList;
import java.util.List;

public class WorkflowConfig {
    private List<WorkflowNode> nodes = new ArrayList<>();

    public List<WorkflowNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<WorkflowNode> nodes) {
        this.nodes = nodes;
    }
}
