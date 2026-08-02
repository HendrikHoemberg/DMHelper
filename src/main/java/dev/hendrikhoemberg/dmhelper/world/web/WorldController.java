package dev.hendrikhoemberg.dmhelper.world.web;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableLinkRole;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.WorldLocationTableLink;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.WorldLocationTableLinkRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableReferenceResolver;
import dev.hendrikhoemberg.dmhelper.world.data.*;
import dev.hendrikhoemberg.dmhelper.world.service.WorldService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/world")
public class WorldController {

    private final WorldService worldService;
    private final CampaignRepository campaignRepository;
    private final RollableTableRepository rollableTableRepository;
    private final WorldLocationTableLinkRepository locationTableLinkRepository;
    private final TableReferenceResolver referenceResolver;
    private final AudioCueRepository audioCueRepository;

    public WorldController(WorldService worldService,
                           CampaignRepository campaignRepository,
                           RollableTableRepository rollableTableRepository,
                           WorldLocationTableLinkRepository locationTableLinkRepository,
                           TableReferenceResolver referenceResolver,
                           AudioCueRepository audioCueRepository) {
        this.worldService = worldService;
        this.campaignRepository = campaignRepository;
        this.rollableTableRepository = rollableTableRepository;
        this.locationTableLinkRepository = locationTableLinkRepository;
        this.referenceResolver = referenceResolver;
        this.audioCueRepository = audioCueRepository;
    }

    @ModelAttribute
    public void addCampaign(@PathVariable UUID campaignId, Model model) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        model.addAttribute("campaign", campaign);
        model.addAttribute("campaignId", campaignId);
    }

    // ---- NPCs ----

    @GetMapping("/npcs")
    public String listNpcs(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("npcs", worldService.getNpcs(campaignId));
        return "world/npcs-list";
    }

    @GetMapping("/npcs/{npcId}")
    public String npcDetail(@PathVariable UUID campaignId, @PathVariable UUID npcId, Model model) {
        model.addAttribute("npc", worldService.getNpc(campaignId, npcId));
        return "world/npcs-detail";
    }

    @GetMapping("/npcs/new")
    public String newNpcForm(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("npc", new WorldNpc());
        model.addAttribute("factions", worldService.getFactions(campaignId));
        model.addAttribute("locations", worldService.getLocations(campaignId));
        return "world/npcs-form";
    }

    @PostMapping("/npcs")
    public String createNpc(@PathVariable UUID campaignId,
                            @RequestParam String name,
                            @RequestParam(required = false) String role,
                            @RequestParam(required = false) WorldDisposition disposition,
                            @RequestParam(required = false) UUID factionId,
                            @RequestParam(required = false) UUID locationId,
                            @RequestParam(required = false) String appearance,
                            @RequestParam(required = false) String voice,
                            @RequestParam(required = false) String motivation,
                            @RequestParam(required = false) String secret,
                            @RequestParam(required = false) String inventoryText,
                            @RequestParam(required = false) WorldNpcStatus status,
                            @RequestParam(required = false) String tags,
                            @RequestParam(required = false) String sourceLocator,
                            Model model) {
        try {
            WorldNpc npc = worldService.createNpc(campaignId,
                    new WorldService.NpcCommand(name, role, disposition, factionId, locationId,
                            null, null, appearance, voice, motivation, secret,
                            inventoryText, status, tags, sourceLocator));
            return "redirect:/campaigns/" + campaignId + "/world/npcs/" + npc.getId();
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("npc", new WorldNpc());
            model.addAttribute("factions", worldService.getFactions(campaignId));
            model.addAttribute("locations", worldService.getLocations(campaignId));
            return "world/npcs-form";
        }
    }

    @GetMapping("/npcs/{npcId}/edit")
    public String editNpcForm(@PathVariable UUID campaignId, @PathVariable UUID npcId, Model model) {
        model.addAttribute("npc", worldService.getNpc(campaignId, npcId));
        model.addAttribute("factions", worldService.getFactions(campaignId));
        model.addAttribute("locations", worldService.getLocations(campaignId));
        return "world/npcs-form";
    }

    @PutMapping("/npcs/{npcId}")
    public String updateNpc(@PathVariable UUID campaignId,
                            @PathVariable UUID npcId,
                            @RequestParam String name,
                            @RequestParam(required = false) String role,
                            @RequestParam(required = false) WorldDisposition disposition,
                            @RequestParam(required = false) UUID factionId,
                            @RequestParam(required = false) UUID locationId,
                            @RequestParam(required = false) String appearance,
                            @RequestParam(required = false) String voice,
                            @RequestParam(required = false) String motivation,
                            @RequestParam(required = false) String secret,
                            @RequestParam(required = false) String inventoryText,
                            @RequestParam(required = false) WorldNpcStatus status,
                            @RequestParam(required = false) String tags,
                            @RequestParam(required = false) String sourceLocator,
                            Model model) {
        try {
            worldService.updateNpc(campaignId, npcId,
                    new WorldService.NpcCommand(name, role, disposition, factionId, locationId,
                            null, null, appearance, voice, motivation, secret,
                            inventoryText, status, tags, sourceLocator));
            return "redirect:/campaigns/" + campaignId + "/world/npcs/" + npcId;
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("npc", worldService.getNpc(campaignId, npcId));
            model.addAttribute("factions", worldService.getFactions(campaignId));
            model.addAttribute("locations", worldService.getLocations(campaignId));
            return "world/npcs-form";
        }
    }

    @DeleteMapping("/npcs/{npcId}")
    public ResponseEntity<Void> deleteNpc(@PathVariable UUID campaignId, @PathVariable UUID npcId) {
        worldService.deleteNpc(campaignId, npcId);
        return ResponseEntity.ok()
                .header("HX-Redirect", "/campaigns/" + campaignId + "/world/npcs")
                .build();
    }

    // ---- Locations ----

    @GetMapping("/locations")
    public String listLocations(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("locations", worldService.getLocations(campaignId));
        return "world/locations-list";
    }

    @GetMapping("/locations/{locationId}")
    public String locationDetail(@PathVariable UUID campaignId, @PathVariable UUID locationId, Model model) {
        model.addAttribute("location", worldService.getLocation(campaignId, locationId));
        model.addAttribute("tableLinks", locationTableLinkRepository.findByLocationIdWithTable(locationId));
        model.addAttribute("tables", rollableTableRepository.findByCampaignIdOrderByNameAsc(campaignId));
        model.addAttribute("audioCues", audioCueRepository.findByCampaignIdOrderByNameAsc(campaignId));
        return "world/locations-detail";
    }

    @GetMapping("/locations/new")
    public String newLocationForm(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("location", new WorldLocation());
        model.addAttribute("allLocations", worldService.getLocations(campaignId));
        model.addAttribute("audioCues", audioCueRepository.findByCampaignIdOrderByNameAsc(campaignId));
        return "world/locations-form";
    }

    @PostMapping("/locations")
    public String createLocation(@PathVariable UUID campaignId,
                                 @RequestParam String name,
                                 @RequestParam(required = false) LocationKind kind,
                                 @RequestParam(required = false) UUID parentLocationId,
                                 @RequestParam(required = false) String summary,
                                 @RequestParam(required = false) String services,
                                 @RequestParam(required = false) String secrets,
                                 @RequestParam(required = false) String tags,
                                 @RequestParam(required = false) String sourceLocator,
                                 Model model) {
        try {
            WorldLocation location = worldService.createLocation(campaignId,
                    new WorldService.LocationCommand(name, kind, parentLocationId, null, null,
                            null, summary, services, secrets, null, null, tags, sourceLocator));
            return "redirect:/campaigns/" + campaignId + "/world/locations/" + location.getId();
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("location", new WorldLocation());
            model.addAttribute("allLocations", worldService.getLocations(campaignId));
            return "world/locations-form";
        }
    }

    @GetMapping("/locations/{locationId}/edit")
    public String editLocationForm(@PathVariable UUID campaignId, @PathVariable UUID locationId, Model model) {
        model.addAttribute("location", worldService.getLocation(campaignId, locationId));
        model.addAttribute("allLocations", worldService.getLocations(campaignId));
        model.addAttribute("audioCues", audioCueRepository.findByCampaignIdOrderByNameAsc(campaignId));
        return "world/locations-form";
    }

    @PutMapping("/locations/{locationId}")
    public String updateLocation(@PathVariable UUID campaignId,
                                 @PathVariable UUID locationId,
                                 @RequestParam String name,
                                 @RequestParam(required = false) LocationKind kind,
                                 @RequestParam(required = false) UUID parentLocationId,
                                 @RequestParam(required = false) String summary,
                                 @RequestParam(required = false) String services,
                                 @RequestParam(required = false) String secrets,
                                 @RequestParam(required = false) String tags,
                                 @RequestParam(required = false) String sourceLocator,
                                 Model model) {
        try {
            worldService.updateLocation(campaignId, locationId,
                    new WorldService.LocationCommand(name, kind, parentLocationId, null, null,
                            null, summary, services, secrets, null, null, tags, sourceLocator));
            return "redirect:/campaigns/" + campaignId + "/world/locations/" + locationId;
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("location", worldService.getLocation(campaignId, locationId));
            model.addAttribute("allLocations", worldService.getLocations(campaignId));
            return "world/locations-form";
        }
    }

    @DeleteMapping("/locations/{locationId}")
    public ResponseEntity<Void> deleteLocation(@PathVariable UUID campaignId, @PathVariable UUID locationId) {
        worldService.deleteLocation(campaignId, locationId);
        return ResponseEntity.ok()
                .header("HX-Redirect", "/campaigns/" + campaignId + "/world/locations")
                .build();
    }

    // ---- Factions ----

    @GetMapping("/factions")
    public String listFactions(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("factions", worldService.getFactions(campaignId));
        return "world/factions-list";
    }

    @GetMapping("/factions/{factionId}")
    public String factionDetail(@PathVariable UUID campaignId, @PathVariable UUID factionId, Model model) {
        Faction faction = worldService.getFaction(campaignId, factionId);
        List<WorldRelationship> relationships = worldService.getRelationships(campaignId);
        model.addAttribute("faction", faction);
        model.addAttribute("relationships", relationships);
        model.addAttribute("outboundRelationships", relationships.stream()
                .filter(r -> "FACTION".equals(r.getFromType()) && factionId.equals(r.getFromId()))
                .toList());
        model.addAttribute("inboundRelationships", relationships.stream()
                .filter(r -> "FACTION".equals(r.getToType()) && factionId.equals(r.getToId()))
                .toList());
        model.addAttribute("clocks", worldService.getClocksForFaction(campaignId, factionId));
        model.addAttribute("members", worldService.getNpcs(campaignId).stream()
                .filter(n -> n.getFaction() != null && n.getFaction().getId().equals(factionId))
                .toList());
        return "world/factions-detail";
    }

    @GetMapping("/factions/new")
    public String newFactionForm(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("faction", new Faction());
        return "world/factions-form";
    }

    @PostMapping("/factions")
    public String createFaction(@PathVariable UUID campaignId,
                                @RequestParam String name,
                                @RequestParam(required = false) String goals,
                                @RequestParam(required = false) String resources,
                                @RequestParam(required = false) String reputationNotes,
                                @RequestParam(required = false) String tags,
                                @RequestParam(required = false) String sourceLocator,
                                Model model) {
        try {
            Faction faction = worldService.createFaction(campaignId,
                    new WorldService.FactionCommand(name, goals, resources, reputationNotes,
                            null, tags, sourceLocator));
            return "redirect:/campaigns/" + campaignId + "/world/factions/" + faction.getId();
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("faction", new Faction());
            return "world/factions-form";
        }
    }

    @GetMapping("/factions/{factionId}/edit")
    public String editFactionForm(@PathVariable UUID campaignId, @PathVariable UUID factionId, Model model) {
        model.addAttribute("faction", worldService.getFaction(campaignId, factionId));
        return "world/factions-form";
    }

    @PutMapping("/factions/{factionId}")
    public String updateFaction(@PathVariable UUID campaignId,
                                @PathVariable UUID factionId,
                                @RequestParam String name,
                                @RequestParam(required = false) String goals,
                                @RequestParam(required = false) String resources,
                                @RequestParam(required = false) String reputationNotes,
                                @RequestParam(required = false) String tags,
                                @RequestParam(required = false) String sourceLocator,
                                Model model) {
        try {
            worldService.updateFaction(campaignId, factionId,
                    new WorldService.FactionCommand(name, goals, resources, reputationNotes,
                            null, tags, sourceLocator));
            return "redirect:/campaigns/" + campaignId + "/world/factions/" + factionId;
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("faction", worldService.getFaction(campaignId, factionId));
            return "world/factions-form";
        }
    }

    @DeleteMapping("/factions/{factionId}")
    public ResponseEntity<Void> deleteFaction(@PathVariable UUID campaignId, @PathVariable UUID factionId) {
        worldService.deleteFaction(campaignId, factionId);
        return ResponseEntity.ok()
                .header("HX-Redirect", "/campaigns/" + campaignId + "/world/factions")
                .build();
    }

    // ---- Relationships ----

    @PostMapping("/factions/{factionId}/relationships")
    public String createRelationship(@PathVariable UUID campaignId,
                                     @PathVariable UUID factionId,
                                     @RequestParam RelationshipKind kind,
                                     @RequestParam String fromType,
                                     @RequestParam UUID fromId,
                                     @RequestParam String toType,
                                     @RequestParam UUID toId,
                                     @RequestParam(defaultValue = "true") boolean directed,
                                     @RequestParam(defaultValue = "PUBLIC") RelationshipKnowledge knowledge,
                                     @RequestParam(defaultValue = "ACTIVE") RelationshipStatus status,
                                     @RequestParam(required = false) String notes,
                                     @RequestParam(required = false) String sourceLocator,
                                     @RequestParam(defaultValue = "0") int sortOrder,
                                     Model model) {
        try {
            worldService.createRelationship(campaignId,
                    new WorldService.RelationshipCommand(kind, fromType, fromId, toType, toId,
                            directed, knowledge, status, notes, sourceLocator, sortOrder));
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "redirect:/campaigns/" + campaignId + "/world/factions/" + factionId;
    }

    @DeleteMapping("/factions/{factionId}/relationships/{relId}")
    public String deleteRelationship(@PathVariable UUID campaignId,
                                     @PathVariable UUID factionId,
                                     @PathVariable UUID relId) {
        worldService.deleteRelationship(campaignId, relId);
        return "redirect:/campaigns/" + campaignId + "/world/factions/" + factionId;
    }

    // ---- Clocks ----

    @PostMapping("/factions/{factionId}/clocks")
    public String createClock(@PathVariable UUID campaignId,
                              @PathVariable UUID factionId,
                              @RequestParam String title,
                              @RequestParam int segments,
                              @RequestParam(defaultValue = "0") int filled,
                              @RequestParam(required = false) String notes,
                              @RequestParam(required = false) String sourceLocator,
                              @RequestParam(defaultValue = "0") int sortOrder,
                              Model model) {
        try {
            worldService.createClock(campaignId,
                    new WorldService.ClockCommand(factionId, title, segments, filled,
                            null, null, notes, sourceLocator, sortOrder));
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "redirect:/campaigns/" + campaignId + "/world/factions/" + factionId;
    }

    @PutMapping("/factions/{factionId}/clocks/{clockId}")
    public String updateClock(@PathVariable UUID campaignId,
                              @PathVariable UUID factionId,
                              @PathVariable UUID clockId,
                              @RequestParam String title,
                              @RequestParam int segments,
                              @RequestParam(defaultValue = "0") int filled,
                              @RequestParam(required = false) String notes,
                              @RequestParam(required = false) String sourceLocator,
                              @RequestParam(defaultValue = "0") int sortOrder,
                              Model model) {
        try {
            worldService.updateClock(campaignId, clockId,
                    new WorldService.ClockCommand(factionId, title, segments, filled,
                            null, null, notes, sourceLocator, sortOrder));
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "redirect:/campaigns/" + campaignId + "/world/factions/" + factionId;
    }

    @DeleteMapping("/factions/{factionId}/clocks/{clockId}")
    public String deleteClock(@PathVariable UUID campaignId,
                               @PathVariable UUID factionId,
                               @PathVariable UUID clockId) {
        worldService.deleteClock(campaignId, clockId);
        return "redirect:/campaigns/" + campaignId + "/world/factions/" + factionId;
    }

    // ---- Location table links ----

    @PostMapping("/locations/{locationId}/tables")
    public String addLocationTableLink(@PathVariable UUID campaignId,
                                       @PathVariable UUID locationId,
                                       @RequestParam UUID tableId,
                                       @RequestParam(defaultValue = "RANDOM_ENCOUNTERS") RollableTableLinkRole role,
                                       @RequestParam(defaultValue = "0") int sortOrder,
                                       Model model) {
        try {
            WorldLocation location = worldService.getLocation(campaignId, locationId);
            RollableTable table = rollableTableRepository.findById(tableId)
                    .orElseThrow(() -> new IllegalArgumentException("Table not found"));

            if (!referenceResolver.isVisibleToCampaign(table.getId(), campaignId)) {
                throw new IllegalArgumentException("Table is not visible to this campaign");
            }

            WorldLocationTableLink link = new WorldLocationTableLink();
            link.setLocation(location);
            link.setTable(table);
            link.setRole(role);
            link.setSortOrder(sortOrder);
            locationTableLinkRepository.save(link);
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "redirect:/campaigns/" + campaignId + "/world/locations/" + locationId;
    }

    @DeleteMapping("/locations/{locationId}/tables/{linkId}")
    public String deleteLocationTableLink(@PathVariable UUID campaignId,
                                           @PathVariable UUID locationId,
                                           @PathVariable UUID linkId,
                                           Model model) {
        try {
            worldService.getLocation(campaignId, locationId);
            WorldLocationTableLink link = locationTableLinkRepository.findByIdAndLocationId(linkId, locationId)
                    .orElseThrow(() -> new NotFoundException("Location table link not found in location"));
            locationTableLinkRepository.delete(link);
        } catch (NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "redirect:/campaigns/" + campaignId + "/world/locations/" + locationId;
    }
}
