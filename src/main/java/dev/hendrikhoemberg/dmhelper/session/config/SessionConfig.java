package dev.hendrikhoemberg.dmhelper.session.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class SessionConfig {
    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
