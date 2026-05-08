# Java RAG Local

基于 Spring Boot + Spring AI + pgvector + Redis 构建的本地 RAG（Retrieval-Augmented Generation）知识库问答系统。

支持：

* 文档上传（docx/pdf/txt）
* 向量检索（pgvector）
* 本地 Embedding
* 流式回答（SSE）
* 多轮对话（Redis Memory）
* Query Rewrite
* Multi-Query Retrieval
* Rerank（无模型 / 模型版）
* 引用来源（sources）
* 命中句高亮
* 文件分页管理
* 文件级去重（fileHash）
* chunk 去重（chunkHash）

---

# 项目架构

```text
用户问题
    ↓
Query Rewrite
    ↓
Multi Query
    ↓
向量召回（pgvector）
    ↓
去重（chunkHash）
    ↓
Rerank（规则 / LLM）
    ↓
构造 Context
    ↓
大模型生成回答（SSE流式输出）
```

---

# 技术栈

| 技术                    | 说明          |
| --------------------- | ----------- |
| Spring Boot 3         | Web 框架      |
| Spring AI             | AI 集成       |
| PostgreSQL + pgvector | 向量数据库       |
| Redis                 | 多轮对话 Memory |
| SSE                   | 流式输出        |
| Docker                | 本地部署        |
| Tika                  | 文档解析        |
| Ollama / 本地 Embedding | 向量生成        |
| 智谱 GLM                | 大模型回答       |

---

# 核心功能

## 1. 文档上传

支持上传：

* docx
* pdf
* txt

接口：

```http
POST /ingest/upload
```

功能：

* 文档解析
* chunk 切分
* chunk 去重
* 向量化
* 写入 pgvector
* fileHash 去重

---

## 2. 文件管理

### 分页查询文件

```http
GET /ingest/files/page?pageNum=1&pageSize=10
```

### 删除文件

```http
DELETE /ingest/file?fileHash=xxx
```

特点：

* 幂等删除
* fileHash 精准删除
* 分页展示
* 前端自动刷新

---

## 3. RAG 问答

接口：

```http
GET /rag/stream/ask
```

参数：

| 参数        | 说明   |
| --------- | ---- |
| question  | 用户问题 |
| sessionId | 会话ID |

特点：

* SSE 流式输出
* 多轮对话
* sources 引用来源
* 命中句展示
* Query Rewrite
* Multi Query
* Rerank

---

# Query Rewrite

在向量检索前，对用户问题进行语义改写。

例如：

```text
原问题：毛泽东怎么打天下？

改写后：
毛泽东思想中新民主主义革命道路、农村包围城市、武装夺取政权的相关内容是什么？
```

作用：

* 提升召回命中率
* 解决口语化问题
* 提升知识库匹配效果

---

# Multi Query

针对同一个问题生成多个检索表达。

例如：

```text
问题：毛泽东怎么打天下？

生成：
1. 新民主主义革命道路
2. 农村包围城市战略
3. 武装夺取政权
```

作用：

* 提高召回覆盖率
* 从不同语义角度检索
* 提升复杂问题效果

---

# Rerank

## 无模型 Rerank

基于：

* 关键词命中
* 位置权重
* 文本长度

进行重新排序。

## 模型版 Rerank

通过大模型判断：

```text
哪个 chunk 与问题最相关
```

并支持：

* fallback
* 配置开关
* 自动降级

---

# 多轮对话（Redis Memory）

使用 Redis 存储历史对话。

特点：

* session 级隔离
* token 裁剪
* 最大历史限制
* 自动 trim

Redis Key：

```text
rag:chat:memory:{sessionId}
```

---

# Sources 引用来源

回答中支持：

```text
[1][2][3]
```

并展示：

* 文件名
* 命中句
* 高亮关键词
* 引用跳转

---

# Chunk 策略

当前采用：

```text
小 chunk 检索
```

特点：

* 检索精准
* 适合 rerank
* 适合 multi-query

后续可升级：

```text
Parent Document Retrieval
```

实现：

```text
小 chunk 检索
→ 大 parent chunk 回答
```

---

# 本地运行

## 1. 启动 PostgreSQL + pgvector

```bash
docker run -d \
  --name rag-postgres \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=rag_db \
  -p 5432:5432 \
  ankane/pgvector
```

---

## 2. 启动 Redis

```bash
docker run -d \
  --name rag-redis \
  -p 6379:6379 \
  redis:7
```

---

## 3. 配置 application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/rag_db
    username: postgres
    password: postgres

  data:
    redis:
      host: localhost
      port: 6379
```

---

## 4. 启动项目

```bash
mvn spring-boot:run
```

---

# 前端页面

## 问答页面

```text
stream-chat.html
```

功能：

* SSE 流式回答
* 多轮对话
* rewrite 展示
* sources 引用

## 文件管理页面

```text
file-manager.html
```

功能：

* 上传文件
* 删除文件
* 文件分页
* fileHash 管理

---

# 项目亮点

* 基于 Spring AI 构建完整 RAG Pipeline
* 实现 Query Rewrite + Multi Query + Rerank
* 支持 Redis 多轮 Memory
* 支持 SSE 流式回答
* 支持文件级与 chunk 级去重
* 支持可解释 Sources 展示
* 支持模型版与规则版双 Rerank
* 支持本地 Embedding

---

# 后续规划

* Parent Document Retrieval
* Hybrid Search（BM25 + Vector）
* Agent Workflow
* 权限隔离
* Chunk Parent Mapping
* 知识库标签系统
* 多知识库管理

---

# License

MIT
