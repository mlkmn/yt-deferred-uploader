package pl.mlkmn.ytdeferreduploader.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "server.forward-headers-strategy", havingValue = "native")
public class ForwardedHostPortConfig {

    // A context valve always runs after the engine-level RemoteIpValve that Spring Boot installs.
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> forwardedHostPortValve(ServerProperties server) {
        ServerProperties.Tomcat.Remoteip remoteIp = server.getTomcat().getRemoteip();
        return factory -> factory.addContextValves(new ForwardedHostPortValve(
                remoteIp.getInternalProxies(), remoteIp.getHostHeader(), remoteIp.getPortHeader()));
    }
}
