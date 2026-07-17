package dev.hendrikhoemberg.dmhelper.library.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.library.data.BackgroundRepository;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.FeatRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.SpeciesRepository;
import dev.hendrikhoemberg.dmhelper.library.data.SpellRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class StatBlockReferenceResolverTest {

    @Test
    void exportsSrdAsCatalogAndCustomAsPackageReference() {
        var keyService = mock(CampaignPackageKeyService.class);
        when(keyService.getOrCreate(any(), any(), any(), any())).thenReturn("custom-dragon");
        var campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        var context = new CampaignExportContext(
                campaign.getId(), campaign, CampaignExportOptions.complete(),
                keyService, new CampaignAssetCollector());
        var resolver = new StatBlockReferenceResolver(libraryRefs(mock(StatBlockRepository.class)));
        var srd = statBlock(ContentSource.SRD, "srd-2024_goblin");
        srd.setId(UUID.randomUUID());
        var custom = statBlock(ContentSource.CUSTOM, "custom-dragon");
        custom.setId(UUID.randomUUID());
        custom.setCampaign(campaign);

        assertThat(resolver.referenceFor(srd, context)).isEqualTo(
                ContentReference.catalogRef(CampaignContentType.STATBLOCK, "SRD_5_2", "srd-2024_goblin"));
        assertThat(resolver.referenceFor(custom, context)).isEqualTo(
                ContentReference.packageRef(CampaignContentType.STATBLOCK, "custom-dragon"));
    }

    @Test
    void rejectsUserGlobalCustomOnExport() {
        var keyService = mock(CampaignPackageKeyService.class);
        var campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        var context = new CampaignExportContext(
                campaign.getId(), campaign, CampaignExportOptions.complete(),
                keyService, new CampaignAssetCollector());
        var resolver = new StatBlockReferenceResolver(libraryRefs(mock(StatBlockRepository.class)));
        var global = statBlock(ContentSource.CUSTOM, "global-dragon");
        global.setId(UUID.randomUUID());
        global.setCampaign(null);

        assertThatThrownBy(() -> resolver.referenceFor(global, context))
                .hasMessageContaining("user-global custom");
    }

    @Test
    void resolvesCatalogStatBlockBySrdSourceKey() {
        var repository = mock(StatBlockRepository.class);
        var resolver = new StatBlockReferenceResolver(libraryRefs(repository));
        var goblin = statBlock(ContentSource.SRD, "srd-2024_goblin");
        when(repository.findBySourceAndSourceKey(ContentSource.SRD, "srd-2024_goblin"))
                .thenReturn(Optional.of(goblin));

        var resolved = resolver.resolve(
                ContentReference.catalogRef(CampaignContentType.STATBLOCK, "SRD_5_2", "srd-2024_goblin"),
                importContext());

        assertThat(resolved).isSameAs(goblin);
    }

    @Test
    void resolvesCampaignStatBlockFromPackageRegistry() {
        var resolver = new StatBlockReferenceResolver(libraryRefs(mock(StatBlockRepository.class)));
        var context = importContext();
        var custom = statBlock(ContentSource.CUSTOM, "custom-dragon");
        custom.setId(UUID.randomUUID());
        context.register(CampaignContentType.STATBLOCK, "custom-dragon", custom, custom.getId());

        assertThat(resolver.resolve(
                ContentReference.packageRef(CampaignContentType.STATBLOCK, "custom-dragon"), context))
                .isSameAs(custom);
    }

    private static LibraryContentReferenceResolver libraryRefs(StatBlockRepository statBlockRepository) {
        return new LibraryContentReferenceResolver(
                mock(SpellRepository.class),
                mock(SpeciesRepository.class),
                mock(BackgroundRepository.class),
                mock(CharacterClassRepository.class),
                mock(FeatRepository.class),
                mock(MagicItemRepository.class),
                mock(EquipmentItemRepository.class),
                statBlockRepository);
    }

    private static StatBlock statBlock(ContentSource source, String sourceKey) {
        var statBlock = new StatBlock();
        statBlock.setSource(source);
        statBlock.setSourceKey(sourceKey);
        statBlock.setName("Goblin");
        return statBlock;
    }

    private static CampaignImportContext importContext() {
        var campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        var context = new CampaignImportContext(
                campaign.getId(), mock(CampaignPackageKeyService.class),
                new PendingCampaignImport(UUID.randomUUID(), null, null, null));
        context.setCampaign(campaign);
        return context;
    }
}
