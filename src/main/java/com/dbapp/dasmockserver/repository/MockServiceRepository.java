package com.dbapp.dasmockserver.repository;

import com.dbapp.dasmockserver.model.MockService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MockServiceRepository extends JpaRepository<MockService, Long> {
    
    /**
     * 根据状态查找服务
     */
    List<MockService> findByStatus(MockService.ServiceStatus status);
    
    /**
     * 根据名称查找服务
     */
    MockService findByName(String name);
    
    /**
     * 根据名称模糊查找服务
     */
    List<MockService> findByNameContainingIgnoreCase(String name);
    
    /**
     * 查找运行中的服务
     */
    @Query("SELECT m FROM MockService m WHERE m.status = 'RUNNING'")
    List<MockService> findRunningServices();
    
    /**
     * 根据端口查找服务
     */
    MockService findByPort(Integer port);
} 