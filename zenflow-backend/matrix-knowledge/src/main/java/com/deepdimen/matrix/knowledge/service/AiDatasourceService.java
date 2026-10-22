package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.entity.AiDatasourceEntity;
import org.anyline.entity.DataSet;

/**
 * AI数据源服务接口
 *
 * @author caoyuan
 * @date 2025/09/22
 */
public interface AiDatasourceService extends IService<AiDatasourceEntity> {

	/**
	 * 验证数据源连接是否有效
	 * @param aiDatasource 数据源实体
	 * @return 连接有效返回true，否则返回false
	 */
	R validateConnection(AiDatasourceEntity aiDatasource);

	/**
	 * 注册数据源
	 * @param dsName 数据源名称
	 */
	boolean registerDataSource(String dsName);

	/**
	 * 销毁数据源
	 * @param dsName 数据源名称
	 * @return 是否销毁成功
	 */
	boolean destroyDataSource(String dsName);

	/**
	 * 执行查询
	 * @param dsName 数据源名称
	 * @param sql SQL语句
	 * @return 查询结果
	 */
	R<DataSet> executeQuery(String dsName, String sql);

}
