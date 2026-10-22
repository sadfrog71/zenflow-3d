package com.deepdimen.matrix.capsule.api.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CapsuleDetailVO extends CapsuleSummaryVO {

    private String content;

    private String contentFormat;

    private String contentHash;
}
