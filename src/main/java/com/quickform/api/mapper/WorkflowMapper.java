package com.quickform.api.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface WorkflowMapper {

    int insertTask(@Param("recordId") UUID recordId,
                   @Param("pageCode") String pageCode,
                   @Param("nodeCode") String nodeCode,
                   @Param("assignee") String assignee,
                   @Param("status") String status,
                   @Param("action") String action,
                   @Param("comment") String comment);

    List<Map<String, Object>> listTasks(@Param("assignee") String assignee, @Param("pageCode") String pageCode);

    Map<String, Object> getWorkflow(@Param("pageCode") String pageCode);

    Long findWorkflowIdByPageCode(@Param("pageCode") String pageCode);

    Long insertWorkflow(@Param("pageCode") String pageCode,
                        @Param("name") String name,
                        @Param("configJson") String configJson);

    int updateWorkflow(@Param("id") long id,
                       @Param("name") String name,
                       @Param("configJson") String configJson);

    List<Long> findPendingTaskIds(@Param("recordId") UUID recordId, @Param("nodeCode") String nodeCode);

    Long findPendingTaskIdByAssignee(@Param("recordId") UUID recordId,
                                     @Param("nodeCode") String nodeCode,
                                     @Param("assignee") String assignee);

    Long findPendingTaskIdByRecordAndAssignee(@Param("recordId") UUID recordId,
                                              @Param("assignee") String assignee);

    String getTaskNodeCode(@Param("id") long id);

    int updateTask(@Param("id") long id,
                   @Param("status") String status,
                   @Param("action") String action,
                   @Param("comment") String comment);

    int cancelPendingTasks(@Param("recordId") UUID recordId, @Param("nodeCode") String nodeCode);

    int cancelAllPendingTasks(@Param("recordId") UUID recordId);

    Long countPendingTasks(@Param("recordId") UUID recordId, @Param("nodeCode") String nodeCode);

    int updateRecordStatus(@Param("recordId") UUID recordId,
                           @Param("pageCode") String pageCode,
                           @Param("status") String status,
                           @Param("operator") String operator);

    int touchRecord(@Param("recordId") UUID recordId,
                    @Param("pageCode") String pageCode,
                    @Param("operator") String operator);
}
