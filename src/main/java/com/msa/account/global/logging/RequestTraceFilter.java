package com.msa.account.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청별 식별자를 로그 MDC와 응답 헤더에 연결하여 같은 요청의 로그를 추적할 수 있게 합니다.
 */
@Component
public class RequestTraceFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String TRACE_HEADER = "X-Request-Id";

    @Override
    // 전달받은 요청 ID가 있으면 사용하고 없으면 새로 만들어 다음 필터로 전달합니다.
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String incomingTraceId = request.getHeader(TRACE_HEADER);
        String traceId = StringUtils.hasText(incomingTraceId)
                ? incomingTraceId
                : UUID.randomUUID().toString();

        MDC.put(TRACE_ID, traceId);
        response.setHeader(TRACE_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 서버 스레드는 재사용되므로 요청 종료 시 이전 요청의 식별자가 남지 않게 정리합니다.
            MDC.remove(TRACE_ID);
        }
    }
}
