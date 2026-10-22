package com.deepdimen.matrix.work.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory.AssignableUser;
import com.deepdimen.matrix.work.api.dto.WorkTaskCreateDTO;
import com.deepdimen.matrix.work.api.dto.WorkTaskTransitionDTO;
import com.deepdimen.matrix.work.api.dto.WorkTaskUpdateDTO;
import com.deepdimen.matrix.work.api.enums.WorkTaskStatus;
import com.deepdimen.matrix.work.entity.WorkTaskEntity;
import java.util.List;

public interface WorkTaskService extends IService<WorkTaskEntity> {

    WorkTaskEntity create(WorkTaskCreateDTO request);

	WorkTaskEntity update(Long id, WorkTaskUpdateDTO request);

    void deleteTask(Long id);

    WorkTaskEntity transition(Long id, WorkTaskTransitionDTO request);

    Page<WorkTaskEntity> pageForCurrentUser(
            Page<WorkTaskEntity> page, String view, WorkTaskStatus status, String keyword);

    WorkTaskEntity requireVisibleTask(Long id);

    List<AssignableUser> listAssignableUsers();
}
