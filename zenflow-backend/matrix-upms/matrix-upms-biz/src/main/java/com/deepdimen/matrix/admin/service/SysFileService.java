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

package com.deepdimen.matrix.admin.service;

import cn.hutool.core.lang.tree.Tree;
import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.admin.api.dto.SysFileGroupDTO;
import com.deepdimen.matrix.admin.api.entity.SysFile;
import com.deepdimen.matrix.admin.api.entity.SysFileGroup;
import com.deepdimen.matrix.common.core.util.R;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件管理
 *
 * @author Luckly
 * @date 2019-06-18 17:18:42
 */
public interface SysFileService extends IService<SysFile> {

	/**
	 * 上传文件
	 * @param file 文件流
	 * @param fileName 文件名
	 * @param dir 存储目录
	 * @param groupId 文件分组ID
	 * @param type 文件类型
	 * @return 上传结果
	 */
	R uploadFile(MultipartFile file, String fileName, String dir, Long groupId, String type);

	/**
	 * 读取文件
	 * @param fileName 文件名称
	 * @param response 输出流
	 */
	void getFile(String fileName, HttpServletResponse response);

	/**
	 * 读取文件（支持inline预览）
	 * @param fileName 文件名称
	 * @param inline 是否内联预览
	 * @param response 输出流
	 */
	void getFile(String fileName, Boolean inline, HttpServletResponse response);

	/**
	 * 读取登录页公开品牌资源。仅允许被有效租户 Logo、背景图或二维码明确引用的文件。
	 * @param fileName 文件名
	 * @param response 输出流
	 */
	void getPublicBrandFile(String fileName, HttpServletResponse response);

	/**
	 * 为当前租户的私有文件生成短时效签名地址。
	 * @param fileName 文件名
	 * @param inline 是否内联预览
	 * @return 签名地址
	 */
	String createSignedUrl(String fileName, Boolean inline);

	/**
	 * 校验并读取短时效签名文件。
	 * @param fileName 文件名
	 * @param tenantId 签名绑定的租户
	 * @param expires 过期时间（Unix 秒）
	 * @param inline 是否内联预览
	 * @param signature 签名
	 * @param response 输出流
	 */
	void getSignedFile(String fileName, Long tenantId, long expires, Boolean inline, String signature,
			HttpServletResponse response);

	/**
	 * 根据文件ID删除文件
	 * @param id 文件ID
	 * @return 删除是否成功
	 */
	Boolean deleteFile(Long id);

	/**
	 * 查询文件组列表
	 * @param fileGroup SysFileGroup对象，用于筛选条件
	 * @return 包含文件组列表的Tree对象列表
	 */
	List<Tree<Long>> listFileGroup(SysFileGroup fileGroup);

	/**
	 * 添加或更新文件组
	 * @param fileGroup SysFileGroup对象，要添加或更新的文件组信息
	 * @return 添加或更新成功返回true，否则返回false
	 */
	Boolean saveOrUpdateGroup(SysFileGroup fileGroup);

	/**
	 * 删除文件组
	 * @param id 待删除文件组的ID
	 * @return 删除成功返回true，否则返回false
	 */
	Boolean deleteGroup(Long id);

	/**
	 * 移动文件组
	 * @param fileGroupDTO SysFileGroupDTO对象，要移动的文件组信息
	 * @return 移动成功返回true，否则返回false
	 */
	Boolean moveFileGroup(SysFileGroupDTO fileGroupDTO);

}
