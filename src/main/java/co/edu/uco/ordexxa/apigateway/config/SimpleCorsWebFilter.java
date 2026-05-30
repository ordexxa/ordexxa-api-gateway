package co.edu.uco.ordexxa.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SimpleCorsWebFilter implements WebFilter {

    private final Set<String> allowedOrigins;

    public SimpleCorsWebFilter(
            @Value("${GATEWAY_CORS_ALLOWED_ORIGINS:https://ordexxa-frontend.vercel.app,http://localhost:5173,http://localhost:4173,http://localhost:8080}") String allowedOrigins
    ) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .collect(Collectors.toSet());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        var response = exchange.getResponse();
        var origin = request.getHeaders().getOrigin();

        if (origin != null && allowedOrigins.contains(origin)) {
            response.beforeCommit(() -> {
                var headers = response.getHeaders();

                headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
                headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS);
                headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
                headers.remove(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
                headers.remove(HttpHeaders.ACCESS_CONTROL_MAX_AGE);

                headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
                headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,PATCH,DELETE,OPTIONS");
                headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Authorization,Content-Type,Accept,Origin,X-Requested-With");
                headers.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Authorization,Content-Type,X-Ordexxa-WAF");
                headers.set(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");

                return Mono.empty();
            });
        }

        if (request.getMethod() == HttpMethod.OPTIONS) {
            response.setStatusCode(HttpStatus.OK);
            return response.setComplete();
        }

        return chain.filter(exchange);
    }
}
