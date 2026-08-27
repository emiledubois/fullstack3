package com.ecommerce.api.gateway.filter;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.*;

// Runs after AuthFilter has already validated the sl_jwt cookie and derived
// X-User-Email from it. Removes the raw Cookie header before proxying to the
// downstream microservices — they never need the JWT itself, only the
// verified identity in X-User-Email (same minimization posture as that
// header's anti-spoofing strip).
@Component
public class StripCookieFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    @Override
    public ServerResponse filter(ServerRequest req, HandlerFunction<ServerResponse> next)
            throws Exception {
        ServerRequest modified = ServerRequest.from(req)
            .headers(headers -> headers.remove(HttpHeaders.COOKIE))
            .build();
        return next.handle(modified);
    }
}
