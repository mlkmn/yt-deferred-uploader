package pl.mlkmn.ytdeferreduploader.devtools;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("devtools")
@ConditionalOnProperty(name = "app.mode", havingValue = "DEMO")
public class DevtoolsConfig {

    @Bean
    public Sleeper sleeper() {
        return Thread::sleep;
    }
}
