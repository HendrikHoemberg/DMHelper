package dev.hendrikhoemberg.dmhelper.campaign.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(CampaignService.class)
class CampaignServiceTest {

    @Autowired
    private CampaignRepository repository;

    @Autowired
    private CampaignService service;

    @MockitoBean
    private PartyMemberRepository partyMemberRepository;

    @MockitoBean
    private StatBlockRepository statBlockRepository;

    @MockitoBean
    private PartyMemberService partyMemberService;

    @MockitoBean
    private StatBlockService statBlockService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldCreateCampaign() {
        Campaign campaign = service.create("Test Campaign", "A test description");

        assertThat(campaign.getId()).isNotNull();
        assertThat(campaign.getName()).isEqualTo("Test Campaign");
        assertThat(campaign.getDescription()).isEqualTo("A test description");
        assertThat(campaign.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldFindAllOrderedByName() {
        service.create("Zeta Campaign", null);
        service.create("Alpha Campaign", null);

        var campaigns = service.findAll();

        assertThat(campaigns).hasSize(2);
        assertThat(campaigns.get(0).getName()).isEqualTo("Alpha Campaign");
        assertThat(campaigns.get(1).getName()).isEqualTo("Zeta Campaign");
    }

    @Test
    void shouldFindById() {
        Campaign created = service.create("Find Me", null);

        Campaign found = service.findById(created.getId());

        assertThat(found.getName()).isEqualTo("Find Me");
    }

    @Test
    void shouldThrowWhenNotFound() {
        assertThatThrownBy(() -> service.findById(java.util.UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Campaign not found");
    }

    @Test
    void shouldUpdateCampaign() {
        Campaign created = service.create("Original", "Old description");

        Campaign updated = service.update(created.getId(), "Updated", "New description");

        assertThat(updated.getName()).isEqualTo("Updated");
        assertThat(updated.getDescription()).isEqualTo("New description");
    }

    @Test
    void shouldDeleteCampaign() {
        Campaign created = service.create("Delete Me", null);

        service.delete(created.getId());

        assertThat(repository.findById(created.getId())).isEmpty();
    }

    @Test
    void shouldExportEmptyCampaignToJson() throws Exception {
        Campaign created = service.create("Export Test", "Test desc");

        String json = service.exportToJson(created.getId());
        var node = objectMapper.readTree(json);

        assertThat(node.get("formatVersion").asInt()).isEqualTo(1);
        assertThat(node.get("campaign").get("name").asText()).isEqualTo("Export Test");
        assertThat(node.get("campaign").get("description").asText()).isEqualTo("Test desc");
        assertThat(node.get("party").size()).isEqualTo(0);
        assertThat(node.get("statBlocks").size()).isEqualTo(0);
        assertThat(node.get("handouts").size()).isEqualTo(0);
        assertThat(node.get("maps").size()).isEqualTo(0);
        assertThat(node.get("encounters").size()).isEqualTo(0);
        assertThat(node.get("notes").size()).isEqualTo(0);
    }

    @Test
    void shouldImportJsonToNewCampaign() throws Exception {
        String json = """
                {
                  "formatVersion": 1,
                  "campaign": { "name": "Imported", "description": "Imported desc" },
                  "party": [],
                  "statBlocks": [],
                  "handouts": [],
                  "maps": [],
                  "encounters": [],
                  "notes": []
                }
                """;

        Campaign imported = service.importFromJson(json);

        assertThat(imported.getId()).isNotNull();
        assertThat(imported.getName()).isEqualTo("Imported");
        assertThat(imported.getDescription()).isEqualTo("Imported desc");
    }

    @Test
    void shouldRoundTripCampaign() throws Exception {
        Campaign original = service.create("Round Trip", "Round trip test");
        String exported = service.exportToJson(original.getId());

        Campaign reimported = service.importFromJson(exported);

        assertThat(reimported.getName()).isEqualTo("Round Trip");
        assertThat(reimported.getDescription()).isEqualTo("Round trip test");
        assertThat(reimported.getId()).isNotEqualTo(original.getId());
    }

    @Test
    void shouldRejectInvalidFormatVersion() {
        String json = """
                { "formatVersion": 99, "campaign": { "name": "Bad" },
                  "party": [], "statBlocks": [], "handouts": [],
                  "maps": [], "encounters": [], "notes": [] }
                """;

        assertThatThrownBy(() -> service.importFromJson(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("formatVersion");
    }

    @Test
    void shouldRejectMalformedJson() {
        assertThatThrownBy(() -> service.importFromJson("not json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse");
    }

    @Test
    void shouldRejectMissingCampaignName() {
        String json = """
                { "formatVersion": 1, "campaign": { "description": "nope" },
                  "party": [], "statBlocks": [], "handouts": [],
                  "maps": [], "encounters": [], "notes": [] }
                """;

        assertThatThrownBy(() -> service.importFromJson(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }
}
