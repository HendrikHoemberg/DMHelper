package dev.hendrikhoemberg.dmhelper.common.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.HiddenHttpMethodFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final PinManager pinManager;

    @Value("${dmhelper.pin-enabled:true}")
    private boolean pinEnabled;

    public WebMvcConfig(ObjectProvider<PinManager> pinManagerProvider) {
        this.pinManager = pinManagerProvider.getIfAvailable();
    }

    @Bean
    HiddenHttpMethodFilter hiddenHttpMethodFilter() {
        return new HiddenHttpMethodFilter();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/vendor/**")
                .addResourceLocations("classpath:/static/vendor/")
                .setCachePeriod(31536000);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (pinEnabled && pinManager != null) {
            registry.addInterceptor(new PinInterceptor(pinManager))
                    .addPathPatterns("/**")
                    .excludePathPatterns(
                            "/dm/authenticate",
                            "/css/**", "/js/**", "/vendor/**", "/fonts/**",
                            "/api/v1/schemas/**",
                            "/api/v1/catalog/**",
                            "/api/v1/capabilities",
                            "/api/v1/validation-errors",
                            "/error",
                            "/favicon.ico"
                    );
        }
    }
}
