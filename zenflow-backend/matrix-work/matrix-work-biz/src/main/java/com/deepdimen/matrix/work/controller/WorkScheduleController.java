package com.deepdimen.matrix.work.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.work.api.enums.WorkScheduleStatus;
import com.deepdimen.matrix.work.entity.WorkScheduleEntity;
import com.deepdimen.matrix.work.service.WorkScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/work/schedule")
@Tag(description = "workSchedule", name = "Pig AI 内部日程")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class WorkScheduleController {

    private final WorkScheduleService workScheduleService;

    @GetMapping("/page")
    @Operation(summary = "查询内部日程")
    public R page(
            @ParameterObject Page<WorkScheduleEntity> page,
            @RequestParam(required = false) WorkScheduleStatus status,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime start,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime end) {
        LambdaQueryWrapper<WorkScheduleEntity> query = new LambdaQueryWrapper<>();
        query.eq(WorkScheduleEntity::getTargetUserId, SecurityUtils.getUser().getId())
                .eq(status != null, WorkScheduleEntity::getStatus, status)
                .ge(start != null, WorkScheduleEntity::getStartAt, start)
                .le(end != null, WorkScheduleEntity::getStartAt, end)
                .orderByAsc(WorkScheduleEntity::getStartAt);
        return R.ok(workScheduleService.page(page, query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询内部日程详情")
    public R detail(@PathVariable Long id) {
        return R.ok(requireVisibleSchedule(id));
    }

    @PostMapping("/{id}/acknowledge")
    @SysLog("标记 Pig AI 内部日程已知晓")
    @Operation(summary = "标记内部日程已知晓")
    public R acknowledge(@PathVariable Long id) {
        requireVisibleSchedule(id);
        return R.ok(workScheduleService.acknowledge(id));
    }

    private WorkScheduleEntity requireVisibleSchedule(Long id) {
        WorkScheduleEntity schedule = workScheduleService.getById(id);
        if (schedule == null
                || !Objects.equals(
                        schedule.getTargetUserId(), SecurityUtils.getUser().getId())) {
            throw new IllegalArgumentException("未找到对应日程");
        }
        return schedule;
    }
}
