package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.entity.AiOcrConfEntity;

/**
 * AI OCR配置服务接口 提供图像文字识别（OCR）相关的服务功能
 *
 * @author matrix
 */
public interface AiOcrConfService extends IService<AiOcrConfEntity> {

	/**
	 * 解析图像中的文字内容 使用OCR技术识别图像中的文本信息
	 * @param aiOcrConf OCR配置实体，包含待识别图像信息和OCR参数配置
	 * @return 返回统一响应对象，包含识别后的文本内容
	 */
	R parseImage(AiOcrConfEntity aiOcrConf);

}
