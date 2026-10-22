package com.deepdimen.matrix.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.knowledge.entity.AiDataTableEntity;
import com.deepdimen.matrix.knowledge.entity.AiDatasourceEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDataTableMapper;
import com.deepdimen.matrix.knowledge.service.AiDataTableService;
import com.deepdimen.matrix.knowledge.service.AiDatasourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.anyline.metadata.Table;
import org.anyline.proxy.ServiceProxy;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI数据表管理服务实现类
 * <p>
 * 同步和管理多数据源的表元数据 支持自动发现和更新表结构信息 为AI SQL生成提供表结构基础数据
 *
 * @author matrix
 * @date 2025-03-26 21:48:16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDataTableServiceImpl extends ServiceImpl<AiDataTableMapper, AiDataTableEntity>
		implements AiDataTableService {

	private final AiDatasourceService datasourceService;

	/**
	 * 同步所有数据源的表信息
	 * <p>
	 * 自动发现系统中所有数据源 遍历每个数据源获取表列表 更新或新增表元数据记录
	 * @return 同步成功返回true
	 */
	@Override
	public boolean sync(String dsName) {

		AiDatasourceEntity datasourceEntity = datasourceService
			.getOne(Wrappers.<AiDatasourceEntity>lambdaQuery().eq(AiDatasourceEntity::getName, dsName));

		// 注册数据源

		datasourceService.registerDataSource(dsName);
		try {
			// 获取数据源下的所有表
			List<Table> tableList = ServiceProxy.service(dsName).metadata().tables().values().stream().toList();

			for (Table table : tableList) {
				AiDataTableEntity tableEntity = new AiDataTableEntity();
				tableEntity.setDsName(dsName);
				tableEntity.setTableName(table.getName());
				tableEntity.setTableComment(table.getComment());
				tableEntity.setDsType(datasourceEntity.getDsType());

				// 检查表是否已存在，存在则更新注释，不存在则新增
				this.getOneOpt(Wrappers.<AiDataTableEntity>lambdaQuery()
					.eq(AiDataTableEntity::getDsName, dsName)
					.eq(AiDataTableEntity::getTableName, table.getName()), false).ifPresentOrElse(entity -> {
						entity.setTableComment(table.getComment());// 更新表注释
						baseMapper.updateById(entity);
					}, () -> baseMapper.insert(tableEntity));
			}

		}
		catch (Exception e) {
			log.warn("同步数据源 {} 数据表失败，系统跳过此数据源同步", dsName, e);
		}
		finally {
			// 销毁数据源
			datasourceService.destroyDataSource(dsName);
		}
		return true;
	}

}
