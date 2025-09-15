package com.dbapp.dasmockserver.repository;

import com.dbapp.dasmockserver.model.MockService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    
    /**
     * 分页查询服务，支持关键词搜索
     */
    @Query("SELECT m FROM MockService m WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           "LOWER(m.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(m.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(m.tags) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
           "(:status IS NULL OR :status = '' OR m.status = :status) AND " +
           "(:tag IS NULL OR :tag = '' OR LOWER(m.tags) LIKE LOWER(CONCAT('%', :tag, '%')))")
    Page<MockService> findServicesWithFilters(@Param("keyword") String keyword,
                                            @Param("status") MockService.ServiceStatus status,
                                            @Param("tag") String tag,
                                            Pageable pageable);
    
    /**
     * 获取所有标签
     */
    @Query("SELECT m.tags FROM MockService m WHERE m.tags IS NOT NULL AND m.tags != ''")
    List<String> findAllTags();
} 