package com.deepdimen.matrix.knowledge.dto;

import com.deepdimen.matrix.knowledge.entity.AiTerminologyEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * AI术语数据传输对象
 *
 * @author caoyuan
 * @date 2025/09/20
 */
@Data
@FieldNameConstants
@EqualsAndHashCode(callSuper = true)
public class AiTerminologyDTO extends AiTerminologyEntity {

	/** 同义词列表 */
	private List<String> synonymsList = new ArrayList<>();

	/**
	 * null means an old client did not edit bindings; an empty list explicitly unbinds.
	 */
	private List<Long> datasetIds;

	/** Optimistic binding snapshot shared with the dataset editor. */
	private List<Long> expectedDatasetIds;

}
