package com.deepdimen.matrix.common.milvus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
class FieldDefinition {

	private final String idFieldName;

	private final String textFieldName;

	private final String metadataFieldName;

	private final String vectorFieldName;

	private final String sparseFieldName;

}
