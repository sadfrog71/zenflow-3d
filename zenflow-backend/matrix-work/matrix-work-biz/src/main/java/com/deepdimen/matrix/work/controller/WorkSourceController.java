package com.deepdimen.matrix.work.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import com.deepdimen.matrix.work.api.dto.WorkSourceSaveDTO;
import com.deepdimen.matrix.work.api.enums.WorkSourceType;
import com.deepdimen.matrix.work.entity.WorkSourceEntity;
import com.deepdimen.matrix.work.service.WorkSourceService;
import com.deepdimen.matrix.work.service.WorkMailSyncService;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/work/source")
public class WorkSourceController {

    private final WorkSourceService workSourceService;

	private final WorkMailSyncService workMailSyncService;

    @GetMapping("/catalog")
    @HasPermission("work_source_view")
    public R<?> catalog() {
        return R.ok(Arrays.stream(WorkSourceType.values()).map(type -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", type.name());
            item.put("name", type.getDisplayName());
            item.put("configurable", type.isConfigurable());
            item.put("protocols", type.getProtocols());
            return item;
        }).toList());
    }

    @GetMapping("/page")
    @HasPermission("work_source_view")
    public R<?> page(@ParameterObject Page<WorkSourceEntity> page) {
        return R.ok(workSourceService.page(page));
    }

    @PostMapping
    @SysLog("新增工作智能数据源")
    @HasPermission("work_source_add")
    public R<?> create(@Valid @RequestBody WorkSourceSaveDTO request) {
        return R.ok(workSourceService.create(request));
    }

    @PutMapping("/{id}")
    @SysLog("修改工作智能数据源")
    @HasPermission("work_source_edit")
    public R<?> update(@PathVariable Long id, @Valid @RequestBody WorkSourceSaveDTO request) {
        return R.ok(workSourceService.update(id, request));
    }

	@PostMapping("/{id}/sync")
	@SysLog("只读同步工作数据源")
	@HasPermission("work_source_edit")
	public R<?> sync(@PathVariable Long id) {
		return R.ok(workMailSyncService.sync(id));
	}
}
