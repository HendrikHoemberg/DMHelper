package dev.hendrikhoemberg.dmhelper.session.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class SessionConfig {
    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    ZoneId applicationZoneId(@Value("${dmhelper.time-zone}") String value) {
        return ZoneId.of(value);
    }
}
