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
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.lang.tree.TreeNode;
import cn.hutool.core.lang.tree.TreeUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpUtil;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.S3Object;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.admin.api.dto.SysFileGroupDTO;
import com.deepdimen.matrix.admin.api.entity.SysFile;
import com.deepdimen.matrix.admin.api.entity.SysFileGroup;
import com.deepdimen.matrix.admin.api.entity.SysTenant;
import com.deepdimen.matrix.admin.mapper.SysFileGroupMapper;
import com.deepdimen.matrix.admin.mapper.SysFileMapper;
import com.deepdimen.matrix.admin.mapper.SysTenantMapper;
import com.deepdimen.matrix.admin.service.SysFileService;
import com.deepdimen.matrix.common.core.constant.CommonConstants;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.core.util.WebUtils;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.data.tenant.TenantBroker;
import com.deepdimen.matrix.common.file.core.FileProperties;
import com.deepdimen.matrix.common.file.core.FileTemplate;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 文件管理
 *
 * @author Luckly
 * @date 2019-06-18 17:18:42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysFileServiceImpl extends ServiceImpl<SysFileMapper, SysFile> implements SysFileService {

	private static final long SIGNED_URL_TTL_SECONDS = 300L;

	private static final long SIGNED_URL_MAX_FUTURE_SECONDS = 600L;

	private final FileTemplate fileTemplate;

	private final SysFileGroupMapper fileGroupMapper;

	private final FileProperties properties;

	private final SysTenantMapper sysTenantMapper;

	private String signingSecret;

	@Value("${matrix.file.signing-secret:}")
	void setSigningSecret(String signingSecret) {
		this.signingSecret = signingSecret;
	}

	/**
	 * 上传文件
	 * @param file 文件流
	 * @param fileName
	 * @param dir 文件夹
	 * @param groupId 分组ID
	 * @param type 类型
	 * @return
	 */
	@Override
	public R uploadFile(MultipartFile file, String fileName, String dir, Long groupId, String type) {
		// 如果客户端没有传入文件名，则使用UUID生成一个唯一的文件名
		if (StrUtil.isBlank(fileName)) {
			fileName = IdUtil.simpleUUID() + StrUtil.DOT + FileUtil.extName(file.getOriginalFilename());
		}
		final String protectedFileName = fileName;
		if (TenantBroker.noneAs(() -> baseMapper.selectCount(Wrappers.<SysFile>lambdaQuery()
			.eq(SysFile::getFileName, protectedFileName)
			.eq(SysFile::getType, "WORK_TASK"))) > 0) {
			return R.failed("任务证据文件不可覆盖");
		}
		Map<String, String> resultMap = new HashMap<>(4);
		resultMap.put("bucketName", properties.getBucketName());
		resultMap.put("fileName", fileName);

		resultMap.put("url", String.format("/admin/sys-file/oss/file?fileName=%s", fileName));

		try (InputStream inputStream = file.getInputStream()) {
			fileTemplate.putObject(properties.getBucketName(), dir, fileName, inputStream, file.getContentType());
			// 文件管理数据记录,收集管理追踪文件
			fileLog(file, dir, fileName, groupId, type);
		}
		catch (Exception e) {
			log.error("上传失败", e);
			return R.failed(e.getLocalizedMessage());
		}
		return R.ok(resultMap);
	}

	/**
	 * 获取文件内容并响应给客户端。
	 * @param fileName 要获取的文件名称
	 * @param response 响应对象，用于将文件内容发送给客户端
	 */
	@Override
	public void getFile(String fileName, HttpServletResponse response) {
		getFile(fileName, null, response);
	}

	@Override
	public void getFile(String fileName, Boolean inline, HttpServletResponse response) {
		String normalizedFileName = normalizeFileName(fileName);
		Long tenantId = TenantContextHolder.getTenantId();
		if (tenantId == null || StrUtil.isBlank(normalizedFileName)) {
			sendError(response, HttpStatus.BAD_REQUEST, "文件参数无效");
			return;
		}

		SysFile sysFile = baseMapper.selectOne(Wrappers.<SysFile>lambdaQuery()
			.eq(SysFile::getTenantId, tenantId)
			.eq(SysFile::getFileName, normalizedFileName), false);
		if (Objects.isNull(sysFile)) {
			log.warn("租户 {} 访问的文件不存在", tenantId);
			sendError(response, HttpStatus.NOT_FOUND, "文件不存在");
			return;
		}

		writeFile(sysFile, normalizedFileName, inline, response);
	}

	@Override
	public void getPublicBrandFile(String fileName, HttpServletResponse response) {
		String normalizedFileName = normalizeFileName(fileName);
		if (StrUtil.isBlank(normalizedFileName)) {
			sendError(response, HttpStatus.BAD_REQUEST, "文件参数无效");
			return;
		}

		LocalDateTime now = LocalDateTime.now();
		List<SysTenant> tenants = sysTenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery()
			.eq(SysTenant::getStatus, CommonConstants.STATUS_NORMAL)
			.eq(SysTenant::getDelFlag, CommonConstants.STATUS_NORMAL)
			.le(SysTenant::getStartTime, now)
			.ge(SysTenant::getEndTime, now));
		List<SysTenant> referencedTenants = tenants.stream()
			.filter(tenant -> isBrandReference(tenant, normalizedFileName))
			.toList();
		if (referencedTenants.isEmpty()) {
			log.warn("拒绝访问未公开的品牌文件");
			sendError(response, HttpStatus.NOT_FOUND, "文件不存在");
			return;
		}

		SysFile sysFile = null;
		for (SysTenant tenant : referencedTenants) {
			sysFile = TenantBroker.applyAs(tenant.getId(),
					ignored -> baseMapper.selectOne(Wrappers.<SysFile>lambdaQuery()
						.eq(SysFile::getTenantId, tenant.getId())
						.eq(SysFile::getFileName, normalizedFileName), false));
			if (sysFile != null) {
				break;
			}
		}
		if (Objects.isNull(sysFile)) {
			sendError(response, HttpStatus.NOT_FOUND, "文件不存在");
			return;
		}
		writeFile(sysFile, normalizedFileName, Boolean.TRUE, response);
	}

	@Override
	public String createSignedUrl(String fileName, Boolean inline) {
		ensureSigningConfigured();
		String normalizedFileName = normalizeFileName(fileName);
		Long tenantId = TenantContextHolder.getTenantId();
		if (tenantId == null || StrUtil.isBlank(normalizedFileName)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件参数无效");
		}
		SysFile sysFile = baseMapper.selectOne(Wrappers.<SysFile>lambdaQuery()
			.eq(SysFile::getTenantId, tenantId)
			.eq(SysFile::getFileName, normalizedFileName), false);
		if (sysFile == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
		}

		if ("WORK_TASK".equals(sysFile.getType())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "任务附件不支持通用签名链接");
		}

		boolean inlineValue = Boolean.TRUE.equals(inline);
		long expires = Instant.now().getEpochSecond() + SIGNED_URL_TTL_SECONDS;
		String signature = sign(tenantId, normalizedFileName, expires, inlineValue);
		return String.format(
				"/admin/sys-file/oss/signed-file?fileName=%s&tenantId=%s&expires=%s&inline=%s&signature=%s",
				URLUtil.encode(normalizedFileName), tenantId, expires, inlineValue, signature);
	}

	@Override
	public void getSignedFile(String fileName, Long tenantId, long expires, Boolean inline, String signature,
			HttpServletResponse response) {
		String normalizedFileName = normalizeFileName(fileName);
		boolean inlineValue = Boolean.TRUE.equals(inline);
		long now = Instant.now().getEpochSecond();
		if (StrUtil.isBlank(signingSecret) || tenantId == null || StrUtil.isBlank(normalizedFileName)
				|| StrUtil.isBlank(signature) || expires < now || expires > now + SIGNED_URL_MAX_FUTURE_SECONDS
				|| !constantTimeEquals(sign(tenantId, normalizedFileName, expires, inlineValue), signature)) {
			sendError(response, HttpStatus.NOT_FOUND, "文件不存在");
			return;
		}

		LocalDateTime currentTime = LocalDateTime.now();
		Long activeTenantCount = sysTenantMapper.selectCount(Wrappers.<SysTenant>lambdaQuery()
			.eq(SysTenant::getId, tenantId)
			.eq(SysTenant::getStatus, CommonConstants.STATUS_NORMAL)
			.eq(SysTenant::getDelFlag, CommonConstants.STATUS_NORMAL)
			.le(SysTenant::getStartTime, currentTime)
			.ge(SysTenant::getEndTime, currentTime));
		if (activeTenantCount == null || activeTenantCount == 0) {
			sendError(response, HttpStatus.NOT_FOUND, "文件不存在");
			return;
		}

		SysFile sysFile = TenantBroker.applyAs(tenantId,
				ignored -> baseMapper.selectOne(Wrappers.<SysFile>lambdaQuery()
					.eq(SysFile::getTenantId, tenantId)
					.eq(SysFile::getFileName, normalizedFileName), false));
		if (sysFile == null) {
			sendError(response, HttpStatus.NOT_FOUND, "文件不存在");
			return;
		}
		writeFile(sysFile, normalizedFileName, inlineValue, response);
	}

	private void writeFile(SysFile sysFile, String fileName, Boolean inline, HttpServletResponse response) {
		// Task attachments require current business authorization, including after
		// transfer.
		// A generic tenant file URL or old signed URL must never bypass that check.
		if ("WORK_TASK".equals(sysFile.getType())) {
			sendError(response, HttpStatus.FORBIDDEN, "请从任务详情读取附件");
			return;
		}

		try (S3Object s3Object = fileTemplate.getObject(sysFile.getBucketName(), sysFile.getDir(), fileName)) {
			if (Boolean.TRUE.equals(inline)) {
				String contentType = FileUtil.getMimeType(sysFile.getOriginal());
				response.setContentType(contentType != null ? contentType : "application/octet-stream");
				response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "inline");
			}
			else {
				response.setContentType("application/octet-stream; charset=UTF-8");
				response.addHeader(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=" + URLUtil.encode(sysFile.getOriginal()));
			}
			// 文件唯一hash
			response.addHeader(SysFile.Fields.hash, sysFile.getHash());
			IoUtil.copy(s3Object.getObjectContent(), response.getOutputStream());
		}
		catch (AmazonS3Exception e) {
			if (e.getStatusCode() == HttpStatus.NOT_FOUND.value()) {
				log.warn("对象存储文件不存在: bucket={}, fileName={}", sysFile.getBucketName(), fileName);
				if (!response.isCommitted()) {
					sendError(response, HttpStatus.NOT_FOUND, "文件不存在");
				}
				return;
			}
			log.error("对象存储文件读取异常", e);
			if (!response.isCommitted()) {
				sendError(response, HttpStatus.SERVICE_UNAVAILABLE, "文件存储服务暂不可用");
			}
		}
		catch (Exception e) {
			log.error("对象存储文件读取异常", e);
			if (!response.isCommitted()) {
				sendError(response, HttpStatus.SERVICE_UNAVAILABLE, "文件存储服务暂不可用");
			}
		}
	}

	private String normalizeFileName(String fileName) {
		if (StrUtil.isBlank(fileName)) {
			return null;
		}
		Map<String, String> stringMap = HttpUtil.decodeParamMap(fileName, Charset.defaultCharset());
		String urlFileName = MapUtil.getStr(stringMap, SysFile.Fields.fileName);
		String normalized = StrUtil.isNotBlank(urlFileName) ? urlFileName : fileName;
		normalized = StrUtil.trim(normalized);
		return StrUtil.isBlank(normalized) || normalized.length() > 100 ? null : normalized;
	}

	private boolean isBrandReference(SysTenant tenant, String fileName) {
		return referencesFile(tenant.getLogo(), fileName) || referencesFile(tenant.getBackground(), fileName)
				|| referencesFile(tenant.getMiniQr(), fileName);
	}

	private boolean referencesFile(String reference, String fileName) {
		return StrUtil.isNotBlank(reference) && Objects.equals(fileName, normalizeFileName(reference));
	}

	private void sendError(HttpServletResponse response, HttpStatus status, String message) {
		try {
			response.sendError(status.value(), message);
		}
		catch (Exception e) {
			log.error("写入文件接口错误响应失败", e);
		}
	}

	private void ensureSigningConfigured() {
		if (StrUtil.isBlank(signingSecret) || signingSecret.length() < 32) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "文件签名服务未正确配置");
		}
	}

	private String sign(Long tenantId, String fileName, long expires, boolean inline) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			String payload = tenantId + "\n" + fileName + "\n" + expires + "\n" + inline;
			return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("无法生成文件签名", e);
		}
	}

	private boolean constantTimeEquals(String expected, String actual) {
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
				actual.getBytes(StandardCharsets.US_ASCII));
	}

	/**
	 * 根据ID删除文件
	 * @param id 文件ID
	 * @return 删除是否成功，文件不存在时返回false
	 */
	@Override
	@SneakyThrows
	@Transactional(rollbackFor = Exception.class)
	public Boolean deleteFile(Long id) {
		SysFile file = this.getById(id);
		if (Objects.isNull(file)) {
			return Boolean.FALSE;
		}
		if ("WORK_TASK".equals(file.getType())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "任务证据不能从文件管理删除");
		}
		fileTemplate.removeObject(properties.getBucketName(), file.getFileName());
		return this.removeById(id);
	}

	/**
	 * 查询文件组列表
	 * @param fileGroup SysFileGroup对象，用于筛选条件
	 * @return 包含文件组树形结构列表的List对象
	 */
	@Override
	public List<Tree<Long>> listFileGroup(SysFileGroup fileGroup) {
		// 从数据库查询文件组列表
		List<TreeNode<Long>> treeNodeList = fileGroupMapper.selectList(Wrappers.query(fileGroup))
			.stream()
			.map(group -> {
				TreeNode<Long> treeNode = new TreeNode<>();
				treeNode.setName(group.getName());
				treeNode.setId(group.getId());
				treeNode.setParentId(group.getPid());
				return treeNode;
			})
			.toList();

		// 构建树形结构
		List<Tree<Long>> treeList = TreeUtil.build(treeNodeList, CommonConstants.MENU_TREE_ROOT_ID);
		return CollUtil.isEmpty(treeList) ? new ArrayList<>() : treeList;
	}

	/**
	 * 添加或更新文件组
	 * @param fileGroup SysFileGroup对象，要添加或更新的文件组信息
	 * @return 添加或更新成功返回true，否则返回false
	 */
	@Override
	public Boolean saveOrUpdateGroup(SysFileGroup fileGroup) {
		if (Objects.isNull(fileGroup.getId())) {
			// 插入文件组
			fileGroupMapper.insert(fileGroup);
		}
		else {
			// 更新文件组
			fileGroupMapper.updateById(fileGroup);
		}
		return Boolean.TRUE;
	}

	/**
	 * 删除文件组
	 * @param id 待删除文件组的ID
	 * @return 删除成功返回true，否则返回false
	 */
	@Override
	public Boolean deleteGroup(Long id) {
		// 根据ID删除文件组
		fileGroupMapper.deleteById(id);
		return Boolean.TRUE;
	}

	/**
	 * 移动文件组
	 * @param fileGroupDTO SysFileGroupDTO对象，要移动的文件组信息
	 * @return 移动成功返回true，否则返回false
	 */
	@Override
	public Boolean moveFileGroup(SysFileGroupDTO fileGroupDTO) {
		// 创建SysFile对象并设置groupId属性
		SysFile file = new SysFile();
		file.setGroupId(fileGroupDTO.getGroupId());

		// 根据IDS更新对应的SysFile记录
		baseMapper.update(file,
				Wrappers.<SysFile>lambdaQuery().in(SysFile::getId, CollUtil.toList(fileGroupDTO.getIds())));
		return Boolean.TRUE;
	}

	/**
	 * 文件管理数据记录，收集管理追踪文件
	 * @param file 上传的文件格式
	 * @param dir 文件夹
	 * @param fileName 文件名
	 * @param groupId 文件组ID
	 * @param type 文件类型
	 */
	@SneakyThrows
	private void fileLog(MultipartFile file, String dir, String fileName, Long groupId, String type) {
		// 创建SysFile对象并设置相关属性
		SysFile sysFile = new SysFile();
		sysFile.setFileName(fileName);

		String originalFilename;
		if (WebUtils.isMicro()) {
			originalFilename = file.getOriginalFilename();
		}
		else {
			// 对原始文件名进行编码转换
			originalFilename = new String(
					Objects.requireNonNull(file.getOriginalFilename()).getBytes(StandardCharsets.ISO_8859_1),
					StandardCharsets.UTF_8);
		}
		sysFile.setDir(dir);
		sysFile.setOriginal(originalFilename);
		sysFile.setFileSize(file.getSize());
		sysFile.setBucketName(properties.getBucketName());
		sysFile.setType(type);
		sysFile.setGroupId(groupId);

		sysFile.setHash(SecureUtil.md5(file.getInputStream()));
		// 调用save方法保存SysFile对象
		this.save(sysFile);
	}

}
