package com.deepdimen.matrix.mp.entity.vo;

import com.deepdimen.matrix.mp.entity.WxAccountFans;
import com.deepdimen.matrix.mp.entity.WxAccountTag;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 粉丝Vo 对象
 *
 * @author caoyuan
 * @date 2022/1/5
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class WxAccountFansVo extends WxAccountFans {

	/**
	 * 标签名称列表
	 */
	private List<WxAccountTag> tagList;

}
