package com.deepdimen.matrix.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.mail.MailAccount;
import cn.hutool.extra.mail.MailUtil;
import cn.hutool.extra.template.Template;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.TemplateUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.amazonaws.services.s3.model.S3Object;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.admin.api.entity.SysFile;
import com.deepdimen.matrix.admin.api.entity.SysMessageEntity;
import com.deepdimen.matrix.admin.api.entity.SysMessageRelationEntity;
import com.deepdimen.matrix.admin.api.entity.SysSystemConfigEntity;
import com.deepdimen.matrix.admin.api.entity.SysUser;
import com.deepdimen.matrix.admin.mapper.SysFileMapper;
import com.deepdimen.matrix.admin.mapper.SysMessageMapper;
import com.deepdimen.matrix.admin.mapper.SysMessageRelationMapper;
import com.deepdimen.matrix.admin.mapper.SysSystemConfigMapper;
import com.deepdimen.matrix.admin.mapper.SysUserMapper;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.deepdimen.matrix.admin.api.constant.SmsBizCodeEnum;
import com.deepdimen.matrix.admin.api.constant.SystemConfigTypeEnum;
import com.deepdimen.matrix.admin.api.dto.MessageEmailDTO;
import com.deepdimen.matrix.admin.api.dto.MessageHookDTO;
import com.deepdimen.matrix.admin.api.dto.MessageSmsDTO;
import com.deepdimen.matrix.admin.api.dto.SysLogDTO;
import com.deepdimen.matrix.admin.api.entity.*;
import com.deepdimen.matrix.admin.api.feign.RemoteLogService;
import com.deepdimen.matrix.admin.api.vo.OrgTreeVO;
import com.deepdimen.matrix.admin.api.vo.SysMessageReadVO;
import com.deepdimen.matrix.admin.api.vo.SysMessageVO;
import com.deepdimen.matrix.admin.mapper.*;
import com.deepdimen.matrix.admin.service.SysMessageService;
import com.deepdimen.matrix.common.core.constant.CacheConstants;
import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.core.constant.enums.LoginTypeEnum;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.common.core.exception.CheckedException;
import com.deepdimen.matrix.common.core.exception.ErrorCodes;
import com.deepdimen.matrix.common.core.util.MsgUtils;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.data.cache.RedisUtils;
import com.deepdimen.matrix.common.data.tenant.TenantBroker;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.file.core.FileTemplate;
import com.deepdimen.matrix.common.log.util.JacksonSensitiveFieldUtil;
import com.deepdimen.matrix.common.log.util.LogTypeEnum;
import com.deepdimen.matrix.common.log.util.SysLogUtils;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.dromara.oa.api.OaSender;
import org.dromara.oa.comm.entity.Request;
import org.dromara.oa.comm.enums.MessageType;
import org.dromara.oa.comm.enums.OaType;
import org.dromara.oa.core.byteTalk.config.ByteTalkConfig;
import org.dromara.oa.core.dingTalk.config.DingTalkConfig;
import org.dromara.oa.core.provider.factory.OaFactory;
import org.dromara.oa.core.weTalk.config.WeTalkConfig;
import org.dromara.sms4j.aliyun.config.AlibabaConfig;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.cloopen.config.CloopenConfig;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.dromara.sms4j.ctyun.config.CtyunConfig;
import org.dromara.sms4j.dingzhong.config.DingZhongConfig;
import org.dromara.sms4j.emay.config.EmayConfig;
import org.dromara.sms4j.huawei.config.HuaweiConfig;
import org.dromara.sms4j.jdcloud.config.JdCloudConfig;
import org.dromara.sms4j.netease.config.NeteaseConfig;
import org.dromara.sms4j.provider.config.BaseConfig;
import org.dromara.sms4j.qiniu.config.QiNiuConfig;
import org.dromara.sms4j.tencent.config.TencentConfig;
import org.dromara.sms4j.unisms.config.UniConfig;
import org.dromara.sms4j.yunpian.config.YunpianConfig;
import org.dromara.sms4j.zhutong.config.ZhutongConfig;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 站内信息
 *
 * @author matrix
 * @date 2023-10-25 13:37:25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysMessageServiceImpl extends ServiceImpl<SysMessageMapper, SysMessageEntity>
		implements SysMessageService {

	@org.springframework.beans.factory.annotation.Autowired(required = false)
	private com.deepdimen.matrix.admin.mobilepush.MobilePushOutboxServiceImpl pushOutbox;

	/**
	 * 支持短信服务商
	 */
	private static final Class[] SUPPORT_CONFIG_CLAZZ = { AlibabaConfig.class, CloopenConfig.class, CtyunConfig.class,
			EmayConfig.class, HuaweiConfig.class, JdCloudConfig.class, NeteaseConfig.class, TencentConfig.class,
			UniConfig.class, YunpianConfig.class, ZhutongConfig.class, DingZhongConfig.class, QiNiuConfig.class };

	private final RemoteLogService remoteLogService;

	private final SysSystemConfigMapper sysSystemConfigMapper;

	private final SysMessageRelationMapper relationMapper;

	private final SysUserMapper userMapper;

	private final SysFileMapper fileMapper;

	private final FileTemplate fileTemplate;

	/**
	 * 发送信息
	 * @param id id
	 * @return true/false
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean sendMessage(Long id) {
		// 更新信息状态
		SysMessageEntity sysMessage = new SysMessageEntity();
		sysMessage.setSendFlag(YesNoEnum.YES.getCode());
		baseMapper.update(sysMessage, Wrappers.<SysMessageEntity>lambdaQuery().eq(SysMessageEntity::getId, id));
		if (pushOutbox != null)
			pushOutbox.enqueue(TenantContextHolder.getTenantId(), id);
		return Boolean.TRUE;
	}

	/**
	 * 保存消息及其关联关系
	 * @param sysMessage 消息对象
	 * @return 是否保存成功
	 */
	@Transactional(rollbackFor = Exception.class)
	public Boolean saveOrUpdateMessage(SysMessageVO sysMessage) {
		SysMessageEntity messageEntity = new SysMessageEntity();
		BeanUtils.copyProperties(sysMessage, messageEntity);

		if (Objects.isNull(sysMessage.getId())) {
			baseMapper.insert(messageEntity);
		}
		else {
			baseMapper.updateById(sysMessage);
		}

		// 删除原有用户
		relationMapper.delete(Wrappers.<SysMessageRelationEntity>lambdaQuery()
			.eq(SysMessageRelationEntity::getMsgId, messageEntity.getId()));

		List<OrgTreeVO> userList = sysMessage.getUserList();
		if (CollUtil.isNotEmpty(userList)) {
			userList.forEach(user -> {
				SysMessageRelationEntity relationEntity = new SysMessageRelationEntity();
				relationEntity.setMsgId(messageEntity.getId());
				relationEntity.setUserId(user.getId());
				relationMapper.insert(relationEntity);
			});
		}
		else {
			userMapper.selectList(Wrappers.emptyWrapper()).stream().map(SysUser::getUserId).forEach(userId -> {
				SysMessageRelationEntity relationEntity = new SysMessageRelationEntity();
				relationEntity.setMsgId(messageEntity.getId());
				relationEntity.setUserId(userId);
				relationMapper.insert(relationEntity);
			});
		}
		if (pushOutbox != null)
			pushOutbox.enqueue(TenantContextHolder.getTenantId(), messageEntity.getId());
		return Boolean.TRUE;
	}

	/**
	 * 获取用户的信息列表
	 * @return list
	 */
	@Override
	public IPage<SysMessageVO> pageUserMessage(Page page, SysMessageVO sysMessage) {
		MPJLambdaWrapper<SysMessageEntity> wrapper = new MPJLambdaWrapper<SysMessageEntity>()
			.selectAll(SysMessageEntity.class)
			.select(SysMessageRelationEntity::getReadFlag)
			.leftJoin(SysMessageRelationEntity.class, SysMessageRelationEntity::getMsgId, SysMessageEntity::getId)
			.eq(SysMessageRelationEntity::getUserId, SecurityUtils.getUser().getId())
			.eq(SysMessageEntity::getSendFlag, YesNoEnum.YES.getCode())
			.eq(StrUtil.isNotBlank(sysMessage.getCategory()), SysMessageEntity::getCategory, sysMessage.getCategory())
			.eq(StrUtil.isNotBlank(sysMessage.getReadFlag()), SysMessageRelationEntity::getReadFlag,
					sysMessage.getReadFlag())
			.eq(StrUtil.isNotBlank(sysMessage.getSourceType()), SysMessageEntity::getSourceType,
					sysMessage.getSourceType())
			.eq(StrUtil.isNotBlank(sysMessage.getEventType()), SysMessageEntity::getEventType,
					sysMessage.getEventType())
			.and(condition -> condition.isNull(SysMessageEntity::getExpireTime)
				.or()
				.gt(SysMessageEntity::getExpireTime, LocalDateTime.now()))
			.orderByDesc(SysMessageEntity::getSort)
			.orderByDesc(SysMessageEntity::getCreateTime);
		return baseMapper.selectJoinPage(page, SysMessageVO.class, wrapper);
	}

	/**
	 * 读取信息
	 * @param id id
	 * @return true/false
	 */
	@Override
	public Boolean readMessage(Long id) {
		if (getUserMessage(id) == null) {
			return Boolean.FALSE;
		}
		SysMessageRelationEntity relationEntity = new SysMessageRelationEntity();
		relationEntity.setReadFlag(YesNoEnum.YES.getCode());
		relationMapper.update(relationEntity,
				Wrappers.<SysMessageRelationEntity>lambdaQuery()
					.eq(SysMessageRelationEntity::getMsgId, id)
					.eq(SysMessageRelationEntity::getUserId, SecurityUtils.getUser().getId()));
		return Boolean.TRUE;
	}

	@Override
	public SysMessageVO getUserMessage(Long id) {
		Long tenantId = TenantContextHolder.getTenantId();
		if (id == null || tenantId == null || SecurityUtils.getUser() == null) {
			return null;
		}
		MPJLambdaWrapper<SysMessageEntity> wrapper = new MPJLambdaWrapper<SysMessageEntity>()
			.selectAll(SysMessageEntity.class)
			.select(SysMessageRelationEntity::getReadFlag)
			.innerJoin(SysMessageRelationEntity.class, SysMessageRelationEntity::getMsgId, SysMessageEntity::getId)
			.eq(SysMessageEntity::getId, id)
			.eq(SysMessageEntity::getTenantId, tenantId)
			.eq(SysMessageRelationEntity::getTenantId, tenantId)
			.eq(SysMessageRelationEntity::getUserId, SecurityUtils.getUser().getId())
			.eq(SysMessageEntity::getSendFlag, YesNoEnum.YES.getCode())
			.and(condition -> condition.isNull(SysMessageEntity::getExpireTime)
				.or()
				.gt(SysMessageEntity::getExpireTime, LocalDateTime.now()));
		return baseMapper.selectJoinOne(SysMessageVO.class, wrapper);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean readAllMessages(String category) {
		List<Long> messageIds = baseMapper
			.selectList(Wrappers.<SysMessageEntity>lambdaQuery()
				.eq(SysMessageEntity::getSendFlag, YesNoEnum.YES.getCode())
				.eq(StrUtil.isNotBlank(category), SysMessageEntity::getCategory, category)
				.and(condition -> condition.isNull(SysMessageEntity::getExpireTime)
					.or()
					.gt(SysMessageEntity::getExpireTime, LocalDateTime.now())))
			.stream()
			.map(SysMessageEntity::getId)
			.toList();
		if (messageIds.isEmpty()) {
			return Boolean.TRUE;
		}
		SysMessageRelationEntity relationEntity = new SysMessageRelationEntity();
		relationEntity.setReadFlag(YesNoEnum.YES.getCode());
		relationMapper.update(relationEntity,
				Wrappers.<SysMessageRelationEntity>lambdaQuery()
					.eq(SysMessageRelationEntity::getUserId, SecurityUtils.getUser().getId())
					.eq(SysMessageRelationEntity::getReadFlag, YesNoEnum.NO.getCode())
					.in(SysMessageRelationEntity::getMsgId, messageIds));
		return Boolean.TRUE;
	}

	/**
	 * 获取信息
	 * @param id id
	 * @return messageVO
	 */
	@Override
	public SysMessageVO getMessage(Long id) {
		SysMessageEntity messageEntity = baseMapper.selectById(id);
		SysMessageVO SysMessageVO = new SysMessageVO();
		BeanUtils.copyProperties(messageEntity, SysMessageVO);

		// 全体通知
		if (messageEntity.getAllFlag().equals(YesNoEnum.YES.getCode())) {
			return SysMessageVO;
		}
		List<Long> userIdList = relationMapper
			.selectList(Wrappers.<SysMessageRelationEntity>lambdaQuery().eq(SysMessageRelationEntity::getMsgId, id))
			.stream()
			.map(SysMessageRelationEntity::getUserId)
			.toList();

		if (CollUtil.isNotEmpty(userIdList)) {
			List<OrgTreeVO> orgNodeUserVoList = userMapper.selectByIds(userIdList).stream().map(user -> {
				OrgTreeVO nodeUserVo = new OrgTreeVO();
				nodeUserVo.setId(user.getUserId());
				nodeUserVo.setAvatar(user.getAvatar());
				nodeUserVo.setName(user.getName());
				return nodeUserVo;
			}).toList();

			SysMessageVO.setUserList(orgNodeUserVoList);
		}

		return SysMessageVO;
	}

	/**
	 * 查询用户阅读情况
	 * @param page 分页
	 * @param messageId 消息ID
	 * @param name 姓名
	 * @return page
	 */
	@Override
	public Page pageUserRead(Page page, Long messageId, String name) {
		MPJLambdaWrapper<SysMessageRelationEntity> wrapper = new MPJLambdaWrapper<SysMessageRelationEntity>()
			.selectAll(SysMessageRelationEntity.class)
			.select(SysUser::getUsername, SysUser::getName, SysUser::getUserId)
			.select(SysMessageEntity::getTitle)
			.leftJoin(SysUser.class, SysUser::getUserId, SysMessageRelationEntity::getUserId)
			.leftJoin(SysMessageEntity.class, SysMessageEntity::getId, SysMessageRelationEntity::getMsgId)
			.eq(SysMessageRelationEntity::getMsgId, messageId)
			.like(StrUtil.isNotBlank(name), SysUser::getName, name);
		return relationMapper.selectJoinPage(page, SysMessageReadVO.class, wrapper);
	}

	/**
	 * 发送手机验证码
	 * @param mobile 手机号码
	 * @param registered 是否已注册
	 * @return 发送结果
	 */
	@Override
	public R<Boolean> sendSmsCode(String mobile, boolean registered) {
		List<SysUser> userList = TenantBroker
			.noneAs(() -> userMapper.selectList(Wrappers.<SysUser>query().lambda().eq(SysUser::getPhone, mobile)));

		if (registered && CollUtil.isEmpty(userList)) {
			log.info("手机号未注册:{}", mobile);
			return R.ok(Boolean.FALSE, MsgUtils.getMessage(ErrorCodes.SYS_APP_PHONE_UNREGISTERED, mobile));
		}

		String codeObj = RedisUtils
			.get(CacheConstants.DEFAULT_CODE_KEY + LoginTypeEnum.SMS.getType() + StringPool.AT + mobile);

		if (StrUtil.isNotBlank(codeObj)) {
			log.info("手机号验证码未过期:{}，{}", mobile, codeObj);
			return R.ok(Boolean.FALSE, MsgUtils.getMessage(ErrorCodes.SYS_APP_SMS_OFTEN));
		}

		String code = RandomUtil.randomNumbers(Integer.parseInt(SecurityConstants.CODE_SIZE));
		log.info("手机号生成验证码成功:{},{}", mobile, code);
		RedisUtils.set(CacheConstants.DEFAULT_CODE_KEY + LoginTypeEnum.SMS.getType() + StringPool.AT + mobile, code,
				SecurityConstants.CODE_TIME);

		// 发送
		return this.sendSms(MessageSmsDTO.builder()
			.mobile(mobile)
			.biz(SmsBizCodeEnum.SMS_NORMAL_CODE.getCode())
			.param(code)
			.build());
	}

	/**
	 * 发送短信
	 * @param messageSmsDTO 消息 短信 DTO
	 * @return {@link R }
	 */
	@Override
	public R sendSms(MessageSmsDTO messageSmsDTO) {

		// 根据业务编码获取短信通道配置
		List<SysSystemConfigEntity> configEntityList = sysSystemConfigMapper
			.selectList(Wrappers.<SysSystemConfigEntity>lambdaQuery()
				.eq(SysSystemConfigEntity::getConfigType, SystemConfigTypeEnum.SMS.getValue())
				.eq(SysSystemConfigEntity::getConfigStatus, YesNoEnum.YES.getCode())
				.eq(SysSystemConfigEntity::getConfigKey, messageSmsDTO.getBizCode()));
		if (CollUtil.isEmpty(configEntityList)) {
			return R.failed("发送失败，短信通道配置不存在");
		}

		SysSystemConfigEntity configEntity = configEntityList.get(0);
		JSONObject configMap = JSONUtil.parseObj(configEntity.getConfigValue());

		// 组装SMS4J 的发送对象
		SmsBlend smsBlend = SmsFactory.getSmsBlend(messageSmsDTO.getBizCode());
		if (Objects.isNull(smsBlend)) {
			Class targetClass = Arrays.stream(SUPPORT_CONFIG_CLAZZ)
				.filter(config -> config.getSimpleName()
					.toLowerCase()
					.contains(configMap.getStr(MessageSmsDTO.Fields.supplier)))
				.findFirst()
				.orElseThrow(() -> new CheckedException("发送失败，短信通道配置不存在"));
			BaseConfig baseConfig = (BaseConfig) JSONUtil.toBean(configEntity.getConfigValue(), targetClass);

			// 如果渠道扩展参数不为空则增加特性化参数
			if (StrUtil.isNotBlank(configMap.getStr(MessageSmsDTO.Fields.extParams))) {
				JSONObject extParams = configMap.getJSONObject(MessageSmsDTO.Fields.extParams);
				BeanUtil.copyProperties(extParams, baseConfig);
			}

			baseConfig.setConfigId(configEntity.getConfigKey());
			SmsFactory.createSmsBlend(baseConfig);
			smsBlend = SmsFactory.getSmsBlend(messageSmsDTO.getBizCode());
		}

		// 异步发送短信
		SysLogDTO sysLog = SysLogUtils.getSysLog();
		Long start = System.currentTimeMillis();
		sysLog.setTenantId(TenantContextHolder.getTenantId());
		for (String mobile : messageSmsDTO.getMobiles()) {
			smsBlend.sendMessageAsync(mobile, configMap.getStr(MessageSmsDTO.Fields.templateId),
					messageSmsDTO.getParams(), smsResponse -> {
						log.info("发送短信结果:{}", smsResponse);
						sysLog.setTitle(String.format("发送短信 %s", mobile));
						sysLog.setLogType(LogTypeEnum.NORMAL.getType());
						sysLog.setServiceId(SystemConfigTypeEnum.SMS.getValue());
						sysLog.setParams(JacksonSensitiveFieldUtil.writeValueAsString(messageSmsDTO));
						sysLog.setTime(System.currentTimeMillis() - start);
						sysLog.setException(JacksonSensitiveFieldUtil.writeValueAsString(smsResponse));
						remoteLogService.saveLog(sysLog);
					});
		}
		return R.ok();
	}

	/**
	 * 发送钩子消息
	 * @param messageHookDTO 消息挂钩 DTO
	 * @return {@link R }
	 */
	@Override
	public R sendHook(MessageHookDTO messageHookDTO) {

		// 根据业务编码获取短信通道配置
		List<SysSystemConfigEntity> configEntityList = sysSystemConfigMapper
			.selectList(Wrappers.<SysSystemConfigEntity>lambdaQuery()
				.eq(SysSystemConfigEntity::getConfigType, SystemConfigTypeEnum.WEBHOOK.getValue())
				.eq(SysSystemConfigEntity::getConfigStatus, YesNoEnum.YES.getCode())
				.eq(SysSystemConfigEntity::getConfigKey, messageHookDTO.getBizCode()));
		if (CollUtil.isEmpty(configEntityList)) {
			return R.failed("发送失败，短信通道配置不存在");
		}

		SysSystemConfigEntity configEntity = configEntityList.get(0);
		JSONObject configMap = JSONUtil.parseObj(configEntity.getConfigValue());
		String supplier = configMap.getStr("supplier");

		// 根据配置创建服务实例并注册
		OaSender alarm = OaFactory.getSmsOaBlend(messageHookDTO.getBizCode());
		if (Objects.isNull(alarm)) {
			if (StrUtil.equalsAnyIgnoreCase(OaType.DING_TALK.toString(), supplier)) {
				DingTalkConfig dingTalkConfig = new DingTalkConfig();
				dingTalkConfig.setConfigId(messageHookDTO.getBizCode());
				dingTalkConfig.setSign(configMap.getStr("sign"));
				dingTalkConfig.setTokenId(configMap.getStr("tokenId"));
				OaFactory.createAndRegisterOaSender(dingTalkConfig);
			}
			if (StrUtil.equalsAnyIgnoreCase(OaType.BYTE_TALK.toString(), supplier)) {
				ByteTalkConfig byteTalkConfig = new ByteTalkConfig();
				byteTalkConfig.setConfigId(messageHookDTO.getBizCode());
				byteTalkConfig.setSign(configMap.getStr("sign"));
				byteTalkConfig.setTokenId(configMap.getStr("tokenId"));
				OaFactory.createAndRegisterOaSender(byteTalkConfig);
			}

			if (StrUtil.equalsAnyIgnoreCase(OaType.WE_TALK.toString(), supplier)) {
				WeTalkConfig weTalkConfig = new WeTalkConfig();
				weTalkConfig.setConfigId(messageHookDTO.getBizCode());
				weTalkConfig.setSign(configMap.getStr("sign"));
				weTalkConfig.setTokenId(configMap.getStr("tokenId"));
				OaFactory.createAndRegisterOaSender(weTalkConfig);
			}
			alarm = OaFactory.getSmsOaBlend(messageHookDTO.getBizCode());
		}

		Request request = new Request();
		request.setContent(messageHookDTO.getMessageContent());
		request.setTitle(messageHookDTO.getMessageTitle());
		request.setMessageUrl(messageHookDTO.getMessageUrl());
		request.setPicUrl(messageHookDTO.getPicUrl());
		request.setIsNoticeAll(messageHookDTO.isNoticeAll());
		request.setPhoneList(messageHookDTO.getPhoneList());

		SysLogDTO sysLog = SysLogUtils.getSysLog();
		sysLog.setTenantId(TenantContextHolder.getTenantId());
		Long start = System.currentTimeMillis();
		alarm.senderAsync(request,
				MessageType.valueOf(String.format("%s_%s", supplier, messageHookDTO.getMessageType()).toUpperCase()),
				response -> {
					log.info("发送Hook结果:{}", response);
					sysLog.setTitle(String.format("发送Hook %s", messageHookDTO.getBizCode()));
					sysLog.setLogType(LogTypeEnum.NORMAL.getType());
					sysLog.setServiceId(SystemConfigTypeEnum.WEBHOOK.getValue());
					sysLog.setParams(JacksonSensitiveFieldUtil.writeValueAsString(messageHookDTO));
					sysLog.setTime(System.currentTimeMillis() - start);
					sysLog.setException(JacksonSensitiveFieldUtil.writeValueAsString(response));
					remoteLogService.saveLog(sysLog);
				});
		return R.ok();
	}

	/**
	 * 发送邮件
	 * @param messageEmailDTO 留言内容 电子邮件 DTO
	 * @return {@link R }
	 */
	@SneakyThrows
	@Override
	public R sendEmail(MessageEmailDTO messageEmailDTO) {
		// 根据业务编码获取短信通道配置
		List<SysSystemConfigEntity> configEntityList = sysSystemConfigMapper
			.selectList(Wrappers.<SysSystemConfigEntity>lambdaQuery()
				.eq(SysSystemConfigEntity::getConfigType, SystemConfigTypeEnum.EMAIL.getValue())
				.eq(SysSystemConfigEntity::getConfigStatus, YesNoEnum.YES.getCode())
				.eq(SysSystemConfigEntity::getConfigKey, messageEmailDTO.getBizCode()));
		if (CollUtil.isEmpty(configEntityList)) {
			return R.failed("发送失败，通道配置不存在");
		}
		SysSystemConfigEntity configEntity = configEntityList.get(0);
		JSONObject configMap = JSONUtil.parseObj(configEntity.getConfigValue());

		List<File> tempFileList = new ArrayList<>();
		if (CollUtil.isNotEmpty(messageEmailDTO.getAttachmentList())) {
			TenantContextHolder.setTenantSkip();
			for (String fileName : messageEmailDTO.getAttachmentList()) {
				SysFile sysFile = fileMapper
					.selectOne(Wrappers.<SysFile>lambdaQuery().eq(SysFile::getFileName, fileName));
				S3Object s3Object = fileTemplate.getObject(sysFile.getBucketName(), sysFile.getDir(), fileName);
				File file = FileUtil.file(FileUtil.getTmpDirPath(), sysFile.getOriginal());
				tempFileList.add(file);
				IoUtil.copy(s3Object.getObjectContent(), FileUtil.getOutputStream(file));
			}
		}

		MailAccount account = new MailAccount();
		account.setHost(configMap.getStr("smtpServer"));
		account.setPort(configMap.getInt("port"));
		account.setAuth(configMap.getBool("isAuth"));
		account.setSslEnable(configMap.getBool("isSSL"));
		account.setFrom(configMap.getStr("username"));
		account.setUser(configMap.getStr("username"));
		account.setPass(configMap.getStr("password"));

		// 渲染模板
		TemplateEngine engine = TemplateUtil.createEngine(new TemplateConfig());
		Template template = engine.getTemplate(configMap.getStr("html"));
		String result = template.render(messageEmailDTO.getHtmlValues());

		Long start = System.currentTimeMillis();
		MailUtil.send(account, messageEmailDTO.getMailAddress(), messageEmailDTO.getCcList(),
				messageEmailDTO.getBccList(), messageEmailDTO.getTitle(), result, true,
				tempFileList.toArray(new File[0]));
		Long end = System.currentTimeMillis();
		// 写入日志
		SysLogDTO sysLog = SysLogUtils.getSysLog();
		sysLog.setTitle(String.format("发送邮件 %s", messageEmailDTO.getTitle()));
		sysLog.setLogType(LogTypeEnum.NORMAL.getType());
		sysLog.setServiceId(SystemConfigTypeEnum.EMAIL.getValue());
		sysLog.setParams(JacksonSensitiveFieldUtil.writeValueAsString(messageEmailDTO));
		sysLog.setTenantId(TenantContextHolder.getTenantId());
		sysLog.setTime(end - start);
		remoteLogService.saveLog(sysLog);
		// 删除临时文件
		tempFileList.forEach(FileUtil::del);
		return R.ok();
	}

	/**
	 * 列出 Hook 业务代码
	 * @param messageHookDTO 消息钩子 DTO
	 * @return {@link R }
	 */
	@Override
	public R listHookBizCode(MessageHookDTO messageHookDTO) {
		List<SysSystemConfigEntity> configEntityList = sysSystemConfigMapper
			.selectList(Wrappers.<SysSystemConfigEntity>lambdaQuery()
				.eq(SysSystemConfigEntity::getConfigType, messageHookDTO.getMessageType())
				.eq(SysSystemConfigEntity::getConfigStatus, YesNoEnum.YES.getCode()));
		return R.ok(configEntityList);
	}

}
