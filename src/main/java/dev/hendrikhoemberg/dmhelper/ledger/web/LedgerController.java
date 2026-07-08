package dev.hendrikhoemberg.dmhelper.ledger.web;

import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.ledger.service.LedgerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/ledger")
public class LedgerController {

    private final LedgerService ledgerService;
    private final CalendarService calendarService;
    private final CampaignRepository campaignRepository;

    public LedgerController(LedgerService ledgerService, CalendarService calendarService,
                            CampaignRepository campaignRepository) {
        this.ledgerService = ledgerService;
        this.calendarService = calendarService;
        this.campaignRepository = campaignRepository;
    }

    @ModelAttribute
    void addCampaign(@PathVariable UUID campaignId, Model model) {
        var campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        model.addAttribute("campaign", campaign);
        model.addAttribute("campaignId", campaignId);
    }

    @GetMapping
    String list(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("entries", ledgerService.findByCampaignId(campaignId));
        model.addAttribute("balances", ledgerService.computeAllGoldBalances(campaignId));
        return "ledger/list";
    }

    @GetMapping("/new")
    String newForm(@PathVariable UUID campaignId, Model model) {
        var date = calendarService.getCurrentDate(campaignId);
        model.addAttribute("currentYear", date.year());
        model.addAttribute("currentMonth", date.month());
        model.addAttribute("currentDay", date.day());
        return "ledger/_form :: form";
    }

    @PostMapping
    String create(@PathVariable UUID campaignId,
                  @RequestParam String kind,
                  @RequestParam String direction,
                  @RequestParam BigDecimal amount,
                  @RequestParam(defaultValue = "GP") String currency,
                  @RequestParam String holder,
                  @RequestParam(required = false) String note,
                  @RequestParam(required = false) Integer inGameYear,
                  @RequestParam(required = false) Integer inGameMonth,
                  @RequestParam(required = false) Integer inGameDay,
                  Model model) {
        ledgerService.create(new LedgerService.CreateLedgerEntryRequest(
                campaignId, kind, direction, amount, currency,
                holder, note, inGameYear, inGameMonth, inGameDay));
        return list(campaignId, model);
    }

    @GetMapping("/balances")
    String balances(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("balances", ledgerService.computeAllGoldBalances(campaignId));
        return "ledger/_balance :: balanceSection";
    }
}
