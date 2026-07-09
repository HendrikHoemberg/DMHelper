package dev.hendrikhoemberg.dmhelper.common.web;

import dev.hendrikhoemberg.dmhelper.common.service.CommandPaletteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CommandPaletteApiController {

    private final CommandPaletteService commandPaletteService;

    public CommandPaletteApiController(CommandPaletteService commandPaletteService) {
        this.commandPaletteService = commandPaletteService;
    }

    @GetMapping("/search")
    public List<CommandPaletteService.SearchResultItem> search(
            @RequestParam String q,
            @RequestParam(required = false) UUID campaignId,
            @RequestParam(required = false) String type) {
        return commandPaletteService.search(q, campaignId, type);
    }
}
