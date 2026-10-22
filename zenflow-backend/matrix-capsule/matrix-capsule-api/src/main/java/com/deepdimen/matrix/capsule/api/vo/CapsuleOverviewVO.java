package com.deepdimen.matrix.capsule.api.vo;

import lombok.Data;

@Data
public class CapsuleOverviewVO {

    private Long totalCount;

    private Long activeCount;

    private Long archivedCount;

    private Long trashedCount;

    private Long todayCreatedCount;
}
