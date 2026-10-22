/*
 *
 *      Copyright (c) 2018-2025, DeepDimen All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 *  this list of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in the
 *  documentation and/or other materials provided with the distribution.
 *  Neither the name of the deepdimen.com developer nor the names of its
 *  contributors may be used to endorse or promote products derived from
 *  this software without specific prior written permission.
 *  Author: caoyuan (caoyuanchn@gmail.com)
 *
 */

package com.deepdimen.matrix.discover.feign;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.feign.annotation.NoToken;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * @author caoyuan
 * @date 2023-02-27
 */
@FeignClient(contextId = "remoteDatasetService", value = "matrix-knowledge")
public interface RemoteDatasetService {

	/**
	 * 获取数据集
	 * @return {@link R }<{@link Boolean }>
	 */
	@NoToken
	@GetMapping("/aiDataset/share/list")
	R<List<Map<String, Object>>> getShares();

	/**
	 * @param id
	 * @return
	 */
	@NoToken
	@GetMapping("/aiDataset/share/info")
	R<Map<String, Object>> info(@RequestParam("id") Long id);

}
