package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.dto.AiTerminologyDTO;
import com.deepdimen.matrix.knowledge.entity.AiTerminologyEntity;

/**
 * AI术语服务接口
 *
 * @author caoyuan
 * @date 2025/09/20
 */
public interface AiTerminologyService extends IService<AiTerminologyEntity> {

	/**
	 * 获取术语详情
	 * @param aiTerminology 术语实体
	 * @return 术语详情
	 */
	R getDetails(AiTerminologyEntity aiTerminology);

	/**
	 * 保存AI术语
	 * @param aiTerminology AI术语DTO对象
	 * @return 保存后的AI术语对象
	 */
	R saveAiTerminology(AiTerminologyDTO aiTerminology);

	/**
	 * 更新AI术语
	 * @param aiTerminology AI术语数据传输对象
	 * @return 更新后的AI术语
	 */
	R updateAiTerminology(AiTerminologyDTO aiTerminology);

}
