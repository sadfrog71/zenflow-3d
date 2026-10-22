package com.deepdimen.matrix.work.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TenantTable
@TableName("ai_information_delivery")
public class InformationDelivery {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;
	private Long itemId;
	private Long orgId;
	private LocalDate deliveryDate;
	private Integer sortOrder;
	private String status;
	private LocalDateTime publishedAt;
	private LocalDateTime withdrawnAt;
	private LocalDateTime createTime;
	private LocalDateTime updateTime;
}
