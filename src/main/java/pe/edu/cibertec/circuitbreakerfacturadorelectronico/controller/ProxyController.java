package pe.edu.cibertec.circuitbreakerfacturadorelectronico.controller;

import pe.edu.cibertec.circuitbreakerfacturadorelectronico.service.ResilientProxyService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProxyController {
	private final ResilientProxyService proxyService;

    public ProxyController(ResilientProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @RequestMapping("/proxy/{service}/**")
    public ResponseEntity<byte[]> proxy(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body)
            throws IOException {

        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();

        if (contextPath != null && !contextPath.isEmpty()) {
            uri = uri.substring(contextPath.length());
        }

        String prefix = "/proxy/";
        String remaining = uri.substring(prefix.length());

        int separator = remaining.indexOf('/');
        String service = separator < 0
                ? remaining : remaining.substring(0, separator);

        String path = separator < 0
                ? "" : remaining.substring(separator);

        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        HttpHeaders headers = new HttpHeaders();
        java.util.Collections.list(request.getHeaderNames())
                .forEach(name -> headers.put(
                        name,
                        java.util.Collections.list(
                                request.getHeaders(name))));

        return proxyService.forward(
                service,
                path,
                request.getQueryString(),
                method,
                headers,
                body);
    }
}
