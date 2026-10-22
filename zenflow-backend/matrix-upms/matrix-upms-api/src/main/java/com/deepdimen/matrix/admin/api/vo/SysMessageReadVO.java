package com.deepdimen.matrix.admin.api.vo;

import com.deepdimen.matrix.admin.api.entity.SysMessageRelationEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息阅读vo
 *
 * @author caoyuan
 * @date 2023/10/26
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class SysMessageReadVO extends SysMessageRelationEntity {

	/**
	 * 文章标题
	 */
	private String title;

	/**
	 * 用户名
	 */
	private String username;

	/**
	 * 姓名
	 */
	private String name;

}
