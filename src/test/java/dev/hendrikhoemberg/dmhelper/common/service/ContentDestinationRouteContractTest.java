package dev.hendrikhoemberg.dmhelper.common.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.server.PathContainer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ContentDestinationRouteContractTest {
    @Autowired private ContentDestinationRegistry registry;
    @Autowired private RequestMappingHandlerMapping handlerMapping;

    private final UUID campaignId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID entityId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID adventureId = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void everyGeneratedDestinationMatchesARegisteredControllerRoute() {
        Set<String> registered = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .collect(Collectors.toSet());
        PathPatternParser parser = new PathPatternParser();

        var campaignUrls = Arrays.stream(ContentDestinationRegistry.CampaignType.values())
                .map(type -> registry.campaign(type, campaignId, entityId, adventureId));
        var libraryUrls = Arrays.stream(ContentDestinationRegistry.LibraryType.values())
                .map(type -> registry.library(type, entityId, "source-key", "Display Name"));

        java.util.stream.Stream.concat(campaignUrls, libraryUrls).forEach(url -> {
            String path = URI.create(url).getPath();
            boolean matched = registered.stream()
                    .map(parser::parse)
                    .anyMatch(pattern -> pattern.matches(PathContainer.parsePath(path)));
            assertThat(matched).as("registered controller route for %s", url).isTrue();
        });
    }
}
