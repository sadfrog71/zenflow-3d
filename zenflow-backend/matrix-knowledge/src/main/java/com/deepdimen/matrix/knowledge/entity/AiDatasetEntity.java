package com.deepdimen.matrix.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import com.deepdimen.matrix.common.data.handler.JsonStringArrayTypeHandler;
import com.deepdimen.matrix.common.sensitive.annotation.Sensitive;
import com.deepdimen.matrix.common.sensitive.core.SensitiveTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

import java.time.LocalDateTime;

/**
 * AI知识库表 核心配置表，用于管理知识库的基本信息、向量存储配置、模型选择等
 *
 * @author matrix
 * @date 2024-03-14 13:39:21
 */
@Data
@TableName("ai_dataset")
@FieldNameConstants
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI知识库表")
@TenantTable
public class AiDatasetEntity extends Model<AiDatasetEntity> {

	/**
	 * 主键
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键")
	private Long id;

	/**
	 * 租户ID
	 */
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "租户ID")
	private String tenantId;

	/**
	 * 名称
	 */
	@Schema(description = "名称")
	private String name;

	/**
	 * 集合名称
	 */
	@Schema(description = "集合名称")
	private String collectionName;

	/**
	 * 向量库 ID
	 */
	@Schema(description = "集合ID")
	private Long storeId;

	/**
	 * 头像
	 */
	@Schema(description = "头像")
	private String avatarUrl;

	/**
	 * 描述
	 */
	@Schema(description = "描述")
	private String description;

	/**
	 * 知识数量
	 */
	@Schema(description = "知识数量")
	private Long units = 0L;

	/**
	 * 大小
	 */
	@Schema(description = "大小")
	private Long fileSize = 0L;

	/**
	 * 支持多轮会话 0 不支持 1 / 2/ 3
	 */
	@Schema(description = "多轮会话")
	private Integer multiRound = 5;

	/**
	 * 匹配率 80%
	 */
	@Schema(description = "匹配率")
	private Integer score;

	/**
	 * 匹配条数 2-5
	 */
	@Schema(description = "匹配条数")
	private Integer topK;

	/**
	 * 分片长度 (默认分配长度)
	 */
	private Integer fragmentSize = 2000;

	/**
	 * 展示排序值
	 */
	@Schema(description = "排序值")
	private Integer sortOrder;

	/**
	 * 未匹配是否查询大模型 0 不查询 1 查询
	 */
	@Schema(description = "未匹配是否查询大模型")
	private String emptyLlmFlag;

	/**
	 * 未匹配数据提示
	 */
	@Schema(description = "未匹配提示")
	private String emptyDesc;

	/**
	 * 敏感词开关
	 */
	@Schema(description = "敏感词开关")
	private String sensitiveFlag;

	/**
	 * 敏感词提示
	 */
	@Schema(description = "命中敏感词提示")
	private String sensitiveMsg;

	/**
	 * 知识库欢迎语
	 */
	@Schema(description = "知识库欢迎语")
	private String welcomeMsg;

	/**
	 * 是否对外开放
	 */
	@Schema(description = "是否对外开放")
	private String publicFlag;

	/**
	 * 对外开放密码
	 */
	@Sensitive(type = SensitiveTypeEnum.KEY)
	@Schema(description = "对外开放密码")
	private String publicPassword;

	/**
	 * 可见性: public/private
	 */
	@Schema(description = "可见性")
	private String visibility;

	/**
	 * 使用标注数据
	 */
	@Schema(description = "使用标注数据")
	private String standardFlag;

	/**
	 * 上传文档是否提前预总结
	 */
	@Schema(description = "是否预先总结")
	private String preSummary;

	/**
	 * 会话是否提前压缩
	 */
	@Schema(description = "是否预先总结")
	private String preCompress;

	/**
	 * AI OCR 0 不支持 1 支持
	 */
	@Schema(description = "AI OCR")
	private String aiOcrFlag;

	/**
	 * 嵌入模型
	 */
	@Schema(description = "嵌入模型")
	private String embeddingModel;

	/**
	 * 摘要模型
	 */
	@Schema(description = "摘要模型")
	private String summaryModel;

	/**
	 * 重排模型
	 */
	@Schema(description = "重排模型")
	@TableField(updateStrategy = FieldStrategy.ALWAYS)
	private String rerankerModel;

	/**
	 * 底部版权信息
	 */
	@Schema(description = "底部版权信息")
	private String footer;

	/**
	 * 知识库分类
	 */
	@Schema(description = "分类")
	private String category;

	/**
	 * AI标签开关（0-关闭 1-开启）
	 */
	@Schema(description = "AI标签开关")
	private String aiTagFlag;

	/**
	 * 知识库标签
	 */
	@Schema(description = "知识库标签")
	@TableField(typeHandler = JsonStringArrayTypeHandler.class)
	private String[] tags;

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

}
