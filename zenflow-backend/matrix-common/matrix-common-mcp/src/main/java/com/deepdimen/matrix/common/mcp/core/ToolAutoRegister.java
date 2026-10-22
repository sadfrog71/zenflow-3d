package com.deepdimen.matrix.common.mcp.core;

import org.jetbrains.annotations.NotNull;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * MCP工具自动配置类：自动扫描并注册带有@Tool注解的方法
 *
 * @author caoyuan
 * @date 2025/07/22
 */
public class ToolAutoRegister implements BeanPostProcessor, ApplicationContextAware {

	private ApplicationContext applicationContext;

	@Override
	public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	/**
	 * 只扫描Controller中的工具方法
	 * @return ToolCallbackProvider实例
	 */
	@Bean("controllerToolCallbackProvider")
	public ToolCallbackProvider controllerToolCallbackProvider() {
		List<Object> toolObjects = new ArrayList<>();
		String[] restBeanNames = applicationContext.getBeanNamesForAnnotation(RestController.class);
		for (String beanName : restBeanNames) {
			Object bean = applicationContext.getBean(beanName);
			Class<?> clazz = AopUtils.getTargetClass(bean); // 处理被代理类
			boolean match = Arrays.stream(clazz.getDeclaredMethods())
				.anyMatch(method -> method.isAnnotationPresent(Tool.class));
			if (match) {
				toolObjects.add(bean);
			}

		}

		return MethodToolCallbackProvider.builder().toolObjects(toolObjects.toArray()).build();
	}

}
