package com.deepdimen.matrix.knowledge.service.impl;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.entity.AiDatasourceEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDatasourceMapper;
import com.deepdimen.matrix.knowledge.service.AiDatasourceService;
import com.deepdimen.matrix.knowledge.support.constant.DatabaseEnums;
import lombok.extern.slf4j.Slf4j;
import org.anyline.data.datasource.DataSourceHolder;
import org.anyline.entity.DataSet;
import org.anyline.proxy.CacheProxy;
import org.anyline.proxy.ServiceProxy;
import org.anyline.util.ConfigTable;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

/**
 * 数据源表
 *
 * @author matrix
 * @date 2025-09-21 17:25:49
 */
@Slf4j
@Service
public class AiDatasourceServiceImpl extends ServiceImpl<AiDatasourceMapper, AiDatasourceEntity>
		implements AiDatasourceService, InitializingBean {

	/**
	 * 验证数据源连接是否有效
	 * @param aiDatasource 数据源实体
	 * @return 连接有效返回true，否则返回false
	 */
	@Override
	public R validateConnection(AiDatasourceEntity aiDatasource) {

		DruidDataSource druidDataSource = new DruidDataSource();
		druidDataSource.setUrl(aiDatasource.getUrl());
		druidDataSource.setDriverClassName(DatabaseEnums.getDriverClassNameByDbType(aiDatasource.getDsType()));
		druidDataSource.setUsername(aiDatasource.getUsername());
		druidDataSource.setPassword(aiDatasource.getPassword());
		druidDataSource.setConnectionErrorRetryAttempts(0);
		druidDataSource.setBreakAfterAcquireFailure(true);
		druidDataSource.setFailFast(true); // 启用快速失败模式

		try {
			DataSourceHolder.reg(aiDatasource.getName(), druidDataSource);
		}
		catch (Exception e) {
			log.warn("注册数据源失败", e);
			return R.ok(false, "链接数据源失败");
		}

		try {
			boolean validitied = DataSourceHolder.hit(aiDatasource.getName());
			DataSourceHolder.destroy(aiDatasource.getName());
			return R.ok(validitied);
		}
		catch (Exception e) {
			log.warn("销毁数据源失败", e);
			return R.failed(false, e.getLocalizedMessage());
		}
	}

	/**
	 * 注册数据源
	 * @param dsName 数据源名称
	 */
	@Override
	public boolean registerDataSource(String dsName) {
		CacheProxy.clear();
		AiDatasourceEntity datasourceEntity = baseMapper
			.selectOne(Wrappers.<AiDatasourceEntity>lambdaQuery().eq(AiDatasourceEntity::getName, dsName));
		DruidDataSource druidDataSource = new DruidDataSource();
		druidDataSource.setUrl(datasourceEntity.getUrl());
		druidDataSource.setDriverClassName(DatabaseEnums.getDriverClassNameByDbType(datasourceEntity.getDsType()));
		druidDataSource.setUsername(datasourceEntity.getUsername());
		druidDataSource.setPassword(datasourceEntity.getPassword());
		druidDataSource.setBreakAfterAcquireFailure(true);
		druidDataSource.setFailFast(true); // 启用快速失败模式

		try {
			DataSourceHolder.reg(datasourceEntity.getName(), druidDataSource);
			return true;
		}
		catch (Exception e) {
			log.warn("注册数据源失败", e);
		}
		return false;
	}

	/**
	 * 销毁数据源
	 * @param dsName 数据源名称
	 * @return 是否销毁成功
	 */
	@Override
	public boolean destroyDataSource(String dsName) {
		try {
			DataSourceHolder.destroy(dsName);
			return true;
		}
		catch (Exception e) {
			log.warn("销毁数据源失败", e);
		}
		return false;
	}

	/**
	 * 执行查询
	 * @param dsName 数据源名称
	 * @param sql SQL语句
	 * @return 查询结果
	 */
	@Override
	public R<DataSet> executeQuery(String dsName, String sql) {
		boolean exists = DataSourceHolder.exists(dsName);
		if (!exists) {
			registerDataSource(dsName);
		}

		try {
			return R.ok(ServiceProxy.service(dsName).querys(sql));
		}
		catch (Exception e) {
			return R.failed(e.getLocalizedMessage());
		}
	}

	/**
	 * <a href="http://doc.anyline.org/aa/3c_14011">集成mybatis动态数据源</a>
	 * @throws Exception 例外
	 */
	@Override
	public void afterPropertiesSet() throws Exception {
		ConfigTable.KEEP_ADAPTER = 0;
	}

}
