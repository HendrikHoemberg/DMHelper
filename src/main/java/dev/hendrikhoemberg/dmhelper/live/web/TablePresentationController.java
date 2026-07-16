package dev.hendrikhoemberg.dmhelper.live.web;

import dev.hendrikhoemberg.dmhelper.live.LiveTableState;
import dev.hendrikhoemberg.dmhelper.live.TablePresentationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/table")
public class TablePresentationController {

    private final TablePresentationService presentationService;

    public TablePresentationController(TablePresentationService presentationService) {
        this.presentationService = presentationService;
    }

    @GetMapping("/state")
    public LiveTableState getState() {
        return presentationService.getCurrentState();
    }
}
