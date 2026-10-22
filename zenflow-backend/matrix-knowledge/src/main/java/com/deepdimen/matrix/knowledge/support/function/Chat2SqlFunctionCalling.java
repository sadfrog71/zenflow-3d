package com.deepdimen.matrix.knowledge.support.function;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.knowledge.dto.BaseAiRequest;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.entity.AiDataEntity;
import com.deepdimen.matrix.knowledge.service.AiChartGenerateService;
import com.deepdimen.matrix.knowledge.service.AiDataService;
import com.deepdimen.matrix.knowledge.service.AiDatasourceService;
import com.deepdimen.matrix.knowledge.support.annotation.FieldPrompt;
import com.deepdimen.matrix.knowledge.support.provider.ChatMemoryAdvisorProvider;
import com.deepdimen.matrix.knowledge.support.util.ChatMessageContextHolder;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import dev.langchain4j.internal.Utils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import org.anyline.entity.DataSet;
import org.springframework.stereotype.Component;

/**
 * Chat2SQL函数调用实现类 该类实现了将自然语言转换为SQL语句并执行的功能 支持SQL查询结果的表格展示和图表生成
 *
 * @author caoyuan
 * @date 2024/07/30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Chat2SqlFunctionCalling implements FunctionCalling<SqlRequest> {

	private final ChatMemoryAdvisorProvider chatMemoryAdvisorProvider;

	private final AiChartGenerateService chartGenerateService;

	private final AiDataService aiDataService;

	private final com.deepdimen.matrix.knowledge.support.dataquery.DataQueryPilotGate dataQueryPilotGate;

	private final AiDatasourceService datasourceService;

	/** Only the authenticated controlled chat advertises non-SQL response choices. */
	@Data
	@EqualsAndHashCode(callSuper = false)
	public static class ConversationRequest extends SqlRequest {

		@FieldPrompt("回答类型：QUERY查新数据；DATASET_GUIDANCE介绍/推荐问题；RESULT_EXPLANATION解释已有结果；CLARIFICATION针对性澄清。非QUERY不得生成SQL。")
		private String responseType;

		@FieldPrompt("非QUERY时的自然语言回答或澄清问题；只依据当前选中数据集的元数据或明确引用的已有结果，不编造统计数据。QUERY时留空。")
		private String answer;

		@FieldPrompt("仅RESULT_EXPLANATION填写同一会话内已成功查询结果的queryId，其他类型留空，不得编造。")
		private String sourceQueryId;

	}

	public R<String> executeFromChat(ChatMessageDTO message, dev.langchain4j.agent.tool.ToolExecutionRequest request) {
		return executeFromChat(message, request, null);
	}

	public R<String> executeFromChat(ChatMessageDTO message, dev.langchain4j.agent.tool.ToolExecutionRequest request,
			com.deepdimen.matrix.knowledge.support.dataquery.DataQuerySqlRepair.Advisor advisor) {
		if (message.getDataQueryContext() == null)
			return execute(request);
		// Only the authenticated DatabaseChatRule creates this server-only context.
		// Ignore the model's messageKey, tenant, identity and data-source hints.
		if (!message.getDataQueryContext()
			.identity()
			.equals(com.deepdimen.matrix.knowledge.support.dataquery.DataQuerySecurity.identity()))
			return R.failed("UNAUTHENTICATED");
		var service = dataQueryPilotGate.aiChatService();
		var sql = cn.hutool.json.JSONUtil.toBean(request.arguments(), ConversationRequest.class);
		var result = service.respond(message.getDataQueryContext(), sql.getResponseType(), sql.getSql(),
				sql.getAnswer(), sql.getSourceQueryId(), advisor);
		message.setDataQuery(result.getDataQuery());
		ChatMessageContextHolder.set(message);
		if ("ANSWERED".equals(result.getDataQuery().get("status")))
			return R.ok(result.getMessage());
		if (!"SUCCEEDED".equals(result.getDataQuery().get("status")))
			return R.failed(result.getMessage());
		boolean detailPreview = result.getDataQuery().get("resultPreview") instanceof java.util.Map<?, ?> preview
				&& !"GROUPS".equals(preview.get("kind"));
		if (!detailPreview && sql.isChartOutput() && sql.getChartType() >= 1 && sql.getChartType() <= 3
				&& ((Number) result.getDataQuery().get("rowCount")).intValue() > 0) {
			try {
				service.attachChart(message.getDataQueryContext(), Integer.toString(sql.getChartType()));
				message.getExtDetails().setChartId("dq-" + message.getDataQueryContext().queryId());
				message.getExtDetails().setChartType(Integer.toString(sql.getChartType()));
				chartGenerateService.generateChart(message, result.getMessage());
			}
			catch (RuntimeException unavailable) {
				// Chart scheduling failure must not turn a completed query into a
				// retry/re-execution.
				message.getExtDetails().setChartId(null);
				message.getExtDetails().setChartType(null);
				result.getDataQuery()
					.put("warnings", java.util.List.of("AI_SQL_SEMANTICS_REQUIRE_REVIEW", "CHART_UNAVAILABLE"));
			}
		}
		return R.ok(result.getMessage());
	}

	/**
	 * 是否在前端展示此函数 返回false表示该函数不会在前端功能列表中显示
	 * @return boolean false-不展示
	 */
	@Override
	public boolean showFunction() {
		return false;
	}

	/**
	 * 检查参数是否正确 主要验证SQL语句的合法性和安全性 只允许执行SELECT查询语句，拒绝其他操作语句
	 * @param sqlRequest SQL请求对象，包含要执行的SQL语句
	 * @param userDetails 当前用户信息
	 * @param extDetails 扩展详情，包含数据源等信息
	 * @return R<String> 参数检查结果
	 */
	@Override
	public R<String> checkParams(SqlRequest sqlRequest, MatrixUser userDetails, ChatMessageDTO.ExtDetails extDetails) {
		try {
			dataQueryPilotGate.rejectLegacy(extDetails == null ? null : extDetails.getDataId());
		}
		catch (com.deepdimen.matrix.knowledge.support.dataquery.DataQueryException e) {
			return R.failed(e.getMessage());
		}

		log.info("Chat2SQL 结果：{}", sqlRequest);

		Statement statement = null;
		try {
			statement = CCJSqlParserUtil.parse(sqlRequest.getSql());
		}
		catch (JSQLParserException e) {
			return R.failed(StrUtil.format("SQL: {} 解析失败", sqlRequest.getSql()));
		}
		if (statement instanceof Select) {
			sqlRequest.setSql(statement.toString());
			return R.ok();
		}

		return R.failed(StrUtil.format("SQL: {} 解析失败，安全问题不支持非查询语句", sqlRequest.getSql()));
	}

	/**
	 * 处理SQL执行请求 1. 执行SQL查询 2. 处理查询结果 3. 如果需要，生成图表 4. 将结果转换为Markdown表格格式返回
	 * @param sqlRequest SQL请求对象
	 * @param userDetails 用户详细信息
	 * @param extDetails 扩展详细信息，包含数据源ID等
	 * @return R<String> 执行结果，包含Markdown格式的表格数据
	 */
	@Override
	public R<String> handle(SqlRequest sqlRequest, MatrixUser userDetails, ChatMessageDTO.ExtDetails extDetails) {
		try {
			dataQueryPilotGate.rejectLegacy(extDetails == null ? null : extDetails.getDataId());
		}
		catch (com.deepdimen.matrix.knowledge.support.dataquery.DataQueryException e) {
			return R.failed(e.getMessage());
		}
		AiDataEntity dataEntity = aiDataService.getById(extDetails.getDataId());

		R<DataSet> execSQLResult = datasourceService.executeQuery(dataEntity.getDsName(), sqlRequest.getSql());

		// 处理异常情况
		if (!execSQLResult.isOk()) {
			// 异常情况下，清空多轮会话,避免后续丢失精度
			ChatMessageDTO chatMessageDTO = ChatMessageContextHolder.get();
			chatMemoryAdvisorProvider.get(chatMessageDTO.getConversationId()).clear();

			return R.failed(StrUtil.format("""
					执行后结果为空，不进行表格和图表渲染。
					```sql
					{}
					```
					请进行条件调整后重试，或联系管理员进行字段评估
					```bash
					{}
					```
					""", sqlRequest.getSql(), execSQLResult.getMsg()));
		}

		DataSet dataRows = execSQLResult.getData();

		if (CollUtil.isEmpty(dataRows)) {
			// 为空判定为：异常情况，清空多轮会话,避免后续丢失精度
			ChatMessageDTO chatMessageDTO = ChatMessageContextHolder.get();
			chatMemoryAdvisorProvider.get(chatMessageDTO.getConversationId()).clear();

			return R.failed(StrUtil.format("""
					执行后结果为空，不进行表格和图表渲染。
					```sql
					{}
					```
					请进行条件调整后重试，或联系管理员进行字段评估
					""", sqlRequest.getSql()));
		}

		String markdownTable = PromptBuilder.toMarkdownTable(dataRows);
		log.info("SQL 执行结果：{}", markdownTable);

		// 如果建议图表输出,则执行异步的图表计算逻辑
		if (sqlRequest.isChartOutput() && sqlRequest.getChartType() > 0 && sqlRequest.getChartType() <= 3) {
			ChatMessageDTO chatMessageDTO = ChatMessageContextHolder.get();
			chatMessageDTO.getExtDetails().setChartType(String.valueOf(sqlRequest.getChartType()));
			chatMessageDTO.getExtDetails().setChartId(Utils.randomUUID());
			ChatMessageContextHolder.set(chatMessageDTO);
			chartGenerateService.generateChart(chatMessageDTO, markdownTable);
		}

		return R.ok(markdownTable);

	}

}

/**
 * SQL请求参数类 用于接收AI生成的SQL查询相关参数
 */
@Data
@EqualsAndHashCode(callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
class SqlRequest extends BaseAiRequest {

	/**
	 * AI生成的可执行SQL语句
	 */
	@FieldPrompt("可以执行的SQL语句")
	private String sql;

	/**
	 * 是否建议使用图表形式展示查询结果
	 */
	@FieldPrompt(value = "是否推荐用图表的形式输出结果")
	private boolean chartOutput;

	/**
	 * 推荐的图表类型 1: 折线图(Line chart) 2: 饼图(Pie chart) 3: 柱状图(Bar chart)
	 */
	@FieldPrompt(value = "如果推荐图表输出的话，更适合哪种图表? 1. Line chart 2. Pie chart 3. Bar chart")
	private int chartType;

}
