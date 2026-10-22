package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.dto.AiFlowExecuteDTO;
import com.deepdimen.matrix.knowledge.entity.AiFlowEntity;
import com.deepdimen.matrix.knowledge.mapper.AiFlowMapper;
import com.deepdimen.matrix.knowledge.service.AiFlowService;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowException;
import com.deepdimen.matrix.knowledge.support.flow.model.AiFlowDSLDefinition;
import com.deepdimen.matrix.knowledge.support.flow.model.processor.AiFlowProcessor;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI流程服务实现类
 * <p>
 * 管理和执行AI工作流 支持流式和非流式执行模式 提供流程复制和导出功能
 *
 * @author matrix
 * @date 2025-03-03 10:21:32
 */
@Service
@RequiredArgsConstructor
public class AiFlowServiceImpl extends ServiceImpl<AiFlowMapper, AiFlowEntity> implements AiFlowService {

	/**
	 * 线程池执行器 用于异步执行流程任务
	 */
	private final ExecutorService executorService = Executors.newCachedThreadPool();

	/**
	 * AI流程处理器 执行具体的流程逻辑
	 */
	private final AiFlowProcessor flowProcessor;

	/**
	 * 流程数据访问层
	 */
	private final AiFlowMapper aiFlowMapper;

	/**
	 * 执行工作流
	 * <p>
	 * 支持流式和非流式两种执行模式 流式模式使用SSE推送执行过程数据 非流式模式直接返回执行结果
	 * @param executeDTO 工作流执行参数
	 * @return 流式返回SseEmitter，非流式返回执行结果
	 * @throws FlowException 当工作流不存在、DSL解析失败或执行出错时抛出
	 */
	@Override
	public Object executeFlow(AiFlowExecuteDTO executeDTO) {
		// 1. 验证并解析工作流
		AiFlowEntity flow = aiFlowMapper.selectById(executeDTO.getId());
		if (flow == null) {
			throw FlowException.invalidFlow("未找到指定工作流");
		}

		if (StrUtil.isBlank(flow.getDsl())) {
			throw FlowException.invalidDSL("工作流DSL不能为空");
		}

		AiFlowDSLDefinition dsl = JSONUtil.toBean(flow.getDsl(), AiFlowDSLDefinition.class);
		if (dsl == null) {
			throw FlowException.invalidDSL("工作流DSL解析失败");
		}

		// 2. 根据执行模式处理
		if (executeDTO.isStream()) {
			return executeStreamFlow(flow, dsl, executeDTO);
		}
		else {
			return executeNormalFlow(flow, dsl, executeDTO);
		}
	}

	/**
	 * 执行流式工作流
	 */
	private SseEmitter executeStreamFlow(AiFlowEntity flow, AiFlowDSLDefinition dsl, AiFlowExecuteDTO executeDTO) {
		SseEmitter emitter = new SseEmitter(300000L);
		AtomicReference<Boolean> completed = new AtomicReference<>(false);

		// 连接关闭时标记状态
		emitter.onCompletion(() -> completed.set(true));

		// 设置流式回调
		executeDTO.setCallback(data -> {
			try {
				if (!completed.get() && Objects.nonNull(data.getData())
						&& StrUtil.isNotBlank(data.getData().getContent())) {
					emitter.send(SseEmitter.event().data(data));
				}
			}
			catch (IOException e) {
				log.error("SSE发送失败", e);
				emitter.completeWithError(e);
			}
		});

		// 异步执行工作流
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		executorService.execute(() -> {
			try {
				SecurityContextHolder.getContext().setAuthentication(authentication);
				flowProcessor.execute(flow, dsl, executeDTO);
			}
			catch (Exception e) {
				log.error("异步执行工作流失败", e);
				emitter.completeWithError(e);
			}
			finally {
				emitter.complete();
			}
		});

		return emitter;
	}

	/**
	 * 执行非流式工作流
	 */
	private R executeNormalFlow(AiFlowEntity flow, AiFlowDSLDefinition dsl, AiFlowExecuteDTO executeDTO) {
		try {
			return R.ok(flowProcessor.execute(flow, dsl, executeDTO));
		}
		catch (Exception e) {
			log.error("执行工作流失败", e);
			throw FlowException.executeError(e.getMessage());
		}
	}

	/**
	 * 复制流程
	 * <p>
	 * 创建流程的副本，自动添加Copy后缀 新流程将获得新的ID
	 * @param id 原流程ID
	 * @return 复制成功返回成功响应
	 */
	@Override
	public R copyFlow(Long id) {
		AiFlowEntity aiFlowEntity = baseMapper.selectById(id);
		aiFlowEntity.setId(null);
		aiFlowEntity.setName(aiFlowEntity.getName() + "Copy");
		baseMapper.insert(aiFlowEntity);
		return R.ok();
	}

	/**
	 * 导出流程定义
	 * <p>
	 * 将流程配置导出为DSL文件 导出的文件可用于流程迁移或备份
	 * @param id 流程ID
	 * @param response HTTP响应对象
	 */
	@SneakyThrows
	@Override
	public void exportFlow(Long id, HttpServletResponse response) {
		AiFlowEntity aiFlowEntity = baseMapper.selectById(id);
		aiFlowEntity.setId(null);
		String jsonPrettyStr = JSONUtil.toJsonPrettyStr(aiFlowEntity);

		// 将JSON转换为字节数组
		byte[] data = jsonPrettyStr.getBytes(StandardCharsets.UTF_8);

		response.reset();
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
				String.format("attachment; filename=%s.dsl", aiFlowEntity.getName()));
		response.addHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(data.length));
		response.setContentType("application/octet-stream; charset=UTF-8");
		IoUtil.write(response.getOutputStream(), false, data);
	}

}
