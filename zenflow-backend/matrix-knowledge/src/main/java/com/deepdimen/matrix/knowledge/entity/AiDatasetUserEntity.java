package com.deepdimen.matrix.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库用户权限
 *
 * @author matrix
 * @date 2025-06-06 10:20:20
 */
@Data
@TableName("ai_dataset_user")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "知识库用户权限")
public class AiDatasetUserEntity extends Model<AiDatasetUserEntity> {

	/**
	 * 知识库ID
	 */
	@Schema(description = "知识库ID")
	private Long datasetId;

	/**
	 * 用户ID
	 */
	@Schema(description = "用户ID")
	private Long userId;

}
