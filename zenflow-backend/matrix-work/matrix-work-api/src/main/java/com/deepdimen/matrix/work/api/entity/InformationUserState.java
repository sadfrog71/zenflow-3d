package com.deepdimen.matrix.work.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TenantTable
@TableName("ai_information_user_state")
public class InformationUserState {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;
	private Long itemId;
	private Long orgId;
	private Long userId;
	private Integer readFlag;
	private Integer favoriteFlag;
	private Integer ignoredFlag;
	private LocalDateTime readAt;
	private LocalDateTime favoriteAt;
	private LocalDateTime ignoredAt;
	private LocalDateTime createTime;
	private LocalDateTime updateTime;
}
