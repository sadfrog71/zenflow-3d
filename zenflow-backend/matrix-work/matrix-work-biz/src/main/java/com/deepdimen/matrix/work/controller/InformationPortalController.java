package com.deepdimen.matrix.work.controller;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.work.api.dto.InformationPageQuery;
import com.deepdimen.matrix.work.service.InformationPortalService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/work/information")
public class InformationPortalController {

	private final InformationPortalService portalService;

	@GetMapping("/page")
	public R<?> page(@ParameterObject InformationPageQuery query) {
		return R.ok(portalService.page(query));
	}

	@GetMapping("/{id}")
	public R<?> detail(@PathVariable Long id) {
		return R.ok(portalService.detail(id));
	}

	@GetMapping("/stats")
	public R<?> stats() {
		return R.ok(portalService.stats());
	}

	@PostMapping("/{id}/read")
	public R<?> read(@PathVariable Long id) {
		portalService.markRead(id);
		return R.ok();
	}

	@PutMapping("/{id}/favorite")
	public R<?> favorite(@PathVariable Long id, @RequestParam boolean value) {
		portalService.favorite(id, value);
		return R.ok();
	}

	@PutMapping("/{id}/ignore")
	public R<?> ignore(@PathVariable Long id) {
		portalService.ignore(id);
		return R.ok();
	}
}
