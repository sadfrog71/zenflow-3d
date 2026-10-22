/*
 *    Copyright (c) 2018-2025, deepdimen All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * Neither the name of the deepdimen.com developer nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * Author: caoyuan (caoyuanchn@gmail.com)
 */
package com.deepdimen.matrix.admin.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.admin.api.entity.SysDict;
import com.deepdimen.matrix.admin.api.entity.SysDictItem;
import com.deepdimen.matrix.admin.mapper.SysDictItemMapper;
import com.deepdimen.matrix.admin.mapper.SysDictMapper;
import com.deepdimen.matrix.admin.service.SysDictService;
import com.deepdimen.matrix.common.core.constant.CacheConstants;
import com.deepdimen.matrix.common.core.constant.enums.DictTypeEnum;
import com.deepdimen.matrix.common.core.exception.ErrorCodes;
import com.deepdimen.matrix.common.core.util.MsgUtils;
import com.deepdimen.matrix.common.core.util.R;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 字典表
 *
 * @author caoyuan
 * @date 2019/03/19
 */
@Service
@AllArgsConstructor
public class SysDictServiceImpl extends ServiceImpl<SysDictMapper, SysDict> implements SysDictService {

	private final SysDictItemMapper dictItemMapper;

	/**
	 * 根据ID 删除字典
	 * @param ids 字典ID 列表
	 * @return
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	@CacheEvict(value = CacheConstants.DICT_DETAILS, allEntries = true)
	public R removeDictByIds(Long[] ids) {

		List<Long> dictIdList = baseMapper.selectByIds(CollUtil.toList(ids))
			.stream()
			.filter(sysDict -> !sysDict.getSystemFlag().equals(DictTypeEnum.SYSTEM.getType()))// 系统内置类型不删除
			.map(SysDict::getId)
			.toList();

		baseMapper.deleteByIds(dictIdList);

		dictItemMapper.delete(Wrappers.<SysDictItem>lambdaQuery().in(SysDictItem::getDictId, dictIdList));
		return R.ok();
	}

	/**
	 * 更新字典
	 * @param dict 字典
	 * @return
	 */
	@Override
	@CacheEvict(value = CacheConstants.DICT_DETAILS, key = "#dict.dictType")
	public R updateDict(SysDict dict) {
		SysDict sysDict = this.getById(dict.getId());
		// 系统内置
		if (DictTypeEnum.SYSTEM.getType().equals(sysDict.getSystemFlag())) {
			return R.failed(MsgUtils.getMessage(ErrorCodes.SYS_DICT_UPDATE_SYSTEM));
		}
		this.updateById(dict);
		return R.ok(dict);
	}

	/**
	 * 同步缓存 （清空缓存）
	 * @return R
	 */
	@Override
	@CacheEvict(value = CacheConstants.DICT_DETAILS, allEntries = true)
	public R syncDictCache() {
		return R.ok();
	}

}
