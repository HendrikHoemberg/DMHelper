package dev.hendrikhoemberg.dmhelper.campaign.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.service.SpellSeedService;
import dev.hendrikhoemberg.dmhelper.library.service.SrdSeedService;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({CampaignService.class, PartyMemberService.class, StatBlockService.class})
class CampaignImportExportRoundTripTest {

    @MockitoBean
    private SrdSeedService srdSeedService;

    @MockitoBean
    private SpellSeedService spellSeedService;

    @Autowired private CampaignService campaignService;
    @Autowired private PartyMemberService partyMemberService;
    @Autowired private StatBlockService statBlockService;
    @Autowired private StatBlockRepository statBlockRepository;

    @Test
    void roundTripPreservesPartyActiveAndStatblockSourceKey() {
        Campaign c = campaignService.create("Round Trip", "full graph");

        partyMemberService.create(c.getId(), "Thia", "Anna", "Rogue 5",
                16, 38, 4, 30, 17, 12, 11, "darkvision");
        PartyMember retired = partyMemberService.create(c.getId(), "Borin", "Ben", "Fighter 5",
                18, 45, 1, 30, 12, 10, 10, "retired PC");
        partyMemberService.setActive(retired.getId(), false);

        StatBlock sb = statBlockService.createCustom(c.getId(), "Amber Knight", "5", "Humanoid",
                18, "75 (10d8 + 30)", "30 ft.",
                16, 12, 16, 10, 12, 14,
                null, null,
                null, null, null, null, null, null,
                null, null, null, null, null,
                "passive Perception 12", "Common");
        sb.setSourceKey("amber-knight");
        statBlockRepository.save(sb);

        String json = campaignService.exportToJson(c.getId());
        Campaign imported = campaignService.importFromJson(json);

        List<PartyMember> members = partyMemberService.findByCampaignId(imported.getId());
        assertThat(members).hasSize(2);
        PartyMember reBorin = members.stream()
                .filter(m -> m.getCharacterName().equals("Borin")).findFirst().orElseThrow();
        assertThat(reBorin.isActive()).isFalse();

        List<StatBlock> blocks = statBlockService.findByCampaignId(imported.getId());
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).getSourceKey()).isEqualTo("amber-knight");
        assertThat(blocks.get(0).getName()).isEqualTo("Amber Knight");
    }
}
