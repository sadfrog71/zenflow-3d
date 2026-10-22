# MATRIX Knowledge Framework Reference

## Invelle 产品适用范围

在此产品目录中先遵守[根规则](../../AGENTS.md)和[服务端规则](../AGENTS.md)。下文保留衍智云框架内部技术参考，不代表 Invelle 要启用所有模型、工作流、MCP、向量库或 Nacos 服务，也不是新增依赖的要求；版本与配置以实际 POM 和产品环境为准。

- 优先复用现有文档解析、切片、模型、嵌入与存储适配器；产品的发布快照、资料授权、索引版本、引用回查和撤回由 `zenflow/zenflow-biz` 的产品适配负责。
- 公共参考与私人记忆保持范围隔离；向量召回必须回查获准原文，不连接旧数据库、集合、对象桶或模型密钥。
- `FlowContextHolder` 约定仅用于本模块 Flow 节点。Invelle 自然对话沿既有助手运行与托管 Skill 执行，不因本参考说明改走另一套工作流。
- 下文历史版本、默认 Milvus、Nacos 等描述不作为产品当前部署事实；环境入口与已验证适配器以根规则和部署配置为准。

本文件为编码 Agent 在本模块工作时提供指引。

## Module Overview

**matrix-knowledge** is the AI knowledge base and intelligent processing module of the MATRIX platform. It provides
comprehensive AI capabilities including:

- **Knowledge Base Management** - Document parsing, vectorization, and intelligent retrieval (RAG)
- **AI Workflows** - Visual AI workflow orchestration with drag-and-drop nodes
- **Multi-modal AI** - LLM chat, image generation, OCR, TTS, video generation
- **MCP Integration** - Model Context Protocol support for external tool integration

## Technology Stack

- **Java 17** with Spring Boot 3.5.8
- **AI Frameworks**: LangChain4j 1.8.0, Spring AI 1.1.0
- **Document Processing**: Apache Tika 2.9.4, POI 5.4.0
- **Workflow Engine**: Custom AI Flow engine (reactive streaming)
- **Database**: MyBatis-Plus with MySQL
- **Vector Storage**: Milvus/Pgvector/Chroma support

## Project Structure

```
matrix-knowledge/
├── controller/           # REST API controllers
├── service/             # Business logic services
│   └── impl/           # Service implementations
├── entity/             # Database entities (MyBatis-Plus)
├── mapper/             # MyBatis mappers
├── dto/                # Data transfer objects
├── config/             # Spring configuration
│   └── properties/     # @ConfigurationProperties classes
└── support/            # Core AI processing logic
    ├── flow/           # AI Workflow engine
    │   ├── core/       # FlowContextHolder, exceptions
    │   ├── model/      # Flow DSL definitions
    │   │   ├── processor/  # Node processors (LLM, RAG, OCR, etc.)
    │   │   └── nodes/      # Node configuration models
    │   └── constants/  # Flow-related constants
    ├── handler/        # Document and data handlers
    │   ├── parse/      # Document parsers (PDF, Word, etc.)
    │   ├── source/     # Data source handlers
    │   └── rag/        # RAG strategy implementations
    ├── provider/       # AI model providers (ModelProvider, EmbeddingProvider)
    ├── rule/           # AI chat rules (VectorChatRule, McpChatRule, etc.)
    ├── function/       # LLM function calling tools
    └── util/           # Utilities (prompt builders, JSON repair, etc.)
```

## AI Workflow System

### Architecture Overview

The AI Workflow system (`support/flow/`) is a reactive streaming-based orchestration engine that executes visual AI
workflows defined in JSON DSL format.

**Key Components**:

1. **FlowContextHolder** (`support/flow/core/FlowContextHolder.java`)
    - Central context for workflow execution
    - Manages variables, parameters, and execution state
    - **Streaming Helpers** (added 2025-10-25):
        - `sendStreamContent(String content)` - Send streaming content fragments
        - `sendStreamEnd(AiNodeDefinition node, Integer tokens)` - Send completion signal with auto state management
        - `sendStreamError(String error)` - Send error notifications
    - Thread-safe variable storage with node-scoped namespacing

2. **Node Processors** (`support/flow/model/processor/`)
    - Each node type has a dedicated processor (implements `AiNodeProcessor`)
    - **Important**: When implementing streaming nodes, ALWAYS use `FlowContextHolder` streaming helpers instead of
      manually constructing callbacks
    - Current processors:
        - `LLMNodeProcessor` - LLM chat with streaming support
        - `RagNodeExecutor` - Knowledge base retrieval + generation
        - `OcrNodeExecutor` - OCR image recognition
        - `MCPNodeProcessor` - MCP tool invocation
        - `StructuredNodeProcessor` - JSON structured output
        - `HttpNodeProcessor`, `TextNodeProcessor`, etc.

3. **Flow Execution** (`AiDefaultAiFlowProcessor`)
    - DFS (Depth-First Search) node execution
    - Branch handling (switch/question nodes)
    - Cycle detection and error handling

### Streaming Implementation Pattern

**CORRECT** pattern for streaming nodes (post-optimization):

```java
// In doExecute() method
context.sendStreamContent(contentFragment);  // Send each fragment

// On completion
context.

sendStreamEnd(node, tokens);  // Auto-handles node status, duration, tokens

// On error
context.

sendStreamError(errorMessage);  // Auto-handles error state
```

**AVOID** this old pattern (deprecated):

```java
// DON'T manually build FlowCallbackResult/FlowCallbackData
context.getAiFlowExecuteDTO().

getCallback().

execute(
        AiFlowExecuteDTO.FlowCallbackResult.builder()
        .

data(AiFlowExecuteDTO.FlowCallbackData.builder()
            .

content(msg)
            .

build())
        .

build()
);
```

### Adding New Node Types

1. Create node configuration class in `support/flow/model/nodes/`
2. Create processor class in `support/flow/model/processor/` implementing `AbstractNodeProcessor`
3. Use `@Component(NodeTypeConstants.YOUR_TYPE)` annotation
4. For streaming nodes, use `FlowContextHolder` streaming helpers
5. Register node type constant in `NodeTypeConstants`

## RAG (Retrieval Augmented Generation)

### Components

- **VectorChatRule** - Main RAG orchestration (reactive streaming)
- **EmbeddingProvider** - Text embedding generation
- **AiSliceService** - Document chunking and vector storage
- **Reranker** - Result reranking for improved relevance

### RAG Flow

1. User query → Embedding generation
2. Vector similarity search in knowledge base
3. Retrieve top-K chunks
4. Optional reranking
5. Construct prompt with context
6. Stream LLM response

## Code Style and Conventions

### Naming Conventions

- **Entities**: Suffix with `Entity` (e.g., `AiFlowEntity`)
- **DTOs**: Suffix with `DTO` (e.g., `AiFlowExecuteDTO`)
- **Services**: Suffix with `Service` + `Impl` for implementations
- **Processors**: Suffix with `Processor` or `Executor`
- **Handlers**: Suffix with `Handler`

### Logging

- Use `@Slf4j` (Lombok) for logging
- Log levels:
    - `log.error()` - Exceptions and critical failures
    - `log.warn()` - Recoverable issues
    - `log.info()` - Important state changes
    - `log.debug()` - Detailed diagnostic info

## Configuration

Configuration is managed through:

1. **Nacos Config Server** - Centralized configuration (production)
2. **application.yml** - Local development config
3. **@ConfigurationProperties** - Type-safe config classes in `config/properties/`

Key configuration prefixes:

- `ai.knowledge.*` - General AI settings
- `ai.connection.*` - AI model connection settings
- `ai.vision.*` - Vision model settings
- `ai.websearch.*` - Web search integration

## Dependencies

### Core AI Libraries

- **LangChain4j** - AI orchestration framework
    - Chat models, embeddings, memory management
    - Function calling support
- **Spring AI** - Spring-native AI integration
    - Vector stores, embeddings
    - Reactive streaming support

### Document Processing

- **Apache Tika** - Universal document parser
- **Apache POI** - Office document handling (Excel, Word)
- **EasyWord** - Advanced Word document processing

### Vector Stores

Supported vector databases (configured via `ai.knowledge.vector-store`):

- Milvus (default)
- Pgvector (PostgreSQL extension)
- Chroma

## Important Implementation Notes

### Streaming Best Practices

1. **Always use `FlowContextHolder` streaming helpers** for consistency
2. **Auto state management**: `sendStreamEnd()` automatically updates node status, duration, and tokens
3. **Error handling**: Use `sendStreamError()` for unified error notifications
4. **Backward compatibility**: Old callback-based code still works but should be migrated

### Flow Development

1. **Variable scoping**: Use `nodeId.variableName` format for node-scoped variables
2. **Error handling**: Wrap node logic in try-catch, throw `FlowException` for flow-level errors
3. **Cycle detection**: Framework automatically detects and prevents infinite loops
4. **Branch execution**: Only one branch executes in switch/question nodes

### Performance Considerations

1. **Reactive streaming**: Use Project Reactor for async/non-blocking operations
2. **Connection pooling**: AI model clients are singleton-scoped
3. **Vector search**: Limit top-K results to balance relevance and performance
4. **Memory management**: Clear chat memory after long conversations

## Database Schema

Key tables:

- `ai_flow` - Workflow definitions
- `ai_dataset` - Knowledge base metadata
- `ai_slice` - Document chunks (with embeddings)
- `ai_model` - AI model configurations
- `ai_mcp_config` - MCP server configurations
- `ai_flow_log` - Workflow execution logs

## External Dependencies

**Required Services**:

- MySQL/PostgreSQL database
- Redis (caching and session)
- Nacos (service discovery and config)
- Vector database (Milvus/Pgvector/Chroma)

**Optional Services**:

- Unstructured API (advanced document parsing)
- Markitdown API (Markdown conversion)
- OCR services (Paddle OCR, UmiOCR)

## Recent Improvements (2025-10-25)

**AI Flow Streaming Optimization**:

- Added unified streaming helper methods to `FlowContextHolder`
- Simplified node processors: reduced code by 60-75% in streaming nodes
- Improved error handling consistency across all streaming nodes
- Auto state management for node execution tracking

See git history for detailed changes.
