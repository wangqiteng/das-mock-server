package com.dbapp.dasmockserver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class ProcessManagementService {
    
    // 存储运行中的进程
    private final Map<Long, Process> runningProcesses = new ConcurrentHashMap<>();
    
    // 存储进程ID
    private final Map<Long, Long> processIds = new ConcurrentHashMap<>();
    
    // 存储进程输出日志
    private final Map<Long, StringBuilder> processLogs = new ConcurrentHashMap<>();
    
    /**
     * 启动Mock Server进程
     */
    public boolean startMockServer(Long serviceId, String projectPath, Integer port) {
        try {
            // 检查项目路径是否存在
            Path projectDir = Paths.get(projectPath);
            if (!Files.exists(projectDir)) {
                log.error("项目路径不存在: {}", projectPath);
                return false;
            }
            
            // 检查是否已经有进程在运行
            if (runningProcesses.containsKey(serviceId)) {
                log.warn("服务 {} 已经在运行中", serviceId);
                return true;
            }
            
            // 构建启动命令
            String[] command = buildStartCommand(projectPath, port);
            
            // 创建进程构建器
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(projectDir.toFile());
            
            // 设置环境变量
            Map<String, String> env = processBuilder.environment();
            env.put("JAVA_HOME", System.getProperty("java.home"));
            env.put("PATH", System.getenv("PATH"));
            
            // 重定向错误流到标准输出
            processBuilder.redirectErrorStream(true);
            
            log.info("启动Mock Server: serviceId={}, projectPath={}, port={}", serviceId, projectPath, port);
            log.info("执行命令: {}", String.join(" ", command));
            
            // 启动进程
            Process process = processBuilder.start();
            
            // 存储进程信息
            runningProcesses.put(serviceId, process);
            processIds.put(serviceId, getProcessId(process));
            
            // 启动日志收集线程
            startLogCollector(serviceId, process);
            
            // 等待一段时间检查进程是否正常启动
            Thread.sleep(3000);
            
            if (process.isAlive()) {
                log.info("Mock Server启动成功: serviceId={}, pid={}", serviceId, getProcessId(process));
                return true;
            } else {
                log.error("Mock Server启动失败: serviceId={}", serviceId);
                cleanupProcess(serviceId);
                return false;
            }
            
        } catch (Exception e) {
            log.error("启动Mock Server时发生错误: serviceId={}", serviceId, e);
            cleanupProcess(serviceId);
            return false;
        }
    }
    
    /**Å
     * 停止Mock Server进程
     */
    public boolean stopMockServer(Long serviceId) {
        try {
            Process process = runningProcesses.get(serviceId);
            if (process == null) {
                log.warn("服务 {} 没有运行中的进程", serviceId);
                return true;
            }
            
            log.info("停止Mock Server: serviceId={}, pid={}", serviceId, getProcessId(process));
            
            // 优雅关闭进程
            process.destroy();
            
            // 等待进程结束
            boolean terminated = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            
            if (!terminated) {
                // 强制终止进程
                log.warn("优雅关闭失败，强制终止进程: serviceId={}", serviceId);
                process.destroyForcibly();
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            }
            
            cleanupProcess(serviceId);
            log.info("Mock Server停止成功: serviceId={}", serviceId);
            return true;
            
        } catch (Exception e) {
            log.error("停止Mock Server时发生错误: serviceId={}", serviceId, e);
            cleanupProcess(serviceId);
            return false;
        }
    }
    
    /**
     * 检查服务是否正在运行
     */
    public boolean isServiceRunning(Long serviceId) {
        Process process = runningProcesses.get(serviceId);
        if (process == null) {
            return false;
        }
        
        boolean alive = process.isAlive();
        if (!alive) {
            // 进程已经结束，清理资源
            cleanupProcess(serviceId);
        }
        
        return alive;
    }
    
    /**
     * 获取服务进程ID
     */
    public Long getProcessId(Long serviceId) {
        return processIds.get(serviceId);
    }
    
    /**
     * 获取服务日志
     */
    public String getServiceLog(Long serviceId) {
        StringBuilder log = processLogs.get(serviceId);
        return log != null ? log.toString() : "";
    }
    
    /**
     * 构建启动命令
     */
    private String[] buildStartCommand(String projectPath, Integer port) {
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("win")) {
            // Windows系统
            return new String[]{
                "cmd", "/c", 
                "mvn", "spring-boot:run", 
                "-Dspring-boot.run.jvmArguments=-Dserver.port=" + port
            };
        } else {
            // Unix/Linux/Mac系统
            return new String[]{
                "mvn", "spring-boot:run", 
                "-Dspring-boot.run.jvmArguments=-Dserver.port=" + port
            };
        }
    }
    
    /**
     * 获取进程ID
     */
    private Long getProcessId(Process process) {
        try {
            // 在Java 9+中，Process接口有pid()方法
            if (process.pid() > 0) {
                return process.pid();
            }
        } catch (Exception e) {
            log.warn("无法通过Process.pid()获取进程ID", e);
        }
        
        try {
            // 尝试使用反射获取进程ID（兼容性方案）
            if (process.getClass().getName().equals("java.lang.UNIXProcess")) {
                java.lang.reflect.Field pidField = process.getClass().getDeclaredField("pid");
                pidField.setAccessible(true);
                return (Long) pidField.get(process);
            } else if (process.getClass().getName().equals("java.lang.ProcessImpl")) {
                java.lang.reflect.Field pidField = process.getClass().getDeclaredField("pid");
                pidField.setAccessible(true);
                return (Long) pidField.get(process);
            }
        } catch (Exception e) {
            log.warn("无法通过反射获取进程ID", e);
        }
        
        return -1L;
    }
    
    /**
     * 启动日志收集线程
     */
    private void startLogCollector(Long serviceId, Process process) {
        Thread logThread = new Thread(() -> {
            try {
                StringBuilder logBuilder = new StringBuilder();
                processLogs.put(serviceId, logBuilder);
                
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream())
                );
                
                String line;
                while ((line = reader.readLine()) != null) {
                    logBuilder.append(line).append("\n");
                    log.debug("Mock Server {}: {}", serviceId, line);
                    
                    // 检查是否包含启动成功的标志
                    if (line.contains("Started") && line.contains("seconds")) {
                        log.info("Mock Server启动完成: serviceId={}", serviceId);
                    }
                }
            } catch (IOException e) {
                log.error("读取Mock Server日志时发生错误: serviceId={}", serviceId, e);
            }
        });
        
        logThread.setDaemon(true);
        logThread.setName("MockServer-Log-" + serviceId);
        logThread.start();
    }
    
    /**
     * 清理进程资源
     */
    private void cleanupProcess(Long serviceId) {
        runningProcesses.remove(serviceId);
        processIds.remove(serviceId);
        processLogs.remove(serviceId);
    }
} 