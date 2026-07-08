package dev.hendrikhoemberg.dmhelper.handout.web;

import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
public class FileServeController {

    private final HandoutService handoutService;

    public FileServeController(HandoutService handoutService) {
        this.handoutService = handoutService;
    }

    @GetMapping("/files/{id}")
    public ResponseEntity<byte[]> serveFile(@PathVariable UUID id) throws IOException {
        var handout = handoutService.findById(id);
        byte[] content = handoutService.getFileContent(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        handout.getContentType() != null ? handout.getContentType() : "application/octet-stream"))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(content);
    }
}
