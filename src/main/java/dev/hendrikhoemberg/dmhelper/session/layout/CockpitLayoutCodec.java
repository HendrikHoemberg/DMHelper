package dev.hendrikhoemberg.dmhelper.session.layout;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class CockpitLayoutCodec {
    private final ObjectMapper mapper;

    public CockpitLayoutCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(CockpitLayoutDocument document) {
        try {
            return mapper.writeValueAsString(document);
        } catch (tools.jackson.core.JacksonException ex) {
            throw new IllegalArgumentException("Cockpit layout JSON could not be written.", ex);
        }
    }

    public CockpitLayoutDocument read(String json) {
        try {
            return mapper.readValue(json, CockpitLayoutDocument.class);
        } catch (tools.jackson.core.JacksonException ex) {
            throw new IllegalArgumentException("Cockpit layout JSON could not be read.", ex);
        }
    }
}
