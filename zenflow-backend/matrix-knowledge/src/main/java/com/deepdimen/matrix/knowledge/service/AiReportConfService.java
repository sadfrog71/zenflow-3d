package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.dto.AiReportCompletionsDTO;
import com.deepdimen.matrix.knowledge.entity.AiReportConfEntity;

/**
 * AI报告配置服务接口 提供文档解析和报告生成的服务功能
 *
 * @author matrix
 */
public interface AiReportConfService extends IService<AiReportConfEntity> {

	/**
	 * 解析文档内容 对上传的文档进行智能解析，提取文档中的关键信息和结构化数据
	 * @param aiReportConf AI报告配置实体，包含文档路径、解析参数等配置信息
	 * @return 返回统一响应对象，包含解析后的结构化数据和文档摘要
	 */
	R parseDoc(AiReportConfEntity aiReportConf);

	/**
	 * 下载AI生成的报告 根据报告完成情况数据，生成并下载最终的报告文件
	 * @param completionsDTO AI报告完成情况数据对象，包含报告内容、格式等信息
	 */
	String generate(AiReportCompletionsDTO completionsDTO);

}