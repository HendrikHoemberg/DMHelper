package dev.hendrikhoemberg.dmhelper.session.runtime;

import dev.hendrikhoemberg.dmhelper.session.service.SessionTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class EncounterModuleViewServiceTest {

    @Autowired
    private EncounterModuleViewService service;

    @Autowired
    private SessionTestFixtures fixtures;

    @Test
    void plannedEncountersPrioritiseTheCurrentSceneAndMap() {
        var f = fixtures.campaignWithEncountersAcrossFourChapters();
        var view = service.buildView(f.campaignId(), CockpitModuleMode.STANDARD);
        assertThat(view.plannedEncounters()).hasSizeLessThanOrEqualTo(8);
        assertThat(view.plannedEncounters().getFirst().mapId()).isEqualTo(f.currentMapId());
    }
}
