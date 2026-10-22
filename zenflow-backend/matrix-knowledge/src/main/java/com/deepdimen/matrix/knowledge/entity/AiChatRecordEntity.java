package com.deepdimen.matrix.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

import java.time.LocalDateTime;

/**
 * AI聊天记录表 用于记录用户与AI的对话历史，包括问答内容、使用的模型、关联的知识库等信息
 *
 * @author matrix
 * @date 2024-07-03 12:39:32
 */
@Data
@TableName("ai_chat_record")
@FieldNameConstants
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI聊天记录表")
@TenantTable
public class AiChatRecordEntity extends Model<AiChatRecordEntity> {

	/** Metadata-only explanation, not controlled SQL execution evidence. */
	@TableField(exist = false)
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	private java.util.Map<String, Object> metadataGuidance;

	/**
	 * 主键
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键")
	private Long recordId;

	/**
	 * 租户ID
	 */
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "租户ID")
	private String tenantId;

	/**
	 * 模型名称 使用的AI模型标识（如：gpt-4、claude-3等）
	 */
	@Schema(description = "型号名称")
	private String modelName;

	/**
	 * 浏览器标识 用户使用的浏览器User-Agent信息
	 */
	@Schema(description = "浏览器标识")
	private String userAgent;

	/**
	 * 用户标识 发起对话的用户名
	 */
	@Schema(description = "用户标识")
	private String username;

	/**
	 * 问题文本 用户提出的问题内容
	 */
	@NotBlank
	@Schema(description = "问题")
	private String questionText;

	/**
	 * 答案文本 AI生成的回复内容
	 */
	@NotBlank
	@Schema(description = "答案")
	private String answerText;

	/**
	 * 扩展信息 附加的详细信息，如上下文、引用来源等
	 */
	@Schema(description = "扩展信息")
	private String extDetails;

	/**
	 * 关联智能体ID，NULL表示未关联智能体
	 */
	@Schema(description = "关联智能体ID")
	private Long agentId;

	/**
	 * 消息评价: like/dislike
	 */
	@Schema(description = "消息评价")
	private String evaluate;

	@TableField(exist = false)
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	private java.util.Map<String, Object> dataQuery;

	@TableField(exist = false)
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	private String dataQueryReferenceState;

	/** Live non-pilot execution only; not a controlled-query receipt. */
	@TableField(exist = false)
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	private String databaseChatState;

	@TableField(exist = false)
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	private String dataQueryFeedbackId;

	/**
	 * 所属知识库 关联的知识库ID
	 */
	@Schema(description = "所属知识库")
	private Long datasetId;

	/**
	 * 知识库名称 非数据库字段，仅用于展示
	 */
	@TableField(exist = false)
	private String datasetName;

	/**
	 * 关联文档IDs 回答引用的文档ID列表，逗号分隔
	 */
	@Schema(description = "关联文档IDs")
	private String documentIds;

	/**
	 * 匹配度 问题与知识库内容的匹配度
	 */
	@Schema(description = "匹配度")
	private String suitability;

	/**
	 * 客户端IP 发起请求的客户端IP地址
	 */
	@Schema(description = "客户端IP")
	private String ip;

	/**
	 * 会话ID 属于同一会话的唯一标识
	 */
	@Schema(description = "会话ID")
	private String conversationId;

	/**
	 * 消息ID，用于关联第三方模型消息和评价
	 */
	@Schema(description = "消息ID")
	private String messageId;

	/**
	 * 网络搜索标记 是否使用了网络搜索功能
	 */
	@Schema(description = "网络搜索")
	private String websearchFlag;

	/**
	 * 标注标记 是否为标注的高质量问答
	 */
	@Schema(description = "标注")
	private String standardFlag;

	/**
	 * 大模型交互结果 记录与大模型交互的状态或结果 如：成功、失败、触发敏感词等
	 */
	@Schema(description = "大模型交互结果")
	private String llmFlag;

	/**
	 * 内部调用标记 是否为系统内部调用
	 */
	@Schema(description = "内部调用")
	private String innerFlag;

	/**
	 * 向量ID 在向量数据库中的存储ID
	 */
	@Schema(description = "向量ID")
	private String qdrantId;

	/**
	 * 创建人
	 */
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	/**
	 * 创建时间
	 */
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	/**
	 * 修改人
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "修改人")
	private String updateBy;

	/**
	 * 修改时间
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	/**
	 * 删除标记
	 */
	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "删除标记")
	private String delFlag;

	/**
	 * 使用时间（毫秒）
	 */
	@Schema(description = "使用时间，单位毫秒")
	private Long useTime;

}
