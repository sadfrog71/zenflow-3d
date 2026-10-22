package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;

import java.util.ArrayList;

/**
 * AI模型管理服务接口
 * <p>
 * 统一管理系统中的AI模型配置，包括模型的注册、更新、删除 和同步等操作，支持多厂商、多模型的管理。
 *
 * @author caoyuan
 * @date 2024/06/01
 */
public interface AiModelService extends IService<AiModelEntity> {

	/**
	 * 更新AI模型配置
	 * <p>
	 * 更新已注册模型的配置信息，包括API密钥、端点地址、 参数设置等，更新后立即生效。
	 * @param aiModel AI模型实体，包含更新的配置信息
	 * @return 操作结果，包含成功状态和消息
	 */
	R updateModel(AiModelEntity aiModel);

	/**
	 * 批量删除AI模型
	 * <p>
	 * 根据模型ID列表批量删除模型配置，删除前会检查 模型是否正在使用，避免影响业务。
	 * @param list 待删除的模型ID列表
	 * @return 操作结果，包含成功状态和删除数量
	 */
	R removeModel(ArrayList<Long> list);

	/**
	 * 保存新的AI模型
	 * <p>
	 * 注册新的AI模型到系统中，包括模型基本信息、 认证配置、能力参数等。
	 * @param aiModel AI模型实体，包含完整的模型配置
	 * @return 操作结果，包含保存的模型信息
	 */
	R saveModel(AiModelEntity aiModel);

	/**
	 * 指定或取消当前租户的统一助手模型。模型配置和凭据继续复用 ai_model。
	 * @param modelId 已配置模型ID
	 * @param enabled 是否设为统一助手模型
	 * @return 操作结果
	 */
	R setAssistantModel(Long modelId, boolean enabled);

	/**
	 * 同步AI模型列表
	 * <p>
	 * 从各AI服务提供商同步最新的模型列表， 更新模型能力、价格、限制等信息。
	 * @return 同步结果，包含更新的模型数量和状态
	 */
	R syncModel();

	/**
	 * 获取Ollama模型列表
	 * <p>
	 * 从指定的Ollama服务器获取可用的模型列表， 通过调用Ollama API的 /api/tags 端点获取动态模型信息。
	 * @param baseUrl Ollama服务器的基础URL地址
	 * @return 操作结果，包含模型列表(格式化为前端需要的SimpleModel格式)
	 */
	R getOllamaModels(String baseUrl);

	/**
	 * 动态拉取供应商模型列表
	 * <p>
	 * 根据 baseUrl+apiKey 实时从供应商获取可用模型名列表，替代前端写死的模型常量。 OpenAI兼容供应商解析 /models 端点的
	 * data[].id，Ollama解析 /api/tags 的 models[].name， Azure/Gemini等暂不支持的供应商返回空列表由前端兜底。
	 * @param aiModel AI模型实体，需包含 provider、baseUrl、apiKey（id 可选，用于脱敏回查真实apiKey）
	 * @return 操作结果，data 为模型名列表 List&lt;String&gt;
	 */
	R fetchProviderModels(AiModelEntity aiModel);

	/**
	 * 测试AI模型连接
	 * <p>
	 * 根据模型配置参数临时构建模型实例，验证连通性。 不需要先保存模型配置到数据库，直接使用传入的参数进行测试。 按模型类型执行最小化测试请求，10秒超时。
	 * @param aiModel AI模型实体，包含测试所需的配置信息
	 * @return 操作结果，包含 success(是否成功)、message(结果信息)、latency(耗时ms)
	 */
	R testModel(AiModelEntity aiModel);

}
