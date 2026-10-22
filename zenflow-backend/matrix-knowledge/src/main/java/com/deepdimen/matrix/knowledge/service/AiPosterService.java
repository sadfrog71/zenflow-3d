package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.AiPosterDTO;
import com.deepdimen.matrix.knowledge.entity.AiPosterEntity;
import reactor.core.publisher.Flux;

/**
 * AI海报生成服务接口 提供基于AI技术的海报自动生成功能
 *
 * @author matrix
 */
public interface AiPosterService extends IService<AiPosterEntity> {

	/**
	 * 生成AI海报 根据提供的海报配置信息，使用AI技术自动生成海报图片
	 * @param posterDTO 海报生成配置对象，包含海报主题、风格、文案等参数
	 * @return 返回响应式流对象，支持流式输出海报生成进度和结果
	 */
	Flux<AiMessageResultDTO> generatePoster(AiPosterDTO posterDTO);

}
