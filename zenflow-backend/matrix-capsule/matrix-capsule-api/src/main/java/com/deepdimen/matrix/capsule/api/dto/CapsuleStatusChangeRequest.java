package com.deepdimen.matrix.capsule.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CapsuleStatusChangeRequest {

    @NotNull
    @Min(1)
    private Integer version;
}
