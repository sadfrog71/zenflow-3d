package com.deepdimen.matrix.capsule.api.dto;

import com.deepdimen.matrix.capsule.api.enums.CapsuleRecordType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CapsuleCreateRequest {
    private java.time.LocalDateTime capturedAt;

    @Size(max = 120)
    private String title;

    @NotNull private CapsuleRecordType recordType;

    @NotBlank
    @Size(max = 204800)
    private String content;

    @Size(max = 255)
    private String sourceLabel;

    /** Comma-separated labels. Commas inside a label are not allowed. */
    @Size(max = 1000)
    private String tags;
}
