package com.deepdimen.matrix.capsule.api.dto;

import com.deepdimen.matrix.capsule.api.enums.CapsuleRecordType;
import com.deepdimen.matrix.capsule.api.enums.CapsuleStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class CapsulePageQuery {

    private long current = 1;

    private long size = 20;

    private String keyword;

    private String tag;

    private CapsuleRecordType recordType;

    private CapsuleStatus status = CapsuleStatus.ACTIVE;

    private LocalDateTime from;

    private LocalDateTime to;

    private List<Long> explicitIds;
}
