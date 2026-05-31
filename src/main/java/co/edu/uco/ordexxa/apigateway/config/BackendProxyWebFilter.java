package co.edu.uco.ordexxa.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

        final var finalTargetUrl = targetUrl;

        return readBody(exchange)
                .flatMap(bodyBytes -> forwardRequest(exchange, method, finalTargetUrl, bodyBytes))
                .timeout(Duration.ofSeconds(60))
                .onErrorResume(error -> writeProxyError(exchange, error));
    }

    private Mono<byte[]> readBody(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .map(dataBuffer -> {
                    var bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .defaultIfEmpty(new byte[0]);
    }

    private Mono<Void> forwardRequest(
            ServerWebExchange exchange,
            HttpMethod method,
            String targetUrl,
            byte[] bodyBytes
    ) {
        var request = exchange.getRequest();

        var requestSpec = webClient
                .method(method)
                .uri(targetUrl);

        requestSpec.headers(headers -> request.getHeaders().forEach((name, values) -> {
            if (!isHopByHopRequestHeader(name)) {
                headers.put(name, values);
            }
        }));

        var responseMono = shouldSendBody(method)
                ? requestSpec.bodyValue(bodyBytes).exchangeToMono(response -> writeBackendResponse(exchange, response))
                : requestSpec.exchangeToMono(response -> writeBackendResponse(exchange, response));

        return responseMono;
    }

    private Mono<Void> writeBackendResponse(ServerWebExchange exchange, ClientResponse clientResponse) {
        var response = exchange.getResponse();

        response.setStatusCode(clientResponse.statusCode());
        response.getHeaders().set("X-Ordexxa-Proxy", "DirectBackend");

        clientResponse.headers().asHttpHeaders().forEach((name, values) -> {
            if (!isManagedResponseHeader(name)) {
                response.getHeaders().put(name, values);
            }
        });

        return clientResponse.bodyToMono(byte[].class)
                .defaultIfEmpty(new byte[0])
                .flatMap(bytes -> {
                    if (bytes.length == 0) {
                        return response.setComplete();
                    }

                    var buffer = response.bufferFactory().wrap(bytes);
                    return response.writeWith(Mono.just(buffer));
                });
    }

    private Mono<Void> writeProxyError(ServerWebExchange exchange, Throwable error) {
        var response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(error);
        }

        response.setStatusCode(HttpStatus.BAD_GATEWAY);
        response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
        response.getHeaders().set("X-Ordexxa-Proxy", "Error");

        var message = "Gateway proxy error: " + error.getClass().getSimpleName();
        var bytes = message.getBytes(StandardCharsets.UTF_8);
        var buffer = response.bufferFactory().wrap(bytes);

        return response.writeWith(Mono.just(buffer));
    }

    private static boolean shouldSendBody(HttpMethod method) {
        return method == HttpMethod.POST
                || method == HttpMethod.PUT
                || method == HttpMethod.PATCH;
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
                || normalized.equals("content-encoding")
                || normalized.startsWith("access-control-");
    }
}
