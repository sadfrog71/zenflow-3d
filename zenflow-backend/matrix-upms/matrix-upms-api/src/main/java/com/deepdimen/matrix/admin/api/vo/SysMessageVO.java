package com.deepdimen.matrix.admin.api.vo;

import com.deepdimen.matrix.admin.api.entity.SysMessageEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息发送实体
 *
 * @author caoyuan
 * @date 2023/10/25
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class SysMessageVO extends SysMessageEntity {

	/**
	 * 是否已读
	 */
	private String readFlag;

	/**
	 * 发送用户列表
	 */
	private List<OrgTreeVO> userList = new ArrayList<>();

}
