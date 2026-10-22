package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.dto.AiTerminologyDTO;
import com.deepdimen.matrix.knowledge.entity.AiTerminologyEntity;
import com.deepdimen.matrix.knowledge.mapper.AiTerminologyMapper;
import com.deepdimen.matrix.knowledge.service.AiTerminologyService;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * AI术语
 *
 * @author MATRIX AI
 * @date 2025-09-20 18:59:42
 */
@Service
@RequiredArgsConstructor
public class AiTerminologyServiceImpl extends ServiceImpl<AiTerminologyMapper, AiTerminologyEntity>
		implements AiTerminologyService {

	private final AiTerminologyDatasetBindings datasetBindings;

	/**
	 * 获取术语详情
	 * @param aiTerminology 术语实体
	 * @return 术语详情
	 */
	@Override
	public R getDetails(AiTerminologyEntity aiTerminology) {
		AiTerminologyEntity terminology = baseMapper.selectById(aiTerminology.getTermId());
		if (terminology == null || terminology.getPid() != null)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "术语不存在");
		AiTerminologyDTO terminologyDTO = new AiTerminologyDTO();
		BeanUtil.copyProperties(terminology, terminologyDTO);
		baseMapper
			.selectList(Wrappers.<AiTerminologyEntity>lambdaQuery()
				.eq(AiTerminologyEntity::getPid, terminology.getTermId()))
			.forEach(item -> terminologyDTO.getSynonymsList().add(item.getWord()));

		var selected = datasetBindings.selected(terminology.getTermId());
		terminologyDTO.setDatasetIds(selected);
		terminologyDTO.setExpectedDatasetIds(selected);
		return R.ok(terminologyDTO);
	}

	/**
	 * 保存AI术语
	 * @param aiTerminology AI术语DTO对象
	 * @return 保存后的AI术语对象
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public R saveAiTerminology(AiTerminologyDTO aiTerminology) {
		if (aiTerminology.getTermId() != null)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新建术语不能指定已有编号");
		AiTerminologyEntity terminologyEntity = new AiTerminologyEntity();
		terminologyEntity.setTermId(aiTerminology.getTermId());
		terminologyEntity.setWord(aiTerminology.getWord());
		terminologyEntity.setDescription(aiTerminology.getDescription());
		terminologyEntity.setTenantId(com.deepdimen.matrix.common.data.tenant.TenantContextHolder.getTenantId());
		if (baseMapper.insert(terminologyEntity) != 1)
			throw new ResponseStatusException(HttpStatus.CONFLICT, "术语保存未成功");

		// 插入同义词
		if (CollUtil.isNotEmpty(aiTerminology.getSynonymsList())) {
			for (String word : aiTerminology.getSynonymsList()) {
				AiTerminologyEntity wordEntity = new AiTerminologyEntity();
				wordEntity.setWord(word);
				wordEntity.setPid(terminologyEntity.getTermId());
				baseMapper.insert(wordEntity);
			}
		}
		datasetBindings.replace(terminologyEntity.getTermId(), aiTerminology.getDatasetIds(), java.util.List.of());
		return R.ok(terminologyEntity.getTermId());
	}

	/**
	 * 更新AI术语
	 * @param aiTerminology AI术语数据传输对象
	 * @return 更新后的AI术语
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public R updateAiTerminology(AiTerminologyDTO aiTerminology) {
		var existing = baseMapper.selectById(aiTerminology.getTermId());
		if (existing == null || existing.getPid() != null)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "术语不存在");
		datasetBindings.replace(existing.getTermId(), aiTerminology.getDatasetIds(),
				aiTerminology.getExpectedDatasetIds());
		AiTerminologyEntity terminologyEntity = new AiTerminologyEntity();
		terminologyEntity.setTermId(aiTerminology.getTermId());
		terminologyEntity.setWord(aiTerminology.getWord());
		terminologyEntity.setDescription(aiTerminology.getDescription());
		terminologyEntity.setTenantId(com.deepdimen.matrix.common.data.tenant.TenantContextHolder.getTenantId());
		if (baseMapper.updateById(terminologyEntity) != 1)
			throw new ResponseStatusException(HttpStatus.CONFLICT, "术语已被修改或删除，请重新打开");

		// 删除旧的同义词
		baseMapper.delete(Wrappers.<AiTerminologyEntity>lambdaQuery()
			.eq(AiTerminologyEntity::getPid, terminologyEntity.getTermId()));

		if (CollUtil.isNotEmpty(aiTerminology.getSynonymsList())) {
			// 插入新的同义词
			for (String word : aiTerminology.getSynonymsList()) {
				AiTerminologyEntity wordEntity = new AiTerminologyEntity();
				wordEntity.setWord(word);
				wordEntity.setPid(terminologyEntity.getTermId());
				baseMapper.insert(wordEntity);
			}
		}
		return R.ok();
	}

}
