package dev.hendrikhoemberg.dmhelper.agent.web;

import dev.hendrikhoemberg.dmhelper.agent.AgentContractService;
import dev.hendrikhoemberg.dmhelper.agent.ValidationErrorCatalog;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1")
public class AgentContractController {

    private final AgentContractService service;

    public AgentContractController(AgentContractService service) {
        this.service = service;
    }

    @GetMapping("/validation-errors")
    public ResponseEntity<ValidationErrorCatalog> validationErrors() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(service.validationErrors());
    }
}
