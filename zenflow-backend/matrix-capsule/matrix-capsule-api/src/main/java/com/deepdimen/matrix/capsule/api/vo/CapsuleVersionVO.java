package com.deepdimen.matrix.capsule.api.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CapsuleVersionVO {

    private Long id;

    private Long capsuleId;

    private Integer revisionNo;

    private String contentFormat;

    private String content;

    private String contentHash;

    private String changeReason;

    private LocalDateTime createTime;
}
