package dev.hendrikhoemberg.dmhelper.handout.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HandoutControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private jakarta.persistence.EntityManager em;

    private UUID campaignId;

    @BeforeEach
    void setUp() {
        Campaign c = new Campaign();
        c.setName("Test Campaign");
        em.persist(c);
        em.flush();
        campaignId = c.getId();
    }

    @Test
    void shouldShowHandoutGalleryPage() throws Exception {
        mvc.perform(get("/campaigns/" + campaignId + "/handouts"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Handouts")));
    }

    @Test
    void shouldUploadHandout() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "letter.png", "image/png", "fake-png-data".getBytes());

        mvc.perform(multipart("/campaigns/" + campaignId + "/handouts")
                        .file(file)
                        .param("title", "The Regent's Letter")
                        .param("tags", "quest, regent"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/handouts"));
    }

    @Test
    void shouldDeleteHandout() throws Exception {
        Handout h = new Handout();
        h.setCampaign(em.find(Campaign.class, campaignId));
        h.setTitle("Test");
        h.setFileName("test.png");
        h.setContentType("image/png");
        em.persist(h);
        em.flush();

        mvc.perform(delete("/campaigns/" + campaignId + "/handouts/" + h.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect", "/campaigns/" + campaignId + "/handouts"));
    }
}
