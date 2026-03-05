package com.quickform.api.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface WorkflowMapper {

    Long countTemplates(@Param("pageCode") String pageCode,
                        @Param("enabled") Boolean enabled,
                        @Param("keyword") String keyword);

    List<Map<String, Object>> listTemplates(@Param("pageCode") String pageCode,
                                            @Param("enabled") Boolean enabled,
                                            @Param("keyword") String keyword,
                                            @Param("limit") int limit,
                                            @Param("offset") int offset);

    Map<String, Object> getTemplate(@Param("pageCode") String pageCode,
                                    @Param("templateCode") String templateCode);

    Map<String, Object> getDefaultTemplate(@Param("pageCode") String pageCode);

    Long insertTemplate(@Param("pageCode") String pageCode,
                        @Param("templateCode") String templateCode,
                        @Param("name") String name,
                        @Param("configJson") String configJson,
                        @Param("enabled") boolean enabled,
                        @Param("isDefault") boolean isDefault);

    int updateTemplate(@Param("id") long id,
                       @Param("name") String name,
                       @Param("configJson") String configJson,
                       @Param("enabled") boolean enabled,
                       @Param("isDefault") boolean isDefault);

    int clearDefaultByPage(@Param("pageCode") String pageCode);

    int updateTemplateDefault(@Param("id") long id,
                              @Param("isDefault") boolean isDefault);

    int deleteTemplate(@Param("pageCode") String pageCode,
                       @Param("templateCode") String templateCode);

    Long countPendingTasksByTemplate(@Param("pageCode") String pageCode,
                                     @Param("templateCode") String templateCode);

    Map<String, Object> getRecord(@Param("recordId") UUID recordId,
                                  @Param("pageCode") String pageCode);

    Long countRecordPendingTasks(@Param("recordId") UUID recordId,
                                 @Param("pageCode") String pageCode);

    Long insertInstance(@Param("pageCode") String pageCode,
                        @Param("recordId") UUID recordId,
                        @Param("templateId") Long templateId,
                        @Param("templateCode") String templateCode,
                        @Param("starter") String starter,
                        @Param("currentNodeCode") String currentNodeCode);

    Map<String, Object> getActiveInstance(@Param("recordId") UUID recordId,
                                          @Param("pageCode") String pageCode);

    Map<String, Object> getLatestInstance(@Param("recordId") UUID recordId,
                                          @Param("pageCode") String pageCode);

    int updateInstanceCurrentNode(@Param("instanceId") long instanceId,
                                  @Param("currentNodeCode") String currentNodeCode);

    int completeInstance(@Param("instanceId") long instanceId,
                         @Param("status") String status);

    int insertTask(@Param("instanceId") long instanceId,
                   @Param("recordId") UUID recordId,
                   @Param("pageCode") String pageCode,
                   @Param("templateId") Long templateId,
                   @Param("templateCode") String templateCode,
                   @Param("nodeCode") String nodeCode,
                   @Param("assignee") String assignee,
                   @Param("status") String status,
                   @Param("action") String action,
                   @Param("comment") String comment,
                   @Param("operatedBy") String operatedBy);

    List<Long> findPendingTaskIds(@Param("instanceId") long instanceId,
                                  @Param("nodeCode") String nodeCode);

    Long findPendingTaskIdByAssignee(@Param("instanceId") long instanceId,
                                     @Param("nodeCode") String nodeCode,
                                     @Param("assignee") String assignee);

    Long findPendingUnassignedTaskId(@Param("instanceId") long instanceId,
                                     @Param("nodeCode") String nodeCode);

    Long findPendingTaskIdByInstanceAndAssignee(@Param("instanceId") long instanceId,
                                                @Param("assignee") String assignee);

    String getTaskNodeCode(@Param("id") long id);

    int completeTask(@Param("id") long id,
                     @Param("action") String action,
                     @Param("comment") String comment,
                     @Param("operator") String operator);

    int cancelPendingTasks(@Param("instanceId") long instanceId,
                           @Param("nodeCode") String nodeCode);

    int cancelAllPendingTasks(@Param("instanceId") long instanceId);

    Long countPendingTasks(@Param("instanceId") long instanceId,
                           @Param("nodeCode") String nodeCode);

    Long countTodo(@Param("assignee") String assignee,
                   @Param("pageCode") String pageCode,
                   @Param("keywords") String keywords);

    List<Map<String, Object>> queryTodo(@Param("assignee") String assignee,
                                        @Param("pageCode") String pageCode,
                                        @Param("keywords") String keywords,
                                        @Param("limit") int limit,
                                        @Param("offset") int offset);

    Long countDone(@Param("operator") String operator,
                   @Param("pageCode") String pageCode,
                   @Param("keywords") String keywords);

    List<Map<String, Object>> queryDone(@Param("operator") String operator,
                                        @Param("pageCode") String pageCode,
                                        @Param("keywords") String keywords,
                                        @Param("limit") int limit,
                                        @Param("offset") int offset);

    Long countMyApply(@Param("operator") String operator,
                      @Param("pageCode") String pageCode,
                      @Param("status") String status,
                      @Param("keywords") String keywords);

    List<Map<String, Object>> queryMyApply(@Param("operator") String operator,
                                           @Param("pageCode") String pageCode,
                                           @Param("status") String status,
                                           @Param("keywords") String keywords,
                                           @Param("limit") int limit,
                                           @Param("offset") int offset);

    List<Map<String, Object>> listTimelineByInstance(@Param("instanceId") long instanceId);
}
