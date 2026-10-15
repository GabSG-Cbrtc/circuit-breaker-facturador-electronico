package pe.edu.cibertec.circuitbreakerfacturadorelectronico.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "proxy")
public class ProxyProperties {
	private Map<String, String> services = new HashMap<>();

    public Map<String, String> getServices() {
        return services;
    }

    public void setServices(Map<String, String> services) {
        this.services = services;
    }
}
