package com.deepdimen.matrix.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.knowledge.config.DocumentParseAsyncConfiguration;
import com.deepdimen.matrix.knowledge.dto.AiSuggestionFieldResultDTO;
import com.deepdimen.matrix.knowledge.entity.AiDataFieldEntity;
import com.deepdimen.matrix.knowledge.entity.AiDataTableEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDataFieldMapper;
import com.deepdimen.matrix.knowledge.mapper.AiDataTableMapper;
import com.deepdimen.matrix.knowledge.service.AiAssistantService;
import com.deepdimen.matrix.knowledge.service.AiDataFieldService;
import com.deepdimen.matrix.knowledge.service.AiDataService;
import com.deepdimen.matrix.knowledge.service.AiDatasourceService;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.anyline.metadata.Column;
import org.anyline.proxy.ServiceProxy;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI数据字段管理服务实现类
 * <p>
 * 管理数据库表字段的元数据信息 支持字段同步、注释优化和AI智能评估 提供虚拟注释功能增强字段可读性
 *
 * @author matrix
 * @date 2025-03-26 21:49:03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDataFieldServiceImpl extends ServiceImpl<AiDataFieldMapper, AiDataFieldEntity>
		implements AiDataFieldService {

	/**
	 * 数据源服务
	 */
	private final AiDatasourceService datasourceService;

	/**
	 * 数据表数据访问层
	 */
	private final AiDataTableMapper aiDataTableMapper;

	/**
	 * 模型提供者 用于获取AI模型进行字段评估
	 */
	private final ModelProvider modelProvider;

	/**
	 * 数据服务（延迟加载） 避免循环依赖
	 */
	@Lazy
	private final AiDataService aiDataService;

	/**
	 * 字段数据访问层
	 */
	private final AiDataFieldMapper aiDataFieldMapper;

	@Override
	public void validateTableSelection(String dsName, String tableName) {
		requireTable(dsName, tableName);
	}

	private AiDataTableEntity requireTable(String dsName, String tableName) {
		if (!validName(dsName) || !validName(tableName))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择有效的数据源和数据表");
		Long tenantId = TenantContextHolder.getTenantId();
		if (tenantId == null || TenantContextHolder.getTenantSkip())
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "缺少有效租户上下文");
		// Keep the normal tenant interceptor; explicit scope also fails closed if it is
		// absent.
		var tables = aiDataTableMapper.selectList(Wrappers.<AiDataTableEntity>lambdaQuery()
			.eq(AiDataTableEntity::getDsName, dsName)
			.eq(AiDataTableEntity::getTableName, tableName)
			.apply("tenant_id = {0}", tenantId));
		if (tables.size() != 1 || tables.get(0).getTableId() == null || !dsName.equals(tables.get(0).getDsName())
				|| !tableName.equals(tables.get(0).getTableName()))
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据表不存在或当前租户不可见，请刷新后重新选择");
		return tables.get(0);
	}

	private boolean validName(String name) {
		return name != null && !name.isBlank() && name.length() <= 128 && name.equals(name.trim())
				&& !List.of("undefined", "null", "[object Object]").contains(name)
				&& name.chars().noneMatch(c -> c < 32 || c == 127 || "/\\,?#".indexOf(c) >= 0);
	}

	/**
	 * 获取表字段信息
	 * <p>
	 * 如果字段信息不存在则自动同步 确保返回最新的字段元数据
	 * @param dsName 数据源名称
	 * @param tableName 表名
	 * @return 字段信息列表
	 */
	@Override
	public List<AiDataFieldEntity> getOrSync(String dsName, String tableName) {

		AiDataTableEntity dataTableEntity = requireTable(dsName, tableName);

		List<AiDataFieldEntity> fieldEntityList = baseMapper.selectList(Wrappers.<AiDataFieldEntity>lambdaQuery()
			.eq(AiDataFieldEntity::getTableId, dataTableEntity.getTableId()));

		if (fieldEntityList.isEmpty()) {
			// 字段列表为空，执行同步操作
			syncTableField(dataTableEntity.getDsName(), dataTableEntity.getTableName());
			// 同步完成后重新查询
			fieldEntityList = baseMapper.selectList(Wrappers.<AiDataFieldEntity>lambdaQuery()
				.eq(AiDataFieldEntity::getTableId, dataTableEntity.getTableId()));
		}
		return fieldEntityList;
	}

	/**
	 * 同步表字段元数据
	 * <p>
	 * 从数据库获取最新字段信息并更新本地缓存 保留已有的自定义注释，仅更新系统注释
	 * @param dsName 数据源名称
	 * @param tableName 表名
	 * @return 同步成功返回true
	 */
	public boolean syncTableField(String dsName, String tableName) {
		// 查询数据表实体
		AiDataTableEntity dataTableEntity = requireTable(dsName, tableName);

		// 手动切换数据源
		datasourceService.registerDataSource(dsName);
		List<Column> columnList = ServiceProxy.service(dsName).metadata().columns(tableName).values().stream().toList();

		for (Column column : columnList) {
			AiDataFieldEntity fieldEntity = new AiDataFieldEntity();
			fieldEntity.setTableId(dataTableEntity.getTableId());
			fieldEntity.setDsName(dataTableEntity.getDsName());
			fieldEntity.setDbType(dataTableEntity.getDsType());
			fieldEntity.setFieldName(column.getName());
			fieldEntity.setFieldComment(column.getComment());
			fieldEntity.setFieldType(column.getOriginType());
			fieldEntity.setModifyStatus(YesNoEnum.NO.getCode());

			this.getOneOpt(Wrappers.<AiDataFieldEntity>lambdaQuery()
				.eq(AiDataFieldEntity::getTableId, dataTableEntity.getTableId())
				.eq(AiDataFieldEntity::getFieldName, fieldEntity.getFieldName()), false).ifPresentOrElse(entity -> {
					// 字段已存在，仅更新系统注释
					entity.setFieldComment(fieldEntity.getFieldComment());
					baseMapper.updateById(entity);
				}, () -> baseMapper.insert(fieldEntity)); // 字段不存在，新增记录
		}

		datasourceService.destroyDataSource(dsName);
		return true;
	}

	/**
	 * 使用AI评估表字段质量
	 * <p>
	 * 异步分析表结构和字段信息 生成优化建议和改进的字段注释 更新虚拟注释供开发参考
	 * @param dsName 数据源名称
	 * @param tableName 表名
	 */
	@Override
	@Async(DocumentParseAsyncConfiguration.DOCUMENT_PARSE_EXECUTOR)
	public void assessTableField(String dsName, String tableName) {
		AiDataTableEntity dataTableEntity = requireTable(dsName, tableName);
		Triple<ChatModel, AiAssistantService, String> jsonAssistantTriple = modelProvider.getAiJSONAssistant(null);

		String dataSchema = aiDataService.queryTableSchema(List.of(dataTableEntity));
		AiSuggestionFieldResultDTO aiSuggestionFieldResultDTO = jsonAssistantTriple.getMiddle()
			.assessTableField(dataSchema);
		log.info("评估表字段结果：{}", aiSuggestionFieldResultDTO);

		for (AiSuggestionFieldResultDTO.AiSuggestionFieldDTO suggestion : aiSuggestionFieldResultDTO.getSuggestions()) {
			AiDataFieldEntity aiDataFieldEntity = new AiDataFieldEntity();
			aiDataFieldEntity.setModifyStatus(YesNoEnum.YES.getCode());
			aiDataFieldEntity.setModifyTime(LocalDateTime.now());
			aiDataFieldEntity.setVirtualComment(suggestion.getFieldSuggestionComment());

			// 更新字段的虚拟注释
			aiDataFieldMapper.update(aiDataFieldEntity,
					Wrappers.<AiDataFieldEntity>lambdaQuery()
						.eq(AiDataFieldEntity::getTableId, dataTableEntity.getTableId())
						.eq(AiDataFieldEntity::getFieldName, suggestion.getFieldName()));
		}

		// 更新表的虚拟注释
		dataTableEntity.setVirtualComment(aiSuggestionFieldResultDTO.getTableSuggestionComment());
		aiDataTableMapper.updateById(dataTableEntity);
	}

}
