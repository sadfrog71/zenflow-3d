package com.deepdimen.matrix.capsule.api.dto;

import com.deepdimen.matrix.capsule.api.enums.CapsuleRecordType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CapsuleUpdateRequest {

    @NotNull
    @Min(1)
    private Integer version;

    @Size(max = 120)
    private String title;

    @NotNull private CapsuleRecordType recordType;

    @NotBlank
    @Size(max = 204800)
    private String content;

    @Size(max = 255)
    private String sourceLabel;

    @Size(max = 1000)
    private String tags;
}
