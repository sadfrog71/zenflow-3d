/*
 *    Copyright (c) 2018-2025, deepdimen All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * Neither the name of the deepdimen.com developer nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * Author: caoyuan (caoyuanchn@gmail.com)
 */

package com.deepdimen.matrix.bi;

import com.deepdimen.matrix.common.feign.annotation.EnableMatrixFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * @author caoyuan
 * @date 2022-04-06
 * <p>
 */
@EnableMatrixFeignClients
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = { "org.jeecg.modules.jmreport", "com.deepdimen.matrix.bi" },
		exclude = MongoAutoConfiguration.class)
public class MatrixJimuApplication {

	public static void main(String[] args) {
		SpringApplication.run(MatrixJimuApplication.class, args);
	}

}
