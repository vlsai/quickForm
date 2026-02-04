package com.quickform.api.service;

import com.quickform.api.dto.*;
import com.quickform.api.exception.BadRequestException;
import com.quickform.api.exception.NotFoundException;
import com.quickform.api.mapper.MetaMapper;
import com.quickform.api.model.DatasetInfo;
import com.quickform.api.model.FieldUpdateParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MetaService {
    private final MetaMapper metaMapper;
    private final JsonHelper jsonHelper;

    public MetaService(MetaMapper metaMapper, JsonHelper jsonHelper) {
        this.metaMapper = metaMapper;
        this.jsonHelper = jsonHelper;
    }

    public List<Map<String, Object>> listDatasets(DatasetListRequest request) {
        if (request == null) {
            return metaMapper.listDatasets();
        }
        if (request.getAppId() != null) {
            return metaMapper.listDatasetsByAppId(request.getAppId());
        }
        if (request.getAppCode() != null && !request.getAppCode().isBlank()) {
            return metaMapper.listDatasetsByAppCode(request.getAppCode());
        }
        return metaMapper.listDatasets();
    }

    public Map<String, Object> getDataset(DatasetGetRequest request) {
        if (request == null || (request.getId() == null && (request.getCode() == null || request.getCode().isBlank()))) {
            throw new BadRequestException("dataset id or code required");
        }
        Map<String, Object> dataset = request.getId() != null
            ? metaMapper.getDatasetById(request.getId())
            : metaMapper.getDatasetByCode(request.getCode());
        if (dataset == null) {
            throw new NotFoundException("dataset not found");
        }
        return dataset;
    }

    public long createDataset(DatasetCreateRequest request) {
        Long appId = request.getAppId();
        if (appId == null && request.getAppCode() != null && !request.getAppCode().isBlank()) {
            appId = metaMapper.getAppIdByCode(request.getAppCode());
            if (appId == null) {
                throw new NotFoundException("app code not found");
            }
        }
        String primaryKey = request.getPrimaryKey() == null || request.getPrimaryKey().isBlank() ? "id" : request.getPrimaryKey();
        String optionsJson = jsonHelper.toJson(request.getOptions());
        return metaMapper.createDataset(appId, request.getName(), request.getCode(), primaryKey, optionsJson);
    }

    public List<Map<String, Object>> listFields(Long datasetId, String datasetCode) {
        long id = resolveDatasetId(datasetId, datasetCode);
        return metaMapper.listFields(id);
    }

    public long createField(FieldCreateRequest request) {
        long datasetId = resolveDatasetId(request.getDatasetId(), request.getDatasetCode());
        String optionsJson = jsonHelper.toJson(request.getOptions());
        return metaMapper.createField(
            datasetId,
            request.getName(),
            request.getCode(),
            request.getType(),
            request.isRequired(),
            request.getDefaultValue(),
            optionsJson,
            request.getOrderNo()
        );
    }

    public int updateField(FieldUpdateRequest request) {
        if (request.getId() == null) {
            throw new BadRequestException("field id required");
        }
        boolean hasUpdate = request.getName() != null ||
            request.getType() != null ||
            request.getRequired() != null ||
            request.getDefaultValue() != null ||
            request.getOptions() != null ||
            request.getOrderNo() != null ||
            request.getIsDeleted() != null;
        if (!hasUpdate) {
            throw new BadRequestException("no fields to update");
        }
        String optionsJson = request.getOptions() == null ? null : jsonHelper.toJson(request.getOptions());
        FieldUpdateParam param = new FieldUpdateParam();
        param.setId(request.getId());
        param.setName(request.getName());
        param.setType(request.getType());
        param.setRequired(request.getRequired());
        param.setDefaultValue(request.getDefaultValue());
        param.setOptionsJson(optionsJson);
        param.setOrderNo(request.getOrderNo());
        param.setIsDeleted(request.getIsDeleted());
        return metaMapper.updateField(param);
    }

    public int deleteField(FieldDeleteRequest request) {
        if (request == null || request.getId() == null) {
            throw new BadRequestException("field id required");
        }
        return metaMapper.softDeleteField(request.getId());
    }

    public DatasetInfo getDatasetInfoByCode(String code) {
        Map<String, Object> dataset = metaMapper.getDatasetByCode(code);
        if (dataset == null) {
            return null;
        }
        long id = ((Number) dataset.get("id")).longValue();
        String primaryKey = dataset.get("primary_key") == null ? "id" : dataset.get("primary_key").toString();
        return new DatasetInfo(id, code, primaryKey);
    }

    public long resolveDatasetId(Long datasetId, String datasetCode) {
        if (datasetId != null) {
            return datasetId;
        }
        if (datasetCode == null || datasetCode.isBlank()) {
            throw new BadRequestException("dataset id or code required");
        }
        DatasetInfo info = getDatasetInfoByCode(datasetCode);
        if (info == null) {
            throw new NotFoundException("dataset not found");
        }
        return info.getId();
    }
}
