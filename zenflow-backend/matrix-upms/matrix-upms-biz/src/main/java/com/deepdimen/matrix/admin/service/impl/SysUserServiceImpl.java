/*
 *
 *      Copyright (c) 2018-2025, deepdimen All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 *  this list of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in the
 *  documentation and/or other materials provided with the distribution.
 *  Neither the name of the deepdimen.com developer nor the names of its
 *  contributors may be used to endorse or promote products derived from
 *  this software without specific prior written permission.
 *  Author: caoyuan (caoyuanchn@gmail.com)
 *
 */

package com.deepdimen.matrix.admin.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.admin.api.constant.UserStateEnum;
import com.deepdimen.matrix.admin.api.dto.RegisterUserDTO;
import com.deepdimen.matrix.admin.api.dto.UserDTO;
import com.deepdimen.matrix.admin.api.dto.UserInfo;
import com.deepdimen.matrix.admin.api.entity.SysDept;
import com.deepdimen.matrix.admin.api.entity.SysMenu;
import com.deepdimen.matrix.admin.api.entity.SysPost;
import com.deepdimen.matrix.admin.api.entity.SysRole;
import com.deepdimen.matrix.admin.api.entity.SysTenantUser;
import com.deepdimen.matrix.admin.api.entity.SysUser;
import com.deepdimen.matrix.admin.api.entity.SysUserDept;
import com.deepdimen.matrix.admin.api.entity.SysUserPost;
import com.deepdimen.matrix.admin.api.entity.SysUserRole;
import com.deepdimen.matrix.admin.mapper.SysDeptMapper;
import com.deepdimen.matrix.admin.mapper.SysTenantUserMapper;
import com.deepdimen.matrix.admin.mapper.SysUserDeptMapper;
import com.deepdimen.matrix.admin.mapper.SysUserMapper;
import com.deepdimen.matrix.admin.mapper.SysUserPostMapper;
import com.deepdimen.matrix.admin.mapper.SysUserRoleMapper;
import com.deepdimen.matrix.admin.service.SysDeptService;
import com.deepdimen.matrix.admin.service.SysMenuService;
import com.deepdimen.matrix.admin.service.SysPostService;
import com.deepdimen.matrix.admin.service.SysRoleService;
import com.deepdimen.matrix.admin.service.SysTenantService;
import com.deepdimen.matrix.admin.service.SysUserService;
import com.deepdimen.matrix.admin.api.entity.*;
import com.deepdimen.matrix.admin.api.vo.UserExcelVO;
import com.deepdimen.matrix.admin.api.vo.UserVO;
import com.deepdimen.matrix.admin.mapper.*;
import com.deepdimen.matrix.common.audit.annotation.Audit;
import com.deepdimen.matrix.common.core.constant.CacheConstants;
import com.deepdimen.matrix.common.core.constant.CommonConstants;
import com.deepdimen.matrix.common.core.constant.enums.LoginTypeEnum;
import com.deepdimen.matrix.common.core.exception.ErrorCodes;
import com.deepdimen.matrix.common.core.exception.CheckedException;
import com.deepdimen.matrix.common.core.util.MsgUtils;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.data.cache.RedisUtils;
import com.deepdimen.matrix.common.data.datascope.DataScope;
import com.deepdimen.matrix.common.data.resolver.ParamResolver;
import com.deepdimen.matrix.common.data.tenant.TenantBroker;
import com.deepdimen.matrix.common.excel.vo.ErrorMessage;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author caoyuan
 * @date 2017/10/31
 */
@Slf4j
@Service
@AllArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

	private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

	private final SysMenuService sysMenuService;

	private final SysRoleService sysRoleService;

	private final SysPostService sysPostService;

	private final SysDeptService sysDeptService;

	private final SysTenantService sysTenantService;

	private final SysUserRoleMapper sysUserRoleMapper;

	private final SysUserPostMapper sysUserPostMapper;

	private final SysUserDeptMapper sysUserDeptMapper;

	private final SysTenantUserMapper sysTenantUserMapper;

	private final SysLogMapper sysLogMapper;

	private final CacheManager cacheManager;

	private final SysDeptMapper sysDeptMapper;

	/**
	 * 保存用户信息
	 * @param userDto DTO 对象
	 * @return success/fail
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean saveUser(UserDTO userDto) {
		// 字符串字段 trim，避免首尾空格干扰唯一性查询和库内一致性
		trimUserStringFields(userDto);
		// 工号租户内唯一性校验（非空才校验）
		checkJobNumberUnique(userDto.getJobNumber(), null);
		SysUser sysUser = new SysUser();
		BeanUtils.copyProperties(userDto, sysUser);
		sysUser.setLockFlag(
				StrUtil.isBlank(userDto.getLockFlag()) ? UserStateEnum.NORMAL.getCode() : userDto.getLockFlag());
		sysUser.setCreateBy(userDto.getUsername());
		sysUser.setUpdateBy(userDto.getUsername());
		sysUser.setPassword(ENCODER.encode(userDto.getPassword()));
		sysUser.setPasswordModifyTime(LocalDateTime.now());
		baseMapper.insert(sysUser);
		// 保存用户岗位信息
		Optional.ofNullable(userDto.getPost()).ifPresent(posts -> {
			posts.stream().map(postId -> {
				SysUserPost userPost = new SysUserPost();
				userPost.setUserId(sysUser.getUserId());
				userPost.setPostId(postId);
				return userPost;
			}).forEach(sysUserPostMapper::insert);
		});

		// 如果角色为空，赋默认角色
		if (CollUtil.isEmpty(userDto.getRole())) {
			// 获取默认角色编码
			String defaultRole = ParamResolver.getStr("USER_DEFAULT_ROLE");
			// 默认角色
			SysRole sysRole = sysRoleService
				.getOne(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, defaultRole));
			userDto.setRole(Collections.singletonList(sysRole.getRoleId()));
		}

		// 插入用户角色关系表
		userDto.getRole().stream().map(roleId -> {
			SysUserRole userRole = new SysUserRole();
			userRole.setUserId(sysUser.getUserId());
			userRole.setRoleId(roleId);
			return userRole;
		}).forEach(sysUserRoleMapper::insert);

		// 插入用户部门信息
		List<Long> deptIdList = CollUtil.isNotEmpty(userDto.getDeptIds()) ? userDto.getDeptIds()
				: (Objects.nonNull(userDto.getDeptId()) ? Collections.singletonList(userDto.getDeptId()) : null);

		if (CollUtil.isNotEmpty(deptIdList)) {
			// 设置第一个部门为主部门
			sysUser.setDeptId(deptIdList.get(0));
			baseMapper.updateById(sysUser);

			// 插入用户部门关联关系
			deptIdList.forEach(deptId -> {
				SysUserDept sysUserDept = new SysUserDept();
				sysUserDept.setUserId(sysUser.getUserId());
				sysUserDept.setDeptId(deptId);
				sysUserDeptMapper.insert(sysUserDept);
			});
		}

		// 插入用户租户关系表
		SysTenantUser sysTenantUser = new SysTenantUser();
		sysTenantUser.setUserId(sysUser.getUserId());
		sysTenantUser.setTenantId(Objects.nonNull(SecurityUtils.getUser()) ? SecurityUtils.getUser().getTenantId()
				: userDto.getTenantId());
		sysTenantUserMapper.insert(sysTenantUser);
		return Boolean.TRUE;
	}

	/**
	 * 根据用户信息查询用户全部信息，包括角色和权限
	 * @param userDTO 用户信息DTO
	 * @return 包含用户角色和权限的用户信息对象
	 */
	@Override
	public R<UserInfo> getUserInfo(UserDTO userDTO) {
		UserVO dbUser = baseMapper.getUserVo(userDTO);

		if (dbUser == null) {
			return R.failed(MsgUtils.getMessage(ErrorCodes.SYS_USER_USERINFO_EMPTY, userDTO.getUsername()));
		}

		// 设置角色列表 （ID）
		UserInfo userInfo = new UserInfo();
		BeanUtils.copyProperties(dbUser, userInfo);
		// 多租户账号的 sys_user.tenant_id 可能是默认租户，登录目标租户由
		// sys_tenant_user 关系和当前请求上下文确定，Token 必须携带目标租户。
		if (userDTO.getTenantId() != null) {
			userInfo.setTenantId(userDTO.getTenantId());
		}
		// 设置权限列表（menu.permission）
		List<String> permissions = dbUser.getRoleList()
			.stream()
			.map(SysRole::getRoleId)
			.flatMap(roleId -> sysMenuService.findMenuByRoleId(roleId).stream())
			.map(SysMenu::getPermission)
			.filter(StrUtil::isNotEmpty)
			.toList();
		userInfo.setPermissions(permissions);

		// 查询最后登录时间
		SysLog lastLoginLog = sysLogMapper.selectOne(Wrappers.<SysLog>lambdaQuery()
			.eq(SysLog::getCreateBy, dbUser.getUsername())
			.eq(SysLog::getTitle, "登录成功")
			.eq(SysLog::getLogType, "0")
			.orderByDesc(SysLog::getCreateTime)
			.last("LIMIT 1"));
		if (lastLoginLog != null) {
			userInfo.setLastLoginTime(lastLoginLog.getCreateTime());
		}

		// 如果是登录成功以后查询租户信息并进行校验
		if (Objects.nonNull(SecurityUtils.getUser())) {
			Long updateTenantId = sysTenantService.getOrUpdateTenant();
			if (Objects.nonNull(updateTenantId)) {
				userInfo.setTenantId(updateTenantId);
			}
		}

		return R.ok(userInfo);
	}

	/**
	 * 分页查询用户信息（含有角色信息）
	 * @param page 分页对象
	 * @param userDTO 参数列表
	 * @return
	 */
	@Override
	public IPage getUsersWithRolePage(Page page, UserDTO userDTO) {
		userDTO.setTenantId(SecurityUtils.getUser().getTenantId());
		return baseMapper.getUserVosPage(page, userDTO, DataScope.of());
	}

	/**
	 * 通过ID查询用户信息
	 * @param id 用户ID
	 * @return 用户信息
	 */
	@Override
	public UserVO selectUserVoById(Long id) {
		return baseMapper.getUserVoById(id, SecurityUtils.getUser().getTenantId());
	}

	/**
	 * 删除用户
	 * @param ids 用户ID 列表
	 * @return Boolean
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean deleteUserByIds(Long[] ids) {
		// 删除 spring cache
		List<SysUser> userList = baseMapper.selectByIds(CollUtil.toList(ids));
		Cache cache = cacheManager.getCache(CacheConstants.USER_DETAILS);
		if (!userList.isEmpty()) {
			cache.clear();
		}

		// 删除用户角色
		sysUserRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().in(SysUserRole::getUserId, CollUtil.toList(ids)));
		sysUserDeptMapper.delete(Wrappers.<SysUserDept>lambdaQuery().in(SysUserDept::getUserId, CollUtil.toList(ids)));
		sysUserPostMapper.delete(Wrappers.<SysUserPost>lambdaQuery().in(SysUserPost::getUserId, CollUtil.toList(ids)));
		sysTenantUserMapper.delete(Wrappers.<SysTenantUser>lambdaQuery()
			.in(SysTenantUser::getUserId, CollUtil.toList(ids))
			.eq(SysTenantUser::getTenantId, SecurityUtils.getUser().getTenantId()));
		this.removeBatchByIds(CollUtil.toList(ids));
		return Boolean.TRUE;
	}

	@Override
	@CacheEvict(value = CacheConstants.USER_DETAILS, allEntries = true)
	public R<Boolean> updateUserInfo(UserDTO userDto) {
		Long userId = SecurityUtils.getUser().getId();
		userDto.setUsername(SecurityUtils.getUser().getUsername());
		SysUser sysUser = new SysUser();
		sysUser.setUserId(userId);
		// 字段 trim，与 saveUser 行为对齐
		trimUserStringFields(userDto);
		sysUser.setAvatar(userDto.getAvatar());
		sysUser.setNickname(userDto.getNickname());
		sysUser.setName(userDto.getName());
		sysUser.setEmail(userDto.getEmail());

		// 校验手机号
		if (StrUtil.isNotBlank(userDto.getPhone())) {
			Object codeObj = RedisUtils.get(
					CacheConstants.DEFAULT_CODE_KEY + LoginTypeEnum.SMS.getType() + StringPool.AT + userDto.getPhone());
			if (!StrUtil.equals(userDto.getCode(), codeObj.toString())) {
				return R.failed(MsgUtils.getMessage(ErrorCodes.SYS_APP_SMS_ERROR));
			}
			sysUser.setPhone(userDto.getPhone());
		}

		return R.ok(this.updateById(sysUser));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	@Audit(name = "用户更新", spel = "@sysUserMapper.selectById(#userDto.userId)")
	@CacheEvict(value = CacheConstants.USER_DETAILS, allEntries = true)
	public Boolean updateUser(UserDTO userDto) {
		// 字段 trim，与 saveUser 行为对齐
		trimUserStringFields(userDto);
		// 工号租户内唯一性校验（非空才校验，排除自身）
		checkJobNumberUnique(userDto.getJobNumber(), userDto.getUserId());
		// 更新用户表信息
		SysUser sysUser = new SysUser();
		BeanUtils.copyProperties(userDto, sysUser);
		sysUser.setUpdateTime(LocalDateTime.now());

		if (StrUtil.isNotBlank(userDto.getPassword())) {
			sysUser.setPassword(ENCODER.encode(userDto.getPassword()));
		}

		this.updateById(sysUser);

		// 更新用户角色表
		if (userDto.getRole() != null) {
			sysUserRoleMapper
				.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userDto.getUserId()));
			userDto.getRole().stream().map(roleId -> {
				SysUserRole userRole = new SysUserRole();
				userRole.setUserId(sysUser.getUserId());
				userRole.setRoleId(roleId);
				return userRole;
			}).forEach(SysUserRole::insert);
		}

		// 更新用户岗位表
		if (userDto.getPost() != null) {
			sysUserPostMapper
				.delete(Wrappers.<SysUserPost>lambdaQuery().eq(SysUserPost::getUserId, userDto.getUserId()));
			userDto.getPost().stream().map(postId -> {
				SysUserPost userPost = new SysUserPost();
				userPost.setUserId(sysUser.getUserId());
				userPost.setPostId(postId);
				return userPost;
			}).forEach(SysUserPost::insert);
		}

		// 更新用户部门表
		List<Long> deptIdList = CollUtil.isNotEmpty(userDto.getDeptIds()) ? userDto.getDeptIds()
				: (Objects.nonNull(userDto.getDeptId()) ? Collections.singletonList(userDto.getDeptId()) : null);

		if (CollUtil.isNotEmpty(deptIdList)) {
			// 设置第一个部门为主部门
			sysUser.setDeptId(deptIdList.get(0));
			this.updateById(sysUser);

			// 删除原有部门关联关系
			sysUserDeptMapper
				.delete(Wrappers.<SysUserDept>lambdaQuery().eq(SysUserDept::getUserId, userDto.getUserId()));

			// 插入新的部门关联关系
			deptIdList.forEach(deptId -> {
				SysUserDept sysUserDept = new SysUserDept();
				sysUserDept.setUserId(sysUser.getUserId());
				sysUserDept.setDeptId(deptId);
				sysUserDept.insert();
			});
		}

		// 同时清理用户失败次数的缓存
		String key = String.format("%s%s:%s", CacheConstants.GLOBALLY, CacheConstants.LOGIN_ERROR_TIMES,
				userDto.getUsername());
		RedisUtils.delete(key);

		return Boolean.TRUE;
	}

	/**
	 * 查询上级部门的用户信息
	 * @param username 用户名
	 * @return R
	 */
	@Override
	public List<SysUser> listAncestorUsers(String username) {
		SysUser sysUser = this.getOne(Wrappers.<SysUser>query().lambda().eq(SysUser::getUsername, username));

		// 查询所属部门
		List<SysDept> sysDeptList = sysDeptMapper.listDeptsByUserId(sysUser.getUserId(), sysUser.getTenantId());
		if (CollUtil.isEmpty(sysDeptList)) {
			return null;
		}

		// 查询用户部门ID
		List<Long> parentDeptList = sysDeptList.stream().map(SysDept::getParentId).toList();
		List<Long> userIdList = sysUserDeptMapper
			.selectList(Wrappers.<SysUserDept>lambdaQuery().in(SysUserDept::getDeptId, parentDeptList))
			.stream()
			.map(SysUserDept::getUserId)
			.toList();
		return this.list(Wrappers.<SysUser>lambdaQuery().in(SysUser::getUserId, userIdList));
	}

	/**
	 * 查询全部的用户
	 * @param userDTO 查询条件
	 * @param ids ids 用户列表
	 * @return list
	 */
	@Override
	public List<UserExcelVO> listUser(UserDTO userDTO, Long[] ids) {
		// 根据数据权限查询全部的用户信息
		userDTO.setTenantId(SecurityUtils.getUser().getTenantId());
		List<UserVO> voList = baseMapper.getUserVoListByScope(userDTO, ids, DataScope.of());
		return voList.stream().map(userVO -> {
			UserExcelVO excelVO = new UserExcelVO();
			BeanUtils.copyProperties(userVO, excelVO);
			// 转换角色名称
			String roleNameList = userVO.getRoleList()
				.stream()
				.map(SysRole::getRoleName)
				.collect(Collectors.joining(StrUtil.COMMA));
			excelVO.setRoleNameList(roleNameList);
			// 转化岗位名称
			String postNameList = userVO.getPostList()
				.stream()
				.map(SysPost::getPostName)
				.collect(Collectors.joining(StrUtil.COMMA));
			excelVO.setPostNameList(postNameList);
			// 转化部门名称
			String deptNameList = userVO.getDeptList()
				.stream()
				.map(SysDept::getName)
				.collect(Collectors.joining(StrUtil.COMMA));
			excelVO.setDeptNameList(deptNameList);
			return excelVO;
		}).toList();
	}

	/**
	 * excel 导入用户, 插入正确的 错误的提示行号
	 * @param excelVOList excel 列表数据
	 * @param bindingResult 错误数据
	 * @return ok fail
	 */
	@Override
	public R importUser(List<UserExcelVO> excelVOList, BindingResult bindingResult) {
		// 通用校验获取失败的数据
		List<ErrorMessage> errorMessageList = (List<ErrorMessage>) bindingResult.getTarget();
		List<SysDept> deptList = sysDeptService.list();
		List<SysRole> roleList = sysRoleService.list();
		List<SysPost> postList = sysPostService.list();

		// 执行数据插入操作 组装 UserDto
		// Excel 批内工号去重（记录已在本批次出现过的非空工号，若重复则拦截）
		Set<String> batchJobNumberSet = new HashSet<>();
		for (UserExcelVO excel : excelVOList) {
			// 字段 trim，避免首尾空格干扰后续的唯一性匹配
			excel.setUsername(StrUtil.trim(excel.getUsername()));
			excel.setPhone(StrUtil.trim(excel.getPhone()));
			excel.setName(StrUtil.trim(excel.getName()));
			excel.setNickname(StrUtil.trim(excel.getNickname()));
			excel.setEmail(StrUtil.trim(excel.getEmail()));
			excel.setJobNumber(StrUtil.trim(excel.getJobNumber()));
			// 个性化校验逻辑
			List<SysUser> userList = TenantBroker.noneAs(this::list);

			Set<String> errorMsg = new HashSet<>();
			// 校验用户名是否存在
			boolean exsitUserName = userList.stream()
				.anyMatch(sysUser -> excel.getUsername().equals(sysUser.getUsername()));

			if (exsitUserName) {
				errorMsg.add(MsgUtils.getMessage(ErrorCodes.SYS_USER_USERNAME_EXISTING, excel.getUsername()));
			}

			// 校验手机号是否存在
			boolean exsitPhone = userList.stream().anyMatch(sysUser -> excel.getPhone().equals(sysUser.getPhone()));
			if (exsitPhone) {
				errorMsg.add(MsgUtils.getMessage(ErrorCodes.SYS_USER_PHONE_EXISTING, excel.getPhone()));
			}

			// 校验工号租户内唯一：数据库已有 + Excel 批内重复（空值跳过）
			if (StrUtil.isNotBlank(excel.getJobNumber())) {
				boolean existJobNumber = userList.stream()
					.anyMatch(sysUser -> excel.getJobNumber().equals(sysUser.getJobNumber()));
				if (existJobNumber || !batchJobNumberSet.add(excel.getJobNumber())) {
					errorMsg.add(MsgUtils.getMessage(ErrorCodes.SYS_USER_JOBNUMBER_EXISTING, excel.getJobNumber()));
				}
			}

			// 判断输入的部门名称列表是否合法
			Optional<SysDept> deptOptional = deptList.stream()
				.filter(dept -> excel.getDeptNameList().equals(dept.getName()))
				.findFirst();
			if (deptOptional.isEmpty()) {
				errorMsg.add(MsgUtils.getMessage(ErrorCodes.SYS_DEPT_DEPTNAME_INEXISTENCE, excel.getDeptNameList()));
			}

			// 判断输入的角色名称列表是否合法
			List<String> roleNameList = StrUtil.split(excel.getRoleNameList(), StrUtil.COMMA);
			List<SysRole> roleCollList = roleList.stream()
				.filter(role -> roleNameList.stream().anyMatch(name -> role.getRoleName().equals(name)))
				.toList();

			if (roleCollList.size() != roleNameList.size()) {
				errorMsg.add(MsgUtils.getMessage(ErrorCodes.SYS_ROLE_ROLENAME_INEXISTENCE, excel.getRoleNameList()));
			}

			// 判断输入的部门名称列表是否合法
			List<String> postNameList = StrUtil.split(excel.getPostNameList(), StrUtil.COMMA);
			List<SysPost> postCollList = postList.stream()
				.filter(post -> postNameList.stream().anyMatch(name -> post.getPostName().equals(name)))
				.toList();

			if (postCollList.size() != postNameList.size()) {
				errorMsg.add(MsgUtils.getMessage(ErrorCodes.SYS_POST_POSTNAME_INEXISTENCE, excel.getPostNameList()));
			}

			// 数据合法情况
			if (CollUtil.isEmpty(errorMsg)) {
				insertExcelUser(excel, deptOptional, roleCollList, postCollList);
			}
			else {
				// 数据不合法情况
				errorMessageList.add(new ErrorMessage(excel.getLineNum(), errorMsg));
			}

		}

		if (CollUtil.isNotEmpty(errorMessageList)) {
			return R.failed(errorMessageList);
		}
		return R.ok(null, MsgUtils.getMessage(ErrorCodes.SYS_USER_IMPORT_SUCCEED));
	}

	/**
	 * 插入excel User
	 */
	private void insertExcelUser(UserExcelVO excel, Optional<SysDept> deptOptional, List<SysRole> roleCollList,
			List<SysPost> postCollList) {
		UserDTO userDTO = new UserDTO();
		userDTO.setUsername(excel.getUsername());
		userDTO.setPhone(excel.getPhone());
		userDTO.setNickname(excel.getNickname());
		userDTO.setName(excel.getName());
		userDTO.setEmail(excel.getEmail());
		userDTO.setLockFlag(excel.getLockFlag());
		// 批量导入初始密码为手机号
		userDTO.setPasswordModifyTime(LocalDateTime.now());
		userDTO.setPassword(userDTO.getPhone());
		// 根据部门名称查询部门ID
		userDTO.setDeptId(deptOptional.get().getDeptId());
		// 插入岗位名称
		List<Long> postIdList = postCollList.stream().map(SysPost::getPostId).toList();
		userDTO.setPost(postIdList);
		// 根据角色名称查询角色ID
		List<Long> roleIdList = roleCollList.stream().map(SysRole::getRoleId).toList();
		userDTO.setRole(roleIdList);
		// 插入用户
		this.saveUser(userDTO);
	}

	/**
	 * 注册用户 赋予用户默认角色
	 * @param userDto 用户信息
	 * @return success/false
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public R<Boolean> registerUser(RegisterUserDTO userDto) {
		// 判断用户名是否存在
		boolean usernameExists = TenantBroker
			.noneAs(() -> this.exists(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, userDto.getUsername())));
		if (usernameExists) {
			String message = MsgUtils.getMessage(ErrorCodes.SYS_USER_USERNAME_EXISTING, userDto.getUsername());
			return R.failed(message);
		}

		// 判断手机号是否存在
		boolean phoneExists = TenantBroker
			.noneAs(() -> this.exists(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getPhone, userDto.getPhone())));
		if (phoneExists) {
			String message = MsgUtils.getMessage(ErrorCodes.SYS_USER_PHONE_EXISTING, userDto.getPhone());
			return R.failed(message);
		}

		// 单独的用户避免越权
		UserDTO user = new UserDTO();
		BeanUtils.copyProperties(userDto, user);
		return R.ok(saveUser(user));
	}

	/**
	 * 锁定用户
	 * @param username 用户名
	 * @return 操作结果，包含是否成功
	 */
	@Override
	@CacheEvict(value = CacheConstants.USER_DETAILS, allEntries = true)
	public R<Boolean> lockUser(String username) {
		SysUser sysUser = baseMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));

		if (Objects.nonNull(sysUser)) {
			sysUser.setLockFlag(UserStateEnum.LOCK.getCode());
			baseMapper.updateById(sysUser);
		}
		return R.ok();
	}

	/**
	 * 修改用户密码
	 * @param userDto 用户数据传输对象，包含原密码和新密码
	 * @return 操作结果
	 * @CacheEvict 清除用户详情缓存
	 */
	@Override
	@CacheEvict(value = CacheConstants.USER_DETAILS, allEntries = true)
	public R changePassword(UserDTO userDto) {
		MatrixUser currentUser = SecurityUtils.getUser();
		if (currentUser == null || currentUser.getId() == null) {
			return R.failed("登录状态已失效");
		}
		if (userDto == null) {
			return R.failed("密码修改参数无效");
		}
		if (StrUtil.isEmpty(userDto.getPassword())) {
			return R.failed("原密码不能为空");
		}

		// sys_user 是共享身份表，多租户账号切换到非主租户后，当前租户行过滤
		// 可能屏蔽该用户的主记录。这里仅按认证上下文中的 userId 读取，不能使用
		// 前端传入的用户 ID，因此不会扩大可修改用户范围。
		SysUser user = TenantBroker.noneAs(() -> baseMapper.selectById(currentUser.getId()));
		if (user == null || StrUtil.isEmpty(user.getPassword())) {
			return R.failed("当前用户不存在或尚未设置密码");
		}

		if (!ENCODER.matches(userDto.getPassword(), user.getPassword())) {
			log.info("原密码错误，修改个人信息失败:{}", userDto.getUsername());
			return R.failed(MsgUtils.getMessage(ErrorCodes.SYS_USER_UPDATE_PASSWORDERROR));
		}

		if (StrUtil.isEmpty(userDto.getNewpassword1())) {
			return R.failed("新密码不能为空");
		}
		String password = ENCODER.encode(userDto.getNewpassword1());

		boolean updated = TenantBroker.noneAs(() -> this.update(Wrappers.<SysUser>lambdaUpdate()
			.set(SysUser::getPassword, password)
			.set(SysUser::getPasswordModifyTime, LocalDateTime.now())
			.eq(SysUser::getUserId, user.getUserId())));
		if (!updated) {
			return R.failed("密码修改失败");
		}
		return R.ok();
	}

	/**
	 * 解绑用户第三方登录账号
	 * @param type 登录类型，参见LoginTypeEnum枚举
	 * @return 操作结果，成功返回R.ok()，失败返回R.failed()
	 */
	@Override
	public R unbinding(String type) {
		MatrixUser user = SecurityUtils.getUser();
		LambdaUpdateWrapper<SysUser> wrapper = null;

		// 微信开放平台 （普通用户扫码登录）
		if (type.equals(LoginTypeEnum.WECHAT.getType())) {
			wrapper = Wrappers.<SysUser>lambdaUpdate()
				.set(SysUser::getWxOpenid, null)
				.eq(SysUser::getUserId, user.getId());
			// 码云登录 （方便申请）
		}
		else if (type.equals(LoginTypeEnum.GITEE.getType())) {
			wrapper = Wrappers.<SysUser>lambdaUpdate()
				.set(SysUser::getGiteeLogin, null)
				.eq(SysUser::getUserId, user.getId());
			// 企业微信登录
		}
		else if (type.equals(LoginTypeEnum.WEIXIN_CP.getType())) {
			wrapper = Wrappers.<SysUser>lambdaUpdate()
				.set(SysUser::getWxCpUserid, null)
				.eq(SysUser::getUserId, user.getId());
			// 钉钉登录
		}
		else if (type.equals(LoginTypeEnum.DINGTALK.getType())) {
			wrapper = Wrappers.<SysUser>lambdaUpdate()
				.set(SysUser::getWxDingUserid, null)
				.eq(SysUser::getUserId, user.getId());
		}

		if (Objects.isNull(wrapper)) {
			return R.failed("解绑账号类型不存在");
		}
		this.update(wrapper);
		return R.ok();
	}

	/**
	 * 检查用户密码是否正确
	 * @param username 用户名
	 * @param password 待验证的密码
	 * @return 验证结果，成功返回R.ok()，失败返回R.failed()
	 */
	@Override
	public R checkPassword(String username, String password) {
		SysUser condition = new SysUser();
		condition.setUsername(username);
		SysUser sysUser = this.getOne(new QueryWrapper<>(condition));

		if (!ENCODER.matches(password, sysUser.getPassword())) {
			log.info("原密码错误");
			return R.failed("密码输入错误");
		}
		else {
			return R.ok();
		}
	}

	/**
	 * 重置用户密码
	 * @param userDto 用户信息DTO，包含用户名、原密码和新密码等信息
	 * @return 返回操作结果，成功返回true，失败返回错误信息
	 */
	@Override
	@CacheEvict(value = CacheConstants.USER_DETAILS, allEntries = true)
	public R resetUserPassword(RegisterUserDTO userDto) {
		// 校验密码
		R checkedPassword = checkPassword(userDto.getUsername(), userDto.getPassword());
		if (!checkedPassword.isOk()) {
			return checkedPassword;
		}

		// 新密码校验
		if (StrUtil.equals(userDto.getPassword(), userDto.getNewpassword1())) {
			return R.failed("新旧密码不能相同");
		}

		// 重置密码
		String password = ENCODER.encode(userDto.getNewpassword1());
		this.update(Wrappers.<SysUser>lambdaUpdate()
			.set(SysUser::getPassword, password)
			.set(SysUser::getPasswordModifyTime, LocalDateTime.now())
			.set(SysUser::getPasswordExpireFlag, CommonConstants.STATUS_NORMAL)
			.eq(SysUser::getUsername, userDto.getUsername()));
		return R.ok();
	}

	/**
	 * 忘记用户密码
	 * @param userDto 用户信息DTO
	 * @param code 验证码
	 * @return 操作结果，成功返回true
	 */
	@Override
	public R<Boolean> forgetUserPassword(RegisterUserDTO userDto, String code) {
		if (StrUtil.isBlank(userDto.getPhone())) {
			return R.failed("非法参数");
		}

		String codeObj = RedisUtils
			.get(CacheConstants.DEFAULT_CODE_KEY + LoginTypeEnum.SMS.getType() + StringPool.AT + userDto.getPhone());
		if (!StrUtil.equals(codeObj, code)) {
			return R.failed("验证码错误");
		}

		TenantBroker.noneAs(() -> {
			String username = lambdaQuery().select(SysUser::getUsername)
				.eq(SysUser::getPhone, userDto.getPhone())
				.one()
				.getUsername();

			// 重置密码
			String password = ENCODER.encode(userDto.getNewpassword1());
			this.update(Wrappers.<SysUser>lambdaUpdate()
				.set(SysUser::getPassword, password)
				.set(SysUser::getPasswordModifyTime, LocalDateTime.now())
				.set(SysUser::getPasswordExpireFlag, CommonConstants.STATUS_NORMAL)
				.eq(SysUser::getPhone, userDto.getPhone()));
			cacheManager.getCache(CacheConstants.USER_DETAILS).clear();
			return username;
		});

		return R.ok();
	}

	@Override
	public List<Long> listUserIdByRoleIds(List<Long> roleIdList) {
		return sysUserRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery().in(SysUserRole::getRoleId, roleIdList))
			.stream()
			.map(SysUserRole::getUserId)
			.toList();
	}

	/**
	 * 根据部门ID列表获取用户ID列表接口
	 * @param deptIdList 部门ID列表
	 * @return List<Long> 返回结果对象，包含根据部门ID列表获取到的用户ID列表信息
	 */
	@Override
	public List<SysUser> listUserIdByDeptIds(List<Long> deptIdList) {
		List<Long> userList = sysUserDeptMapper
			.selectList(Wrappers.<SysUserDept>lambdaQuery().in(SysUserDept::getDeptId, deptIdList))
			.stream()
			.map(SysUserDept::getUserId)
			.toList();
		return baseMapper.selectList(Wrappers.<SysUser>lambdaQuery().in(SysUser::getUserId, userList));
	}

	/**
	 * 获取当前用户的部门列表
	 * @return 部门列表，主部门排在第一位
	 */
	@Override
	public List<SysDept> getUserDeptList() {
		Long userId = SecurityUtils.getUser().getId();

		// 1. 查询用户的所有部门关联
		List<SysUserDept> userDepts = sysUserDeptMapper
			.selectList(Wrappers.<SysUserDept>lambdaQuery().eq(SysUserDept::getUserId, userId));

		if (CollUtil.isEmpty(userDepts)) {
			return Collections.emptyList();
		}

		// 2. 提取部门ID列表
		List<Long> deptIds = userDepts.stream().map(SysUserDept::getDeptId).toList();

		// 3. 查询部门详细信息
		return sysDeptService.list(Wrappers.<SysDept>lambdaQuery().in(SysDept::getDeptId, deptIds));

	}

	/**
	 * 切换用户当前部门
	 * @param deptId 部门ID
	 * @return 操作结果
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public R<Boolean> updateUserDept(Long deptId) {
		MatrixUser user = SecurityUtils.getUser();
		if (user == null || deptId == null) {
			return R.failed("组织切换参数无效");
		}
		Long userId = user.getId();
		String username = user.getUsername();

		// 1. 客户端传入的组织ID不可信，必须校验用户-组织关系和组织有效性
		long membership = sysUserDeptMapper.selectCount(Wrappers.<SysUserDept>lambdaQuery()
			.eq(SysUserDept::getUserId, userId)
			.eq(SysUserDept::getDeptId, deptId));
		SysDept targetDept = sysDeptService.getById(deptId);
		if (membership == 0 || targetDept == null) {
			return R.failed("无权切换到该组织");
		}

		// 2. 更新用户当前组织
		this.update(Wrappers.<SysUser>lambdaUpdate().set(SysUser::getDeptId, deptId).eq(SysUser::getUserId, userId));

		// 3. 清除用户缓存，触发权限重新计算
		cacheManager.getCache(CacheConstants.USER_DETAILS).clear();

		return R.ok(true, "部门切换成功");
	}

	/**
	 * 对用户字符串字段做 trim，避免首尾空格干扰唯一性查询和后续登录匹配
	 */
	private void trimUserStringFields(UserDTO userDto) {
		userDto.setUsername(StrUtil.trim(userDto.getUsername()));
		userDto.setPhone(StrUtil.trim(userDto.getPhone()));
		userDto.setName(StrUtil.trim(userDto.getName()));
		userDto.setNickname(StrUtil.trim(userDto.getNickname()));
		userDto.setEmail(StrUtil.trim(userDto.getEmail()));
		// 工号仅 trim，保留空串：NOT_NULL 策略下 null=不更新(保留原值)，空串=清空，有值=更新
		userDto.setJobNumber(StrUtil.trim(userDto.getJobNumber()));
	}

	/**
	 * 工号租户内唯一性校验：仅当工号非空时校验，租户隔离由 MyBatis-Plus 拦截器自动处理。
	 * @param jobNumber 工号（已 trim/归一化）
	 * @param excludeUserId 需排除的用户 ID（更新时传自身 userId，新增时传 null）
	 */
	private void checkJobNumberUnique(String jobNumber, Long excludeUserId) {
		if (StrUtil.isBlank(jobNumber)) {
			return;
		}
		boolean exists = this.exists(Wrappers.<SysUser>lambdaQuery()
			.eq(SysUser::getJobNumber, jobNumber)
			.ne(Objects.nonNull(excludeUserId), SysUser::getUserId, excludeUserId));
		if (exists) {
			throw new CheckedException(MsgUtils.getMessage(ErrorCodes.SYS_USER_JOBNUMBER_EXISTING, jobNumber));
		}
	}

}
