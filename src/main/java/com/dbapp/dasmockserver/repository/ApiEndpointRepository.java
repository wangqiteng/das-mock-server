package com.dbapp.dasmockserver.repository;

import com.dbapp.dasmockserver.model.ApiEndpoint;
import com.dbapp.dasmockserver.model.MockService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, Long> {
    
    /**
     * 根据Mock服务查找所有端点
     */
    List<ApiEndpoint> findByMockService(MockService mockService);
    
    /**
     * 根据Mock服务ID查找所有端点
     */
    List<ApiEndpoint> findByMockServiceId(Long mockServiceId);
    
    /**
     * 根据HTTP方法查找端点
     */
    List<ApiEndpoint> findByMethod(ApiEndpoint.HttpMethod method);
    
    /**
     * 根据路径查找端点
     */
    ApiEndpoint findByPath(String path);
    
    /**
     * 根据路径和HTTP方法查找端点
     */
    ApiEndpoint findByPathAndMethod(String path, ApiEndpoint.HttpMethod method);
    
    /**
     * 根据Mock服务和HTTP方法查找端点
     */
    List<ApiEndpoint> findByMockServiceAndMethod(MockService mockService, ApiEndpoint.HttpMethod method);
    
    /**
     * 查找所有GET端点
     */
    @Query("SELECT e FROM ApiEndpoint e WHERE e.method = 'GET'")
    List<ApiEndpoint> findAllGetEndpoints();
    
    /**
     * 查找所有POST端点
     */
    @Query("SELECT e FROM ApiEndpoint e WHERE e.method = 'POST'")
    List<ApiEndpoint> findAllPostEndpoints();
} 