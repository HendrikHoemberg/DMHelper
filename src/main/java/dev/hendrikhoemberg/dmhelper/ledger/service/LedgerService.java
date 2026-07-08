package dev.hendrikhoemberg.dmhelper.ledger.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntry;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class LedgerService {

    private final LedgerEntryRepository repository;
    private final CampaignRepository campaignRepository;

    public LedgerService(LedgerEntryRepository repository,
                         CampaignRepository campaignRepository) {
        this.repository = repository;
        this.campaignRepository = campaignRepository;
    }

    public record CreateLedgerEntryRequest(
        UUID campaignId, String kind, String direction,
        BigDecimal amount, String currency,
        String holder, String note,
        Integer inGameYear, Integer inGameMonth, Integer inGameDay
    ) {}

    public record LedgerEntryDto(
        UUID id, UUID campaignId, Instant timestamp,
        Integer inGameYear, Integer inGameMonth, Integer inGameDay,
        String kind, String direction, BigDecimal amount, String currency,
        String holder, String note, UUID itemAssignmentId
    ) {
        public static LedgerEntryDto from(LedgerEntry le) {
            return new LedgerEntryDto(
                le.getId(), le.getCampaign().getId(),
                le.getTimestamp(),
                le.getInGameYear(), le.getInGameMonth(), le.getInGameDay(),
                le.getKind().name(), le.getDirection().name(),
                le.getAmount(), le.getCurrency(),
                le.getHolder(), le.getNote(),
                le.getItemAssignmentRef() != null ? le.getItemAssignmentRef().getId() : null
            );
        }
    }

    public record HolderBalance(String holder, BigDecimal balance, String currency) {}

    public LedgerEntryDto create(CreateLedgerEntryRequest req) {
        if (req.holder == null || req.holder.isBlank()) {
            throw new IllegalArgumentException("Holder is required");
        }
        Campaign campaign = campaignRepository.findById(req.campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        LedgerEntry le = new LedgerEntry();
        le.setCampaign(campaign);
        le.setKind(LedgerEntry.Kind.valueOf(req.kind.toUpperCase()));
        le.setDirection(LedgerEntry.Direction.valueOf(req.direction.toUpperCase()));
        le.setAmount(req.amount != null ? req.amount : BigDecimal.ZERO);
        le.setCurrency(req.currency != null ? req.currency.toUpperCase() : "GP");
        le.setHolder(req.holder.trim());
        le.setNote(req.note);
        le.setInGameYear(req.inGameYear);
        le.setInGameMonth(req.inGameMonth);
        le.setInGameDay(req.inGameDay);
        return LedgerEntryDto.from(repository.save(le));
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryDto> findByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdOrderByTimestampDesc(campaignId).stream()
                .map(LedgerEntryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryDto> findByCampaignAndHolder(UUID campaignId, String holder) {
        return repository.findByCampaignIdAndHolderOrderByTimestampDesc(campaignId, holder).stream()
                .map(LedgerEntryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal computeGoldBalance(UUID campaignId, String holder, String currency) {
        BigDecimal balance = repository.computeGoldBalance(campaignId, holder,
                currency != null ? currency.toUpperCase() : "GP");
        return balance != null ? balance.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public List<HolderBalance> computeAllGoldBalances(UUID campaignId) {
        var entries = repository.findByCampaignIdOrderByTimestampDesc(campaignId);
        Map<String, Map<String, BigDecimal>> acc = new LinkedHashMap<>();
        for (var le : entries) {
            if (le.getKind() != LedgerEntry.Kind.GOLD) continue;
            String key = le.getHolder();
            String cur = le.getCurrency() != null ? le.getCurrency() : "GP";
            acc.computeIfAbsent(key, k -> new LinkedHashMap<>());
            var inner = acc.get(key);
            BigDecimal delta = le.getAmount() != null ? le.getAmount() : BigDecimal.ZERO;
            if (le.getDirection() == LedgerEntry.Direction.SPEND) {
                delta = delta.negate();
            }
            inner.merge(cur, delta, BigDecimal::add);
        }
        List<HolderBalance> result = new ArrayList<>();
        for (var outer : acc.entrySet()) {
            for (var inner : outer.getValue().entrySet()) {
                result.add(new HolderBalance(outer.getKey(),
                        inner.getValue().setScale(2, RoundingMode.HALF_UP), inner.getKey()));
            }
        }
        return result;
    }
}
