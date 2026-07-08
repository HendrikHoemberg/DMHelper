package dev.hendrikhoemberg.dmhelper.handout.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(HandoutService.class)
class HandoutServiceTest {

    @Autowired private HandoutService service;
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
    void shouldCreateAndFindHandout() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "map.png", "image/png", "fake-data".getBytes());
        Handout h = service.create(campaignId, "The Map", "quest", file);

        assertThat(h.getId()).isNotNull();
        assertThat(h.getTitle()).isEqualTo("The Map");
        assertThat(h.getTags()).isEqualTo("quest");
        assertThat(h.getContentType()).isEqualTo("image/png");
        assertThat(h.isDmOnly()).isTrue();
        assertThat(h.isPresented()).isFalse();
    }

    @Test
    void shouldFindByCampaignId() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile("f", "a.png", "image/png", "a".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("f", "b.png", "image/png", "b".getBytes());
        service.create(campaignId, "B", "", file1);
        service.create(campaignId, "A", "", file2);

        var handouts = service.findByCampaignId(campaignId);
        assertThat(handouts).hasSize(2);
        assertThat(handouts.get(0).getTitle()).isEqualTo("A");
    }

    @Test
    void shouldSetPresented() throws Exception {
        MockMultipartFile file = new MockMultipartFile("f", "test.png", "image/png", "data".getBytes());
        Handout h = service.create(campaignId, "Test", "", file);

        Handout presented = service.setPresented(h.getId(), true);
        assertThat(presented.isPresented()).isTrue();
        assertThat(presented.isDmOnly()).isFalse();
    }

    @Test
    void shouldDeleteHandout() throws Exception {
        MockMultipartFile file = new MockMultipartFile("f", "del.png", "image/png", "data".getBytes());
        Handout h = service.create(campaignId, "To Delete", "", file);

        service.delete(h.getId());

        assertThatThrownBy(() -> service.findById(h.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldThrowWhenHandoutNotFound() {
        assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }
}
