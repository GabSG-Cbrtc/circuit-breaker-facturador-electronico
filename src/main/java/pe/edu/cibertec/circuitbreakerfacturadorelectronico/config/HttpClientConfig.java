package pe.edu.cibertec.circuitbreakerfacturadorelectronico.config;

import java.time.Duration;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class HttpClientConfig {
	@Bean
    RestClientCustomizer restClientTimeoutCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory =
                    new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(2));
            factory.setReadTimeout(Duration.ofSeconds(5));
            builder.requestFactory(factory);
        };
	}
}
