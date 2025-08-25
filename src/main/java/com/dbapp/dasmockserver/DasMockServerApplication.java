package com.dbapp.dasmockserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Paths;

@SpringBootApplication
@ConfigurationProperties
public class DasMockServerApplication {

	public static void main(String[] args) {
		// 创建必要的目录
		createDirectories();
		
		SpringApplication.run(DasMockServerApplication.class, args);
	}
	
	/**
	 * 创建必要的目录
	 */
	private static void createDirectories() {
		try {
			Files.createDirectories(Paths.get("./uploads"));
			Files.createDirectories(Paths.get("./generated"));
			Files.createDirectories(Paths.get("./data"));
		} catch (Exception e) {
			System.err.println("创建目录失败: " + e.getMessage());
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
