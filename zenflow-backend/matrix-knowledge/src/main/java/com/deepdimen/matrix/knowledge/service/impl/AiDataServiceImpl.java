package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.template.Template;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.TemplateUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.common.core.util.SpringContextHolder;
import com.deepdimen.matrix.knowledge.config.AiEmbedInitRunner;
import com.deepdimen.matrix.knowledge.dto.AiDataTableDTO;
import com.deepdimen.matrix.knowledge.dto.AiTerminologyDTO;
import com.deepdimen.matrix.knowledge.entity.AiDataEntity;
import com.deepdimen.matrix.knowledge.entity.AiDataTableEntity;
import com.deepdimen.matrix.knowledge.entity.AiTerminologyEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDataMapper;
import com.deepdimen.matrix.knowledge.mapper.AiDataTableMapper;
import com.deepdimen.matrix.knowledge.mapper.AiTerminologyMapper;
import com.deepdimen.matrix.knowledge.service.AiDataFieldService;
import com.deepdimen.matrix.knowledge.service.AiDataService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * AI数据集管理服务实现类
 * <p>
 * 管理多表数据集的定义和元数据 提供表结构查询和Schema生成功能 支持虚拟注释增强SQL生成质量
 *
 * @author matrix
 * @date 2025-03-26 21:47:45
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDataServiceImpl extends ServiceImpl<AiDataMapper, AiDataEntity> implements AiDataService {

	/**
	 * 模板引擎 用于生成表结构描述文档
	 */
	public static final TemplateEngine engine = TemplateUtil.createEngine(new TemplateConfig());

	/**
	 * 数据表数据访问层
	 */
	private final AiDataTableMapper dataTableMapper;

	/**
	 * 数据字段服务（延迟加载） 避免循环依赖
	 */
	@Lazy
	private final AiDataFieldService dataFieldService;

	/**
	 * 数据集数据访问层
	 */
	private final AiDataMapper aiDataMapper;

	/**
	 * AI术语数据访问层
	 */
	private final AiTerminologyMapper terminologyMapper;

	private final AiTerminologyDatasetBindings datasetBindings;

	/**
	 * 表结构模板资源 FreeMarker模板文件
	 */
	@Value("classpath:/prompts/chat2db-table-schema.ftl")
	private Resource tableSchema;

	/**
	 * 表结构模板资源 FreeMarker模板文件
	 */
	@Value("classpath:/prompts/chat2db-terminologies.ftl")
	private Resource terminologies;

	/**
	 * 查询数据集的Schema定义
	 * <p>
	 * 获取数据集包含的所有表结构信息 使用模板渲染成结构化文档 优先使用虚拟注释提高可读性
	 * @param dataId 数据集ID
	 * @return 格式化的Schema描述文档
	 */
	@SneakyThrows
	@Override
	public String queryDataSchema(Long dataId) {
		AiDataEntity aiDataEntity = aiDataMapper.selectById(dataId);
		String[] tableNames = aiDataEntity.getTableName();
		String dsName = aiDataEntity.getDsName();

		// 查询数据集包含的所有表
		List<AiDataTableEntity> tableEntityList = dataTableMapper.selectList(Wrappers.<AiDataTableEntity>lambdaQuery()
			.eq(AiDataTableEntity::getDsName, dsName)
			.in(AiDataTableEntity::getTableName, CollUtil.toList(tableNames)));

		// 渲染表结构文档
		String renderResult = queryTableSchema(tableEntityList);
		log.info("目标数据集，渲染结果：{}", renderResult);
		return renderResult;
	}

	/**
	 * 根据数据ID查询术语
	 * @param dataId 数据ID
	 * @return 查询到的术语
	 */
	@Override
	@SneakyThrows
	public String queryTerminology(Long dataId) {
		AiDataEntity aiDataEntity = aiDataMapper.selectById(dataId);

		Long[] terminologyIds = aiDataEntity.getTerminologyIds();
		if (ArrayUtil.isEmpty(terminologyIds)) {
			return StrUtil.EMPTY;
		}

		List<AiTerminologyEntity> terminologyEntityList = terminologyMapper
			.selectByIds(Arrays.stream(terminologyIds).toList());
		List<AiTerminologyDTO> terminologyDTOList = new ArrayList<>();
		for (AiTerminologyEntity terminologyEntity : terminologyEntityList) {
			AiTerminologyDTO terminologyDTO = new AiTerminologyDTO();
			BeanUtil.copyProperties(terminologyEntity, terminologyDTO);
			List<AiTerminologyEntity> childTerminologyList = terminologyMapper
				.selectList(Wrappers.<AiTerminologyEntity>lambdaQuery()
					.eq(AiTerminologyEntity::getPid, terminologyEntity.getTermId()));

			if (CollUtil.isNotEmpty(childTerminologyList)) {
				childTerminologyList.forEach(child -> {
					terminologyDTO.getSynonymsList().add(child.getWord());
				});
			}

			terminologyDTOList.add(terminologyDTO);
		}

		Template template = engine.getTemplate(IoUtil.read(terminologies.getInputStream(), Charset.defaultCharset()));
		return template.render(Map.of("terminologies", terminologyDTOList));
	}

	/**
	 * 查询表结构Schema
	 * <p>
	 * 将表和字段信息转换为结构化文档 优先使用AI优化后的虚拟注释
	 * @param tableEntityList 表实体列表
	 * @return 模板渲染后的Schema文档
	 */
	@SneakyThrows
	public String queryTableSchema(List<AiDataTableEntity> tableEntityList) {
		List<AiDataTableDTO> tableDTOList = new ArrayList<>();
		for (AiDataTableEntity tableEntity : tableEntityList) {
			AiDataTableDTO tableDTO = new AiDataTableDTO();
			tableDTO.setTableName(tableEntity.getTableName());
			tableDTO.setDbType(tableEntity.getDsType());
			// 优先使用虚拟注释，否则使用原始注释
			tableDTO.setTableComment(StrUtil.isNotBlank(tableEntity.getVirtualComment())
					? tableEntity.getVirtualComment() : tableEntity.getTableComment());

			List<AiDataTableDTO.AiTableField> fieldList = dataFieldService
				.getOrSync(tableEntity.getDsName(), tableEntity.getTableName())
				.stream()
				.map(fieldEntity -> {
					AiDataTableDTO.AiTableField field = new AiDataTableDTO.AiTableField();
					field.setFieldName(fieldEntity.getFieldName());
					// 字段也优先使用虚拟注释
					field.setFieldComment(StrUtil.isNotBlank(fieldEntity.getVirtualComment())
							? fieldEntity.getVirtualComment() : fieldEntity.getFieldComment());
					field.setDbType(fieldEntity.getDbType());
					field.setFieldType(fieldEntity.getFieldType());
					return field;
				})
				.toList();
			tableDTO.setFields(fieldList);
			tableDTOList.add(tableDTO);
		}

		Template template = engine.getTemplate(IoUtil.read(tableSchema.getInputStream(), Charset.defaultCharset()));
		return template.render(Map.of("tables", tableDTOList));
	}

	/**
	 * 保存或更新数据集
	 * <p>
	 * 同时将数据集描述信息向量化 存储到内存向量库供快速检索 用于Chat2SQL场景的数据集匹配
	 * @param aiData AI数据集实体
	 * @return 保存成功返回true
	 */
	@Override
	@org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
	public boolean saveOrUpdateData(AiDataEntity aiData) {
		datasetBindings.checkDatasetUpdate(aiData);
		baseMapper.insertOrUpdate(aiData);
		SpringContextHolder.publishEvent(new AiEmbedInitRunner.EmbedInitEvent(this));
		return true;
	}

}
