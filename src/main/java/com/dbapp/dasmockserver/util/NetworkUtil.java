package com.dbapp.dasmockserver.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * 网络工具类，用于获取对外的IP地址
 */
@Slf4j
@Component
public class NetworkUtil {
    
    /**
     * 获取对外的IP地址
     * 优先获取非回环、非本地链接的IPv4地址
     * 
     * @return 对外的IP地址，如果获取失败则返回localhost
     */
    public String getExternalIpAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                
                // 跳过回环接口和未启用的接口
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    
                    // 只获取IPv4地址，跳过IPv6和本地链接地址
                    if (address.getHostAddress().indexOf(':') == -1 && 
                        !address.isLinkLocalAddress() && 
                        !address.isLoopbackAddress()) {
                        
                        String ipAddress = address.getHostAddress();
                        log.debug("找到对外IP地址: {}", ipAddress);
                        return ipAddress;
                    }
                }
            }
            
            // 如果没有找到合适的IP地址，尝试获取本机IP
            String localIp = getLocalIpAddress();
            if (localIp != null) {
                log.debug("使用本机IP地址: {}", localIp);
                return localIp;
            }
            
        } catch (SocketException e) {
            log.warn("获取网络接口信息失败: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("获取IP地址时发生异常: {}", e.getMessage());
        }
        
        // 如果所有方法都失败，返回localhost
        log.warn("无法获取对外IP地址，使用localhost");
        return "localhost";
    }
    
    /**
     * 获取本机IP地址
     */
    private String getLocalIpAddress() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            String hostAddress = localHost.getHostAddress();
            
            // 检查是否是有效的IP地址
            if (hostAddress != null && !hostAddress.equals("127.0.0.1")) {
                return hostAddress;
            }
        } catch (Exception e) {
            log.debug("获取本机IP地址失败: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * 构建完整的服务URL
     * 
     * @param port 服务端口
     * @return 完整的服务URL
     */
    public String buildServiceUrl(int port) {
        String ipAddress = getExternalIpAddress();
        return "http://" + ipAddress + ":" + port;
    }
} 