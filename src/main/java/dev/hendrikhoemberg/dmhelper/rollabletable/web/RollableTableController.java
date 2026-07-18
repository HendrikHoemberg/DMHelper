package dev.hendrikhoemberg.dmhelper.rollabletable.web;

import dev.hendrikhoemberg.dmhelper.config.MarkdownUtil;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableCategory;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Controller
@RequestMapping("/library/tables")
public class RollableTableController {

    private final RollableTableRepository repository;
    private final RollableTableService service;
    private final MarkdownUtil markdownUtil;

    public RollableTableController(RollableTableRepository repository,
                                    RollableTableService service,
                                    MarkdownUtil markdownUtil) {
        this.repository = repository;
        this.service = service;
        this.markdownUtil = markdownUtil;
    }

    @GetMapping
    public String list(@RequestParam(required = false) UUID campaignId,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String tag,
                       @RequestParam(required = false) String text,
                       Model model) {
        List<RollableTable> tables;
        if (campaignId != null) {
            tables = repository.findByCampaignIdOrderByNameAsc(campaignId);
        } else {
            tables = repository.findAll();
        }

        Stream<RollableTable> stream = tables.stream();
        if (category != null && !category.isBlank()) {
            stream = stream.filter(t -> {
                try {
                    return t.getCategory() == TableCategory.valueOf(category);
                } catch (IllegalArgumentException e) {
                    return false;
                }
            });
        }
        if (tag != null && !tag.isBlank()) {
            stream = stream.filter(t -> t.getTags() != null && t.getTags().toLowerCase().contains(tag.toLowerCase()));
        }
        if (text != null && !text.isBlank()) {
            String q = text.toLowerCase();
            stream = stream.filter(t -> (t.getName() != null && t.getName().toLowerCase().contains(q))
                    || (t.getDescription() != null && t.getDescription().toLowerCase().contains(q))
                    || (t.getCategory() != null && t.getCategory().name().toLowerCase().contains(q)));
        }
        tables = stream.toList();

        model.addAttribute("tables", tables);
        model.addAttribute("campaignId", campaignId);
        return "rollable-table/list";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) UUID campaignId, Model model) {
        model.addAttribute("campaignId", campaignId);
        return "rollable-table/form";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        RollableTable table = repository.findWithEntriesById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found"));
        String md = table.getDescription();
        model.addAttribute("table", table);
        model.addAttribute("markdownDescription", md != null ? markdownUtil.toHtml(md) : "");
        model.addAttribute("campaignId", table.getCampaign() != null ? table.getCampaign().getId() : null);
        return "rollable-table/detail";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        RollableTable table = repository.findWithEntriesById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found"));
        if (table.getSource() != ContentSource.CUSTOM) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This table is read-only");
        }
        model.addAttribute("table", table);
        model.addAttribute("campaignId", table.getCampaign() != null ? table.getCampaign().getId() : null);
        return "rollable-table/form";
    }
}
