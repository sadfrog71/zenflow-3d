package com.deepdimen.matrix.work.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import com.deepdimen.matrix.work.api.dto.InformationPublishRequest;
import com.deepdimen.matrix.work.api.dto.InformationRunRequest;
import com.deepdimen.matrix.work.api.dto.InformationTavilyConfigRequest;
import com.deepdimen.matrix.work.api.entity.InformationItem;
import com.deepdimen.matrix.work.api.entity.InformationPolicy;
import com.deepdimen.matrix.work.api.entity.InformationRun;
import com.deepdimen.matrix.work.service.InformationAdminService;
import com.deepdimen.matrix.work.service.InformationCollectionService;
import com.deepdimen.matrix.work.service.InformationTavilyConfigService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/work/information/admin")
public class InformationAdminController {

	private final InformationAdminService adminService;
	private final InformationCollectionService collectionService;
	private final InformationTavilyConfigService tavilyConfigService;

	@GetMapping("/page")
	@HasPermission("work_information_view")
	public R<?> page(@ParameterObject Page<InformationItem> page, @ParameterObject InformationItem query) {
		return R.ok(adminService.itemPage(page, query));
	}

	@GetMapping("/policy/page")
	@HasPermission("work_information_policy")
	public R<?> policyPage(@ParameterObject Page<InformationPolicy> page) {
		return R.ok(adminService.policyPage(page));
	}
	@GetMapping("/run/page")
	@HasPermission("work_information_view")
	public R<?> runPage(@ParameterObject Page<InformationRun> page, Long policyId) {
		return R.ok(adminService.runPage(page, policyId));
	}

	@GetMapping("/tavily-config")
	@HasPermission("work_information_policy")
	public R<?> tavilyConfig() {
		return R.ok(tavilyConfigService.getConfig());
	}

	@PutMapping("/tavily-config")
	@SysLog("保存Tavily采集配置")
	@HasPermission("work_information_policy")
	public R<?> saveTavilyConfig(@RequestBody InformationTavilyConfigRequest request) {
		return R.ok(tavilyConfigService.saveConfig(request));
	}

	@PostMapping("/policy")
	@SysLog("保存AI资讯数据源")
	@HasPermission("work_information_policy")
	public R<?> savePolicy(@RequestBody InformationPolicy policy) {
		return R.ok(adminService.savePolicy(policy));
	}

	@PostMapping("/run")
	@SysLog("手动采集AI资讯")
	@HasPermission("work_information_run")
	public R<?> run(@RequestBody InformationRunRequest request) {
		return R.ok(collectionService.runPolicy(request.getPolicyId()));
	}

	@PostMapping("/publish")
	@SysLog("发布AI资讯")
	@HasPermission("work_information_publish")
	public R<?> publish(@RequestBody InformationPublishRequest request) {
		return R.ok(adminService.publish(request));
	}

	@PutMapping("/withdraw")
	@SysLog("撤回AI资讯")
	@HasPermission("work_information_publish")
	public R<?> withdraw(@RequestBody InformationPublishRequest request) {
		adminService.withdraw(request);
		return R.ok();
	}
}
