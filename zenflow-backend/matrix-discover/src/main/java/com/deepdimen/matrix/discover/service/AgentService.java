package com.deepdimen.matrix.discover.service;

import com.deepdimen.matrix.discover.model.AgentDetail;
import com.deepdimen.matrix.discover.model.AgentResponse;

/**
 * AI Agent 服务
 *
 * @author caoyuan
 * @date 2024/09/27
 */
public interface AgentService {

	AgentResponse getAllAgents(String locale);

	AgentDetail getAgentByIdentifier(String identifier, String locale);

}
