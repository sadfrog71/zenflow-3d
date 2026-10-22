/*
 *
 *      Copyright (c) 2018-2025, deepdimen All rights reserved.
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

package com.deepdimen.matrix.knowledge;

import com.deepdimen.matrix.common.security.annotation.EnableMatrixResourceServer;
import com.deepdimen.matrix.common.swagger.annotation.EnableOpenApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 知识库管理模块主启动类
 * <p>
 * 提供AI知识库管理、向量存储、文档处理、智能问答等功能
 *
 */
@EnableOpenApi("knowledge") // 启用OpenAPI文档，模块名称为knowledge
@EnableMatrixResourceServer // 启用资源服务器，集成OAuth2认证
@EnableDiscoveryClient // 启用服务发现客户端，向Nacos注册
@SpringBootApplication // Spring Boot应用主配置
public class MatrixKnowledgeApplication {

	/**
	 * 应用程序主入口
	 * @param args 命令行参数
	 */
	public static void main(String[] args) {
		SpringApplication.run(MatrixKnowledgeApplication.class, args);
	}

}
