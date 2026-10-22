package com.deepdimen.matrix.work.controller;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import com.deepdimen.matrix.work.service.InformationReviewModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

/**
 * Read-only adapter capability, not authorization or proof that a binding can execute.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/work/information/admin/model-capability")
public class InformationModelCapabilityController {

	private final ObjectProvider<InformationReviewModel> models;

	@GetMapping
	@HasPermission("work_information_policy")
	public R<Capability> get(@RequestParam(required = false) String modelName) {
		return R.ok(inspect(modelName));
	}

	Capability inspect(String modelName) {
		if (modelName == null || modelName.isBlank() || modelName.length() > 256)
			return new Capability(false, "MODEL_REQUIRED", "请填写固定模型标识");
		try {
			var model = models.getIfAvailable();
			if (model == null)
				return new Capability(false, "ADAPTER_MISSING", "资讯模型服务尚未接入，托管采集将隔离候选");
			if (!model.supportsManagedModel(modelName))
				return new Capability(false, "MODEL_UNSUPPORTED", "当前资讯模型服务不支持该固定模型");
			return new Capability(true, "SUPPORTED", "已接入该固定模型；仍需校验分析、复核绑定，不代表执行验收通过");
		}
		catch (RuntimeException unavailable) {
			return new Capability(false, "ADAPTER_UNAVAILABLE", "暂时无法检查资讯模型服务，请稍后重试");
		}
	}

	public record Capability(boolean supported, String reason, String message) {
	}

}
