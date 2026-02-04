package com.quickform.api.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface DataMapper {

    UUID createRecord(@Param("datasetId") long datasetId,
                      @Param("dataJson") String dataJson,
                      @Param("status") String status,
                      @Param("operator") String operator);

    int updateRecord(@Param("id") UUID id,
                     @Param("datasetId") long datasetId,
                     @Param("dataJson") String dataJson,
                     @Param("status") String status,
                     @Param("operator") String operator);

    int deleteRecord(@Param("id") UUID id, @Param("datasetId") long datasetId);

    List<Map<String, Object>> query(@Param("sql") String sql, @Param("params") Map<String, Object> params);

    Long count(@Param("sql") String sql, @Param("params") Map<String, Object> params);
}
