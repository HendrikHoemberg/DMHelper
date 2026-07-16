package dev.hendrikhoemberg.dmhelper.handout.web;

import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
import dev.hendrikhoemberg.dmhelper.live.TablePresentationService;
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
    private final TablePresentationService tablePresentationService;

    public FileServeController(HandoutService handoutService,
                               TablePresentationService tablePresentationService) {
        this.handoutService = handoutService;
        this.tablePresentationService = tablePresentationService;
    }

    @GetMapping("/files/{id}")
    public ResponseEntity<byte[]> serveFile(@PathVariable UUID id) throws IOException {
        var handout = handoutService.findById(id);
        return serveFileInternal(handout, CacheControl.maxAge(1, TimeUnit.HOURS));
    }

    @GetMapping("/player/files/{id}")
    public ResponseEntity<byte[]> servePlayerFile(@PathVariable UUID id) throws IOException {
        var handout = handoutService.findById(id);
        if (handout.isDmOnly() || !tablePresentationService.isCurrentlyPresentedHandout(id)) {
            return ResponseEntity.notFound().build();
        }
        return serveFileInternal(handout, CacheControl.noStore());
    }

    private ResponseEntity<byte[]> serveFileInternal(Handout handout, CacheControl cacheControl) throws IOException {
        byte[] content = handoutService.getFileContent(handout.getId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        handout.getContentType() != null ? handout.getContentType() : "application/octet-stream"))
                .cacheControl(cacheControl)
                .body(content);
    }
}
