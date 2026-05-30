package co.edu.uco.ordexxa.apigateway.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class WafLiteWebFilter implements WebFilter {

    private static final Pattern SUSPICIOUS_PATTERN = Pattern.compile(
            ".*(drop\\s*table|union\\s*select|script|javascript:|onerror|onload|%2e%2e|\\.\\./).*",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        var response = exchange.getResponse();

        var path = request.getURI().getPath();
        var query = request.getURI().getRawQuery();

        boolean providerRoute = path != null && path.startsWith("/api/proveedores");
        boolean suspiciousQuery = query != null && SUSPICIOUS_PATTERN.matcher(query.toLowerCase(Locale.ROOT)).matches();

        if (providerRoute && suspiciousQuery) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            response.getHeaders().set("X-Ordexxa-WAF", "Blocked");
            return response.setComplete();
        }

        return chain.filter(exchange);
    }
}
