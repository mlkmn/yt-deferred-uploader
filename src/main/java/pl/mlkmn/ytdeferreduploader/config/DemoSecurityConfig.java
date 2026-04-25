package pl.mlkmn.ytdeferreduploader.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import pl.mlkmn.ytdeferreduploader.filter.DemoAutoLoginFilter;

@Configuration
@ConditionalOnProperty(name = "app.mode", havingValue = "DEMO")
public class DemoSecurityConfig {

    @Bean
    public SecurityFilterChain demoSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .addFilterBefore(new DemoAutoLoginFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .permitAll()
                )
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(31536000)
                                .includeSubDomains(true)
                        )
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .contentTypeOptions(cto -> {})
                );
        return http.build();
    }
}
