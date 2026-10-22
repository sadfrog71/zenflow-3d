package com.deepdimen.matrix.work.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.work.entity.WorkScheduleEntity;
import com.deepdimen.matrix.work.mapper.WorkScheduleMapper;
import com.deepdimen.matrix.work.service.WorkScheduleService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class WorkScheduleServiceImpl extends ServiceImpl<WorkScheduleMapper, WorkScheduleEntity>
        implements WorkScheduleService {

    @Override
    public WorkScheduleEntity acknowledge(Long id) {
        WorkScheduleEntity schedule = getById(id);
        if (schedule == null || !SecurityUtils.getUser().getId().equals(schedule.getTargetUserId())) {
            throw new IllegalArgumentException("未找到对应日程");
        }
        schedule.setAcknowledgedAt(LocalDateTime.now());
        if (!updateById(schedule)) {
            throw new IllegalStateException("日程已被其他操作更新，请刷新后重试");
        }
        return schedule;
    }
}
