# Das Mock Server

基于Spring AI Alibaba框架的智能Mock Server生成平台，能够自动解析API文档并生成完整的Mock Server服务。

## 功能特性

### 核心功能
- **多格式文档支持**: 支持PDF、Word、Markdown、TXT等多种格式的API文档
- **AI智能分析**: 基于Spring AI Alibaba框架，智能分析文档内容并提取API接口信息
- **自动代码生成**: 自动生成完整的Spring Boot Mock Server项目代码
- **可视化界面**: 提供现代化的Web界面，支持拖拽上传和实时预览
- **服务管理**: 支持服务的启动、停止、重新生成、删除等操作
- **端点编辑**: 支持手动调整API端点的参数、响应、鉴权配置等
- **自定义AI模型**: 支持配置自定义AI模型（API Key、API地址等），灵活切换不同AI服务
- **标签管理**: 支持通过标签对Mock服务进行分类管理
- **系统日志**: 实时查看系统运行日志（SSE流式推送）
- **智能期望**: 支持基于请求内容的智能响应匹配
- **鉴权配置**: 支持为每个端点配置独立的鉴权方式

### 支持的文件格式
- PDF文档 (.pdf)
- Word文档 (.docx, .doc)
- Markdown文件 (.md, .markdown)
- 文本文件 (.txt)

### 技术栈
- **后端**: Spring Boot 3.5.5, Spring AI Alibaba 1.0.0.2, Spring Data JPA
- **前端**: Bootstrap 5, Thymeleaf, JavaScript, Font Awesome
- **数据库**: H2 Database (可配置为其他数据库)
- **AI服务**: 阿里云通义千问 / 恒脑 (可配置自定义AI模型)
- **文件处理**: Apache PDFBox 2.0.29, Apache POI 5.2.3
- **代码生成**: JavaPoet 1.13.0
- **构建工具**: Maven 3.6+

## 快速开始

### 环境要求
- Java 17+
- Maven 3.6+
- 阿里云通义千问API Key 或 恒脑API Key (可选，用于AI功能)

### 安装步骤

1. **克隆项目**
```bash
git clone <repository-url>
cd das-mock-server
```

2. **配置环境变量**
```bash
# 设置阿里云通义千问API Key (可选)
export MOCK_DASHSCOPE_API_KEY=your-dashscope-api-key
# 设置恒脑API Key (可选)
export MOCK_HENGNAO_API_KEY=your-hengnao-api-key
# 设置服务端口 (可选，默认8080)
export MOCK_SERVER_PORT=9000
```

3. **启动应用**
```bash
mvn spring-boot:run
```

4. **访问应用**
打开浏览器访问: http://localhost:9000

### 使用流程

1. **上传文档**
   - 访问首页，点击"开始生成"或"上传文档"
   - 拖拽或选择API文档文件
   - 填写服务名称、端口、标签等信息
   - 选择AI模型及参数（模型、温度、最大Token数等）
   - 点击"生成Mock Server"

2. **查看服务**
   - 在首页查看已生成的服务列表
   - 点击"查看详情"进入服务管理页面
   - 支持按标签、名称、状态筛选服务

3. **管理服务**
   - 启动/停止/重启/删除服务
   - 查看API端点列表
   - 编辑端点参数
   - 测试API接口
   - 下载生成的代码
   - 下载原始上传文档

4. **编辑端点**
   - 修改请求/响应参数
   - 调整响应延迟
   - 自定义Mock数据
   - 设置HTTP状态码
   - 配置鉴权方式（支持多种鉴权类型）
   - 配置智能期望（基于请求内容匹配响应）
   - 设置自定义响应头

5. **AI模型管理**
   - 在管理页面配置自定义AI模型
   - 设置模型的API Key、API地址等
   - 支持设置默认模型

6. **系统日志**
   - 实时查看系统运行日志
   - 支持SSE流式推送

## API接口

### 主要接口

#### 生成Mock服务
```http
POST /api/mock-server/generate
Content-Type: multipart/form-data

file: API文档文件
serviceName: 服务名称
description: 服务描述 (可选)
tags: 标签，逗号分隔 (可选)
port: 服务端口 (默认8081)
aiModel: AI模型 (默认qwen-turbo)
aiTemperature: AI温度 (默认0.1)
aiMaxTokens: 最大Token数 (默认4000)
aiTopP: Top P (默认0.7)
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

#### 删除服务
```http
DELETE /api/mock-server/services/{id}
```

#### 获取所有标签
```http
GET /api/mock-server/tags
```

#### 手动创建服务
```http
POST /api/mock-server/services
Content-Type: application/json
```

#### 清理无效服务
```http
POST /api/mock-server/services/cleanup
```

#### 获取运行中的服务
```http
GET /api/mock-server/services/running
```

#### 获取服务运行状态
```http
GET /api/mock-server/services/{id}/status
```

#### 获取服务日志
```http
GET /api/mock-server/services/{id}/logs
```

### AI模型管理接口

#### 获取AI配置
```http
GET /api/ai-config
```

#### 更新AI配置
```http
POST /api/ai-config
Content-Type: application/json
```

#### 重置AI配置
```http
POST /api/ai-config/reset
```

#### 获取默认AI模型
```http
GET /api/ai-config/default
```

#### 获取自定义模型列表
```http
GET /api/custom-models
```

#### 创建自定义模型
```http
POST /api/custom-models
Content-Type: application/json
```

#### 更新自定义模型
```http
PUT /api/custom-models/{id}
Content-Type: application/json
```

#### 删除自定义模型
```http
DELETE /api/custom-models/{id}
```

### 系统日志接口

#### 系统日志页面
```http
GET /system-logs
```

#### 实时日志流（SSE）
```http
GET /api/system-logs/stream
Accept: text/event-stream
```

## 配置说明

### 应用配置 (application.properties)

```properties
# 数据库配置
spring.datasource.url=jdbc:h2:file:./data/mock-server-db
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=password
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# H2控制台
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

# Spring AI Alibaba配置
spring.ai.alibaba.dashscope.api-key=${MOCK_DASHSCOPE_API_KEY:your-dashscope-api-key}
spring.ai.dashscope.api-key=${MOCK_DASHSCOPE_API_KEY:your-dashscope-api-key}
spring.ai.alibaba.dashscope.chat.options.model=qwen-turbo
spring.ai.alibaba.dashscope.chat.options.temperature=0.1
spring.ai.alibaba.dashscope.chat.options.max-tokens=4000

# 文件上传限制
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# 服务器配置
server.port=${MOCK_SERVER_PORT:8080}

# 文件存储路径
app.file.upload.path=./uploads/
app.file.generated.path=./generated/

# 恒脑配置
hengNao.switch=true
hengNao.key=${MOCK_HENGNAO_API_KEY:your-hengnao-api-key}
hengNao.secret=${HENGNAO_API_SECRET:your-hengnao-secret}
```

### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| MOCK_DASHSCOPE_API_KEY | 阿里云通义千问API密钥 | - |
| MOCK_HENGNAO_API_KEY | 恒脑API密钥 | - |
| HENGNAO_API_SECRET | 恒脑API密钥Secret | - |
| MOCK_SERVER_PORT | 服务端口 | 8080 |

## 项目结构

```
das-mock-server/
├── src/main/java/com/dbapp/dasmockserver/
│   ├── config/                # 配置类
│   │   ├── AiConfig.java
│   │   ├── LoggingConfig.java
│   │   └── ProcessCleanupConfig.java
│   ├── controller/            # 控制器层
│   │   ├── MockServerController.java    # Mock服务API
│   │   ├── SystemLogController.java     # 系统日志
│   │   └── WebController.java           # Web页面与配置API
│   ├── model/                 # 数据模型
│   │   ├── ApiEndpoint.java
│   │   ├── AuthConfig.java
│   │   ├── AuthType.java
│   │   ├── CustomModelConfig.java
│   │   └── MockService.java
│   ├── repository/            # 数据访问层
│   │   ├── ApiEndpointRepository.java
│   │   ├── CustomModelConfigRepository.java
│   │   └── MockServiceRepository.java
│   ├── service/               # 业务逻辑层
│   │   ├── AiCodeGenerationService.java
│   │   ├── AiConfigService.java
│   │   ├── CodeGenerationService.java
│   │   ├── DocumentParserService.java
│   │   ├── MockServerService.java
│   │   ├── ParameterInfo.java
│   │   └── ProcessManagementService.java
│   ├── util/                  # 工具类
│   │   ├── logging/
│   │   │   ├── LogbackSseAppender.java
│   │   │   ├── SseLogBroadcaster.java
│   │   │   └── SseLogBroadcasterHolder.java
│   │   └── NetworkUtil.java
│   └── DasMockServerApplication.java
├── src/main/resources/
│   ├── db/migration/          # 数据库迁移脚本
│   ├── static/                # 静态资源
│   │   ├── css/               # Bootstrap, Font Awesome
│   │   ├── js/                # Bootstrap JS
│   │   └── webfonts/          # Font Awesome字体
│   ├── templates/             # Thymeleaf模板
│   │   ├── index.html         # 首页
│   │   ├── upload.html        # 上传文档页
│   │   ├── service-detail.html # 服务详情页
│   │   ├── edit-endpoint.html # 端点编辑页
│   │   ├── manage.html        # 管理页（AI模型配置）
│   │   └── system-logs.html   # 系统日志页
│   ├── application.properties
│   └── start.sh               # 启动脚本
├── uploads/                   # 上传文件存储
├── generated/                 # 生成的代码存储
├── data/                      # 数据库文件
└── pom.xml
```

## 开发指南

### 添加新的文档格式支持

1. 在`DocumentParserService`中添加新的解析方法
2. 在`isSupportedFormat`方法中添加格式验证
3. 更新前端文件类型限制

### 自定义AI提示词

修改`AiCodeGenerationService`中的提示词模板：
- API文档分析与接口提取
- Mock代码生成
- Mock响应生成

### 扩展代码生成模板

在`CodeGenerationService`中添加新的代码生成方法：
- 自定义Controller模板
- 添加新的DTO类生成
- 扩展配置文件生成

## 部署说明

### 安装JDK

linux 安装 java 17

#### Step 1：下载 JDK

官方下载链接：

[https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)

JDK17下载链接：[https://download.oracle.com/java/17/archive/jdk-17.0.12_linux-x64_bin.tar.gz](https://download.oracle.com/java/17/archive/jdk-17.0.12_linux-x64_bin.tar.gz)

> 注意：下载与 Linux 系统对应的版本

```bash
# 查看 Linux 版本
uname -a
```

#### Step 2：上传到 Linux

将下载的 jdk-17.0.12_linux-x64_bin.tar.gz 文件上传至 Linux 服务器，选择 /software/java 文件夹

#### Step 3：解压缩

解压缩到指定目录

```bash
mkdir /usr/local/java/
tar -xzvf /software/java/jdk-17.0.12_linux-x64_bin.tar.gz -C /usr/local/java/
```

#### Step 4：配置环境变量

```bash
vim /etc/profile
```

贴上如下内容：

```bash
export JAVA_HOME=/usr/local/java/jdk-17.0.12
export PATH=$PATH:$JAVA_HOME/bin;
export CLASSPATH=.:$JAVA_HOME/lib/dt.jar:$JAVA_HOME/lib/tools.jar;
```

#### Step 5：测试

```bash
# 加载配置
source /etc/profile
java -version
```

### 安装Maven

#### 1. Maven 压缩包下载与解压

华为云下载源，自行选择版本

下面的示例使用的是 3.8.1 版本

```bash
wget https://repo.huaweicloud.com/apache/maven/maven-3/3.8.1/binaries/apache-maven-3.8.1-bin.tar.gz
```

解压 `apache-maven-3.8.1-bin.tar.gz`

```bash
tar -zxvf apache-maven-3.8.1-bin.tar.gz
```

移动到 `/usr/local` 目录

```bash
mv apache-maven-3.8.1 /usr/local/
```

#### 2. 配置环境变量

```bash
vi /etc/profile
```

在最后面追加

```bash
export MAVEN_HOME=/usr/local/apache-maven-3.8.1/
export PATH=${PATH}:${MAVEN_HOME}/bin
```

使环境变量生效

```bash
source /etc/profile
```

#### 3. 安装完成

使用 `mvn` 检查是否安装成功

```bash
mvn -version
```

参考文章：[https://developer.aliyun.com/article/786760](https://developer.aliyun.com/article/786760)

### 一键打包部署

后续代码变更只要执行该命令即可实现一键部署，不用每次都手动替换jar包发包

```bash
mvn clean package -DskipTests -Pdeploy "-Dserver.username=root" "-Dserver.password=xxx" "-Dserver.host=10.50.2.217" "-Dserver.dir=/root/app" -f pom.xml
```

#### 环境变量说明

| 变量名 | 说明 |
|--------|------|
| server.dir | 程序部署目录 |
| server.host | 程序部署服务器 |
| server.username | 服务器用户名 |
| server.password | 服务器密码 |

#### 仅打包不部署

如果只需要打包，不部署到服务器：
```bash
mvn clean package -DskipTests
```

打包后的 JAR 文件位于 `target/das-mock-server-1.0.0.jar`

### 上传程序包与启动

```bash
mkdir -p /root/app

rz das-mock-server-1.0.0.jar

rz start.sh

chmod 766 start.sh

sh start.sh
```

### 开放端口

如果部署后无法访问，请确认防火墙端口已经开放：

```bash
sudo iptables -A INPUT -p tcp --dport 9000 -j ACCEPT
```

### 异常处理

如果 start.sh 脚本执行有报格式错误，请执行命令：

```bash
sed -i 's/\r$//' start.sh
```

### 更新程序

程序支持一键更新，只需要在IDEA里配置好环境变量即可

### Docker部署

1. **构建镜像**
```bash
docker build -t mock-server-generator .
```

2. **运行容器**
```bash
docker run -d \
  -p 9000:9000 \
  -e MOCK_DASHSCOPE_API_KEY=your-api-key \
  -e MOCK_HENGNAO_API_KEY=your-hengnao-key \
  -e MOCK_SERVER_PORT=9000 \
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
A: 有两种方式：1) 修改`application.properties`中的Spring AI Alibaba配置；2) 在管理页面（/manage）通过Web界面添加自定义AI模型配置。

### Q: 生成的代码在哪里？
A: 生成的代码保存在`./generated/`目录下，每个服务一个独立的项目文件夹。

### Q: 如何自定义Mock响应？
A: 在服务详情页面点击端点的编辑按钮，可以修改Mock响应数据、配置智能期望和鉴权方式。

### Q: 支持哪些HTTP方法？
A: 支持GET、POST、PUT、DELETE、PATCH、HEAD、OPTIONS等所有HTTP方法。

### Q: 如何查看实时日志？
A: 访问系统日志页面（/system-logs），日志通过SSE实时推送到浏览器。

### Q: 如何管理AI模型？
A: 访问管理页面（/manage），可以添加、编辑、删除自定义AI模型配置，支持设置API Key、API地址等参数。

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