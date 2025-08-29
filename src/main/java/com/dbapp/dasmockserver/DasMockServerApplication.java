package com.dbapp.dasmockserver;

import com.dbapp.dasmockserver.config.AiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@SpringBootApplication
@ConfigurationProperties
public class DasMockServerApplication {

	public static void main(String[] args) {
		// 创建必要的目录
		createDirectories();
		
		ApplicationContext context = SpringApplication.run(DasMockServerApplication.class, args);
		
		// 打印AI配置信息
		AiConfig aiConfig = context.getBean(AiConfig.class);
		log.info("AI配置信息: {}", aiConfig.getCurrentConfig());
	}
	
	/**
	 * 创建必要的目录
	 */
	private static void createDirectories() {
		try {
			// 创建生成文件的目录
			Path generatedPath = Paths.get("generated");
			if (!Files.exists(generatedPath)) {
				Files.createDirectories(generatedPath);
			}
			
			// 创建日志目录
			Path logsPath = Paths.get("logs");
			if (!Files.exists(logsPath)) {
				Files.createDirectories(logsPath);
			}
		} catch (Exception e) {
			log.error("创建目录失败: {}", e.getMessage());
		}
	}
	
	/**
	 * 配置CORS
	 */
	@Bean
	public WebMvcConfigurer corsConfigurer() {
		return new WebMvcConfigurer() {
			@Override
			public void addCorsMappings(CorsRegistry registry) {
				registry.addMapping("/**")
						.allowedOrigins("*")
						.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
						.allowedHeaders("*")
						.maxAge(3600);
			}
		};
	}
}
