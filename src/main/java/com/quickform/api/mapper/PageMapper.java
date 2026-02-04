package com.quickform.api.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface PageMapper {
    List<Map<String, Object>> listPages(@Param("keyword") String keyword);

    Map<String, Object> getPageByCode(@Param("pageCode") String pageCode);

    Long findPageIdByCode(@Param("pageCode") String pageCode);

    Long insertPage(@Param("pageCode") String pageCode,
                    @Param("name") String name,
                    @Param("schemaJson") String schemaJson,
                    @Param("optionsJson") String optionsJson);

    int updatePage(@Param("pageCode") String pageCode,
                   @Param("name") String name,
                   @Param("schemaJson") String schemaJson,
                   @Param("optionsJson") String optionsJson);

    int deletePage(@Param("pageCode") String pageCode);
}
