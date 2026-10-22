package com.deepdimen.matrix.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/** 知识库内的文档分类；物理删除前必须检查子分类和文档。 */
@Data
@TenantTable
@TableName("ai_document_category")
public class AiDocumentCategoryEntity {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	@JsonIgnore
	private Long tenantId;

	private Long datasetId;

	private Long parentId;

	private String name;

	private Integer sort;

}
