# Java RAG 本地知识库问答系统

基于 Spring Boot 3 + 智谱 AI + pgvector 的企业级 RAG（检索增强生成）系统，支持文档智能问答、流式输出、多轮对话等功能。

## ✨ 核心特性

- 🚀 **开箱即用**：基于 Spring Boot 3.3，快速启动本地知识库问答
- 📄 **多格式支持**：支持 PDF / Word / TXT / Markdown 文档解析
- 🔍 **智能检索**：向量检索 + LLM 重排序 + 问题改写，提升检索精度
- 💬 **多轮对话**：基于 Redis 的会话记忆，支持上下文关联问答
- 🌊 **流式输出**：SSE 实时流式响应，提升用户体验
- 🗂️ **文档管理**：支持文档上传、删除、查询，基于文件哈希去重
- 🔌 **OpenAPI 文档**：集成 SpringDoc，自动生成 API 文档
- 💰 **低成本部署**：使用智谱 API，无需本地部署大模型

## 🛠️ 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.3.4 | 基础框架 |
| Spring AI | 1.0.6 | AI 能力集成 |
| pgvector | - | PostgreSQL 向量存储 |
| 智谱 SDK | 0.3.3 | 智谱 AI API 集成 |
| Redis | - | 会话记忆存储 |
| Apache Tika | - | 文档解析 |

## 📋 前置条件

1. **Java 17+**
2. **Maven 3.6+**
3. **PostgreSQL 12+**（需安装 pgvector 扩展）
4. **Redis 6+**
5. **智谱 API Key**（[获取地址](https://open.bigmodel.cn/)）

## 🚀 快速开始

### 1. 数据库准备

```sql
-- 创建数据库
CREATE DATABASE rag_db;

-- 连接到数据库后，创建 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;
```

### 2. 配置环境变量

```bash
# macOS / Linux
export ZHIPU_API_KEY=你的智谱API密钥

# Windows
set ZHIPU_API_KEY=你的智谱API密钥
```

### 3. 修改配置文件

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/rag_db
    username: postgres
    password: 你的密码

  data:
    redis:
      host: localhost
      port: 6379
      database: 0
```

### 4. 启动项目

```bash
mvn spring-boot:run
```

启动成功后访问：
- 应用首页：http://localhost:8080
- API 文档：http://localhost:8080/swagger-ui.html

## 📚 核心功能

### 1. 文档入库

#### 方式一：本地文档入库

将文档放入 `src/main/resources/docs/` 目录，调用接口入库：

```bash
curl -X GET "http://localhost:8080/ingest/insert"
```

#### 方式二：上传文档入库

```bash
curl -X POST "http://localhost:8080/ingest/upload" \
  -F "file=@/path/to/your/document.pdf"
```

#### 文档管理接口

```bash
# 查看已入库文件列表
curl "http://localhost:8080/ingest/files"

# 删除文件
curl -X POST "http://localhost:8080/ingest/deleteFile?fileHash=文件哈希值"
```

### 2. RAG 问答

#### 基础问答

```bash
curl "http://localhost:8080/rag/askFromPgsql?question=请总结文档核心观点"
```

响应示例：
```json
{
  "answer": "根据文档内容，核心观点包括...",
  "sources": [
    {
      "fileName": "document.pdf",
      "source": "document.pdf",
      "chunkHash": "abc123",
      "score": 0.89,
      "content": "相关文档片段..."
    }
  ]
}
```

#### 流式问答（推荐）

支持 SSE 实时流式输出和多轮对话：

```bash
curl "http://localhost:8080/rag/stream/ask?question=什么是毛泽东思想&sessionId=session1"
```

前端示例：
```javascript
const eventSource = new EventSource(
  "http://localhost:8080/rag/stream/ask?question=" + encodeURIComponent("你的问题") + 
  "&sessionId=session1"
);

// 接收来源文档
eventSource.addEventListener("sources", function(event) {
  console.log("来源文档:", JSON.parse(event.data));
});

// 接收流式回答
eventSource.addEventListener("message", function(event) {
  console.log("回答片段:", event.data);
});

// 完成
eventSource.addEventListener("done", function() {
  eventSource.close();
});
```

#### 清空会话记忆

```bash
curl -X DELETE "http://localhost:8080/rag/stream/memory?sessionId=session1"
```

## 🔧 高级配置

### 核心配置项

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `app.zhipu.api-key` | 智谱 API Key | 环境变量 `ZHIPU_API_KEY` |
| `app.zhipu.chat-model` | 聊天模型 | `glm-4-flash` |
| `app.zhipu.embedding-model` | 向量模型 | `embedding-3` |
| `app.rag.top-k` | 召回文档数量 | 10 |
| `app.rag.final-top-k` | 最终返回文档数量 | 3 |
| `app.rag.max-context-chars` | 最大上下文字符数 | 1000 |
| `app.rag.max-history-messages` | 最大历史消息数 | 6 |
| `app.rag.max-history-tokens` | 最大历史 Token 数 | 3000 |
| `app.rag.use-llm-rerank` | 是否使用 LLM 重排序 | true |

### 完整配置示例

```yaml
app:
  zhipu:
    api-key: ${ZHIPU_API_KEY:}
    chat-model: glm-4-flash
    embedding-model: embedding-3
    embedding-url: https://open.bigmodel.cn/api/paas/v4/embeddings
    chat-url: https://open.bigmodel.cn/api/paas/v4/chat/completions

  rag:
    docs-path: classpath:/docs/*.docx
    top-k: 10
    final-top-k: 3
    max-context-chars: 1000
    max-history-messages: 6
    max-history-tokens: 3000
    use-llm-rerank: true
```

## 📖 API 接口文档

### RAG 问答接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/rag/ask` | GET | 基础问答（SimpleVectorStore） |
| `/rag/askFromLocalDocx` | GET | 本地文档问答 |
| `/rag/askFromPgsql` | GET | pgvector 问答（推荐） |
| `/rag/stream/ask` | GET | 流式问答 + 多轮对话 |
| `/rag/stream/memory` | DELETE | 清空会话记忆 |

### 文档管理接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/ingest/insert` | GET | 本地文档入库 |
| `/ingest/upload` | POST | 上传文档入库 |
| `/ingest/files` | GET | 查询已入库文件 |
| `/ingest/deleteFile` | POST | 删除文件 |

## 🏗️ 项目结构

```
src/main/java/com/example/rag/
├── controller/               # 控制器层
│   ├── RagController.java        # RAG 问答接口
│   ├── StreamRagController.java  # 流式问答接口
│   └── IngestController.java     # 文档入库接口
├── chat/                     # 聊天相关
│   ├── ChatMemory.java           # 会话记忆管理
│   └── ChatMessage.java          # 消息模型
├── dto/                      # 数据传输对象
├── model/                    # 模型类
├── config/                   # 配置类
├── utils/                    # 工具类
├── AiConfig.java             # AI 配置
├── ZhipuConfig.java          # 智谱配置
├── ZhipuChatClient.java      # 智谱聊天客户端
├── ZhipuChatService.java     # 智谱聊天服务
├── ZhipuEmbeddingModel.java  # 智谱向量模型
├── IngestionService.java     # 文档入库服务
├── RagProperties.java        # RAG 配置属性
└── Application.java          # 应用入口
```

## 🔍 技术亮点

### 1. 智能检索策略

- **问题改写**：使用 LLM 将用户问题改写为更适合向量检索的查询语句
- **多路召回**：向量检索召回 Top-K 相关文档
- **智能重排序**：
  - LLM 重排序：利用大模型判断文档相关性
  - 本地重排序：基于关键词匹配和位置加权

### 2. 多轮对话支持

- 基于 Redis 存储会话历史
- 支持 Token 和消息数量双重限制
- 自动管理上下文窗口，避免超出模型限制

### 3. 文档处理

- 支持多种文档格式（PDF、Word、TXT、Markdown）
- 基于文件哈希去重
- 自动提取文档元数据（文件名、章节等）

## 📝 使用示例

### 场景一：知识库问答

```bash
# 1. 上传文档
curl -X POST "http://localhost:8080/ingest/upload" -F "file=@教材.docx"

# 2. 问答
curl "http://localhost:8080/rag/askFromPgsql?question=什么是马克思主义?"
```

### 场景二：多轮对话

```bash
# 第一轮
curl "http://localhost:8080/rag/stream/ask?question=毛泽东思想的主要内容是什么&sessionId=chat001"

# 第二轮（带上下文）
curl "http://localhost:8080/rag/stream/ask?question=它有什么历史意义&sessionId=chat001"

# 清空会话
curl -X DELETE "http://localhost:8080/rag/stream/memory?sessionId=chat001"
```

## ⚠️ 注意事项

1. **数据库扩展**：确保 PostgreSQL 已安装 pgvector 扩展
2. **API 限额**：智谱 API 有调用频率限制，请合理控制并发
3. **文档大小**：默认支持最大 100MB 文件上传
4. **会话管理**：建议定期清理长期未使用的会话记忆

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

本项目采用 MIT 许可证。

## 🙏 致谢

- [Spring AI](https://spring.io/projects/spring-ai)
- [智谱 AI](https://open.bigmodel.cn/)
- [pgvector](https://github.com/pgvector/pgvector)# Java RAG (Zhipu API)

本项目是一个低成本、本地部署的 Java RAG 示例：
- 模型服务：智谱 API（官方 `zai-sdk`）
- 文档类型：PDF / Word / TXT / Markdown
- 检索：Spring AI `SimpleVectorStore`

## 1. 环境准备

1) 安装 Java 17+  
2) 安装 Maven  
3) 准备智谱 API Key（`ZHIPU_API_KEY`）

macOS/Linux:
```bash
export ZHIPU_API_KEY=你的key
```

## 2. 放入文档

把 PDF/Word 文件放到：

`src/main/resources/docs`

## 3. 启动项目

```bash
mvn spring-boot:run
```

启动时会自动扫描 `docs` 目录并完成向量化入库。

## 4. 调用问答接口

```bash
curl "http://localhost:8080/api/chat?question=请总结文档的核心观点"
```

返回示例：
- `answer`: 最终回答
- `sources`: 命中的片段来源与内容

## 5. 关键配置（智谱）

配置文件：`src/main/resources/application.properties`

- `app.zhipu.api-key`：智谱 API Key（默认读取环境变量 `ZHIPU_API_KEY`）
- `app.zhipu.chat-model`：聊天模型（默认 `glm-4-flash`）
- `app.zhipu.embedding-model`：向量模型（默认 `embedding-3`）
- `app.rag.docs-path`：文档目录
- `app.rag.top-k`：检索条数
