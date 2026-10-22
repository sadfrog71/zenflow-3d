package com.deepdimen.matrix.admin.api.feign;

import com.deepdimen.matrix.admin.api.vo.ApiTokenValidationVO;
import com.deepdimen.matrix.common.core.constant.ServiceNameConstants;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.feign.annotation.NoToken;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * API Token 内部服务
 */
@FeignClient(contextId = "remoteApiTokenService", value = ServiceNameConstants.UPMS_SERVICE)
public interface RemoteApiTokenService {

	@NoToken
	@GetMapping("/aiApiToken/internal/valid")
	R<ApiTokenValidationVO> findValidById(@RequestParam("id") Long id);

}
