package dev.hendrikhoemberg.dmhelper.live.web;

import dev.hendrikhoemberg.dmhelper.live.LiveTableState;
import dev.hendrikhoemberg.dmhelper.live.TablePresentationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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

    @PutMapping("/presentation")
    public LiveTableState setPresentation(@RequestBody PresentationRequest request) {
        return switch (request.mode()) {
            case "MAP" -> presentationService.presentMap(UUID.fromString(request.ref()));
            case "HANDOUT" -> presentationService.presentHandout(UUID.fromString(request.ref()));
            case "CURTAIN" -> presentationService.curtain();
            default -> throw new IllegalArgumentException("Unknown presentation mode: " + request.mode());
        };
    }

    @PostMapping("/refresh")
    public LiveTableState refresh() {
        return presentationService.broadcastCurrentState();
    }

    @PostMapping("/aoes")
    public LiveTableState updateAoEs(@RequestBody List<LiveTableState.AoeTemplateSnapshot> aoes) {
        presentationService.updateAoEs(aoes);
        return presentationService.broadcastCurrentState();
    }

    public record PresentationRequest(String mode, String ref) {}
}
