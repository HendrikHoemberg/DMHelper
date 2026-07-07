package dev.hendrikhoemberg.dmhelper.library.web;

import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/library")
public class LibraryApiController {

    private final StatBlockService service;

    public LibraryApiController(StatBlockService service) {
        this.service = service;
    }

    @GetMapping("/srd-keys")
    public List<String> srdKeys() {
        return service.findAll().stream()
                .filter(sb -> sb.getSource() == StatBlock.Source.SRD)
                .map(StatBlock::getSourceKey)
                .sorted()
                .toList();
    }
}
