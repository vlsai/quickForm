package com.quickform.api.mapper;

import com.quickform.api.model.FieldInfo;
import com.quickform.api.model.FieldUpdateParam;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface MetaMapper {

    List<Map<String, Object>> listDatasets();

    List<Map<String, Object>> listDatasetsByAppId(@Param("appId") Long appId);

    List<Map<String, Object>> listDatasetsByAppCode(@Param("appCode") String appCode);

    Map<String, Object> getDatasetById(@Param("id") Long id);

    Map<String, Object> getDatasetByCode(@Param("code") String code);

    Long getAppIdByCode(@Param("code") String code);

    Long createDataset(@Param("appId") Long appId,
                       @Param("name") String name,
                       @Param("code") String code,
                       @Param("primaryKey") String primaryKey,
                       @Param("optionsJson") String optionsJson);

    List<Map<String, Object>> listFields(@Param("datasetId") Long datasetId);

    List<FieldInfo> listFieldInfos(@Param("datasetId") Long datasetId);

    Long createField(@Param("datasetId") Long datasetId,
                     @Param("name") String name,
                     @Param("code") String code,
                     @Param("type") String type,
                     @Param("required") boolean required,
                     @Param("defaultValue") String defaultValue,
                     @Param("optionsJson") String optionsJson,
                     @Param("orderNo") int orderNo);

    int updateField(FieldUpdateParam param);

    int softDeleteField(@Param("id") Long id);
}
