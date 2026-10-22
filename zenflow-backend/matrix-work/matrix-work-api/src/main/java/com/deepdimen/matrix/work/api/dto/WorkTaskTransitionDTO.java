package com.deepdimen.matrix.work.api.dto;

import com.deepdimen.matrix.work.api.enums.WorkTaskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkTaskTransitionDTO {

    @NotNull(message = "目标状态不能为空")
    private WorkTaskStatus targetStatus;

    private String comment;
}
