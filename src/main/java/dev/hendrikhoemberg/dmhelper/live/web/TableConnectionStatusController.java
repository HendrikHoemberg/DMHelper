package dev.hendrikhoemberg.dmhelper.live.web;

import dev.hendrikhoemberg.dmhelper.live.TableStateWebSocketHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The DM's answer to "is the table screen still listening?". Deliberately a poll rather than
 * a second socket: a socket opened to observe the socket would count itself.
 */
@RestController
public class TableConnectionStatusController {

    private final TableStateWebSocketHandler handler;

    public TableConnectionStatusController(TableStateWebSocketHandler handler) {
        this.handler = handler;
    }

    @GetMapping("/api/table/status")
    public Map<String, Integer> status() {
        return Map.of("connected", handler.connectedCount());
    }
}
