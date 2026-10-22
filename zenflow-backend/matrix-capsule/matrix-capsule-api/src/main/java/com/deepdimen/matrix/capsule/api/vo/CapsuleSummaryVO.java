package com.deepdimen.matrix.capsule.api.vo;

import com.deepdimen.matrix.capsule.api.enums.CapsuleRecordType;
import com.deepdimen.matrix.capsule.api.enums.CapsuleStatus;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CapsuleSummaryVO {

    private Long id;

    private String title;

    private CapsuleRecordType recordType;

    private CapsuleStatus status;

    private String sourceType;

    private String sourceLabel;

    private String tags;
    private String favoriteFlag;

    private String excerpt;

    private Long currentRevisionId;

    private Integer revisionNo;

    private Integer version;

    private LocalDateTime capturedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
