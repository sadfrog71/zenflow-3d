package com.deepdimen.matrix.knowledge.support.flow.model.nodes;

import lombok.Data;

/**
 * AI RAG（检索增强生成）节点 用于在工作流中集成知识库检索功能，实现基于知识库的智能问答 RAG技术结合了信息检索和文本生成，提高回答的准确性和相关性
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@Data
public class AiRagNode {

	/**
	 * 知识库数据集ID 关联的知识库唯一标识符
	 */
	private Long datasetId;

	/**
	 * 知识库数据集名称 知识库的可读名称，用于界面显示
	 */
	private String datasetName;

	/**
	 * 查询提示词 用于在知识库中检索相关信息的查询内容
	 */
	private String prompt;

	/**
	 * 仅检索模式标识 "1"表示仅返回检索结果，不进行文本生成 "0"或空表示检索后进行智能回答生成
	 */
	private String onlyRecall;

}
