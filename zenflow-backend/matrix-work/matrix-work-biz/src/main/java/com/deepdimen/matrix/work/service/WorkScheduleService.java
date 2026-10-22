package com.deepdimen.matrix.work.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.work.entity.WorkScheduleEntity;

public interface WorkScheduleService extends IService<WorkScheduleEntity> {

    WorkScheduleEntity acknowledge(Long id);
}
