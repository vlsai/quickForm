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

    int updateRecordOnSubmit(@Param("recordId") UUID recordId,
                             @Param("pageCode") String pageCode,
                             @Param("operator") String operator,
                             @Param("templateCode") String templateCode);

    Long countRecordPendingTasks(@Param("recordId") UUID recordId);

    int insertTask(@Param("recordId") UUID recordId,
                   @Param("pageCode") String pageCode,
                   @Param("templateId") Long templateId,
                   @Param("templateCode") String templateCode,
                   @Param("nodeCode") String nodeCode,
                   @Param("assignee") String assignee,
                   @Param("status") String status,
                   @Param("action") String action,
                   @Param("comment") String comment,
                   @Param("operatedBy") String operatedBy);

    List<Long> findPendingTaskIds(@Param("recordId") UUID recordId,
                                  @Param("nodeCode") String nodeCode);

    Long findPendingTaskIdByAssignee(@Param("recordId") UUID recordId,
                                     @Param("nodeCode") String nodeCode,
                                     @Param("assignee") String assignee);

    Long findPendingTaskIdByRecordAndAssignee(@Param("recordId") UUID recordId,
                                              @Param("assignee") String assignee);

    String getTaskNodeCode(@Param("id") long id);

    int completeTask(@Param("id") long id,
                   @Param("action") String action,
                   @Param("comment") String comment,
                   @Param("operator") String operator);

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

    List<Map<String, Object>> listTimeline(@Param("recordId") UUID recordId,
                                           @Param("pageCode") String pageCode);
}
