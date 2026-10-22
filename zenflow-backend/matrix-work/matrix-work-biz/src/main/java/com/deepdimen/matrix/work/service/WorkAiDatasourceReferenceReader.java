package com.deepdimen.matrix.work.service;

import org.springframework.stereotype.Component;

import com.deepdimen.matrix.work.mapper.WorkAiDatasourceReferenceMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WorkAiDatasourceReferenceReader {

	private final WorkAiDatasourceReferenceMapper mapper;

	public boolean exists(Long tenantId, Long datasourceId) {
		return tenantId != null && datasourceId != null && mapper.countVisible(tenantId, datasourceId) > 0;
	}

}
