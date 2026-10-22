package com.deepdimen.matrix.work.api.dto;

import com.deepdimen.matrix.work.api.enums.WorkSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkSourceSaveDTO {

    @NotBlank(message = "数据源名称不能为空")
    private String name;

    @NotNull(message = "数据源类型不能为空")
    private WorkSourceType sourceType;

    private String protocolType;

    private Long configRefId;

    private String secretRef;

    private String configJson;

    private Boolean enabled;
}
