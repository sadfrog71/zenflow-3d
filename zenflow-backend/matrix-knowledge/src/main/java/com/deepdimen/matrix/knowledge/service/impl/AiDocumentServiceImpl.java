package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.yulichang.toolkit.SpringContentUtils;
import com.deepdimen.matrix.admin.api.feign.RemoteFileService;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.config.DocumentParseAsyncConfiguration;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.dto.AiCrawleDocumentDTO;
import com.deepdimen.matrix.knowledge.dto.AiDocumentDTO;
import com.deepdimen.matrix.knowledge.dto.JinaReadAndFetchContentResponse;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.entity.AiSliceEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetMapper;
import com.deepdimen.matrix.knowledge.mapper.AiDocumentMapper;
import com.deepdimen.matrix.knowledge.mapper.AiModelMapper;
import com.deepdimen.matrix.knowledge.service.AiDocumentService;
import com.deepdimen.matrix.knowledge.service.AiJinaReaderAssistantService;
import com.deepdimen.matrix.knowledge.service.AiNoMemoryStreamAssistantService;
import com.deepdimen.matrix.knowledge.service.AiSliceService;
import com.deepdimen.matrix.knowledge.support.constant.EmbedBizTypeEnums;
import com.deepdimen.matrix.knowledge.support.constant.ModelSupportEnums;
import com.deepdimen.matrix.knowledge.support.constant.SourceTypeEnums;
import com.deepdimen.matrix.knowledge.support.constant.SummaryStatusEnums;
import com.deepdimen.matrix.knowledge.support.handler.rag.Neo4jEmbeddingStoreFactory;
import com.deepdimen.matrix.knowledge.support.handler.source.FileSourceTypeHandler;
import com.deepdimen.matrix.knowledge.support.handler.source.UploadSourceTypeHandler;
import com.deepdimen.matrix.knowledge.support.provider.EmbeddingProvider;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.provider.TagClassifierProvider;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import dev.langchain4j.classification.TextClassifier;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import feign.Response;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static com.deepdimen.matrix.knowledge.support.provider.EmbeddingProvider.TEMP_ID;

/**
 * AI知识文档服务实现类
 * <p>
 * 管理知识库中的文档资源 支持文档上传、解析、切片和向量化 提供网页抓取和文档总结功能
 *
 * @author matrix
 * @date 2024-03-14 13:38:59
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiDocumentServiceImpl extends ServiceImpl<AiDocumentMapper, AiDocumentEntity>
		implements AiDocumentService {

	/**
	 * Jina Reader服务 用于网页内容抓取
	 */
	private final AiJinaReaderAssistantService readerAssistantService;

	/**
	 * 文件上传处理器 处理文档解析和切片
	 */
	private final UploadSourceTypeHandler uploadSourceTypeHandler;

	/**
	 * AI知识库配置属性
	 */
	private final AiKnowledgeProperties knowledgeProperties;

	/**
	 * 数据集数据访问层
	 */
	private final AiDatasetMapper datasetMapper;

	/**
	 * 文档切片服务
	 */
	private final AiSliceService sliceService;

	/**
	 * 模型提供者
	 */
	private final ModelProvider modelProvider;

	/**
	 * 远程文件服务
	 */
	private final RemoteFileService remoteFileService;

	/**
	 * AI模型数据访问层
	 */
	private final AiModelMapper aiModelMapper;

	/**
	 * 标签分类器提供者
	 */
	private final TagClassifierProvider classifierProvider;

	private final com.deepdimen.matrix.knowledge.service.AiDocumentCategoryService categoryService;

	/**
	 * 分页查询文档列表
	 * <p>
	 * 支持按条件筛选文档
	 * @param page 分页参数
	 * @param aiDocument 查询条件
	 * @return 文档分页数据
	 */
	@Override
	public Page<AiDocumentEntity> getDocumentPage(Page<AiDocumentEntity> page, AiDocumentEntity aiDocument) {
		categoryService.applyFilter(aiDocument);
		return baseMapper.getDocumentPage(page, aiDocument);
	}

	/**
	 * 异步保存文档
	 * <p>
	 * 使用指定的处理器异步处理文档 支持多种文档类型的解析和存储
	 * @param handler 文档处理器
	 * @param aiDocumentDTO 文档信息
	 */
	@Override
	@Async(DocumentParseAsyncConfiguration.DOCUMENT_PARSE_EXECUTOR)
	public void save(FileSourceTypeHandler handler, AiDocumentDTO aiDocumentDTO) {
		handler.handle(aiDocumentDTO);
	}

	/**
	 * 批量删除文档及其切片
	 * <p>
	 * 级联删除文档对应的所有切片和向量数据 使用事务确保数据一致性
	 * @param idList 文档ID列表
	 * @return 删除成功返回true
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean removeDocumentAndSliceBatchByIds(List<Long> idList) {
		for (Long documentId : idList) {
			sliceService.removeSliceAndEmbeddingByDocumentId(documentId);
		}

		// 删除文档记录
		baseMapper.deleteBatchIds(idList);
		return Boolean.TRUE;
	}

	/**
	 * 重试失败的文档处理
	 * <p>
	 * 删除历史切片后重新解析文档 异步执行避免阻塞主线程
	 * @param documentDTO 文档信息
	 */
	@SneakyThrows
	@Override
	@Async(DocumentParseAsyncConfiguration.DOCUMENT_PARSE_EXECUTOR)
	public void retryDocument(AiDocumentDTO documentDTO) {
		AiDocumentEntity documentEntity = baseMapper.selectById(documentDTO.getId());
		if (documentEntity == null) {
			throw new IllegalStateException("文档不存在或无权限访问，documentId=" + documentDTO.getId());
		}
		// 设置需要重新处理的文件
		documentDTO.setDatasetId(documentEntity.getDatasetId());
		documentDTO.setCategoryId(documentEntity.getCategoryId());
		documentDTO.setSourceType(documentEntity.getSourceType());
		documentDTO.setPreparedDocument(null);
		documentDTO.setFiles(List.of(documentEntity));
		// 删除历史切片和向量数据
		sliceService.removeSliceAndEmbeddingByDocumentId(documentDTO.getId());
		// 重新执行文档处理流程
		uploadSourceTypeHandler.handle(documentDTO);
	}

	/**
	 * 生成文档总结
	 * <p>
	 * 使用AI模型对文档内容进行总结 仅处理开启了预总结功能的知识库文档 QA类型文档不需要总结
	 * @param documentEntity 待总结的文档
	 */
	@Override
	public void summaryDocument(AiDocumentEntity documentEntity) {
		AiDatasetEntity aiDataset = datasetMapper.selectById(documentEntity.getDatasetId());
		AiNoMemoryStreamAssistantService memoryStreamAssistantService = modelProvider
			.getAiNoMemoryStreamAssistant(aiDataset.getSummaryModel())
			.getValue();

		// 检查是否开启文档总结功能
		if (YesNoEnum.NO.getCode().equals(aiDataset.getPreSummary())) {
			return;
		}

		// QA类型文档不需要总结
		if (SourceTypeEnums.QA.getType().equals(documentEntity.getSourceType())) {
			return;
		}

		// 查询文档的所有切片
		List<AiSliceEntity> sliceEntityList = sliceService
			.list(Wrappers.<AiSliceEntity>lambdaQuery().eq(AiSliceEntity::getDocumentId, documentEntity.getId()));

		if (sliceEntityList.isEmpty()) {
			return;
		}

		// 合并所有切片内容
		String documentContent = sliceEntityList.stream()
			.map(AiSliceEntity::getContent)
			.collect(Collectors.joining(StrUtil.LF));

		try {
			Mono<String> resultFlux = memoryStreamAssistantService
				.chat(PromptBuilder.render("knowledge-rag-summary.st",
						Map.of(AiDocumentEntity.Fields.summary,
								StrUtil.subSufByLength(documentContent, knowledgeProperties.getMaxSummary()))))
				.reduce(StrUtil.EMPTY, (acc, value) -> acc + value);

			// 处理R1模型的思维链标记
			String replacedAll = Objects.requireNonNull(resultFlux.block())
				.replaceAll("<think>[\\s\\S]*?</think>", StrUtil.EMPTY);
			documentEntity.setSummary(documentEntity.getName() + replacedAll);
			documentEntity.setSummaryStatus(SummaryStatusEnums.SUMMARYED.getStatus());
		}
		catch (Exception e) {
			log.warn("文档 {} 总结失败", documentEntity.getName(), e);
			documentEntity.setSummaryFailReason(e.getMessage());
			documentEntity.setSummaryStatus(SummaryStatusEnums.FAILED.getStatus());
		}

		baseMapper.updateById(documentEntity);
	}

	/**
	 * 为文档实体打标签 (异步打标签)
	 * @param documentEntity 需要打标签的文档实体
	 */
	@Async(DocumentParseAsyncConfiguration.DOCUMENT_PARSE_EXECUTOR)
	@Override
	public void tagsDocument(AiDocumentEntity documentEntity) {
		// 文档有设置标签，跳过 AI 自动打标签
		if (!ArrayUtil.isEmpty(documentEntity.getTags())) {
			return;
		}

		// 知识库没有设置标签，跳过 AI 自动打标签
		AiDatasetEntity aiDatasetEntity = datasetMapper.selectById(documentEntity.getDatasetId());
		if (ArrayUtil.isEmpty(aiDatasetEntity.getTags())) {
			return;
		}

		// 查询文档的所有切片
		List<AiSliceEntity> sliceEntityList = sliceService
			.list(Wrappers.<AiSliceEntity>lambdaQuery().eq(AiSliceEntity::getDocumentId, documentEntity.getId()));

		if (sliceEntityList.isEmpty()) {
			return;
		}

		// 合并所有切片内容
		String documentContent = sliceEntityList.stream()
			.map(AiSliceEntity::getContent)
			.collect(Collectors.joining(StrUtil.LF));
		TextClassifier<String> classify = classifierProvider.getClassify(documentEntity.getDatasetId().toString(),
				CollUtil.toList(aiDatasetEntity.getTags()));
		// 向量分类
		List<String> classified = classify.classify(
				StrUtil.subPre(documentContent, knowledgeProperties.getInMemorySearch().getMaxSegmentSizeInChars()));
		if (CollUtil.isEmpty(classified)) {
			return;
		}

		// 更新自动分类结果
		documentEntity.setTags(classified.toArray(String[]::new));
		baseMapper.updateById(documentEntity);

	}

	/**
	 * 文档向量化处理
	 * <p>
	 * 将文档内容切片并向量化存储 用于内存向量库的快速检索 支持多种文档格式的解析
	 * @param name 文件名称
	 * @return 处理结果
	 * @throws IOException 文件读取异常
	 */
	@Override
	public R embedDocument(String name) throws IOException {
		// 从远程服务获取文件
		Response response = remoteFileService.getFile(name);
		InputStream inputStream = response.body().asInputStream();
		// 使用Apache POI解析文档
		Document textDocument = new ApachePoiDocumentParser().parse(inputStream);
		// 创建文档切片器
		DocumentSplitter documentSplitter = DocumentSplitters.recursive(
				knowledgeProperties.getInMemorySearch().getMaxSegmentSizeInChars(),
				knowledgeProperties.getInMemorySearch().getMaxOverlapSizeInChars(),
				new OpenAiTokenCountEstimator(OpenAiChatModelName.GPT_3_5_TURBO.toString()));

		// 对每个切片进行向量化存储
		documentSplitter.split(textDocument).forEach(segment -> {
			segment.metadata().put(TEMP_ID, SecureUtil.md5(name));
			segment.metadata().put(EmbedBizTypeEnums.Fields.type, EmbedBizTypeEnums.CHAT2FILE.getType());
			EmbeddingProvider.add(segment);
		});
		return R.ok();
	}

	/**
	 * 爬取网页文档内容
	 * <p>
	 * 使用Jina Reader服务抓取网页内容 支持自定义选择器提取特定内容 返回标题、描述和正文的合并文本
	 * @param crawleDocumentDTO 包含URL和抓取配置的请求参数
	 * @return 抓取到的文档内容，失败返回错误信息
	 */
	@Override
	public String crawleDocument(AiCrawleDocumentDTO crawleDocumentDTO) {
		AiModelEntity aiModelEntity = aiModelMapper.selectOne(Wrappers.<AiModelEntity>lambdaQuery()
			.eq(AiModelEntity::getProvider, ModelSupportEnums.JINA_READER.getProvider()), false);

		if (aiModelEntity == null) {
			return StrUtil.format("未配置爬虫模型：{}，数据爬取失败", ModelSupportEnums.JINA_READER.getProvider());
		}

		// 调用 Jina Reader Assistant API 获取内容
		Map<String, String> headers = new HashMap<>(4);
		headers.put(HttpHeaders.AUTHORIZATION,
				StrUtil.format("{} {}", OAuth2AccessToken.TokenType.BEARER.getValue(), aiModelEntity.getApiKey()));
		headers.put("X-Retain-Images", "none");
		headers.put("X-Proxy", "auto");

		if (StrUtil.isNotBlank(crawleDocumentDTO.getSettings())) {
			headers.put("X-Target-Selector", crawleDocumentDTO.getSettings());
		}

		try {
			JinaReadAndFetchContentResponse res = readerAssistantService.readAndFetchContent(crawleDocumentDTO.getUrl(),
					headers);

			return res.getData().getTitle() + res.getData().getDescription() + res.getData().getContent();
		}
		catch (Exception e) {
			log.warn("数据爬虫失败{}", e.getMessage());
			return "数据爬取失败：" + e.getMessage();
		}
	}

	/**
	 * 根据数据集ID和节点ID获取图节点数据
	 * @param datasetId 数据集ID
	 * @param nodeId 节点ID
	 * @return 图节点数据
	 */
	@Override
	public R getGraphNodeData(Long datasetId, Long nodeId) {
		// 1. 获取数据集信息（假设有这个方法）
		AiDatasetEntity datasetEntity = datasetMapper.selectById(datasetId);

		// 2. 获取Neo4j驱动（修复重复初始化问题）
		Driver driver = SpringContentUtils.getBean(Neo4jEmbeddingStoreFactory.class)
			.getDriver(datasetEntity.getCollectionName());

		try (Session session = driver.session()) {
			// 3. 查询节点信息
			String cypher = "MATCH (n) WHERE id(n) = $nodeId RETURN n";

			Result result = session.run(cypher, Values.parameters("nodeId", nodeId));

			if (result.hasNext()) {
				Record record = result.next();
				Node node = record.get("n").asNode();

				// 4. 构建返回数据
				Map<String, Object> nodeData = new HashMap<>();
				nodeData.put("id", node.id());
				nodeData.put("labels", node.labels());

				Map resultMap = new HashMap(node.asMap());
				resultMap.remove("embedding"); // 移除embedding字段
				nodeData.put("properties", resultMap);

				return R.ok(nodeData);
			}
			else {
				return R.failed("节点不存在");
			}

		}
		catch (Exception e) {
			log.error("查询节点失败: datasetId={}, nodeId={}", datasetId, nodeId, e);
			return R.failed("查询节点失败: " + e.getMessage());
		}
	}

	/**
	 * 获取图表数据
	 * @param documentId 文档ID
	 * @return 图表数据
	 */
	@Override
	public R getGraphData(Long documentId) {
		// 查询文档和对应知识库的配置信息
		AiDocumentEntity aiDocumentEntity = baseMapper.selectById(documentId);
		AiDatasetEntity datasetEntity = datasetMapper.selectById(aiDocumentEntity.getDatasetId());

		Driver driver = SpringContentUtils.getBean(Neo4jEmbeddingStoreFactory.class)
			.getDriver(datasetEntity.getCollectionName());

		// 查询图谱数据并转换为D3.js格式
		Map<String, Object> graph = new HashMap<>();
		List<Map<String, Object>> nodes = new ArrayList<>();
		List<Map<String, Object>> relationships = new ArrayList<>();
		Set<String> nodeIds = new HashSet<>();

		try (Session session = driver.session()) {
			// 查询节点和关系
			String query = """
					MATCH (n)-[r]->(m)
					WHERE n.documentId = $documentId
					RETURN n, r, m
					LIMIT $limit
					""";
			Result result = session.run(query,
					Map.of("documentId", documentId.toString(), "limit", knowledgeProperties.getGraphLimit()));

			while (result.hasNext()) {
				org.neo4j.driver.Record record = result.next();

				// 处理源节点
				Node sourceNode = record.get("n").asNode();
				String sourceId = String.valueOf(sourceNode.id());
				if (!nodeIds.contains(sourceId)) {
					nodes.add(createNodeMap(sourceNode, sourceId));
					nodeIds.add(sourceId);
				}

				// 处理目标节点
				Node targetNode = record.get("m").asNode();
				String targetId = String.valueOf(targetNode.id());
				if (!nodeIds.contains(targetId)) {
					nodes.add(createNodeMap(targetNode, targetId));
					nodeIds.add(targetId);
				}

				// 处理关系
				Relationship relationship = record.get("r").asRelationship();
				relationships.add(createRelationshipMap(relationship, sourceId, targetId));
			}

			// 如果没有关系，只查询节点
			if (nodes.isEmpty()) {
				Result nodeResult = session.run("""
						MATCH (n)
						WHERE n.documentId = $documentId
						RETURN n
						LIMIT $limit
						""", Map.of("documentId", documentId.toString(), "limit", knowledgeProperties.getGraphLimit()));
				while (nodeResult.hasNext()) {
					Record record = nodeResult.next();
					Node node = record.get("n").asNode();
					String nodeId = String.valueOf(node.id());
					if (!nodeIds.contains(nodeId)) {
						nodes.add(createNodeMap(node, nodeId));
						nodeIds.add(nodeId);
					}
				}
			}
		}

		graph.put("nodes", nodes);
		graph.put("links", relationships);
		return R.ok(graph);
	}

	/**
	 * 创建节点映射
	 */
	private Map<String, Object> createNodeMap(Node node, String nodeId) {
		Map<String, Object> nodeMap = new HashMap<>();
		nodeMap.put("id", nodeId);
		nodeMap.put("label", getNodeLabel(node));
		nodeMap.put("group", getNodeGroup(node));

		// 处理节点属性，移除或清空embedding字段
		Map<String, Object> properties = new HashMap<>(node.asMap());
		properties.remove("embedding"); // 移除embedding字段
		properties.remove("text"); // 移除text字段
		nodeMap.put("properties", properties);
		return nodeMap;
	}

	/**
	 * 创建关系映射
	 */
	private Map<String, Object> createRelationshipMap(Relationship relationship, String sourceId, String targetId) {
		Map<String, Object> linkMap = new HashMap<>();
		linkMap.put("source", sourceId);
		linkMap.put("target", targetId);
		linkMap.put("type", relationship.type());
		linkMap.put("id", String.valueOf(relationship.id()));

		// 关系属性
		Map<String, Object> properties = new HashMap<>(relationship.asMap());
		linkMap.put("properties", properties);

		return linkMap;
	}

	/**
	 * 获取节点标签
	 */
	private String getNodeLabel(Node node) {
		// 尝试从常见的属性中获取标签
		Map<String, Object> props = node.asMap();
		if (props.containsKey("name")) {
			return props.get("name").toString();
		}
		if (props.containsKey("title")) {
			return props.get("title").toString();
		}
		if (props.containsKey("label")) {
			return props.get("label").toString();
		}

		// 如果没有合适的属性，使用节点类型
		Iterator<String> labels = node.labels().iterator();
		if (labels.hasNext()) {
			return labels.next();
		}

		return "Node " + node.id();
	}

	/**
	 * 获取节点分组（用于D3.js着色）
	 */
	private String getNodeGroup(Node node) {
		Iterator<String> labels = node.labels().iterator();
		if (labels.hasNext()) {
			return labels.next();
		}
		return "default";
	}

}
