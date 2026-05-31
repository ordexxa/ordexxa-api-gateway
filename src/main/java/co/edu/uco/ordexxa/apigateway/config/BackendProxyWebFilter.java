package co.edu.uco.ordexxa.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Locale;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class BackendProxyWebFilter implements WebFilter {

    private final WebClient webClient;
    private final String backendBaseUrl;

    public BackendProxyWebFilter(
            WebClient.Builder webClientBuilder,
            @Value("${BACKEND_BASE_URL:http://localhost:8081}") String backendBaseUrl
    ) {
        this.webClient = webClientBuilder.build();
        this.backendBaseUrl = removeTrailingSlash(backendBaseUrl);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        var path = request.getURI().getRawPath();

        if (path == null || !path.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        var method = request.getMethod();
        if (method == null || method == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        var targetUrl = backendBaseUrl + path;
        var rawQuery = request.getURI().getRawQuery();
        if (rawQuery != null && !rawQuery.isBlank()) {
            targetUrl = targetUrl + "?" + rawQuery;
        }

        return webClient
                .method(method)
                .uri(targetUrl)
                .headers(headers -> request.getHeaders().forEach((name, values) -> {
                    if (!isHopByHopRequestHeader(name)) {
                        headers.put(name, values);
                    }
                }))
                .body(BodyInserters.fromDataBuffers(request.getBody()))
                .exchangeToMono(clientResponse -> {
                    var response = exchange.getResponse();
                    response.setStatusCode(clientResponse.statusCode());

                    clientResponse.headers().asHttpHeaders().forEach((name, values) -> {
                        if (!isManagedResponseHeader(name)) {
                            response.getHeaders().put(name, values);
                        }
                    });

                    Flux<DataBuffer> body = clientResponse.bodyToFlux(DataBuffer.class);
                    return response.writeWith(body);
                });
    }

    private static String removeTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8081";
        }

        var cleaned = value.trim();
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    private static boolean isHopByHopRequestHeader(String name) {
        var normalized = name.toLowerCase(Locale.ROOT);
        return normalized.equals("host")
                || normalized.equals("connection")
                || normalized.equals("content-length")
                || normalized.equals("transfer-encoding")
                || normalized.equals("upgrade");
    }

    private static boolean isManagedResponseHeader(String name) {
        var normalized = name.toLowerCase(Locale.ROOT);
        return normalized.equals("connection")
                || normalized.equals("content-length")
                || normalized.equals("transfer-encoding")
                || normalized.startsWith("access-control-");
    }
}
