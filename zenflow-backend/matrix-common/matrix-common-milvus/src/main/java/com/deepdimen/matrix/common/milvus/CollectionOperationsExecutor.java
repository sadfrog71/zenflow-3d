package com.deepdimen.matrix.common.milvus;

import com.google.gson.JsonObject;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.deepdimen.matrix.common.milvus.CollectionRequestBuilder.*;

class CollectionOperationsExecutor {

	static void flush(MilvusClientV2 milvusClient, String collectionName) {
		buildFlushRequest(collectionName);
	}

	static boolean hasCollection(MilvusClientV2 milvusClient, String collectionName) {
		HasCollectionReq request = buildHasCollectionRequest(collectionName);
		return milvusClient.hasCollection(request);
	}

	static void createCollection(MilvusClientV2 milvusClient, String collectionName, FieldDefinition fieldDefinition,
			int dimension) {
		CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();
		schema.addField(AddFieldReq.builder()
			.fieldName(fieldDefinition.getIdFieldName())
			.dataType(DataType.VarChar)
			.maxLength(36)
			.isPrimaryKey(true)
			.build());

		schema.addField(AddFieldReq.builder()
			.fieldName(fieldDefinition.getVectorFieldName())
			.dataType(DataType.FloatVector)
			.dimension(dimension)
			.build());

		schema.addField(AddFieldReq.builder()
			.fieldName(fieldDefinition.getTextFieldName())
			.dataType(DataType.VarChar)
			.maxLength(65535)
			.enableAnalyzer(true)
			.build());

		schema.addField(AddFieldReq.builder()
			.fieldName(fieldDefinition.getMetadataFieldName())
			.dataType(DataType.JSON)
			.build());

		schema.addField(AddFieldReq.builder()
			.fieldName(fieldDefinition.getSparseFieldName())
			.dataType(DataType.SparseFloatVector)
			.build());

		schema.addFunction(CreateCollectionReq.Function.builder()
			.functionType(FunctionType.BM25)
			.name("text_bm25_emb")
			.inputFieldNames(Collections.singletonList(fieldDefinition.getTextFieldName()))
			.outputFieldNames(Collections.singletonList(fieldDefinition.getSparseFieldName()))
			.build());

		List<IndexParam> indexes = new ArrayList<>();
		indexes.add(IndexParam.builder()
			.fieldName(fieldDefinition.getSparseFieldName())
			.indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
			.metricType(IndexParam.MetricType.BM25)
			.build());

		indexes.add(IndexParam.builder()
			.fieldName(fieldDefinition.getVectorFieldName())
			.indexType(IndexParam.IndexType.AUTOINDEX)
			.build());

		CreateCollectionReq requestCreate = CreateCollectionReq.builder()
			.collectionName(collectionName)
			.collectionSchema(schema)
			.indexParams(indexes)
			.build();
		milvusClient.createCollection(requestCreate);

		// =========================================================================================================
	}

	static void dropCollection(MilvusClientV2 milvusClient, String collectionName) {
		DropCollectionReq request = buildDropCollectionRequest(collectionName);
		milvusClient.dropCollection(request);
	}

	static void insert(MilvusClientV2 milvusClient, String collectionName, List<JsonObject> dataList) {
		InsertReq insertReq = InsertReq.builder().collectionName(collectionName).data(dataList).build();
		milvusClient.insert(insertReq);
	}

	static void loadCollectionInMemory(MilvusClientV2 milvusClient, String collectionName) {
		LoadCollectionReq loadCollectionReq = buildLoadCollectionInMemoryRequest(collectionName);
		milvusClient.loadCollection(loadCollectionReq);
	}

	static SearchResp search(MilvusClientV2 milvusClient, SearchReq searchRequest) {
		return milvusClient.search(searchRequest);
	}

	static QueryResp queryForVectors(MilvusClientV2 milvusClient, String collectionName,
			FieldDefinition fieldDefinition, List<String> rowIds, ConsistencyLevel consistencyLevel) {

		QueryReq request = buildQueryRequest(collectionName, fieldDefinition, rowIds, consistencyLevel);
		return milvusClient.query(request);
	}

	static void removeForVector(MilvusClientV2 milvusClient, String collectionName, String expr) {

		DeleteReq deleteReq = buildDeleteRequest(collectionName, expr);
		milvusClient.delete(deleteReq);
	}

}
