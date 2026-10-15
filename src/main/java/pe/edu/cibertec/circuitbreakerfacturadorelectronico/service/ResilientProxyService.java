package pe.edu.cibertec.circuitbreakerfacturadorelectronico.service;

import java.net.URI;
import java.util.Set;
import java.util.function.Supplier;

import pe.edu.cibertec.circuitbreakerfacturadorelectronico.config.ProxyProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ResilientProxyService {
	private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
	"connection", "keep-alive", "proxy-authenticate",
	"proxy-authorization", "te", "trailer", "transfer-encoding",
	"upgrade", "host", "content-length");
	
	private final RestClient restClient;
	private final ProxyProperties properties;
	private final CircuitBreakerRegistry circuitBreakerRegistry;

	public ResilientProxyService(
		RestClient.Builder builder,
	    ProxyProperties properties,
	    CircuitBreakerRegistry circuitBreakerRegistry) {
	        this.restClient = builder.build();
	        this.properties = properties;
	        this.circuitBreakerRegistry = circuitBreakerRegistry;
	    }

	public ResponseEntity<byte[]> forward(String service,
			String path, String rawQuery, HttpMethod method,
	        HttpHeaders incomingHeaders, byte[] body) {

		String baseUrl = properties.getServices().get(service);
		if (baseUrl == null || baseUrl.isBlank()) {
			return error(HttpStatus.NOT_FOUND,"UNKNOWN_SERVICE",
	        "El servicio solicitado no está configurado");
	    }
		URI target = buildTarget(baseUrl, path, rawQuery);
	    CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(service);

	    Supplier<ResponseEntity<byte[]>> operation = () -> restClient.method(method)
	    .uri(target).headers(headers -> copyRequestHeaders(incomingHeaders, headers))
	    .body(body == null ? new byte[0] : body).exchange((request, response) -> {
	    	HttpHeaders responseHeaders = new HttpHeaders();
	        response.getHeaders().forEach((name, values) -> {
	        	if (!isHopByHop(name)) {
	        		responseHeaders.put(name, values);
	            }
	        });

	        byte[] responseBody = StreamUtils.copyToByteArray(response.getBody());
	        
	        	if (response.getStatusCode().is5xxServerError()) {
	        		throw org.springframework.web.client.HttpServerErrorException.create(
	                response.getStatusCode(),response.getStatusText(),
	                response.getHeaders(),responseBody,
	                java.nio.charset.StandardCharsets.UTF_8);
	            }
	        	return ResponseEntity.status(response.getStatusCode())
	            .headers(responseHeaders).body(responseBody);
	    });
	    try {
	    	return circuitBreaker.executeSupplier(operation);
	    } catch (CallNotPermittedException ex) {
	        return error(HttpStatus.SERVICE_UNAVAILABLE,
	        "CIRCUIT_OPEN", "El servicio está temporalmente protegido por el Circuit Breaker");
	    } catch (Exception ex) {
	        return error(HttpStatus.SERVICE_UNAVAILABLE,
	        "UPSTREAM_UNAVAILABLE", "No fue posible completar la solicitud al servicio de destino");
	    }
	}

	private URI buildTarget(String baseUrl, String path, String rawQuery) {
		String normalizedBase = baseUrl.replaceAll("/+$", "");
	    String normalizedPath = path == null ? "" : path;
	    String target = normalizedBase + (normalizedPath.startsWith("/")
	    ? normalizedPath : "/" + normalizedPath);
	    
	    UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(target);

	    if (rawQuery != null && !rawQuery.isBlank()) {
	    	builder.query(rawQuery);
	    }
	    	return builder.build(true).toUri();
	}

	private void copyRequestHeaders(HttpHeaders source, HttpHeaders destination) {
		source.forEach((name, values) -> {
			if (!isHopByHop(name)) {
				destination.put(name, values);
	        }
	    });
	}

	private boolean isHopByHop(String name) {
		return HOP_BY_HOP_HEADERS.contains(name.toLowerCase(java.util.Locale.ROOT));
	}

	private ResponseEntity<byte[]> error(HttpStatus status, String code, String message) {
		String json = """
	            {"status":%d,"code":"%s","message":"%s"}
	            """.formatted(status.value(), code, message).trim();

		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
	    .body(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}
}
