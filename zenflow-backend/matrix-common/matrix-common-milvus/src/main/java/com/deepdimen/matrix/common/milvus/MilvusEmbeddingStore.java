package com.deepdimen.matrix.common.milvus;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import io.milvus.param.IndexType;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.response.SearchResp;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.deepdimen.matrix.common.milvus.CollectionOperationsExecutor.*;
import static com.deepdimen.matrix.common.milvus.CollectionRequestBuilder.buildSearchRequest;
import static com.deepdimen.matrix.common.milvus.Mapper.toEmbeddingMatches;
import static com.deepdimen.matrix.common.milvus.Mapper.toMetadataJson;
import static com.deepdimen.matrix.common.milvus.MilvusMetadataFilterMapper.formatValues;
import static com.deepdimen.matrix.common.milvus.MilvusMetadataFilterMapper.map;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * Represents an <a href="https://milvus.io/">Milvus</a> index as an embedding store. <br>
 * Supports both local and <a href="https://zilliz.com/">managed</a> Milvus instances.
 * <br>
 * Supports storing {@link Metadata} and filtering by it using a {@link Filter} (provided
 * inside an {@link EmbeddingSearchRequest}).
 */
public class MilvusEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final String DEFAULT_ID_FIELD_NAME = "id";

    private static final String DEFAULT_TEXT_FIELD_NAME = "text";

    private static final String DEFAULT_METADATA_FIELD_NAME = "metadata";

    private static final String DEFAULT_VECTOR_FIELD_NAME = "vector";

    private static final String DEFAULT_SPARSE_FIELD_NAME = "sparse";

    private final MilvusClientV2 milvusClient;

    private final String collectionName;

    private final IndexParam.MetricType metricType;

    private final ConsistencyLevel consistencyLevel;

    private final boolean retrieveEmbeddingsOnSearch;

    private final boolean autoFlushOnInsert;

    private final FieldDefinition fieldDefinition;

    public MilvusEmbeddingStore(String collectionName, Integer dimension, IndexType indexType,
                                IndexParam.MetricType metricType, String uri, String token, ConsistencyLevel consistencyLevel,
                                Boolean retrieveEmbeddingsOnSearch, Boolean autoFlushOnInsert, String databaseName, String idFieldName,
                                String textFieldName, String metadataFiledName, String vectorFiledName) {
        this(createMilvusClient(uri, token, databaseName), collectionName, dimension, indexType, metricType,
                consistencyLevel, retrieveEmbeddingsOnSearch, autoFlushOnInsert, idFieldName, textFieldName,
                metadataFiledName, vectorFiledName);
    }

    public MilvusEmbeddingStore(MilvusClientV2 milvusClient, String collectionName, Integer dimension,
                                IndexType indexType, IndexParam.MetricType metricType, ConsistencyLevel consistencyLevel,
                                Boolean retrieveEmbeddingsOnSearch, Boolean autoFlushOnInsert, String idFieldName, String textFieldName,
                                String metadataFiledName, String vectorFiledName) {
        this.milvusClient = ensureNotNull(milvusClient, "milvusClient");
        this.collectionName = getOrDefault(collectionName, "default");
        this.metricType = getOrDefault(metricType, IndexParam.MetricType.COSINE);
        this.consistencyLevel = getOrDefault(consistencyLevel, ConsistencyLevel.EVENTUALLY);
        this.retrieveEmbeddingsOnSearch = getOrDefault(retrieveEmbeddingsOnSearch, false);
        this.autoFlushOnInsert = getOrDefault(autoFlushOnInsert, false);
        this.fieldDefinition = new FieldDefinition(getOrDefault(idFieldName, DEFAULT_ID_FIELD_NAME),
                getOrDefault(textFieldName, DEFAULT_TEXT_FIELD_NAME),
                getOrDefault(metadataFiledName, DEFAULT_METADATA_FIELD_NAME),
                getOrDefault(vectorFiledName, DEFAULT_VECTOR_FIELD_NAME), DEFAULT_SPARSE_FIELD_NAME);

        if (!hasCollection(this.milvusClient, this.collectionName)) {
            createCollection(this.milvusClient, this.collectionName, this.fieldDefinition,
                    ensureNotNull(dimension, "dimension"));
        }

        loadCollectionInMemory(this.milvusClient, collectionName);
    }

    private static MilvusClientV2 createMilvusClient(String uri, String token, String databaseName) {
        ConnectConfig connectConfig = ConnectConfig.builder().uri(uri).token(token).dbName(databaseName).build();

        return new MilvusClientV2(connectConfig);
    }

    public static Builder builder() {
        return new Builder();
    }

    public void dropCollection(String collectionName) {
        CollectionOperationsExecutor.dropCollection(this.milvusClient, collectionName);
    }

    public String add(Embedding embedding) {
        String id = Utils.randomUUID();
        add(id, embedding);
        return id;
    }

    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    public String add(Embedding embedding, TextSegment textSegment) {
        String id = Utils.randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    public List<String> addAll(List<Embedding> embeddings) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        throw new UnsupportedOperationException("Not implemented yet");

    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest embeddingSearchRequest) {
        SearchReq searchRequest = buildSearchRequest(collectionName, fieldDefinition,
                embeddingSearchRequest.queryEmbedding().vectorAsList(), embeddingSearchRequest.filter(),
                embeddingSearchRequest.maxResults(), metricType, consistencyLevel);

        SearchResp searchResp = CollectionOperationsExecutor.search(milvusClient, searchRequest);

        List<EmbeddingMatch<TextSegment>> matches = toEmbeddingMatches(milvusClient, searchResp, collectionName,
                fieldDefinition, consistencyLevel, retrieveEmbeddingsOnSearch);

        List<EmbeddingMatch<TextSegment>> result = matches.stream()
                .filter(match -> match.score() >= embeddingSearchRequest.minScore())
                .collect(toList());

        return new EmbeddingSearchResult<>(result);
    }

    /**
     * 按 BM25 搜索
     *
     * @param query          查询
     * @param searchParams   查询参数
     * @param topK           topK
     * @return {@link EmbeddingSearchResult }<{@link TextSegment }>
     */
    public EmbeddingSearchResult<TextSegment> searchByBm25(String query, Map<String, Object> searchParams, int topK) {
        SearchResp searchResp = milvusClient.search(SearchReq.builder()
                .collectionName(collectionName)
                .data(Collections.singletonList(new EmbeddedText(query)))
                .annsField(DEFAULT_SPARSE_FIELD_NAME)
                .topK(topK)
                .searchParams(searchParams)
                .outputFields(List.of(this.fieldDefinition.getTextFieldName(),
                        this.fieldDefinition.getMetadataFieldName()))
                .build());

        List<EmbeddingMatch<TextSegment>> matches = toEmbeddingMatches(milvusClient, searchResp, collectionName,
                fieldDefinition, consistencyLevel, retrieveEmbeddingsOnSearch);

        return new EmbeddingSearchResult<>(matches);
    }


    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {

        JsonObject jsonObject = new JsonObject();

        List<Float> floatList = embedding.vectorAsList();
        // 将 List<Float> 转换为 JsonArray
        JsonArray jsonArray = new JsonArray();
        for (Float value : floatList) {
            jsonArray.add(value);
        }
        jsonObject.add(fieldDefinition.getVectorFieldName(), jsonArray);
        jsonObject.addProperty(fieldDefinition.getTextFieldName(), textSegment.text());
        jsonObject.addProperty(fieldDefinition.getIdFieldName(), id);
        jsonObject.add(fieldDefinition.getMetadataFieldName(), toMetadataJson(textSegment));

        insert(this.milvusClient, this.collectionName, singletonList(jsonObject));
    }


    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");
        removeForVector(this.milvusClient, this.collectionName,
                format("%s in %s", this.fieldDefinition.getIdFieldName(), formatValues(ids)));
    }


    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");
        removeForVector(this.milvusClient, this.collectionName,
                map(filter, this.fieldDefinition.getMetadataFieldName()));
    }


    @Override
    public void removeAll() {
        removeForVector(this.milvusClient, this.collectionName,
                format("%s != \"\"", this.fieldDefinition.getIdFieldName()));
    }

    public static class Builder {

        private MilvusClientV2 milvusClient;

        private String collectionName;

        private Integer dimension;

        private IndexType indexType;

        private IndexParam.MetricType metricType;

        private String uri;

        private String token;

        private ConsistencyLevel consistencyLevel;

        private Boolean retrieveEmbeddingsOnSearch;

        private String databaseName;

        private Boolean autoFlushOnInsert;

        private String idFieldName;

        private String textFieldName;

        private String metadataFieldName;

        private String vectorFieldName;

        public Builder milvusClient(MilvusClientV2 milvusClient) {
            this.milvusClient = milvusClient;
            return this;
        }

        /**
         * @param collectionName The name of the Milvus collection. If there is no such
         *                       collection yet, it will be created automatically. Default value: "default".
         * @return builder
         */
        public Builder collectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

        /**
         * @param dimension The dimension of the embedding vector. (e.g. 384) Mandatory if
         *                  a new collection should be created.
         * @return builder
         */
        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        /**
         * @param indexType The type of the index. Default value: FLAT.
         * @return builder
         */
        public Builder indexType(IndexType indexType) {
            this.indexType = indexType;
            return this;
        }

        /**
         * @param metricType The type of the metric used for similarity search. Default
         *                   value: COSINE.
         * @return builder
         */
        public Builder metricType(IndexParam.MetricType metricType) {
            this.metricType = metricType;
            return this;
        }

        /**
         * @param uri The URI of the managed Milvus instance. (e.g.
         *            "https://xxx.api.gcp-us-west1.zillizcloud.com")
         * @return builder
         */
        public Builder uri(String uri) {
            this.uri = uri;
            return this;
        }

        /**
         * @param token The token (API key) of the managed Milvus instance.
         * @return builder
         */
        public Builder token(String token) {
            this.token = token;
            return this;
        }

        /**
         * @param consistencyLevel The consistency level used by Milvus. Default value:
         *                         EVENTUALLY.
         * @return builder
         */
        public Builder consistencyLevel(ConsistencyLevel consistencyLevel) {
            this.consistencyLevel = consistencyLevel;
            return this;
        }

        /**
         * @param retrieveEmbeddingsOnSearch During a similarity search in Milvus (when
         *                                   calling findRelevant()), the embedding itself is not retrieved. To retrieve the
         *                                   embedding, an additional query is required. Setting this parameter to "true"
         *                                   will ensure that embedding is retrieved. Be aware that this will impact the
         *                                   performance of the search. Default value: false.
         * @return builder
         */
        public Builder retrieveEmbeddingsOnSearch(Boolean retrieveEmbeddingsOnSearch) {
            this.retrieveEmbeddingsOnSearch = retrieveEmbeddingsOnSearch;
            return this;
        }

        /**
         * @param autoFlushOnInsert Whether to automatically flush after each insert
         *                          ({@code add(...)} or {@code addAll(...)} methods). Default value: false. More
         *                          info can be found <a href=
         *                          "https://milvus.io/api-reference/pymilvus/v2.4.x/ORM/Collection/flush.md">here</a>.
         * @return builder
         */
        public Builder autoFlushOnInsert(Boolean autoFlushOnInsert) {
            this.autoFlushOnInsert = autoFlushOnInsert;
            return this;
        }

        /**
         * @param databaseName Milvus name of database. Default value: null. In this case
         *                     default Milvus database name will be used.
         * @return builder
         */
        public Builder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        /**
         * @param idFieldName the name of the field where the ID of the {@link Embedding}
         *                    is stored. Default value: "id".
         * @return builder
         */
        public Builder idFieldName(String idFieldName) {
            this.idFieldName = idFieldName;
            return this;
        }

        /**
         * @param textFieldName the name of the field where the text of the
         *                      {@link TextSegment} is stored. Default value: "text".
         * @return builder
         */
        public Builder textFieldName(String textFieldName) {
            this.textFieldName = textFieldName;
            return this;
        }

        /**
         * @param metadataFieldName the name of the field where the {@link Metadata} of
         *                          the {@link TextSegment} is stored. Default value: "metadata".
         * @return builder
         */
        public Builder metadataFieldName(String metadataFieldName) {
            this.metadataFieldName = metadataFieldName;
            return this;
        }

        /**
         * @param vectorFieldName the name of the field where the {@link Embedding} is
         *                        stored. Default value: "vector".
         * @return builder
         */
        public Builder vectorFieldName(String vectorFieldName) {
            this.vectorFieldName = vectorFieldName;
            return this;
        }

        public MilvusEmbeddingStore build() {
            if (milvusClient == null) {
                return new MilvusEmbeddingStore(collectionName, dimension, indexType, metricType, uri, token,
                        consistencyLevel, retrieveEmbeddingsOnSearch, autoFlushOnInsert, databaseName, idFieldName,
                        textFieldName, metadataFieldName, vectorFieldName);
            }
            return new MilvusEmbeddingStore(milvusClient, collectionName, dimension, indexType, metricType,
                    consistencyLevel, retrieveEmbeddingsOnSearch, autoFlushOnInsert, idFieldName, textFieldName,
                    metadataFieldName, vectorFieldName);
        }

    }

}
