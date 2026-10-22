package com.deepdimen.matrix.admin.controller;

import com.deepdimen.matrix.admin.api.dto.EmbedAuthorizeDTO;
import com.deepdimen.matrix.admin.api.vo.EmbedAuthorizeVO;
import com.deepdimen.matrix.admin.service.EmbedAuthorizationService;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.core.util.WebUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 嵌入授权接口
 */
@RestController
@RequiredArgsConstructor
public class EmbedAuthorizationController {

	private final EmbedAuthorizationService embedAuthorizationService;

	@PostMapping("/embed/authorize")
	public ResponseEntity<R<EmbedAuthorizeVO>> authorize(@RequestHeader("X-API-Token") String apiToken,
			@Valid @RequestBody EmbedAuthorizeDTO dto, HttpServletRequest request) {
		EmbedAuthorizeVO result = embedAuthorizationService.authorize(apiToken, dto, WebUtils.getIP(request),
				request.getHeader("User-Agent"));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(R.ok(result));
	}

}
