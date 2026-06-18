package com.dbapp.dasmockserver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ProcessManagementService {
    
    // 存储运行中的进程
    private final Map<Long, Process> runningProcesses = new ConcurrentHashMap<>();
    
    // 存储进程ID
    private final Map<Long, Long> processIds = new ConcurrentHashMap<>();
    
    // 存储进程输出日志
    private final Map<Long, StringBuilder> processLogs = new ConcurrentHashMap<>();
    
    // 存储服务端口信息
    private final Map<Long, Integer> servicePorts = new ConcurrentHashMap<>();

    /**
     * 端口重试上限：超过此次数认为从首选端口起无足够可用端口。
     * 避免在端口被密集占用场景下陷入死循环。
     */
    private static final int MAX_PORT_RETRY = 500;
    
    /**
     * 初始化时清理可能残留的进程
     */
    public void cleanupOrphanedProcesses() {
        log.info("开始清理可能残留的Mock Server进程...");
        
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String[] command;
            
            if (os.contains("win")) {
                // Windows系统查找Maven和Java进程
                command = new String[]{"cmd", "/c", "tasklist", "/FI", "IMAGENAME eq java.exe", "/FO", "CSV"};
            } else {
                // Unix/Linux/Mac系统查找Java进程
                command = new String[]{"ps", "aux", "|", "grep", "java"};
            }
            
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();
            
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream())
            );

            String line;
            int suspectCount = 0;
            while ((line = reader.readLine()) != null) {
                // 检查是否包含Spring Boot相关的进程（精简日志：只统计数量，发现可疑进程时才打 warn）
                if (line.contains("spring-boot:run") || line.contains("MockController")) {
                    suspectCount++;
                    if (suspectCount <= 3) {
                        log.warn("发现可能残留的Mock Server进程: {}", line);
                    }
                }
            }

            process.waitFor();
            if (suspectCount > 3) {
                log.warn("可能残留的Mock Server进程共 {} 个（仅显示前 3 条）", suspectCount);
            }
            log.info("进程清理检查完成，共扫描 {} 条匹配项", suspectCount);
            
        } catch (Exception e) {
            log.error("清理残留进程时发生错误", e);
        }
    }
    
    /**
     * 启动Mock Server进程
     * @return 实际使用的端口号（端口冲突时自动递增寻找），失败返回 null
     */
    public Integer startMockServer(Long serviceId, String projectPath, Integer preferredPort) {
        try {
            // 检查项目路径是否存在
            Path projectDir = Paths.get(projectPath);
            if (!Files.exists(projectDir)) {
                log.error("项目路径不存在: {}", projectPath);
                return null;
            }

            // 检查是否已经有进程在运行
            if (runningProcesses.containsKey(serviceId)) {
                log.warn("服务 {} 已经在运行中", serviceId);
                Integer existingPort = servicePorts.get(serviceId);
                return existingPort != null ? existingPort : preferredPort;
            }

            // 端口冲突检测与自动重选
            Integer actualPort = resolveAvailablePort(preferredPort);
            if (actualPort == null) {
                log.error("无法为服务 {} 找到可用端口（首选端口: {}，已达最大重试次数）", serviceId, preferredPort);
                return null;
            }
            if (!actualPort.equals(preferredPort)) {
                log.warn("首选端口 {} 被占用，服务 {} 自动切换到端口 {}", preferredPort, serviceId, actualPort);
            }

            // 构建启动命令
            String[] command = buildStartCommand(projectPath, actualPort);
            
            // 创建进程构建器
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(projectDir.toFile());
            
            // 设置环境变量
            Map<String, String> env = processBuilder.environment();
            env.put("JAVA_HOME", System.getProperty("java.home"));
            env.put("PATH", System.getenv("PATH"));
            
            // 重定向错误流到标准输出
            processBuilder.redirectErrorStream(true);
            
            log.info("启动Mock Server: serviceId={}, projectPath={}, port={}", serviceId, projectPath, actualPort);
            log.info("执行命令: {}", String.join(" ", command));

            // 启动进程
            Process process = processBuilder.start();

            // 存储进程信息
            runningProcesses.put(serviceId, process);

            // 启动日志收集线程
            startLogCollector(serviceId, process);

            // 获取实际的Java进程ID
            Long javaPid = -1L;
            int waitTimeOut = 0;
            while(waitTimeOut < 30 && javaPid < 0){
                // 循环等待等待一段时间让Java进程启动
                log.info("正在等待Mock Server启动: serviceId={}, port={}", serviceId, actualPort);
                Thread.sleep(1000);
                waitTimeOut++;
                javaPid = findJavaProcessId(serviceId, actualPort);
            }

            if (javaPid > 0) {
                processIds.put(serviceId, javaPid);
                servicePorts.put(serviceId, actualPort);
                log.info("Mock Server启动成功: serviceId={}, javaPid={}, port={}", serviceId, javaPid, actualPort);
                return actualPort;
            } else {
                log.error("Mock Server启动失败，未找到Java进程: serviceId={}", serviceId);
                cleanupProcess(serviceId);
                return null;
            }

        } catch (Exception e) {
            log.error("启动Mock Server时发生错误: serviceId={}", serviceId, e);
            cleanupProcess(serviceId);
            return null;
        }
    }

    /**
     * 检测首选端口是否被占用；若被占用，从下一个端口起递增寻找可用端口。
     * 最多尝试 {@link #MAX_PORT_RETRY} 次，避免无限循环。
     */
    private Integer resolveAvailablePort(Integer preferredPort) {
        if (preferredPort == null) {
            return null;
        }
        if (!isPortInUse(preferredPort)) {
            return preferredPort;
        }
        int tryPort = preferredPort + 1;
        int maxPort = 65535;
        for (int tried = 0; tried < MAX_PORT_RETRY && tryPort <= maxPort; tried++, tryPort++) {
            if (!isPortInUse(tryPort)) {
                return tryPort;
            }
        }
        return null;
    }

    /**
     * 查找一个系统级未被占用且不在 excludePorts 中的可用端口。
     * 主要用于服务创建时的初始端口分配。
     *
     * @param startPort    起始端口（包含）
     * @param excludePorts 需排除的端口集合（如数据库中已记录的端口），可传 null
     * @return 可用端口；找不到返回 null
     */
    public Integer findAvailablePort(int startPort, Set<Integer> excludePorts) {
        int tryPort = Math.max(1024, startPort);
        int maxPort = 65535;
        for (int tried = 0; tried < MAX_PORT_RETRY && tryPort <= maxPort; tried++, tryPort++) {
            boolean inExclude = excludePorts != null && excludePorts.contains(tryPort);
            if (!inExclude && !isPortInUse(tryPort)) {
                return tryPort;
            }
        }
        return null;
    }

    /**
     * 一键停止所有运行中的Mock Server
     *
     * @return 已成功停止的服务ID列表
     */
    public List<Long> stopAllMockServers() {
        List<Long> stoppedIds = new ArrayList<>();
        // 复制 keySet 避免 stopMockServer 内清理时出现并发修改异常
        List<Long> ids = new ArrayList<>(runningProcesses.keySet());
        Collections.sort(ids);
        log.info("一键停止所有Mock Server，待停止数量: {}", ids.size());
        for (Long serviceId : ids) {
            try {
                if (stopMockServer(serviceId)) {
                    stoppedIds.add(serviceId);
                }
            } catch (Exception e) {
                log.error("停止服务 {} 失败: {}", serviceId, e.getMessage(), e);
            }
        }
        log.info("一键停止完成，成功停止 {} 个服务: {}", stoppedIds.size(), stoppedIds);
        return stoppedIds;
    }
    
    /**Å
     * 停止Mock Server进程
     */
    public boolean stopMockServer(Long serviceId) {
        try {
            Process process = runningProcesses.get(serviceId);
            Long javaPid = processIds.get(serviceId);
            
            if (process == null && javaPid == null) {
                log.warn("服务 {} 没有运行中的进程", serviceId);
                return true;
            }
            
            log.info("停止Mock Server: serviceId={}, javaPid={}", serviceId, javaPid);

            // 尝试强制终止Java进程
            if (javaPid != null && javaPid > 0) {
                log.warn("尝试强制终止Java进程: serviceId={}, javaPid={}", serviceId, javaPid);
                forceKillProcess(javaPid);
                Thread.sleep(2000);
            }
            
            // 尝试强制终止cmd进程
            if (process != null) {
                log.warn("尝试强制终止cmd进程: serviceId={}", serviceId);
                process.destroyForcibly();
                Thread.sleep(1000);
                process.isAlive();
            }
            
            // 最终检查
            boolean javaStillRunning = javaPid != null && javaPid > 0 && isProcessReallyRunning(javaPid);
            boolean cmdStillRunning = process != null && process.isAlive();
            
            log.info("最终检查结果: serviceId={}, javaStillRunning={}, cmdStillRunning={}", 
                serviceId, javaStillRunning, cmdStillRunning);
            
            if (javaStillRunning || cmdStillRunning) {
                log.error("无法完全停止Mock Server进程: serviceId={}, javaStillRunning={}, cmdStillRunning={}", 
                    serviceId, javaStillRunning, cmdStillRunning);
                cleanupProcess(serviceId);
                return false;
            } else {
                log.info("Mock Server停止成功: serviceId={}", serviceId);
                cleanupProcess(serviceId);
                return true;
            }
            
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
        Long javaPid = processIds.get(serviceId);
        Process process = runningProcesses.get(serviceId);
        
        // 优先检查Java进程是否在运行
        if (javaPid != null && javaPid > 0) {
            boolean javaRunning = isProcessReallyRunning(javaPid);
            if (!javaRunning) {
                // Java进程已停止，清理资源
                cleanupProcess(serviceId);
                return false;
            }
            return true;
        }
        
        // 如果没有Java进程ID，检查cmd进程
        if (process != null) {
            boolean alive = process.isAlive();
            if (!alive) {
                // 进程已经结束，清理资源
                cleanupProcess(serviceId);
            }
            return alive;
        }
        
        return false;
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
     * 获取所有运行中的服务信息
     */
    public Map<String, Object> getAllRunningServices() {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("runningProcesses", runningProcesses.size());
        result.put("processIds", processIds.size());
        result.put("processLogs", processLogs.size());
        result.put("servicePorts", servicePorts.size());
        
        Map<String, Object> services = new java.util.HashMap<>();
        for (Map.Entry<Long, Process> entry : runningProcesses.entrySet()) {
            Long serviceId = entry.getKey();
            Process process = entry.getValue();
            Long pid = processIds.get(serviceId);
            Integer port = servicePorts.get(serviceId);
            
            Map<String, Object> serviceInfo = new java.util.HashMap<>();
            serviceInfo.put("serviceId", serviceId);
            serviceInfo.put("pid", pid);
            serviceInfo.put("port", port);
            serviceInfo.put("isAlive", process.isAlive());
            serviceInfo.put("isReallyRunning", pid > 0 ? isProcessReallyRunning(pid) : false);
            serviceInfo.put("portInUse", port != null ? isPortInUse(port) : false);
            
            services.put(serviceId.toString(), serviceInfo);
        }
        
        result.put("services", services);
        return result;
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
     * 查找Java进程ID
     */
    private Long findJavaProcessId(Long serviceId, Integer port) {
        try {
            // 首先尝试通过netstat查找占用端口的进程
            Long pidFromNetstat = findProcessIdByPort(port);
            if (pidFromNetstat > 0) {
                log.info("通过netstat找到端口 {} 对应的进程ID: {}", port, pidFromNetstat);
                return pidFromNetstat;
            }
            
            // 如果netstat没有找到，返回-1表示未找到进程
            log.warn("暂未找到端口 {} 对应的Java进程", port);
            return -1L;
            
        } catch (Exception e) {
            log.error("查找Java进程ID时发生错误: serviceId={}, port={}", serviceId, port, e);
            return -1L;
        }
    }
    
    /**
     * 通过netstat命令查找占用指定端口的进程ID
     */
    private Long findProcessIdByPort(Integer port) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String[] command;
            
            if (os.contains("win")) {
                // Windows系统使用netstat -ano查找端口占用
                command = new String[]{"cmd", "/c", "netstat", "-ano", "|", "findstr", ":" + port};
            } else if (os.contains("mac")){
                command = new String[]{"lsof", "-P", "-i", ":" + port};
            } else {
                // Unix/Linux系统使用netstat -tulpn查找端口占用
                command = new String[]{"netstat", "-tulpn", "|", "grep", ":" + port};
            }
            
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();
            
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream())
            );
            
            String line;
            int scannedLines = 0;
            while ((line = reader.readLine()) != null) {
                // 匹配占用端口的进程（精简日志：不再逐行打印 netstat/lsof 输出）
                if (os.contains("win")) {
                    // Windows格式: TCP 0.0.0.0:8080 0.0.0.0:0 LISTENING 1234
                    if (line.contains(":" + port) && line.contains("LISTENING")) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 5) {
                            try {
                                Long pid = Long.parseLong(parts[parts.length - 1]);
                                log.info("通过netstat找到Windows进程ID: {} (端口: {})", pid, port);
                                return pid;
                            } catch (NumberFormatException e) {
                                log.warn("无法解析Windows netstat进程ID: {}", parts[parts.length - 1]);
                            }
                        }
                    }
                } else if (os.contains("mac")){
                    if (line.contains(":" + port) && line.contains("LISTEN")) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 7) {
                            String pidStr = parts[1];
                            try {
                                Long pid = Long.parseLong(pidStr);
                                log.info("通过lsof找到Mac进程ID: {} (端口: {})", pid, port);
                                return pid;
                            } catch (NumberFormatException e) {
                                log.warn("无法解析lsof进程ID: {}", pidStr);
                            }
                        }
                    }
                } else {
                    // Unix格式: tcp 0 0 0.0.0.0:8080 0.0.0.0:* LISTEN 1234/java
                    if (line.contains(":" + port) && line.contains("LISTEN")) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 7) {
                            String pidPart = parts[parts.length - 1];
                            if (pidPart.contains("/")) {
                                String pidStr = pidPart.split("/")[0];
                                try {
                                    Long pid = Long.parseLong(pidStr);
                                    log.info("通过netstat找到Unix进程ID: {} (端口: {})", pid, port);
                                    return pid;
                                } catch (NumberFormatException e) {
                                    log.warn("无法解析Unix netstat进程ID: {}", pidStr);
                                }
                            }
                        }
                    }
                }
            }
            
            process.waitFor();
            log.debug("netstat未找到端口 {} 的占用进程", port);
            return -1L;
            
        } catch (Exception e) {
            log.error("通过netstat查找进程ID时发生错误: port={}", port, e);
            return -1L;
        }
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
        servicePorts.remove(serviceId);
    }
    
    /**
     * 通过系统命令强制终止进程
     */
    private void forceKillProcess(Long pid) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String[] command;
            
            if (os.contains("win")) {
                // Windows系统使用taskkill命令
                command = new String[]{"cmd", "/c", "taskkill", "/F", "/PID", String.valueOf(pid)};
            } else {
                // Unix/Linux/Mac系统使用kill命令
                command = new String[]{"kill", "-9", String.valueOf(pid)};
            }
            
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process killProcess = processBuilder.start();
            killProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            
            log.info("执行强制终止命令: {}", String.join(" ", command));
            
        } catch (Exception e) {
            log.error("强制终止进程失败: pid={}", pid, e);
        }
    }
    
    /**
     * 通过系统命令强制终止进程树
     */
    private void forceKillProcessTree(Long pid) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String[] command;
            
            if (os.contains("win")) {
                // Windows系统使用taskkill命令终止进程树
                command = new String[]{"cmd", "/c", "taskkill", "/F", "/T", "/PID", String.valueOf(pid)};
            } else {
                // Unix/Linux/Mac系统使用pkill命令终止进程树
                command = new String[]{"pkill", "-9", "-P", String.valueOf(pid)};
            }
            
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process killProcess = processBuilder.start();
            killProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            
            log.info("执行强制终止进程树命令: {}", String.join(" ", command));
            
        } catch (Exception e) {
            log.error("强制终止进程树失败: pid={}", pid, e);
        }
    }
    
    /**
     * 检查进程是否真的在运行（通过系统命令）
     */
    private boolean isProcessReallyRunning(Long pid) {
        if (pid <= 0) {
            return false;
        }
        
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String[] command;
            
            if (os.contains("win")) {
                // Windows系统使用tasklist命令
                command = new String[]{"cmd", "/c", "tasklist", "/FI", "PID eq " + pid, "/FO", "CSV"};
            } else {
                // Unix/Linux/Mac系统使用ps命令
                command = new String[]{"ps", "-p", String.valueOf(pid)};
            }
            
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();
            
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream())
            );
            
            String line;
            boolean found = false;
            while ((line = reader.readLine()) != null) {
                if (line.contains(String.valueOf(pid))) {
                    found = true;
                    break;
                }
            }
            
            process.waitFor();
            return found;
            
        } catch (Exception e) {
            log.error("检查进程是否运行时发生错误: pid={}", pid, e);
            return false;
        }
    }
    
    /**
     * 检查端口是否被占用（通过netstat命令）
     */
    private boolean isPortInUse(Integer port) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String[] command;
            
            if (os.contains("win")) {
                // Windows系统使用netstat -ano查找端口占用
                command = new String[]{"cmd", "/c", "netstat", "-ano", "|", "findstr", ":" + port};
            } else {
                // Unix/Linux/Mac系统使用netstat -tulpn查找端口占用
                command = new String[]{"netstat", "-tulpn", "|", "grep", ":" + port};
            }
            
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();
            
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream())
            );
            
            // 静默读取端口扫描结果，仅在调试级别需要时开启
            String line;
            boolean portInUse = false;
            while ((line = reader.readLine()) != null) {
                if (os.contains("win")) {
                    // Windows格式: TCP 0.0.0.0:8080 0.0.0.0:0 LISTENING 1234
                    if (line.contains(":" + port) && line.contains("LISTENING")) {
                        portInUse = true;
                        break;
                    }
                } else {
                    // Unix格式: tcp 0 0 0.0.0.0:8080 0.0.0.0:* LISTEN 1234/java
                    if (line.contains(":" + port) && line.contains("LISTEN")) {
                        portInUse = true;
                        break;
                    }
                }
            }
            
            process.waitFor();
            return portInUse;

        } catch (Exception e) {
            log.error("检查端口占用状态时发生错误: port={}", port, e);
            return false;
        }
    }
} 