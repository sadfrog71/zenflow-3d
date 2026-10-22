package com.deepdimen.matrix.knowledge.support.flow.model.processor;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.entity.AiOcrConfEntity;
import com.deepdimen.matrix.knowledge.service.AiOcrConfService;
import com.deepdimen.matrix.knowledge.support.flow.constants.ExecutionStatusEnums;
import com.deepdimen.matrix.knowledge.support.flow.constants.FlowConstant;
import com.deepdimen.matrix.knowledge.support.flow.constants.NodeTypeConstants;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowContextHolder;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowException;
import com.deepdimen.matrix.knowledge.support.flow.model.AiNodeDefinition;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiOcrNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OCR识别节点执行器 用于执行图像文字识别的节点处理器 从输入参数获取 imageUrl，调用 OCR 服务识别，返回完整 JSON 结果
 *
 * @author caoyuan
 * @date 2025/01/25
 */
@Slf4j
@Component(NodeTypeConstants.OCR)
@RequiredArgsConstructor
public class OcrNodeExecutor extends AbstractNodeProcessor {

	/**
	 * OCR配置服务,用于执行图像文字识别
	 */
	private final AiOcrConfService aiOcrConfService;

	/**
	 * 执行OCR识别节点的具体逻辑 1. 验证节点配置（必须选择OCR模板） 2. 从输入参数获取 imageUrl 3. 验证 imageUrl 不为空 4. 构建
	 * OCR 请求实体 5. 根据高阶解析开关决定是否使用聊天模型 6. 调用OCR服务执行图像识别 7. 直接返回后台完整 JSON 结果
	 * @param node 节点定义
	 * @param context 执行上下文
	 * @return 包含完整OCR识别结果的字典
	 */
	@Override
	protected Dict doExecute(AiNodeDefinition node, FlowContextHolder context) {
		try {
			// 1. 验证 OCR 模板配置
			AiOcrNode config = node.getOcrParams();
			if (config == null || config.getOcrId() == null || config.getOcrId().isEmpty()) {
				throw FlowException.invalidParam("OCR节点配置无效: 必须选择OCR模板");
			}

			// 2. 从输入参数获取 imageUrl
			Dict variables = getInputVariables(node, context);
			String imageUrl = variables.getStr("imageUrl");

			// 3. 验证 imageUrl
			if (imageUrl == null || imageUrl.isEmpty()) {
				throw FlowException.invalidParam("OCR节点未接收到 imageUrl 参数，请检查上游节点输出");
			}

			log.info("OCR节点[{}]开始执行: ocrId={}, imageUrl={}", node.getId(), config.getOcrId(), imageUrl);

			// 4. 构建 OCR 请求实体
			AiOcrConfEntity ocrEntity = new AiOcrConfEntity();
			ocrEntity.setId(Long.parseLong(config.getOcrId()));
			ocrEntity.setImageResource(imageUrl);
			ocrEntity.setHigherAnalyze(config.getHigherAnalyze());
			// 5. 根据高阶解析开关决定是否使用聊天模型
			ocrEntity.setChatModelName(config.getChatModelName());
			ocrEntity.setVisionModelName(config.getVisionModelName());

			// 6. 调用 OCR 服务执行图像识别
			R<?> ocrResult = aiOcrConfService.parseImage(ocrEntity);

			if (ocrResult.isOk()) {
				log.info("OCR节点[{}]执行成功", node.getId());

				// 7. 更新节点执行状态（无论是否流式都需要）
				node.setStatus(ExecutionStatusEnums.SUCCESS.getValue());
				node.setDuration(context.getDuration());
				context.getExecutedNodes().add(node);

				// 8. 如果是流式模式，发送流式回调
				if (context.isStream()) {
					// 8.1 提取并发送OCR识别结果内容
					String ocrContent = extractOcrContent(ocrResult.getData());
					if (StrUtil.isNotBlank(ocrContent)) {
						context.sendStreamContent(ocrContent);
					}

					// 8.2 发送流式结束信号，自动处理节点状态
					context.sendStreamEnd(node, null);
				}

				// 9. 返回后台完整 JSON 结果
				return Dict.create().set(FlowConstant.RESULT, ocrResult.getData());
			}
			else {
				throw new RuntimeException("OCR识别失败: " + ocrResult.getMsg());
			}

		}
		catch (NumberFormatException e) {
			log.error("OCR节点[{}]配置错误: ocrId格式不正确", node.getId(), e);
			throw FlowException.invalidParam("OCR模板ID格式不正确");
		}
		catch (Exception e) {
			log.error("OCR节点[{}]执行失败", node.getId(), e);
			throw FlowException.nodeError(node.getId(), "[OCR识别节点] -> " + e.getMessage());
		}
	}

	/**
	 * 从OCR结果中提取文本内容
	 * <p>
	 * 支持多种OCR结果格式： 1. 纯字符串：直接返回 2. Map/Dict结构：尝试提取 text、content、result 字段 3.
	 * 其他对象：转换为JSON字符串
	 * @param ocrData OCR识别结果数据
	 * @return 提取的文本内容，如果无法提取则返回null
	 */
	private String extractOcrContent(Object ocrData) {
		if (ocrData == null) {
			return null;
		}

		// 1. 如果是字符串，直接返回
		if (ocrData instanceof String) {
			return (String) ocrData;
		}

		// 2. 如果是Map/Dict结构，尝试提取常见字段
		if (ocrData instanceof java.util.Map) {
			java.util.Map<?, ?> dataMap = (java.util.Map<?, ?>) ocrData;

			// 尝试提取常见的文本字段
			Object textContent = dataMap.get("text");
			if (textContent != null) {
				return textContent.toString();
			}

			textContent = dataMap.get("content");
			if (textContent != null) {
				return textContent.toString();
			}

			textContent = dataMap.get("result");
			if (textContent != null) {
				return textContent.toString();
			}
		}

		// 3. 其他情况：转换为JSON字符串（方便前端展示）
		try {
			return JSONUtil.toJsonStr(ocrData);
		}
		catch (Exception e) {
			log.warn("无法将OCR结果转换为JSON字符串，使用toString()方法", e);
			return ocrData.toString();
		}
	}

}
