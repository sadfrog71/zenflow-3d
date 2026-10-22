package com.deepdimen.matrix.flow;

import com.deepdimen.matrix.common.feign.annotation.EnableMatrixFeignClients;
import com.deepdimen.matrix.common.security.annotation.EnableMatrixResourceServer;
import com.deepdimen.matrix.common.swagger.annotation.EnableOpenApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 流程引擎服务启动类
 * <p>
 * Matrix流程引擎微服务的主入口，基于Flowable工作流引擎构建。
 * 提供完整的工作流管理功能，包括流程定义、流程实例管理、任务处理等。
 * 主要特性：
 * - 集成Flowable BPMN 2.0流程引擎
 * - 支持动态表单和流程配置
 * - 提供RESTful API接口
 * - 支持多租户隔离
 * - 集成Spring Cloud微服务架构
 * </p>
 * 
 * @author matrix archetype
 */
@EnableOpenApi("task")
@EnableMatrixFeignClients
@EnableDiscoveryClient
@EnableMatrixResourceServer
@SpringBootApplication
public class MatrixFlowApplication {

	/**
	 * 流程引擎服务主方法
	 * <p>
	 * 启动Spring Boot应用，初始化流程引擎相关组件：
	 * - 初始化Flowable流程引擎
	 * - 注册到服务发现中心
	 * - 启用OAuth2资源服务器保护
	 * - 开启Swagger API文档
	 * </p>
	 * 
	 * @param args 命令行参数
	 */
	public static void main(String[] args) {
		SpringApplication.run(MatrixFlowApplication.class, args);
	}

}