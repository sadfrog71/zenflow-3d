package com.deepdimen.matrix.admin.controller;

import com.deepdimen.matrix.admin.api.entity.AiApiTokenEntity;
import com.deepdimen.matrix.admin.api.vo.ApiTokenValidationVO;
import com.deepdimen.matrix.admin.mapper.AiApiTokenMapper;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.security.annotation.Inner;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API Token 内部校验接口
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiApiToken/internal")
public class RemoteApiTokenController {

	private final AiApiTokenMapper aiApiTokenMapper;

	@Inner
	@GetMapping("/valid")
	public R<ApiTokenValidationVO> findValidById(@RequestParam("id") Long id) {
		AiApiTokenEntity entity = aiApiTokenMapper.findValidById(id);
		return R.ok(entity == null ? null
				: new ApiTokenValidationVO(entity.getId(), entity.getTenantId(), entity.getCreateBy()));
	}

}
