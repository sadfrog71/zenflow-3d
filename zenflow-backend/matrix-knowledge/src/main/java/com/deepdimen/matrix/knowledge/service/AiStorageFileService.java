package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.knowledge.entity.AiStorageFileEntity;

import java.util.List;

/**
 * AI存储文件服务接口
 *
 * @author caoyuan
 * @date 2025/09/30
 */
public interface AiStorageFileService extends IService<AiStorageFileEntity> {

	/**
	 * 对文件进行向量化处理
	 * @param fileId 文件ID
	 */
	void embedFile(Long fileId);

	/**
	 * 通过文本向量搜索相似文件
	 * @param keyword 搜索关键词
	 * @return 相似文件列表
	 */
	List<AiStorageFileEntity> searchByVector(String keyword);

	/**
	 * 通过图片向量搜索相似文件（以图搜图）
	 * @param imageBase64 图片的Base64编码字符串
	 * @return 相似文件列表
	 */
	List<AiStorageFileEntity> searchByImage(String imageBase64);

}
