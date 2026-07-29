package dev.hendrikhoemberg.dmhelper.sheet.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SheetCreateFormRenderTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CampaignRepository campaignRepo;

    @Autowired
    private PartyMemberRepository partyMemberRepo;

    private MockMvc mvc;
    private Campaign campaign;
    private PartyMember member;

    @BeforeAll
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();

        campaign = new Campaign();
        campaign.setName("Sheet Create Test");
        campaign.setDescription("Test campaign");
        campaign = campaignRepo.save(campaign);

        member = new PartyMember();
        member.setCampaign(campaign);
        member.setCharacterName("Testy McTestface");
        member.setAc(10);
        member.setMaxHp(10);
        member.setCurrentHp(10);
        member.setInitiativeBonus(0);
        member.setSpeed(30);
        member.setPassivePerception(10);
        member.setPassiveInsight(10);
        member.setPassiveInvestigation(10);
        member = partyMemberRepo.save(member);
    }

    @Test
    void createFormRendersWithAnAbilityFieldForEachScore() throws Exception {
        mvc.perform(get("/campaigns/{cid}/party/{pid}/sheet/create",
                        campaign.getId(), member.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("STR")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CHA")));
    }
}
