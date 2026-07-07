package dev.hendrikhoemberg.dmhelper.campaign.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Controller
@RequestMapping("/campaigns")
public class CampaignController {

    private final CampaignService service;

    public CampaignController(CampaignService service) {
        this.service = service;
    }

    @GetMapping
    public String list(Model model, HttpServletRequest request) {
        model.addAttribute("campaigns", service.findAll());

        String fragment = request.getParameter("fragment");
        if ("form".equals(fragment)) {
            return "campaigns/_form";
        }
        if ("empty-form".equals(fragment)) {
            return "common/_empty-state";
        }

        return "campaigns/list";
    }

    @PostMapping
    public String create(@RequestParam String name,
                         @RequestParam(required = false) String description,
                         Model model) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Campaign name is required");
        }
        Campaign campaign = service.create(name, description);
        model.addAttribute("campaign", campaign);
        return "campaigns/_card";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        model.addAttribute("campaign", service.findById(id));
        return "campaigns/detail";
    }

    @PutMapping("/{id}")
    public String update(@PathVariable UUID id,
                         @RequestParam String name,
                         @RequestParam(required = false) String description,
                         Model model) {
        Campaign campaign = service.update(id, name, description);
        model.addAttribute("campaign", campaign);
        return "campaigns/detail";
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok()
                .header("HX-Redirect", "/campaigns")
                .build();
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportCampaign(@PathVariable UUID id) {
        Campaign campaign = service.findById(id);
        String json = service.exportToJson(id);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        String filename = URLEncoder.encode(
                campaign.getName().replaceAll("[^a-zA-Z0-9._-]", "_") + ".dmcampaign.json",
                StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename)
                .build());

        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    @PostMapping("/import")
    public String importCampaign(@RequestParam("file") MultipartFile file, Model model) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded");
        }
        try {
            String json = new String(file.getBytes(), StandardCharsets.UTF_8);
            Campaign campaign = service.importFromJson(json);
            model.addAttribute("campaign", campaign);
            return "campaigns/_card";
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read uploaded file: " + e.getMessage());
        }
    }
}
