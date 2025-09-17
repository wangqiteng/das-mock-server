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
     * 优先获取物理网卡(eth/ens/enp/bond等)上的私有IPv4地址
     * 跳过回环与常见虚拟/容器网卡(docker0/br-/veth/virbr/cni/tun/tap等)
     * 
     * @return 对外的IP地址，如果获取失败则返回localhost
     */
    public String getExternalIpAddress() {
        try {
            // 第一轮：优先挑选物理网卡(eth/ens/enp/bond等)上的私有IPv4
            String preferred = findIpAddress(true);
            if (preferred != null) {
                log.debug("选择物理网卡私有IPv4地址: {}", preferred);
                return preferred;
            }

            // 第二轮：退而求其次，任意非虚拟网卡上的有效IPv4
            String anyValid = findIpAddress(false);
            if (anyValid != null) {
                log.debug("选择非虚拟网卡IPv4地址: {}", anyValid);
                return anyValid;
            }

            // 第三轮：尝试获取本机IP
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
     * 按策略查找IP地址
     * @param preferPhysicalOnly 是否仅限物理网卡且要求私有IPv4
     */
    private String findIpAddress(boolean preferPhysicalOnly) throws SocketException {
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface networkInterface = networkInterfaces.nextElement();

            // 跳过回环、未启用、虚拟/容器网卡
            if (networkInterface.isLoopback() || !networkInterface.isUp() || isVirtualOrContainerInterface(networkInterface)) {
                continue;
            }

            boolean isPhysicalPreferred = isPhysicalInterfaceName(networkInterface.getName());
            if (preferPhysicalOnly && !isPhysicalPreferred) {
                continue;
            }

            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                String ip = address.getHostAddress();

                if (isValidIpv4(ip)) {
                    if (preferPhysicalOnly) {
                        if (isPrivateIpv4(ip)) {
                            return ip;
                        }
                    } else {
                        return ip;
                    }
                }
            }
        }
        return null;
    }

    private boolean isValidIpv4(String ip) {
        if (ip == null || ip.indexOf(':') != -1) {
            return false; // 排除IPv6
        }
        // 排除环回与链路本地
        if ("127.0.0.1".equals(ip) || ip.startsWith("169.254.")) {
            return false;
        }
        return true;
    }

    private boolean isPrivateIpv4(String ip) {
        // RFC1918 私有网段：10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16
        if (ip.startsWith("10.")) return true;
        if (ip.startsWith("192.168.")) return true;
        if (ip.startsWith("172.")) {
            try {
                String[] parts = ip.split("\\.");
                int second = Integer.parseInt(parts[1]);
                return second >= 16 && second <= 31;
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }

    private boolean isVirtualOrContainerInterface(NetworkInterface nif) {
        String name = nif.getName();
        String display = nif.getDisplayName() != null ? nif.getDisplayName() : "";
        // 常见容器/虚拟网卡前缀
        String[] blockedPrefixes = new String[] {
            "lo", "docker", "br-", "veth", "virbr", "cni", "flannel", "tun", "tap", "wg", "tailscale", "zt", "ham", "kube", "podman"
        };
        for (String p : blockedPrefixes) {
            if (name.startsWith(p) || display.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPhysicalInterfaceName(String name) {
        if (name == null) return false;
        String[] physicalPrefixes = new String[] { "eth", "ens", "enp", "eno", "bond", "em" };
        for (String p : physicalPrefixes) {
            if (name.startsWith(p)) {
                return true;
            }
        }
        return false;
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