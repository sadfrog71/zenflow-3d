package com.deepdimen.matrix.knowledge.support.handler.parse;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.dto.MineruLocalResponseDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.support.constant.FileParserStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.FileTypeEnums;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Mineru 私有化文档解析处理器
 * <p>
 * 通过后台线程池异步调用MinerU本地版POST /file_parse同步API， 解析结果存入MineruLocalTaskCache，由定时任务轮询消费。
 *
 * @author caoyuan
 * @date 2025/06/01
 */
@Slf4j
@Service
public class MineruLocalUploadFileParseHandler implements UploadFileParseHandler {

	private final AiKnowledgeProperties aiKnowledgeProperties;

	private final MineruLocalTaskCache mineruLocalTaskCache;

	private final ExecutorService mineruExecutor;

	public MineruLocalUploadFileParseHandler(AiKnowledgeProperties aiKnowledgeProperties,
			MineruLocalTaskCache mineruLocalTaskCache) {
		this.aiKnowledgeProperties = aiKnowledgeProperties;
		this.mineruLocalTaskCache = mineruLocalTaskCache;
		int maxConcurrent = aiKnowledgeProperties.getMineru().getMaxConcurrent();
		this.mineruExecutor = new ThreadPoolExecutor(maxConcurrent, maxConcurrent, 60L, TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(100), new ThreadPoolExecutor.CallerRunsPolicy());
	}

	@PreDestroy
	public void shutdown() {
		mineruExecutor.shutdown();
	}

	@Override
	public boolean supports(AiDatasetEntity aiDataset, AiDocumentEntity documentEntity) {
		String contentType = FileUtil.extName(documentEntity.getFileUrl());

		if (!aiKnowledgeProperties.getMineru().isEnabled()) {
			return false;
		}

		if (!aiKnowledgeProperties.getMineru().isPrivate()) {
			return false;
		}

		return FileTypeEnums.PDF.getType().equals(contentType) || FileTypeEnums.PNG.getType().equals(contentType)
				|| FileTypeEnums.JPG.getType().equals(contentType) || FileTypeEnums.JPEG.getType().equals(contentType);
	}

	@Override
	public Pair<FileParserStatusEnums, String> file2String(AiDocumentEntity documentEntity, InputStream inputStream,
			String extName) {
		try {
			byte[] fileBytes = inputStream.readAllBytes();
			String taskId = UUID.randomUUID().toString();
			String fileName = documentEntity.getName();

			mineruExecutor.submit(() -> {
				try {
					MineruLocalResponseDTO.ResultDTO result = callMineruFileParse(fileBytes, fileName);
					mineruLocalTaskCache.putSuccess(taskId, result.getMd(), result.getImages());
					log.info("MinerU解析完成, taskId: {}, 文件: {}, 内容长度: {}, 图片数: {}", taskId, fileName,
							result.getMd() != null ? result.getMd().length() : 0,
							result.getImages() != null ? result.getImages().size() : 0);
				}
				catch (Exception e) {
					log.error("MinerU解析失败, taskId: {}, 文件: {}, 错误: {}", taskId, fileName, e.getMessage());
					mineruLocalTaskCache.putFailed(taskId, e.getMessage());
				}
			});

			log.info("MinerU解析任务已提交, taskId: {}, 文件: {}", taskId, fileName);
			return Pair.of(FileParserStatusEnums.AI_PARSING, taskId);
		}
		catch (Exception e) {
			log.error("MinerU任务提交失败, 文件: {}, 错误: {}", documentEntity.getName(), e.getMessage());
			return Pair.of(FileParserStatusEnums.PARSE_FAIL, e.getMessage());
		}
	}

	private MineruLocalResponseDTO.ResultDTO callMineruFileParse(byte[] fileBytes, String fileName) {
		String baseUrl = aiKnowledgeProperties.getMineru().getBaseUrl();
		String backend = aiKnowledgeProperties.getMineru().getBackend();
		String lang = aiKnowledgeProperties.getMineru().getLang();
		int timeout = aiKnowledgeProperties.getMineru().getReadTimeout() * 1000;

		String body = HttpRequest.post(baseUrl + "/file_parse")
			.form("files", fileBytes, fileName)
			.form("backend", backend)
			.form("lang_list", lang)
			.form("return_md", "true")
			.form("return_images", "true")
			.timeout(timeout)
			.execute()
			.body();

		JSONObject json = JSONUtil.parseObj(body);

		if (json.containsKey("error")) {
			throw new RuntimeException("MinerU返回错误: " + json.getStr("error"));
		}

		JSONObject results = json.getJSONObject("results");
		if (results == null || results.isEmpty()) {
			throw new RuntimeException("MinerU返回结果为空");
		}

		String key = results.keySet().iterator().next();
		JSONObject fileResult = results.getJSONObject(key);

		MineruLocalResponseDTO.ResultDTO resultDTO = new MineruLocalResponseDTO.ResultDTO();
		resultDTO.setFileName(fileName);
		resultDTO.setMd(fileResult.getStr("md_content"));

		JSONObject images = fileResult.getJSONObject("images");
		if (images != null && !images.isEmpty()) {
			Map<String, String> imageMap = new HashMap<>();
			for (String imageName : images.keySet()) {
				imageMap.put(imageName, images.getStr(imageName));
			}
			resultDTO.setImages(imageMap);
		}

		return resultDTO;
	}

	@Override
	public int getOrder() {
		return 300;
	}

}
