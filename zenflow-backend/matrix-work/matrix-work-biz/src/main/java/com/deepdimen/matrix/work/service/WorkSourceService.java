package com.deepdimen.matrix.work.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.work.api.dto.WorkSourceSaveDTO;
import com.deepdimen.matrix.work.entity.WorkSourceEntity;

public interface WorkSourceService extends IService<WorkSourceEntity> {

    WorkSourceEntity create(WorkSourceSaveDTO request);

    WorkSourceEntity update(Long id, WorkSourceSaveDTO request);
}
