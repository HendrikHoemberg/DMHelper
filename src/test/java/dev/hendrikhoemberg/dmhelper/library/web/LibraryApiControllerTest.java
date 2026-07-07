package dev.hendrikhoemberg.dmhelper.library.web;

import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.service.SpellSeedService;
import dev.hendrikhoemberg.dmhelper.library.service.SrdSeedService;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LibraryApiController.class)
class LibraryApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private StatBlockService service;
    @MockitoBean private SrdSeedService srdSeedService;
    @MockitoBean private SpellSeedService spellSeedService;

    private StatBlock srd(String key) {
        StatBlock sb = new StatBlock();
        sb.setSource(StatBlock.Source.SRD);
        sb.setSourceKey(key);
        return sb;
    }

    private StatBlock custom() {
        StatBlock sb = new StatBlock();
        sb.setSource(StatBlock.Source.CUSTOM);
        sb.setSourceKey("should-not-appear");
        return sb;
    }

    @Test
    void listsSortedSrdKeysOnly() throws Exception {
        when(service.findAll()).thenReturn(List.of(srd("goblin"), custom(), srd("aboleth")));
        mockMvc.perform(get("/api/v1/library/srd-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("aboleth"))
                .andExpect(jsonPath("$[1]").value("goblin"))
                .andExpect(jsonPath("$.length()").value(2));
    }
}
