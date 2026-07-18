package dev.hendrikhoemberg.dmhelper.dice.web;

import dev.hendrikhoemberg.dmhelper.dice.DiceResult;
import dev.hendrikhoemberg.dmhelper.dice.service.DiceService;
import dev.hendrikhoemberg.dmhelper.dice.service.RollHistoryItem;
import dev.hendrikhoemberg.dmhelper.dice.service.RollHistoryService;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roll")
public class DiceApiController {

    private final DiceService diceService;
    private final RollHistoryService rollHistoryService;

    public DiceApiController(DiceService diceService, RollHistoryService rollHistoryService) {
        this.diceService = diceService;
        this.rollHistoryService = rollHistoryService;
    }

    @PostMapping
    public ResponseEntity<?> roll(@RequestBody Map<String, Object> body) {
        String expression = (String) body.get("expression");
        if (expression == null || expression.isBlank()) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Expression is required");
            problem.setInstance(URI.create("/api/v1/roll"));
            problem.setTitle("Invalid roll request");
            return ResponseEntity.badRequest().body(problem);
        }
        Object campaignIdObj = body.get("campaignId");
        if (campaignIdObj == null) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "campaignId is required");
            problem.setInstance(URI.create("/api/v1/roll"));
            problem.setTitle("Invalid roll request");
            return ResponseEntity.badRequest().body(problem);
        }
        UUID campaignId = UUID.fromString(campaignIdObj.toString());
        Object encounterIdObj = body.get("encounterId");
        UUID encounterId = encounterIdObj != null ? UUID.fromString(encounterIdObj.toString()) : null;

        try {
            DiceResult result = diceService.roll(expression, encounterId, campaignId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    org.springframework.http.HttpStatus.BAD_REQUEST, e.getMessage());
            problem.setInstance(URI.create("/api/v1/roll"));
            problem.setTitle("Invalid dice expression");
            return ResponseEntity.badRequest().body(problem);
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<RollHistoryItem>> history(@RequestParam UUID campaignId) {
        return ResponseEntity.ok(rollHistoryService.recent(campaignId, 20));
    }
}
