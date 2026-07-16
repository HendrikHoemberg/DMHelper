package dev.hendrikhoemberg.dmhelper.ledger.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.LedgerEntryDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionImporter;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntry;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntryRepository;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LedgerSectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerSectionAdapter(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Override
    public String sectionName() {
        return "Ledger";
    }

    @Override
    public int order() {
        return 800;
    }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        var entries = ledgerEntryRepository.findByCampaignIdOrderByTimestampAscIdAsc(context.campaignId());
        List<LedgerEntryDto> dtos = entries.stream()
                .map(e -> exportLedgerEntry(e, context))
                .toList();
        target.ledgerEntries(dtos);
    }

    private LedgerEntryDto exportLedgerEntry(LedgerEntry entry, CampaignExportContext context) {
        String displayName = entry.getNote() != null ? entry.getNote() : "ledger-entry";
        String key = context.key(CampaignContentType.LEDGER_ENTRY, entry.getId(), displayName);

        ContentReference itemAssignmentRef = null;
        if (entry.getItemAssignmentRef() != null) {
            itemAssignmentRef = context.packageRef(CampaignContentType.ASSIGNMENT,
                    entry.getItemAssignmentRef().getId(),
                    entry.getItemAssignmentRef().getItemName());
        }

        return new LedgerEntryDto(key, entry.getTimestamp(),
                entry.getInGameYear(), entry.getInGameMonth(), entry.getInGameDay(),
                entry.getKind().name(), entry.getDirection().name(),
                entry.getAmount(), entry.getCurrency(),
                entry.getHolder(), entry.getNote(), itemAssignmentRef);
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        List<LedgerEntryDto> dtos = source.ledgerEntries();
        if (dtos == null) return;

        var campaign = context.campaign();

        for (LedgerEntryDto dto : dtos) {
            var entry = new LedgerEntry();
            entry.setCampaign(campaign);
            entry.setTimestamp(dto.timestamp());
            entry.setInGameYear(dto.inGameYear());
            entry.setInGameMonth(dto.inGameMonth());
            entry.setInGameDay(dto.inGameDay());
            if (dto.kind() != null) {
                entry.setKind(LedgerEntry.Kind.valueOf(dto.kind()));
            }
            if (dto.direction() != null) {
                entry.setDirection(LedgerEntry.Direction.valueOf(dto.direction()));
            }
            entry.setAmount(dto.amount());
            entry.setCurrency(dto.currency());
            entry.setHolder(dto.holder());
            entry.setNote(dto.note());

            if (dto.itemAssignmentRef() != null) {
                ContentReference ref = dto.itemAssignmentRef();
                context.defer("ledger assignment " + dto.key(), () -> {
                    var assignment = context.require(ref, CampaignContentType.ASSIGNMENT, ItemAssignment.class);
                    entry.setItemAssignmentRef(assignment);
                });
            }

            ledgerEntryRepository.save(entry);
            context.register(CampaignContentType.LEDGER_ENTRY, dto.key(), entry, entry.getId());
        }
    }
}
