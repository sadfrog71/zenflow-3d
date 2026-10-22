package com.deepdimen.matrix.knowledge.support.flow.model.processor;

import cn.hutool.core.lang.Dict;
import cn.hutool.extra.template.Template;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.TemplateUtil;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.service.AiDatasourceService;
import com.deepdimen.matrix.knowledge.support.flow.constants.FlowConstant;
import com.deepdimen.matrix.knowledge.support.flow.constants.NodeTypeConstants;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowContextHolder;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowException;
import com.deepdimen.matrix.knowledge.support.flow.model.AiNodeDefinition;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiDbNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.anyline.entity.DataSet;
import org.springframework.stereotype.Component;

/**
 * 数据库节点执行器 用于执行SQL查询的节点处理器 支持使用模板变量动态构建SQL语句
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@Slf4j
@Component(NodeTypeConstants.DB)
@RequiredArgsConstructor
public class DbNodeExecutor extends AbstractNodeProcessor {

	/**
	 * 模板引擎，用于处理SQL中的变量替换
	 */
	public static final TemplateEngine engine = TemplateUtil.createEngine(new TemplateConfig());

	/**
	 * 远程表信息服务，用于执行SQL查询
	 */

	private final AiDatasourceService datasourceService;

	/**
	 * 执行数据库节点的具体逻辑 1. 验证节点配置 2. 获取输入参数 3. 使用模板引擎替换SQL中的变量 4. 调用远程服务执行SQL 5. 返回查询结果
	 * @param node 节点定义
	 * @param context 执行上下文
	 * @return 包含查询结果的字典
	 */
	@Override
	protected Dict doExecute(AiNodeDefinition node, FlowContextHolder context) {
		try {
			// 节点配置判断
			AiDbNode config = node.getDbParams();
			if (config == null || config.getSql() == null) {
				throw FlowException.invalidParam("数据库节点配置无效");
			}
			// 获取输入参数
			Dict variables = getInputVariables(node, context);

			// 使用模板引擎替换SQL中的变量占位符
			Template template = engine.getTemplate(config.getSql());
			String sql = template.render(variables);

			// 调用远程服务执行SQL查询
			R<DataSet> execSQLResult = datasourceService.executeQuery(config.getDbId(), sql);
			if (execSQLResult.isOk()) {
				return Dict.create()
					.set(FlowConstant.RESULT, execSQLResult.getData().getRows())
					.set(FlowConstant.SQL, sql)
					.set(FlowConstant.TIMESTAMP, System.currentTimeMillis());
			}
			return null;
		}
		catch (Exception e) {
			log.error("数据库节点[{}]执行失败", node.getId(), e);
			throw FlowException.nodeError(node.getId(), "[数据库节点] -> " + e.getMessage());
		}
	}

}
