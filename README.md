# AI Mock Server

基于Spring AI Alibaba框架的智能Mock Server生成平台，能够自动解析API文档并生成完整的Mock Server服务。

## 功能特性

### 🚀 核心功能
- **多格式文档支持**: 支持PDF、Word、Markdown、TXT等多种格式的API文档
- **AI智能分析**: 基于Spring AI Alibaba框架，智能分析文档内容并提取API接口信息
- **自动代码生成**: 自动生成完整的Spring Boot Mock Server项目代码
- **可视化界面**: 提供现代化的Web界面，支持拖拽上传和实时预览
- **服务管理**: 支持服务的启动、停止、重新生成等操作
- **端点编辑**: 支持手动调整API端点的参数和响应

### 📋 支持的文件格式
- PDF文档 (.pdf)
- Word文档 (.docx, .doc)
- Markdown文件 (.md, .markdown)
- 文本文件 (.txt)

### 🔧 技术栈
- **后端**: Spring Boot 3.x, Spring AI Alibaba, Spring Data JPA
- **前端**: Bootstrap 5, Thymeleaf, JavaScript
- **数据库**: H2 Database (可配置为其他数据库)
- **AI服务**: 阿里云通义千问 (可配置其他AI服务)
- **文件处理**: Apache PDFBox, Apache POI

## 快速开始

### 环境要求
- Java 17+
- Maven 3.6+
- 阿里云通义千问API Key (可选，用于AI功能)

### 安装步骤

1. **克隆项目**
```bash
git clone <repository-url>
cd das-mock-server
```

2. **配置环境变量**
```bash
# 设置阿里云通义千问API Key (可选)
export DASHSCOPE_API_KEY=your-dashscope-api-key
```

3. **启动应用**
```bash
mvn spring-boot:run
```

4. **访问应用**
打开浏览器访问: http://localhost:8080

### 使用流程

1. **上传文档**
   - 访问首页，点击"开始生成"或"上传文档"
   - 拖拽或选择API文档文件
   - 填写服务名称、端口等信息
   - 点击"生成Mock Server"

2. **查看服务**
   - 在首页查看已生成的服务列表
   - 点击"查看详情"进入服务管理页面

3. **管理服务**
   - 启动/停止服务
   - 查看API端点列表
   - 编辑端点参数
   - 测试API接口
   - 下载生成的代码

4. **编辑端点**
   - 修改请求/响应参数
   - 调整响应延迟
   - 自定义Mock数据
   - 设置HTTP状态码

## API接口

### 主要接口

#### 生成Mock服务
```http
POST /api/mock-server/generate
Content-Type: multipart/form-data

file: API文档文件
serviceName: 服务名称
description: 服务描述 (可选)
port: 服务端口 (默认8081)
```

#### 获取服务列表
```http
GET /api/mock-server/services
```

#### 获取服务详情
```http
GET /api/mock-server/services/{id}
```

#### 获取端点列表
```http
GET /api/mock-server/services/{id}/endpoints
```

#### 更新端点
```http
PUT /api/mock-server/endpoints/{id}
Content-Type: application/json

{
  "name": "端点名称",
  "path": "/api/endpoint",
  "method": "GET",
  "description": "端点描述",
  "mockResponse": "{\"message\": \"Mock response\"}",
  "responseDelay": 1000,
  "statusCode": 200
}
```

#### 启动服务
```http
POST /api/mock-server/services/{id}/start
```

#### 停止服务
```http
POST /api/mock-server/services/{id}/stop
```

#### 重新生成服务
```http
POST /api/mock-server/services/{id}/regenerate
```

## 配置说明

### 应用配置 (application.properties)

```properties
# 数据库配置
spring.datasource.url=jdbc:h2:file:./data/mock-server-db
spring.datasource.username=sa
spring.datasource.password=password

# Spring AI Alibaba配置
spring.ai.alibaba.dashscope.api-key=${DASHSCOPE_API_KEY:your-dashscope-api-key}
spring.ai.alibaba.dashscope.chat.options.model=qwen-turbo
spring.ai.alibaba.dashscope.chat.options.temperature=0.7

# 文件上传配置
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# 文件存储路径
app.file.upload.path=./uploads/
app.file.generated.path=./generated/
```

### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| DASHSCOPE_API_KEY | 阿里云通义千问API密钥 | your-dashscope-api-key |

## 项目结构

```
das-mock-server/
├── src/main/java/com/dbapp/dasmockserver/
│   ├── controller/          # 控制器层
│   │   ├── MockServerController.java
│   │   └── WebController.java
│   ├── model/              # 数据模型
│   │   ├── ApiEndpoint.java
│   │   └── MockService.java
│   ├── repository/         # 数据访问层
│   │   ├── ApiEndpointRepository.java
│   │   └── MockServiceRepository.java
│   ├── service/           # 业务逻辑层
│   │   ├── AiCodeGenerationService.java
│   │   ├── CodeGenerationService.java
│   │   ├── DocumentParserService.java
│   │   └── MockServerService.java
│   └── DasMockServerApplication.java
├── src/main/resources/
│   ├── templates/         # Thymeleaf模板
│   │   ├── index.html
│   │   ├── upload.html
│   │   └── service-detail.html
│   └── application.properties
├── uploads/              # 上传文件存储
├── generated/            # 生成的代码存储
└── data/                # 数据库文件
```

## 开发指南

### 添加新的文档格式支持

1. 在`DocumentParserService`中添加新的解析方法
2. 在`isSupportedFormat`方法中添加格式验证
3. 更新前端文件类型限制

### 自定义AI提示词

修改`AiCodeGenerationService`中的提示词模板：
- `buildApiAnalysisPrompt`: API分析提示词
- `buildCodeGenerationPrompt`: 代码生成提示词
- `buildMockResponsePrompt`: Mock响应生成提示词

### 扩展代码生成模板

在`CodeGenerationService`中添加新的代码生成方法：
- 自定义Controller模板
- 添加新的DTO类生成
- 扩展配置文件生成

## 部署说明

### Docker部署

1. **构建镜像**
```bash
docker build -t mock-server-generator .
```

2. **运行容器**
```bash
docker run -d \
  -p 8080:8080 \
  -e DASHSCOPE_API_KEY=your-api-key \
  -v $(pwd)/data:/app/data \
  -v $(pwd)/uploads:/app/uploads \
  -v $(pwd)/generated:/app/generated \
  mock-server-generator
```

### 生产环境配置

1. **数据库配置**
```properties
# 使用MySQL或PostgreSQL
spring.datasource.url=jdbc:mysql://localhost:3306/mock_server
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update
```

2. **安全配置**
```properties
# 禁用H2控制台
spring.h2.console.enabled=false

# 配置CORS
spring.web.cors.allowed-origins=https://your-domain.com
```

## 常见问题

### Q: 如何配置其他AI服务？
A: 修改`application.properties`中的Spring AI Alibaba配置，支持通义千问、通义万相等。

### Q: 生成的代码在哪里？
A: 生成的代码保存在`./generated/`目录下，每个服务一个独立的项目文件夹。

### Q: 如何自定义Mock响应？
A: 在服务详情页面点击端点的编辑按钮，可以修改Mock响应数据。

### Q: 支持哪些HTTP方法？
A: 支持GET、POST、PUT、DELETE、PATCH、HEAD、OPTIONS等所有HTTP方法。

## 贡献指南

1. Fork项目
2. 创建功能分支
3. 提交更改
4. 推送到分支
5. 创建Pull Request

## 许可证

MIT License

## 联系方式

如有问题或建议，请提交Issue或联系开发团队。 