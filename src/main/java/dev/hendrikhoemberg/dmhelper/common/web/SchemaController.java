package dev.hendrikhoemberg.dmhelper.common.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/schemas")
public class SchemaController {

    @GetMapping("/{name}")
    public ResponseEntity<String> getSchema(@PathVariable String name) throws IOException {
        String fileName = name.endsWith(".schema.json") ? name : name + ".schema.json";
        ClassPathResource resource = new ClassPathResource("schemas/" + fileName);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/schema+json"))
                .body(content);
    }
}
