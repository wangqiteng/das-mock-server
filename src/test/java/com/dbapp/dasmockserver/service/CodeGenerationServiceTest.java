package com.dbapp.dasmockserver.service;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;

import javax.lang.model.element.Modifier;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

public class CodeGenerationServiceTest {

    @Test
    public void testParseAiSchemaFields() throws Exception {
        // 创建CodeGenerationService实例
        CodeGenerationService service = new CodeGenerationService();
        
        // 使用反射调用私有方法
        Method parseMethod = CodeGenerationService.class.getDeclaredMethod("parseAiSchemaFields", String.class);
        parseMethod.setAccessible(true);
        
        // 测试Schema
        String testSchema = """
            {
              "pathVariables": [],
              "queryParameters": [],
              "requestBody": {
                "type": "array",
                "items": {
                  "type": "object",
                  "properties": {
                    "gjAddress": {
                      "type": "string",
                      "required": true,
                      "description": "攻击地址，ip地址"
                    },
                    "bgjAddress": {
                      "type": "string",
                      "required": true,
                      "description": "被攻击地址，ip地址"
                    },
                    "gjFrom": {
                      "type": "string",
                      "required": false,
                      "description": "攻击者ip归属"
                    },
                    "description": {
                      "type": "string",
                      "required": true,
                      "description": "事件描述"
                    },
                    "dataFrom": {
                      "type": "string",
                      "required": true,
                      "description": "数据来源，单选(市数据局、APT)"
                    },
                    "type": {
                      "type": "string",
                      "required": true,
                      "description": "处置类型，多个类型用"、"来拼接(出口防火墙封禁、服务器区防火墙封禁、华三防火墙封禁)"
                    },
                    "isPush": {
                      "type": "boolean",
                      "required": true,
                      "description": "是否推送"
                    }
                  }
                }
              }
            }
            """;
        
        // 调用解析方法
        List<?> fields = (List<?>) parseMethod.invoke(service, testSchema);
        
        // 输出结果
        System.out.println("解析结果: " + fields.size() + " 个字段");
        for (Object field : fields) {
            System.out.println("字段: " + field);
        }
        
        // 验证结果
        assert fields.size() > 0 : "应该解析到至少一个字段";
    }
    
    @Test
    public void testManualParsing() {
        // 手动测试解析逻辑
        String testSchema = """
            {
              "pathVariables": [],
              "queryParameters": [],
              "requestBody": {
                "type": "array",
                "items": {
                  "type": "object",
                  "properties": {
                    "gjAddress": {
                      "type": "string",
                      "required": true,
                      "description": "攻击地址，ip地址"
                    },
                    "bgjAddress": {
                      "type": "string",
                      "required": true,
                      "description": "被攻击地址，ip地址"
                    }
                  }
                }
              }
            }
            """;
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(testSchema);
            
            System.out.println("开始解析Schema: " + testSchema);
            
            if (jsonNode.has("requestBody")) {
                System.out.println("发现requestBody结构");
                JsonNode requestBody = jsonNode.get("requestBody");
                
                if (requestBody.has("items")) {
                    System.out.println("发现items结构");
                    JsonNode items = requestBody.get("items");
                    
                    if (items.has("properties")) {
                        System.out.println("发现properties结构");
                        JsonNode properties = items.get("properties");
                        Iterator<Map.Entry<String, JsonNode>> fieldsIterator = properties.fields();
                        
                        int fieldCount = 0;
                        while (fieldsIterator.hasNext()) {
                            Map.Entry<String, JsonNode> fieldEntry = fieldsIterator.next();
                            String fieldName = fieldEntry.getKey();
                            JsonNode fieldNode = fieldEntry.getValue();
                            
                            System.out.println("解析字段: " + fieldName + " = " + fieldNode.toString());
                            fieldCount++;
                        }
                        
                        System.out.println("总共解析到 " + fieldCount + " 个字段");
                        assert fieldCount > 0 : "应该解析到至少一个字段";
                    } else {
                        System.out.println("items中没有properties");
                    }
                } else {
                    System.out.println("requestBody中没有items");
                }
            } else {
                System.out.println("没有发现requestBody结构");
            }
            
        } catch (Exception e) {
            System.err.println("解析失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    public void testUserProvidedSchema() {
        // 用户提供的具体Schema
        String userSchema = """
            {
              "pathVariables": [],
              "queryParameters": [],
              "requestBody": {
                "type": "array",
                "items": {
                  "type": "object",
                  "properties": {
                    "gjAddress": {
                      "type": "string",
                      "required": true,
                      "description": "攻击地址，ip地址"
                    },
                    "bgjAddress": {
                      "type": "string",
                      "required": true,
                      "description": "被攻击地址，ip地址"
                    },
                    "gjFrom": {
                      "type": "string",
                      "required": false,
                      "description": "攻击者ip归属"
                    },
                    "description": {
                      "type": "string",
                      "required": true,
                      "description": "事件描述"
                    },
                    "dataFrom": {
                      "type": "string",
                      "required": true,
                      "description": "数据来源，单选(市数据局、APT)"
                    },
                    "type": {
                      "type": "string",
                      "required": true,
                      "description": "处置类型，多个类型用,来拼接(出口防火墙封禁、服务器区防火墙封禁、华三防火墙封禁)"
                    },
                    "isPush": {
                      "type": "boolean",
                      "required": true,
                      "description": "是否推送"
                    }
                  }
                }
              }
            }
            """;
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(userSchema);
            
            System.out.println("=== 开始解析用户提供的Schema ===");
            System.out.println("Schema: " + userSchema);
            
            if (jsonNode.has("requestBody")) {
                System.out.println("✅ 发现requestBody结构");
                JsonNode requestBody = jsonNode.get("requestBody");
                
                if (requestBody.has("items")) {
                    System.out.println("✅ 发现items结构");
                    JsonNode items = requestBody.get("items");
                    
                    if (items.has("properties")) {
                        System.out.println("✅ 发现properties结构");
                        JsonNode properties = items.get("properties");
                        Iterator<Map.Entry<String, JsonNode>> fieldsIterator = properties.fields();
                        
                        int fieldCount = 0;
                        while (fieldsIterator.hasNext()) {
                            Map.Entry<String, JsonNode> fieldEntry = fieldsIterator.next();
                            String fieldName = fieldEntry.getKey();
                            JsonNode fieldNode = fieldEntry.getValue();
                            
                            System.out.println("📝 解析字段: " + fieldName + " = " + fieldNode.toString());
                            fieldCount++;
                        }
                        
                        System.out.println("🎉 总共解析到 " + fieldCount + " 个字段");
                        assert fieldCount == 7 : "应该解析到7个字段，实际解析到" + fieldCount + "个";
                        
                        // 验证具体字段
                        assert properties.has("gjAddress") : "缺少gjAddress字段";
                        assert properties.has("bgjAddress") : "缺少bgjAddress字段";
                        assert properties.has("gjFrom") : "缺少gjFrom字段";
                        assert properties.has("description") : "缺少description字段";
                        assert properties.has("dataFrom") : "缺少dataFrom字段";
                        assert properties.has("type") : "缺少type字段";
                        assert properties.has("isPush") : "缺少isPush字段";
                        
                        System.out.println("✅ 所有字段验证通过");
                        
                    } else {
                        System.out.println("❌ items中没有properties");
                        assert false : "items中没有properties";
                    }
                } else {
                    System.out.println("❌ requestBody中没有items");
                    assert false : "requestBody中没有items";
                }
            } else {
                System.out.println("❌ 没有发现requestBody结构");
                assert false : "没有发现requestBody结构";
            }
            
        } catch (Exception e) {
            System.err.println("❌ 解析失败: " + e.getMessage());
            e.printStackTrace();
            assert false : "解析失败: " + e.getMessage();
        }
    }

    @Test
    public void testActualCodeGeneration() throws Exception {
        // 创建CodeGenerationService实例
        CodeGenerationService service = new CodeGenerationService();
        
        // 使用反射调用私有方法
        Method parseMethod = CodeGenerationService.class.getDeclaredMethod("parseAiSchemaFields", String.class);
        parseMethod.setAccessible(true);
        
        // 用户提供的实际Schema（修复中文字符问题）
        String actualSchema = """
            {
              "pathVariables": [],
              "queryParameters": [],
              "requestBody": {
                "type": "array",
                "items": {
                  "type": "object",
                  "properties": {
                    "gjAddress": {
                      "type": "string",
                      "required": true,
                      "description": "攻击地址，ip地址"
                    },
                    "bgjAddress": {
                      "type": "string",
                      "required": true,
                      "description": "被攻击地址，ip地址"
                    },
                    "gjFrom": {
                      "type": "string",
                      "required": false,
                      "description": "攻击者ip归属"
                    },
                    "description": {
                      "type": "string",
                      "required": true,
                      "description": "事件描述"
                    },
                    "dataFrom": {
                      "type": "string",
                      "required": true,
                      "description": "数据来源，单选(市数据局、APT)"
                    },
                    "type": {
                      "type": "string",
                      "required": true,
                      "description": "处置类型，多个类型用,来拼接(出口防火墙封禁、服务器区防火墙封禁、华三防火墙封禁)"
                    },
                    "isPush": {
                      "type": "boolean",
                      "required": true,
                      "description": "是否推送"
                    }
                  }
                }
              }
            }
            """;
        
        System.out.println("=== 测试实际代码生成流程 ===");
        System.out.println("Schema: " + actualSchema);
        
        // 调用解析方法
        List<?> fields = (List<?>) parseMethod.invoke(service, actualSchema);
        
        System.out.println("解析结果: " + fields.size() + " 个字段");
        for (Object field : fields) {
            System.out.println("字段: " + field);
        }
        
        // 验证结果
        assert fields.size() == 7 : "应该解析到7个字段，实际解析到" + fields.size() + "个";
        System.out.println("✅ 代码生成测试通过");
    }

    @Test
    public void testOriginalUserSchemaWithChineseCharacters() throws Exception {
        // 创建CodeGenerationService实例
        CodeGenerationService service = new CodeGenerationService();
        
        // 使用反射调用私有方法
        Method parseMethod = CodeGenerationService.class.getDeclaredMethod("parseAiSchemaFields", String.class);
        parseMethod.setAccessible(true);
        
        // 用户提供的原始Schema（包含中文字符"、"）
        String originalSchema = """
            {
              "pathVariables": [],
              "queryParameters": [],
              "requestBody": {
                "type": "array",
                "items": {
                  "type": "object",
                  "properties": {
                    "gjAddress": {
                      "type": "string",
                      "required": true,
                      "description": "攻击地址，ip地址"
                    },
                    "bgjAddress": {
                      "type": "string",
                      "required": true,
                      "description": "被攻击地址，ip地址"
                    },
                    "gjFrom": {
                      "type": "string",
                      "required": false,
                      "description": "攻击者ip归属"
                    },
                    "description": {
                      "type": "string",
                      "required": true,
                      "description": "事件描述"
                    },
                    "dataFrom": {
                      "type": "string",
                      "required": true,
                      "description": "数据来源，单选(市数据局、APT)"
                    },
                    "type": {
                      "type": "string",
                      "required": true,
                      "description": "处置类型，多个类型用"、"来拼接(出口防火墙封禁、服务器区防火墙封禁、华三防火墙封禁)"
                    },
                    "isPush": {
                      "type": "boolean",
                      "required": true,
                      "description": "是否推送"
                    }
                  }
                }
              }
            }
            """;
        
        System.out.println("=== 测试原始用户Schema（包含中文字符） ===");
        System.out.println("原始Schema: " + originalSchema);
        
        // 调用解析方法
        List<?> fields = (List<?>) parseMethod.invoke(service, originalSchema);
        
        System.out.println("解析结果: " + fields.size() + " 个字段");
        for (Object field : fields) {
            System.out.println("字段: " + field);
        }
        
        // 验证结果
        assert fields.size() == 7 : "应该解析到7个字段，实际解析到" + fields.size() + "个";
        System.out.println("✅ 原始Schema解析测试通过");
    }

    @Test
    public void testNewRequestSchemaFormat() {
        // 测试新的requestSchema格式
        String newSchemaFormat = """
            {
              "pathVariables": [],
              "queryParameters": [],
              "requestBody": {
                "type": "array",
                "items": {
                  "type": "object",
                  "properties": {
                    "gjAddress": {
                      "type": "string",
                      "required": true,
                      "description": "攻击地址，ip地址"
                    },
                    "bgjAddress": {
                      "type": "string",
                      "required": true,
                      "description": "被攻击地址，ip地址"
                    },
                    "gjFrom": {
                      "type": "string",
                      "required": false,
                      "description": "攻击者ip归属"
                    },
                    "description": {
                      "type": "string",
                      "required": true,
                      "description": "事件描述"
                    },
                    "dataFrom": {
                      "type": "string",
                      "required": true,
                      "description": "数据来源，单选(市数据局、APT)"
                    },
                    "type": {
                      "type": "string",
                      "required": true,
                      "description": "处置类型，多个类型用,来拼接(出口防火墙封禁、服务器区防火墙封禁、华三防火墙封禁)"
                    },
                    "isPush": {
                      "type": "boolean",
                      "required": true,
                      "description": "是否推送"
                    }
                  }
                }
              }
            }
            """;
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(newSchemaFormat);
            
            System.out.println("=== 测试新的requestSchema格式 ===");
            System.out.println("Schema: " + newSchemaFormat);
            
            if (jsonNode.has("requestBody")) {
                System.out.println("✅ 发现requestBody结构");
                JsonNode requestBody = jsonNode.get("requestBody");
                
                if (requestBody.has("items")) {
                    System.out.println("✅ 发现items结构");
                    JsonNode items = requestBody.get("items");
                    
                    if (items.has("properties")) {
                        System.out.println("✅ 发现properties结构");
                        JsonNode properties = items.get("properties");
                        Iterator<Map.Entry<String, JsonNode>> fieldsIterator = properties.fields();
                        
                        int fieldCount = 0;
                        while (fieldsIterator.hasNext()) {
                            Map.Entry<String, JsonNode> fieldEntry = fieldsIterator.next();
                            String fieldName = fieldEntry.getKey();
                            JsonNode fieldNode = fieldEntry.getValue();
                            
                            System.out.println("📝 解析字段: " + fieldName + " = " + fieldNode.toString());
                            fieldCount++;
                        }
                        
                        System.out.println("🎉 总共解析到 " + fieldCount + " 个字段");
                        assert fieldCount == 7 : "应该解析到7个字段，实际解析到" + fieldCount + "个";
                        
                        // 验证具体字段
                        assert properties.has("gjAddress") : "缺少gjAddress字段";
                        assert properties.has("bgjAddress") : "缺少bgjAddress字段";
                        assert properties.has("gjFrom") : "缺少gjFrom字段";
                        assert properties.has("description") : "缺少description字段";
                        assert properties.has("dataFrom") : "缺少dataFrom字段";
                        assert properties.has("type") : "缺少type字段";
                        assert properties.has("isPush") : "缺少isPush字段";
                        
                        System.out.println("✅ 所有字段验证通过");
                        
                    } else {
                        System.out.println("❌ items中没有properties");
                        assert false : "items中没有properties";
                    }
                } else {
                    System.out.println("❌ requestBody中没有items");
                    assert false : "requestBody中没有items";
                }
            } else {
                System.out.println("❌ 没有发现requestBody结构");
                assert false : "没有发现requestBody结构";
            }
            
        } catch (Exception e) {
            System.err.println("❌ 解析失败: " + e.getMessage());
            e.printStackTrace();
            assert false : "解析失败: " + e.getMessage();
        }
    }

    @Test
    public void testRequestBodyObjectFormat() {
        // 测试requestBody的object格式
        String objectSchemaFormat = """
            {
              "pathVariables": [],
              "queryParameters": [],
              "requestBody": {
                "type": "object",
                "properties": {
                  "userId": {
                    "type": "string",
                    "required": true,
                    "description": "用户ID"
                  },
                  "userName": {
                    "type": "string",
                    "required": true,
                    "description": "用户名称"
                  },
                  "age": {
                    "type": "integer",
                    "required": false,
                    "description": "用户年龄"
                  },
                  "isActive": {
                    "type": "boolean",
                    "required": true,
                    "description": "是否激活"
                  }
                }
              }
            }
            """;
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(objectSchemaFormat);
            
            System.out.println("=== 测试requestBody的object格式 ===");
            System.out.println("Schema: " + objectSchemaFormat);
            
            if (jsonNode.has("requestBody")) {
                System.out.println("✅ 发现requestBody结构");
                JsonNode requestBody = jsonNode.get("requestBody");
                
                String requestBodyType = requestBody.has("type") ? requestBody.get("type").asText() : "object";
                System.out.println("📝 requestBody类型: " + requestBodyType);
                
                if ("object".equals(requestBodyType) && requestBody.has("properties")) {
                    System.out.println("✅ 发现object格式的properties结构");
                    JsonNode properties = requestBody.get("properties");
                    Iterator<Map.Entry<String, JsonNode>> fieldsIterator = properties.fields();
                    
                    int fieldCount = 0;
                    while (fieldsIterator.hasNext()) {
                        Map.Entry<String, JsonNode> fieldEntry = fieldsIterator.next();
                        String fieldName = fieldEntry.getKey();
                        JsonNode fieldNode = fieldEntry.getValue();
                        
                        System.out.println("📝 解析字段: " + fieldName + " = " + fieldNode.toString());
                        fieldCount++;
                    }
                    
                    System.out.println("🎉 总共解析到 " + fieldCount + " 个字段");
                    assert fieldCount == 4 : "应该解析到4个字段，实际解析到" + fieldCount + "个";
                    
                    // 验证具体字段
                    assert properties.has("userId") : "缺少userId字段";
                    assert properties.has("userName") : "缺少userName字段";
                    assert properties.has("age") : "缺少age字段";
                    assert properties.has("isActive") : "缺少isActive字段";
                    
                    System.out.println("✅ 所有字段验证通过");
                    
                } else {
                    System.out.println("❌ requestBody不是object格式或缺少properties");
                    assert false : "requestBody不是object格式或缺少properties";
                }
            } else {
                System.out.println("❌ 没有发现requestBody结构");
                assert false : "没有发现requestBody结构";
            }
            
        } catch (Exception e) {
            System.err.println("❌ 解析失败: " + e.getMessage());
            e.printStackTrace();
            assert false : "解析失败: " + e.getMessage();
        }
    }
    
    @Test
    public void testBothFormatsComparison() {
        // 比较array和object两种格式
        String arrayFormat = """
            {
              "requestBody": {
                "type": "array",
                "items": {
                  "type": "object",
                  "properties": {
                    "name": {
                      "type": "string",
                      "required": true,
                      "description": "名称"
                    }
                  }
                }
              }
            }
            """;
        
        String objectFormat = """
            {
              "requestBody": {
                "type": "object",
                "properties": {
                  "name": {
                    "type": "string",
                    "required": true,
                    "description": "名称"
                  }
                }
              }
            }
            """;
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            
            System.out.println("=== 比较array和object两种格式 ===");
            
            // 测试array格式
            JsonNode arrayNode = mapper.readTree(arrayFormat);
            JsonNode arrayRequestBody = arrayNode.get("requestBody");
            String arrayType = arrayRequestBody.get("type").asText();
            System.out.println("📝 Array格式类型: " + arrayType);
            
            // 测试object格式
            JsonNode objectNode = mapper.readTree(objectFormat);
            JsonNode objectRequestBody = objectNode.get("requestBody");
            String objectType = objectRequestBody.get("type").asText();
            System.out.println("📝 Object格式类型: " + objectType);
            
            // 验证两种格式都能正确识别
            assert "array".equals(arrayType) : "Array格式类型识别错误";
            assert "object".equals(objectType) : "Object格式类型识别错误";
            
            // 验证都能找到properties
            JsonNode arrayProperties = arrayRequestBody.get("items").get("properties");
            JsonNode objectProperties = objectRequestBody.get("properties");
            
            assert arrayProperties.has("name") : "Array格式缺少name字段";
            assert objectProperties.has("name") : "Object格式缺少name字段";
            
            System.out.println("✅ 两种格式都能正确解析");
            
        } catch (Exception e) {
            System.err.println("❌ 比较测试失败: " + e.getMessage());
            e.printStackTrace();
            assert false : "比较测试失败: " + e.getMessage();
        }
    }

    @Test
    public void testParseAiSchemaFieldsWithBothFormats() throws Exception {
        // 测试实际的parseAiSchemaFields方法处理两种格式
        CodeGenerationService service = new CodeGenerationService();
        Method parseMethod = CodeGenerationService.class.getDeclaredMethod("parseAiSchemaFields", String.class);
        parseMethod.setAccessible(true);
        
        // 测试array格式
        String arraySchema = """
            {
              "requestBody": {
                "type": "array",
                "items": {
                  "type": "object",
                  "properties": {
                    "name": {
                      "type": "string",
                      "required": true,
                      "description": "名称"
                    },
                    "age": {
                      "type": "integer",
                      "required": false,
                      "description": "年龄"
                    }
                  }
                }
              }
            }
            """;
        
        // 测试object格式
        String objectSchema = """
            {
              "requestBody": {
                "type": "object",
                "properties": {
                  "name": {
                    "type": "string",
                    "required": true,
                    "description": "名称"
                  },
                  "age": {
                    "type": "integer",
                    "required": false,
                    "description": "年龄"
                  }
                }
              }
            }
            """;
        
        System.out.println("=== 测试parseAiSchemaFields方法处理两种格式 ===");
        
        // 测试array格式
        System.out.println("📝 测试array格式...");
        List<?> arrayFields = (List<?>) parseMethod.invoke(service, arraySchema);
        System.out.println("Array格式解析结果: " + arrayFields.size() + " 个字段");
        for (Object field : arrayFields) {
            System.out.println("  - " + field);
        }
        assert arrayFields.size() == 2 : "Array格式应该解析到2个字段，实际解析到" + arrayFields.size() + "个";
        
        // 测试object格式
        System.out.println("📝 测试object格式...");
        List<?> objectFields = (List<?>) parseMethod.invoke(service, objectSchema);
        System.out.println("Object格式解析结果: " + objectFields.size() + " 个字段");
        for (Object field : objectFields) {
            System.out.println("  - " + field);
        }
        assert objectFields.size() == 2 : "Object格式应该解析到2个字段，实际解析到" + objectFields.size() + "个";
        
        System.out.println("✅ 两种格式都能正确解析字段");
    }
} 