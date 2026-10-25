package com.xiehe.common;

import javax.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 获取工具
 *
 * 处理反向代理(Nginx)与直连两种场景:
 * - 直连: request.getRemoteAddr()
 * - 经 Nginx 代理: 真实 IP 在 X-Forwarded-For 请求头(逗号分隔,取第一个)
 */
public final class IpUtil {

    private IpUtil() {
        // 工具类禁止实例化
    }

    /** 获取客户端真实 IP(防御 X-Forwarded-For 伪造的最低限度: 仅取第一个) */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // XFF 格式: client, proxy1, proxy2 -> 取第一个
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty() && !"unknown".equalsIgnoreCase(first)) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
