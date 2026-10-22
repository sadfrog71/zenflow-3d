package com.deepdimen.matrix.common.sequence.range.impl.name;

import com.deepdimen.matrix.common.sequence.range.BizName;
import lombok.AllArgsConstructor;

/**
 * @author caoyuan
 * @date 2019-05-26 根据传入返回bizname
 */
@AllArgsConstructor
public class DefaultBizName implements BizName {

	private String bizName;

	/**
	 * 生成空间名称
	 */
	@Override
	public String create() {
		return bizName;
	}

}
