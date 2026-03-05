package com.skyhigh.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolves a logical "source" identifier for abuse detection / rate limiting.
 * <p>
 * Current strategy:
 * - Prefer the first value from X-Forwarded-For (client IP behind proxies)
 * - Fallback to X-Real-IP header if present
 * - Finally, use HttpServletRequest#getRemoteAddr()
 */
@Component
public class RequestSourceResolver {

    public String resolveSource(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            String first = xForwardedFor.split(",")[0].trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }

        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp;
        }

        return request.getRemoteAddr();
    }
}

