package com.deepdimen.matrix.work.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.work.api.enums.WorkScheduleStatus;
import com.deepdimen.matrix.work.api.enums.WorkTaskStatus;
import com.deepdimen.matrix.work.entity.WorkScheduleEntity;
import com.deepdimen.matrix.work.entity.WorkTaskEntity;
import com.deepdimen.matrix.work.service.WorkScheduleService;
import com.deepdimen.matrix.work.service.WorkTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/work")
@Tag(description = "workOverview", name = "待办中心概览")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class WorkOverviewController {

    private final WorkTaskService workTaskService;
    private final WorkScheduleService workScheduleService;

    @GetMapping("/overview")
    @Operation(summary = "查询待办中心概览")
    public R overview() {
        Long userId = SecurityUtils.getUser().getId();
        long pendingTasks = workTaskService.count(
                Wrappers.<WorkTaskEntity>lambdaQuery()
                        .eq(WorkTaskEntity::getOwnerId, userId)
                        .notIn(
                                WorkTaskEntity::getStatus,
                                WorkTaskStatus.DONE,
                                WorkTaskStatus.CANCELLED));
        LocalDateTime dayStart = LocalDateTime.now().with(LocalTime.MIN);
        LocalDateTime dayEnd = LocalDateTime.now().with(LocalTime.MAX);
        long todaySchedules = workScheduleService.count(
                Wrappers.<WorkScheduleEntity>lambdaQuery()
                        .eq(WorkScheduleEntity::getTargetUserId, userId)
                        .between(WorkScheduleEntity::getStartAt, dayStart, dayEnd)
                        .notIn(
                                WorkScheduleEntity::getStatus,
                                WorkScheduleStatus.CANCELLED,
                                WorkScheduleStatus.HIDDEN));
        return R.ok(Map.of("pendingTasks", pendingTasks, "todaySchedules", todaySchedules));
    }
}
