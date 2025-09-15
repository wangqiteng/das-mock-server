package com.dbapp.dasmockserver.config;

import com.dbapp.dasmockserver.service.ProcessManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProcessCleanupConfig implements ApplicationRunner {
    
    @Autowired
    private ProcessManagementService processManagementService;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("应用启动，开始清理可能残留的Mock Server进程...");
        processManagementService.cleanupOrphanedProcesses();
    }
}
