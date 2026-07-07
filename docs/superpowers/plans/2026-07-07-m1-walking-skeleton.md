# M1: Walking Skeleton — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `java -jar dmhelper.jar` → create, export, and import a campaign in the browser

**Architecture:** Spring Boot 4.1.0 / Java 25 with Thymeleaf + htmx server-rendered UI, H2 file-mode database, Flyway migrations, vendored JS libraries, feature-module package structure, CSS design system with dark theme

**Tech Stack:** Spring Boot 4.1.0, Java 25, Spring Data JPA, H2, Flyway, Thymeleaf, htmx v2, Alpine.js v3, Lombok, JUnit 5, MockMvc

**Design Doc:** `docs/superpowers/specs/2026-07-07-m1-walking-skeleton-design.md`

---

### Task 1: Project cleanup — fix pom.xml dependencies and remove Docker

**Files:**
- Modify: `pom.xml`
- Delete: `compose.yaml`

- [ ] **Step 1: Remove unwanted dependencies from pom.xml**

Remove these three `<dependency>` blocks entirely from `pom.xml`:
- `spring-boot-starter-security`
- `thymeleaf-extras-springsecurity6`
- `spring-boot-docker-compose`

- [ ] **Step 2: Add required dependencies to pom.xml**

Inside `<dependencies>`, add:

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-h2</artifactId>
</dependency>
```

- [ ] **Step 3: Verify pom.xml is valid**

Run: `./mvnw validate`
Expected: BUILD SUCCESS

- [ ] **Step 4: Delete compose.yaml**

Run: `rm compose.yaml`

- [ ] **Step 5: Commit**

```bash
git add pom.xml && git rm compose.yaml && git commit -m "chore: remove Spring Security, Docker Compose; add Flyway and explicit H2"
```

---

### Task 2: Configure application.properties

**Files:**
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Write application.properties**

Overwrite `src/main/resources/application.properties` with:

```properties
spring.datasource.url=jdbc:h2:file:${user.home}/.dmhelper/data/dmhelper;DB_CLOSE_DELAY=-1;AUTO_RECONNECT=TRUE
spring.datasource.driver-class-name=org.h2.Driver

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false

spring.h2.console.enabled=false

spring.flyway.enabled=true

spring.thymeleaf.cache=false
spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.suffix=.html

server.port=8080
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/application.properties && git commit -m "chore: configure H2 file DB, Flyway, Thymeleaf"
```

---

### Task 3: Create Flyway migration V1

**Files:**
- Create: `src/main/resources/db/migration/V1__create_campaign.sql`

- [ ] **Step 1: Create migration directory**

Run: `mkdir -p src/main/resources/db/migration`

- [ ] **Step 2: Write V1 migration**

Create `src/main/resources/db/migration/V1__create_campaign.sql`:

```sql
CREATE TABLE campaign (
    id          UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description CLOB,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settings    CLOB
);
```

- [ ] **Step 3: Verify migration runs**

Run: `./mvnw clean spring-boot:run`
Expected: App starts without errors. Flyway log shows "Successfully applied 1 migration".
Then Ctrl+C to stop.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/migration/V1__create_campaign.sql && git commit -m "feat: add Flyway V1 migration — create campaign table"
```

---

### Task 4: Create Campaign entity

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/data/Campaign.java`

- [ ] **Step 1: Create package directories**

Run: `mkdir -p src/main/java/dev/hendrikhoemberg/dmhelper/campaign/data`

- [ ] **Step 2: Write Campaign entity**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/data/Campaign.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.data;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "campaign")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "CLOB")
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(columnDefinition = "CLOB")
    private String settings;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
```

- [ ] **Step 3: Compile check**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/data/Campaign.java && git commit -m "feat: add Campaign JPA entity"
```

---

### Task 5: Create CampaignRepository

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/data/CampaignRepository.java`

- [ ] **Step 1: Write CampaignRepository**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/data/CampaignRepository.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    List<Campaign> findAllByOrderByNameAsc();
}
```

- [ ] **Step 2: Compile check**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/data/CampaignRepository.java && git commit -m "feat: add CampaignRepository"
```

---

### Task 6: Create CampaignExportDto (import/export JSON format)

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java`

- [ ] **Step 1: Create service package**

Run: `mkdir -p src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service`

- [ ] **Step 2: Write CampaignExportDto**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CampaignExportDto(
        int formatVersion,
        CampaignDto campaign,
        @JsonInclude(JsonInclude.Include.ALWAYS) List<Object> party,
        @JsonInclude(JsonInclude.Include.ALWAYS) List<Object> statBlocks,
        @JsonInclude(JsonInclude.Include.ALWAYS) List<Object> handouts,
        @JsonInclude(JsonInclude.Include.ALWAYS) List<Object> maps,
        @JsonInclude(JsonInclude.Include.ALWAYS) List<Object> encounters,
        @JsonInclude(JsonInclude.Include.ALWAYS) List<Object> notes
) {

    public static CampaignExportDto from(Campaign campaign) {
        return new CampaignExportDto(
                1,
                new CampaignDto(campaign.getName(), campaign.getDescription()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    public record CampaignDto(
            String name,
            @JsonInclude(JsonInclude.Include.NON_DEFAULT) String description
    ) {
        public CampaignDto(String name, String description) {
            this.name = name;
            this.description = (description != null && !description.isBlank()) ? description : null;
        }
    }
}
```

- [ ] **Step 3: Compile check**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java && git commit -m "feat: add CampaignExportDto for JSON import/export format"
```

---

### Task 7: Write CampaignService tests (TDD — red phase)

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignServiceTest.java`

- [ ] **Step 1: Create test package**

Run: `mkdir -p src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service`

- [ ] **Step 2: Write failing tests**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignServiceTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import(CampaignService.class)
class CampaignServiceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CampaignRepository repository;

    @Autowired
    private CampaignService service;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldCreateCampaign() {
        Campaign campaign = service.create("Test Campaign", "A test description");

        assertThat(campaign.getId()).isNotNull();
        assertThat(campaign.getName()).isEqualTo("Test Campaign");
        assertThat(campaign.getDescription()).isEqualTo("A test description");
        assertThat(campaign.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldFindAllOrderedByName() {
        service.create("Zeta Campaign", null);
        service.create("Alpha Campaign", null);

        var campaigns = service.findAll();

        assertThat(campaigns).hasSize(2);
        assertThat(campaigns.get(0).getName()).isEqualTo("Alpha Campaign");
        assertThat(campaigns.get(1).getName()).isEqualTo("Zeta Campaign");
    }

    @Test
    void shouldFindById() {
        Campaign created = service.create("Find Me", null);

        Campaign found = service.findById(created.getId());

        assertThat(found.getName()).isEqualTo("Find Me");
    }

    @Test
    void shouldThrowWhenNotFound() {
        assertThatThrownBy(() -> service.findById(java.util.UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Campaign not found");
    }

    @Test
    void shouldUpdateCampaign() {
        Campaign created = service.create("Original", "Old description");

        Campaign updated = service.update(created.getId(), "Updated", "New description");

        assertThat(updated.getName()).isEqualTo("Updated");
        assertThat(updated.getDescription()).isEqualTo("New description");
    }

    @Test
    void shouldDeleteCampaign() {
        Campaign created = service.create("Delete Me", null);

        service.delete(created.getId());

        assertThat(repository.findById(created.getId())).isEmpty();
    }

    @Test
    void shouldExportEmptyCampaignToJson() throws Exception {
        Campaign created = service.create("Export Test", "Test desc");

        String json = service.exportToJson(created.getId());
        var node = objectMapper.readTree(json);

        assertThat(node.get("formatVersion").asInt()).isEqualTo(1);
        assertThat(node.get("campaign").get("name").asText()).isEqualTo("Export Test");
        assertThat(node.get("campaign").get("description").asText()).isEqualTo("Test desc");
        assertThat(node.get("party").size()).isEqualTo(0);
        assertThat(node.get("statBlocks").size()).isEqualTo(0);
        assertThat(node.get("handouts").size()).isEqualTo(0);
        assertThat(node.get("maps").size()).isEqualTo(0);
        assertThat(node.get("encounters").size()).isEqualTo(0);
        assertThat(node.get("notes").size()).isEqualTo(0);
    }

    @Test
    void shouldImportJsonToNewCampaign() throws Exception {
        String json = """
                {
                  "formatVersion": 1,
                  "campaign": { "name": "Imported", "description": "Imported desc" },
                  "party": [],
                  "statBlocks": [],
                  "handouts": [],
                  "maps": [],
                  "encounters": [],
                  "notes": []
                }
                """;

        Campaign imported = service.importFromJson(json);

        assertThat(imported.getId()).isNotNull();
        assertThat(imported.getName()).isEqualTo("Imported");
        assertThat(imported.getDescription()).isEqualTo("Imported desc");
    }

    @Test
    void shouldRoundTripCampaign() throws Exception {
        Campaign original = service.create("Round Trip", "Round trip test");
        String exported = service.exportToJson(original.getId());

        Campaign reimported = service.importFromJson(exported);

        assertThat(reimported.getName()).isEqualTo("Round Trip");
        assertThat(reimported.getDescription()).isEqualTo("Round trip test");
        assertThat(reimported.getId()).isNotEqualTo(original.getId());
    }

    @Test
    void shouldRejectInvalidFormatVersion() {
        String json = """
                { "formatVersion": 99, "campaign": { "name": "Bad" },
                  "party": [], "statBlocks": [], "handouts": [],
                  "maps": [], "encounters": [], "notes": [] }
                """;

        assertThatThrownBy(() -> service.importFromJson(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("formatVersion");
    }

    @Test
    void shouldRejectMalformedJson() {
        assertThatThrownBy(() -> service.importFromJson("not json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse");
    }

    @Test
    void shouldRejectMissingCampaignName() {
        String json = """
                { "formatVersion": 1, "campaign": { "description": "nope" },
                  "party": [], "statBlocks": [], "handouts": [],
                  "maps": [], "encounters": [], "notes": [] }
                """;

        assertThatThrownBy(() -> service.importFromJson(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignServiceTest.java && git commit -m "test: add CampaignService tests (TDD red phase)"
```

---

### Task 8: Implement CampaignService (TDD — green phase)

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java` (create)

- [ ] **Step 1: Write CampaignService implementation**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CampaignService {

    private final CampaignRepository repository;
    private final ObjectMapper objectMapper;

    public CampaignService(CampaignRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public Campaign create(String name, String description) {
        Campaign campaign = new Campaign();
        campaign.setName(name);
        campaign.setDescription(description);
        return repository.save(campaign);
    }

    @Transactional(readOnly = true)
    public List<Campaign> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Campaign findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + id));
    }

    public Campaign update(UUID id, String name, String description) {
        Campaign campaign = findById(id);
        campaign.setName(name);
        campaign.setDescription(description);
        return repository.save(campaign);
    }

    public void delete(UUID id) {
        Campaign campaign = findById(id);
        repository.delete(campaign);
    }

    @Transactional(readOnly = true)
    public String exportToJson(UUID id) {
        Campaign campaign = findById(id);
        CampaignExportDto dto = CampaignExportDto.from(campaign);
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export campaign", e);
        }
    }

    public Campaign importFromJson(String json) {
        CampaignExportDto dto;
        try {
            dto = objectMapper.readValue(json, CampaignExportDto.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse import JSON: " + e.getMessage(), e);
        }

        if (dto.formatVersion() != 1) {
            throw new IllegalArgumentException(
                    "Unsupported formatVersion: " + dto.formatVersion() + ". Expected: 1");
        }

        if (dto.campaign() == null || dto.campaign().name() == null || dto.campaign().name().isBlank()) {
            throw new IllegalArgumentException("Campaign name is required");
        }

        return create(dto.campaign().name(), dto.campaign().description());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `./mvnw test -pl . -Dtest=CampaignServiceTest`
Expected: All 12 tests pass

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignServiceTest.java && git commit -m "feat: implement CampaignService with CRUD, JSON export/import"
```

---

### Task 9: Write CampaignController tests (TDD — red phase)

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignControllerTest.java`

- [ ] **Step 1: Create web test package**

Run: `mkdir -p src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web`

- [ ] **Step 2: Write controller tests**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignControllerTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CampaignController.class)
class CampaignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CampaignService service;

    private Campaign sampleCampaign() {
        Campaign c = new Campaign();
        c.setId(UUID.randomUUID());
        c.setName("Test Campaign");
        c.setDescription("A test campaign");
        c.setCreatedAt(Instant.now());
        return c;
    }

    @Test
    void shouldRenderCampaignList() throws Exception {
        when(service.findAll()).thenReturn(List.of(sampleCampaign()));

        mockMvc.perform(get("/campaigns"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Test Campaign")));
    }

    @Test
    void shouldRenderEmptyList() throws Exception {
        when(service.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/campaigns"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No campaigns yet")));
    }

    @Test
    void shouldCreateCampaign() throws Exception {
        Campaign c = sampleCampaign();
        when(service.create(eq("Test Campaign"), any())).thenReturn(c);

        mockMvc.perform(post("/campaigns")
                        .param("name", "Test Campaign")
                        .param("description", "A test")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Test Campaign")));
    }

    @Test
    void shouldRejectEmptyCampaignName() throws Exception {
        mockMvc.perform(post("/campaigns")
                        .param("name", "")
                        .param("description", "")
                        .header("HX-Request", "true"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRenderDetail() throws Exception {
        Campaign c = sampleCampaign();
        when(service.findById(c.getId())).thenReturn(c);

        mockMvc.perform(get("/campaigns/{id}", c.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Test Campaign")));
    }

    @Test
    void shouldUpdateCampaign() throws Exception {
        Campaign c = sampleCampaign();
        c.setName("Updated Name");
        when(service.update(eq(c.getId()), eq("Updated Name"), any())).thenReturn(c);

        mockMvc.perform(put("/campaigns/{id}", c.getId())
                        .param("name", "Updated Name")
                        .param("description", "Updated desc")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Updated Name")));
    }

    @Test
    void shouldDeleteCampaign() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/campaigns/{id}", id)
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect", "/campaigns"));
    }

    @Test
    void shouldExportCampaign() throws Exception {
        Campaign c = sampleCampaign();
        when(service.findById(c.getId())).thenReturn(c);
        when(service.exportToJson(c.getId())).thenReturn("{\"formatVersion\":1}");

        mockMvc.perform(get("/campaigns/{id}/export", c.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Content-Disposition", containsString(".dmcampaign.json")));
    }

    @Test
    void shouldImportCampaign() throws Exception {
        String json = """
                {
                  "formatVersion": 1,
                  "campaign": { "name": "Imported", "description": "desc" },
                  "party": [], "statBlocks": [], "handouts": [],
                  "maps": [], "encounters": [], "notes": []
                }
                """;
        Campaign c = sampleCampaign();
        c.setName("Imported");
        when(service.importFromJson(json)).thenReturn(c);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.dmcampaign.json", "application/json", json.getBytes());

        mockMvc.perform(multipart("/campaigns/import")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Imported")));
    }
}
```

- [ ] **Step 3: Run controller tests** (they still fail — HomeController not yet created)

Run: `./mvnw test -pl . -Dtest=CampaignControllerTest`
Expected: BUILD FAILURE (Test compilation errors — CampaignController not found)

- [ ] **Step 4: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignControllerTest.java && git commit -m "test: add CampaignController tests (TDD red phase)"
```

---

### Task 10: Create CSS design system

**Files:**
- Create: `src/main/resources/static/css/app.css`

- [ ] **Step 1: Write app.css**

Create `src/main/resources/static/css/app.css`:

```css
/* === Design Tokens === */
:root {
  --color-bg: #1a1a2e;
  --color-surface: #16213e;
  --color-surface-hover: #1f3460;
  --color-text: #e0e0e0;
  --color-text-muted: #8888a0;
  --color-accent: #7b68ee;
  --color-accent-hover: #9b8fff;
  --color-danger: #e74c3c;
  --color-danger-hover: #ff6b5e;
  --color-success: #2ecc71;
  --color-warning: #f39c12;
  --color-border: #2a2a4a;

  --space-xs: 4px;
  --space-sm: 8px;
  --space-md: 16px;
  --space-lg: 24px;
  --space-xl: 32px;

  --text-sm: 0.875rem;
  --text-base: 1rem;
  --text-lg: 1.25rem;
  --text-xl: 1.5rem;
  --text-2xl: 2rem;

  --radius: 8px;
  --shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  --transition: 150ms ease;

  font-family: 'Segoe UI', system-ui, -apple-system, sans-serif;
  font-size: 16px;
  color: var(--color-text);
  background-color: var(--color-bg);
}

*, *::before, *::after {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  min-height: 100vh;
}

body.dm-mode-off {
  border: 3px solid var(--color-warning);
  border-top: none;
}

/* === Navbar === */
.navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-sm) var(--space-lg);
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  position: sticky;
  top: 0;
  z-index: 100;
}

.navbar-brand {
  font-size: var(--text-xl);
  font-weight: 700;
  color: var(--color-accent);
  letter-spacing: 0.5px;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.dm-toggle {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  font-size: var(--text-sm);
  color: var(--color-text-muted);
  cursor: pointer;
  user-select: none;
}

.dm-toggle input[type="checkbox"] {
  accent-color: var(--color-warning);
}

.pin-display {
  font-family: 'Cascadia Code', 'Fira Code', monospace;
  font-size: var(--text-sm);
  color: var(--color-text-muted);
  background: var(--color-bg);
  padding: 2px 8px;
  border-radius: 4px;
  border: 1px solid var(--color-border);
}

/* === App Layout === */
.app-layout {
  display: grid;
  grid-template-columns: 1fr 280px;
  min-height: calc(100vh - 49px);
}

.app-layout > main {
  padding: var(--space-lg);
  overflow-y: auto;
}

/* === Sidebar === */
.sidebar {
  background: var(--color-surface);
  border-left: 1px solid var(--color-border);
  padding: var(--space-md);
  overflow-y: auto;
}

.sidebar-title {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 1px;
  margin-bottom: var(--space-md);
}

/* === Cards === */
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--space-md);
}

.card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  padding: var(--space-md);
  transition: border-color var(--transition), box-shadow var(--transition);
}

.card:hover {
  border-color: var(--color-accent);
  box-shadow: var(--shadow);
}

.card h3 {
  font-size: var(--text-lg);
  margin-bottom: var(--space-xs);
}

.card h3 a {
  color: var(--color-text);
  text-decoration: none;
}

.card h3 a:hover {
  color: var(--color-accent);
}

.card p {
  color: var(--color-text-muted);
  font-size: var(--text-sm);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-time {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
  margin-top: var(--space-sm);
}

.card-actions {
  display: flex;
  gap: var(--space-xs);
  margin-top: var(--space-md);
  justify-content: flex-end;
}

/* === Buttons === */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-xs);
  padding: var(--space-xs) var(--space-md);
  font-size: var(--text-sm);
  font-weight: 500;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
  transition: all var(--transition);
  text-decoration: none;
  line-height: 1.5;
}

.btn:hover {
  background: var(--color-surface-hover);
  border-color: var(--color-accent);
}

.btn-primary {
  background: var(--color-accent);
  border-color: var(--color-accent);
  color: #fff;
}

.btn-primary:hover {
  background: var(--color-accent-hover);
  border-color: var(--color-accent-hover);
}

.btn-danger {
  color: var(--color-danger);
  border-color: transparent;
}

.btn-danger:hover {
  background: rgba(231, 76, 60, 0.15);
  border-color: var(--color-danger);
}

.btn-ghost {
  background: transparent;
  border-color: transparent;
  color: var(--color-text-muted);
}

.btn-ghost:hover {
  color: var(--color-text);
  background: var(--color-surface-hover);
}

/* === Forms === */
.form-group {
  margin-bottom: var(--space-md);
}

.form-group label {
  display: block;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text-muted);
  margin-bottom: var(--space-xs);
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: var(--space-sm) var(--space-md);
  font-size: var(--text-base);
  font-family: inherit;
  color: var(--color-text);
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  transition: border-color var(--transition);
}

.form-group input:focus,
.form-group textarea:focus {
  outline: none;
  border-color: var(--color-accent);
}

.form-group textarea {
  resize: vertical;
  min-height: 80px;
}

.form-actions {
  display: flex;
  gap: var(--space-sm);
  justify-content: flex-end;
}

/* === Page Header === */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-lg);
}

.page-header h1 {
  font-size: var(--text-2xl);
  font-weight: 700;
}

/* === Detail === */
.detail-section {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  padding: var(--space-lg);
}

.detail-section h2 {
  font-size: var(--text-xl);
  margin-bottom: var(--space-md);
}

.detail-description {
  color: var(--color-text-muted);
  font-size: var(--text-base);
  line-height: 1.7;
  margin-bottom: var(--space-lg);
  white-space: pre-wrap;
}

.detail-meta {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
  margin-bottom: var(--space-lg);
}

.detail-actions {
  display: flex;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

/* === Empty State === */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-xl) * 2;
  color: var(--color-text-muted);
  text-align: center;
}

.empty-state p {
  font-size: var(--text-lg);
  margin-bottom: var(--space-md);
}

/* === Inline Form === */
.inline-form {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  padding: var(--space-md);
  margin-bottom: var(--space-md);
}

.inline-form h3 {
  font-size: var(--text-base);
  margin-bottom: var(--space-md);
}

/* === Badge === */
.badge {
  display: inline-block;
  padding: 2px 8px;
  font-size: var(--text-sm);
  border-radius: 4px;
  background: var(--color-accent);
  color: #fff;
}

/* === Alert === */
.alert {
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius);
  font-size: var(--text-sm);
  margin-bottom: var(--space-md);
}

.alert-error {
  background: rgba(231, 76, 60, 0.15);
  border: 1px solid var(--color-danger);
  color: var(--color-danger);
}

.alert-success {
  background: rgba(46, 204, 113, 0.15);
  border: 1px solid var(--color-success);
  color: var(--color-success);
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/static/css/app.css && git commit -m "feat: add CSS design system — dark theme, layout, cards, forms, buttons"
```

---

### Task 11: Create Thymeleaf fragments (navbar, head, sidebar, scripts)

**Files:**
- Create: `src/main/resources/templates/fragments/head.html`
- Create: `src/main/resources/templates/fragments/navbar.html`
- Create: `src/main/resources/templates/fragments/sidebar.html`
- Create: `src/main/resources/templates/fragments/scripts.html`

- [ ] **Step 1: Create fragments directory**

Run: `mkdir -p src/main/resources/templates/fragments src/main/resources/templates/campaigns src/main/resources/templates/common`

- [ ] **Step 2: Write head.html**

Create `src/main/resources/templates/fragments/head.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:fragment="head">
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" th:href="@{/css/app.css}">
    <script th:src="@{/vendor/htmx.min.js}"></script>
    <script th:src="@{/vendor/alpine.min.js}"></script>
</head>
</html>
```

- [ ] **Step 3: Write navbar.html**

Create `src/main/resources/templates/fragments/navbar.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<nav class="navbar" th:fragment="navbar">
    <a href="/campaigns" class="navbar-brand">DMHelper</a>
    <div class="navbar-right">
        <label class="dm-toggle">
            <input type="checkbox" id="dmModeCheckbox" onclick="document.body.classList.toggle('dm-mode-off')">
            DM Mode
        </label>
        <span class="pin-display" id="pinDisplay">PIN: —</span>
    </div>
</nav>
</html>
```

- [ ] **Step 4: Write sidebar.html**

Create `src/main/resources/templates/fragments/sidebar.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<aside class="sidebar" th:fragment="sidebar">
    <div class="sidebar-title">Quick Access</div>
    <p style="color: var(--color-text-muted); font-size: var(--text-sm);">
        Session plans and quick links will appear here in a future update.
    </p>
</aside>
</html>
```

- [ ] **Step 5: Write scripts.html**

Create `src/main/resources/templates/fragments/scripts.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<th:block th:fragment="scripts">
    <script th:src="@{/vendor/htmx.min.js}"></script>
    <script th:src="@{/vendor/alpine.min.js}"></script>
</th:block>
</html>
```

Note: `head.html` already loads htmx and Alpine in `<head>`, so `scripts.html` is an alternative for pages that need scripts at the bottom. For M1, scripts are loaded in `<head>`.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/fragments/ && git commit -m "feat: add Thymeleaf fragments — head, navbar, sidebar, scripts"
```

---

### Task 12: Create campaign list page and card/form fragments

**Files:**
- Create: `src/main/resources/templates/campaigns/list.html`
- Create: `src/main/resources/templates/campaigns/_card.html`
- Create: `src/main/resources/templates/campaigns/_form.html`
- Create: `src/main/resources/templates/common/_empty-state.html`

- [ ] **Step 1: Write empty-state fragment**

Create `src/main/resources/templates/common/_empty-state.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div class="empty-state" th:fragment="empty-state(message)">
    <p th:text="${message}">Nothing here yet.</p>
</div>
</html>
```

- [ ] **Step 2: Write campaign card fragment**

Create `src/main/resources/templates/campaigns/_card.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div class="card" th:fragment="card(campaign)">
    <h3>
        <a th:href="@{/campaigns/{id}(id=${campaign.id})}" th:text="${campaign.name}">Campaign Name</a>
    </h3>
    <p th:if="${campaign.description != null and !campaign.description.isBlank()}"
       th:text="${campaign.description}">Description</p>
    <div class="card-time" th:text="'Created ' + ${#temporals.format(campaign.createdAt, 'yyyy-MM-dd HH:mm')}">
        Created date
    </div>
    <div class="card-actions">
        <form th:action="@{/campaigns/{id}(id=${campaign.id})}" method="post"
              hx-delete="@{/campaigns/{id}(id=${campaign.id})}"
              hx-confirm="Delete this campaign?"
              hx-target="closest .card"
              hx-swap="outerHTML">
            <input type="hidden" th:name="${_csrf?.parameterName}" th:value="${_csrf?.token}" th:if="${_csrf != null}"/>
            <button type="submit" class="btn btn-danger">Delete</button>
        </form>
    </div>
</div>
</html>
```

- [ ] **Step 3: Write inline form fragment**

Create `src/main/resources/templates/campaigns/_form.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div class="inline-form" th:fragment="form">
    <h3>New Campaign</h3>
    <form hx-post="/campaigns"
          hx-target="previous .card-grid"
          hx-swap="beforeend">
        <div class="form-group">
            <label for="campaignName">Name</label>
            <input type="text" id="campaignName" name="name" required
                   placeholder="Campaign name" autofocus>
        </div>
        <div class="form-group">
            <label for="campaignDescription">Description</label>
            <textarea id="campaignDescription" name="description"
                      placeholder="Optional campaign description"></textarea>
        </div>
        <div class="form-actions">
            <button type="button" class="btn btn-ghost"
                    hx-get="/campaigns?fragment=empty-form"
                    hx-target="closest .inline-form"
                    hx-swap="outerHTML">Cancel</button>
            <button type="submit" class="btn btn-primary">Create</button>
        </div>
    </form>
</div>
</html>
```

- [ ] **Step 4: Write campaign list page**

Create `src/main/resources/templates/campaigns/list.html`:

```html
<!DOCTYPE html>
<html lang="en" data-theme="dark" xmlns:th="http://www.thymeleaf.org">
<head>
    <th:block th:replace="~{fragments/head :: head}"></th:block>
    <title>DMHelper — Campaigns</title>
</head>
<body>
    <th:block th:replace="~{fragments/navbar :: navbar}"></th:block>

    <div class="app-layout">
        <main>
            <div class="page-header">
                <h1>Campaigns</h1>
                <button class="btn btn-primary"
                        hx-get="/campaigns?fragment=form"
                        hx-target="this"
                        hx-swap="outerHTML"
                        th:unless="${campaigns == null || campaigns.isEmpty()}">
                    + New Campaign
                </button>
            </div>

            <th:block th:if="${campaigns == null || campaigns.isEmpty()}">
                <th:block th:replace="~{common/_empty-state :: empty-state('No campaigns yet. Create your first one!')}"></th:block>
                <th:block th:replace="~{campaigns/_form :: form}"></th:block>
            </th:block>

            <div class="card-grid" th:unless="${campaigns == null or campaigns.isEmpty()}">
                <th:block th:each="campaign : ${campaigns}">
                    <th:block th:replace="~{campaigns/_card :: card(campaign=${campaign})}"></th:block>
                </th:block>
            </div>
        </main>

        <th:block th:replace="~{fragments/sidebar :: sidebar}"></th:block>
    </div>
</body>
</html>
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/campaigns/ src/main/resources/templates/common/ && git commit -m "feat: add campaign list page, card, and form fragments"
```

---

### Task 13: Create campaign detail page

**Files:**
- Create: `src/main/resources/templates/campaigns/detail.html`

- [ ] **Step 1: Write detail page**

Create `src/main/resources/templates/campaigns/detail.html`:

```html
<!DOCTYPE html>
<html lang="en" data-theme="dark" xmlns:th="http://www.thymeleaf.org">
<head>
    <th:block th:replace="~{fragments/head :: head}"></th:block>
    <title th:text="'DMHelper — ' + ${campaign.name}">DMHelper — Campaign</title>
</head>
<body>
    <th:block th:replace="~{fragments/navbar :: navbar}"></th:block>

    <div class="app-layout">
        <main>
            <div class="page-header">
                <h1 th:text="${campaign.name}">Campaign Name</h1>
                <a href="/campaigns" class="btn btn-ghost">← Back to Campaigns</a>
            </div>

            <div class="detail-section">
                <div class="detail-meta" th:text="'Created ' + ${#temporals.format(campaign.createdAt, 'yyyy-MM-dd HH:mm')}">
                    Created date
                </div>

                <div class="detail-description" th:if="${campaign.description != null and !campaign.description.isBlank()}"
                     th:text="${campaign.description}">Description</div>

                <h2>Edit</h2>
                <form hx-put="@{/campaigns/{id}(id=${campaign.id})}"
                      hx-target="body"
                      hx-swap="outerHTML">
                    <div class="form-group">
                        <label for="editName">Name</label>
                        <input type="text" id="editName" name="name" required
                               th:value="${campaign.name}">
                    </div>
                    <div class="form-group">
                        <label for="editDescription">Description</label>
                        <textarea id="editDescription" name="description"
                                  th:text="${campaign.description}"></textarea>
                    </div>
                    <div class="detail-actions">
                        <button type="submit" class="btn btn-primary">Save Changes</button>
                    </div>
                </form>

                <hr style="border-color: var(--color-border); margin: var(--space-lg) 0;">

                <div class="detail-actions">
                    <a th:href="@{/campaigns/{id}/export(id=${campaign.id})}"
                       class="btn" download>Export JSON</a>

                    <form hx-post="/campaigns/import"
                          hx-encoding="multipart/form-data"
                          style="display: contents;">
                        <label class="btn">
                            Import JSON
                            <input type="file" name="file" accept=".json"
                                   onchange="this.form.dispatchEvent(new Event('submit', {bubbles: true}))"
                                   style="display: none;">
                        </label>
                    </form>

                    <form th:action="@{/campaigns/{id}(id=${campaign.id})}" method="post"
                          hx-delete="@{/campaigns/{id}(id=${campaign.id})}"
                          hx-confirm="Delete this campaign?"
                          hx-target="body"
                          hx-swap="outerHTML"
                          style="display: contents;">
                        <button type="submit" class="btn btn-danger">Delete Campaign</button>
                    </form>
                </div>
            </div>
        </main>

        <th:block th:replace="~{fragments/sidebar :: sidebar}"></th:block>
    </div>
</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/campaigns/detail.html && git commit -m "feat: add campaign detail page with edit, export, import, delete"
```

---

### Task 14: Implement CampaignController (TDD — green phase)

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/GlobalExceptionHandler.java`

- [ ] **Step 1: Create web and common packages**

Run: `mkdir -p src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web src/main/java/dev/hendrikhoemberg/dmhelper/common/web`

- [ ] **Step 2: Write CampaignController**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java`:

```java
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
```

- [ ] **Step 3: Write GlobalExceptionHandler**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/GlobalExceptionHandler.java`:

```java
package dev.hendrikhoemberg.dmhelper.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import java.net.URI;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        if ("true".equals(request.getHeader("HX-Request"))) {
            ModelAndView mav = new ModelAndView("common/_error");
            mav.addObject("message", ex.getMessage());
            mav.setStatus(HttpStatus.BAD_REQUEST);
            return mav;
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("urn:dmhelper:validation-error"));
        problem.setTitle("Validation Error");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }
}
```

- [ ] **Step 4: Create error fragment**

Create `src/main/resources/templates/common/_error.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div class="alert alert-error" th:fragment="error">
    <span th:text="${message}">Error message</span>
</div>
</html>
```

- [ ] **Step 5: Run controller tests**

Run: `./mvnw test -pl . -Dtest=CampaignControllerTest`
Expected: Tests pass (controller returns correct views/fragments)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java src/main/java/dev/hendrikhoemberg/dmhelper/common/web/GlobalExceptionHandler.java src/main/resources/templates/common/_error.html && git commit -m "feat: implement CampaignController and GlobalExceptionHandler"
```

---

### Task 15: Download and vendor htmx, Alpine.js, Konva.js

**Files:**
- Create: `src/main/resources/static/vendor/htmx.min.js`
- Create: `src/main/resources/static/vendor/alpine.min.js`
- Create: `src/main/resources/static/vendor/konva.min.js`
- Create: `VENDOR.md`

- [ ] **Step 1: Create vendor directory**

Run: `mkdir -p src/main/resources/static/vendor`

- [ ] **Step 2: Download htmx v2**

Run:
```bash
curl -L -o src/main/resources/static/vendor/htmx.min.js \
  "https://unpkg.com/htmx.org@2.0.4/dist/htmx.min.js"
```

- [ ] **Step 3: Download Alpine.js v3**

Run:
```bash
curl -L -o src/main/resources/static/vendor/alpine.min.js \
  "https://unpkg.com/alpinejs@3.14.9/dist/cdn.min.js"
```

- [ ] **Step 4: Download Konva.js v9**

Run:
```bash
curl -L -o src/main/resources/static/vendor/konva.min.js \
  "https://unpkg.com/konva@9.3.18/konva.min.js"
```

- [ ] **Step 5: Compute SHA-256 hashes**

Run:
```bash
sha256sum src/main/resources/static/vendor/htmx.min.js
sha256sum src/main/resources/static/vendor/alpine.min.js
sha256sum src/main/resources/static/vendor/konva.min.js
```

- [ ] **Step 6: Write VENDOR.md**

Replace `<HTMX_HASH>`, `<ALPINE_HASH>`, `<KONVA_HASH>` with the actual hashes from step 5.

Create `VENDOR.md`:

```markdown
# Vendored Frontend Libraries

| File | Version | Upstream URL | SHA-256 |
|---|---|---|---|
| `vendor/htmx.min.js` | 2.0.4 | https://unpkg.com/htmx.org@2.0.4/dist/htmx.min.js | `<HTMX_HASH>` |
| `vendor/alpine.min.js` | 3.14.9 | https://unpkg.com/alpinejs@3.14.9/dist/cdn.min.js | `<ALPINE_HASH>` |
| `vendor/konva.min.js` | 9.3.18 | https://unpkg.com/konva@9.3.18/konva.min.js | `<KONVA_HASH>` |

All three libraries are dependency-free by design. Upgrading any library is a deliberate,
reviewed commit — never an automatic resolution.
```

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/vendor/ VENDOR.md && git commit -m "feat: vendor htmx 2.0.4, Alpine 3.14.9, Konva 9.3.18; add VENDOR.md"
```

---

### Task 16: Add root redirect to CampaignController

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/HomeController.java`

Note: The test expects `GET /` to redirect to `/campaigns`. Spring Boot doesn't have a root handler yet.

- [ ] **Step 1: Write HomeController**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/HomeController.java`:

```java
package dev.hendrikhoemberg.dmhelper.common.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/campaigns";
    }
}
```

- [ ] **Step 2: Run controller tests**

Run: `./mvnw test -pl . -Dtest=CampaignControllerTest`
Expected: All tests pass including `shouldRedirectRootToCampaigns`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/common/web/HomeController.java && git commit -m "feat: add root redirect to /campaigns"
```

---

### Task 17: Integration test — full app startup and smoke test

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/DmhelperApplicationTests.java`

- [ ] **Step 1: Update application test**

Read `src/test/java/dev/hendrikhoemberg/dmhelper/DmhelperApplicationTests.java` and replace its contents with:

```java
package dev.hendrikhoemberg.dmhelper;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true"
})
class DmhelperApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: Run all tests**

Run: `./mvnw test`
Expected: BUILD SUCCESS, all tests pass (ApplicationTests + CampaignServiceTest + CampaignControllerTest)

- [ ] **Step 3: Full build**

Run: `./mvnw clean package`
Expected: BUILD SUCCESS, JAR in `target/`

- [ ] **Step 4: Run the JAR**

Run: `java -jar target/dmhelper-0.0.1-SNAPSHOT.jar`
Expected: App starts on port 8080. Access `http://localhost:8080` — redirects to `/campaigns`, shows empty state + create form. Create a campaign, see it in the grid. View detail page, export JSON, delete. Import the exported JSON — new campaign appears.
Then Ctrl+C to stop.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/DmhelperApplicationTests.java && git commit -m "test: update application test with H2 mem DB; full test suite passes"
```

---

### Task 18: Final verification — clean build from scratch

- [ ] **Step 1: Clean everything**

Run: `rm -rf ~/.dmhelper/data && ./mvnw clean`

- [ ] **Step 2: Run all tests**

Run: `./mvnw test`
Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 3: Package**

Run: `./mvnw clean package -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Start app**

Run: `java -jar target/dmhelper-0.0.1-SNAPSHOT.jar`
Expected: App starts, Flyway runs migration, app listens on port 8080

- [ ] **Step 5: Verify manually**

Open `http://localhost:8080` in a browser.
- Page redirects to `/campaigns`
- Shows "No campaigns yet" + create form
- Create a campaign → card appears in grid
- Click card → detail page with name, description, edit form
- Click "Export JSON" → downloads `.dmcampaign.json` file
- Click "Import JSON" → upload the `.dmcampaign.json` file → new campaign card appears
- Delete a campaign → removed from grid
- DM Mode toggle changes border color
- Stop app (Ctrl+C), restart → data persists (campaigns still there)

---

## Task Summary

| Task | Component | Key Files |
|---|---|---|
| 1 | Project cleanup | `pom.xml`, `compose.yaml` |
| 2 | Configuration | `application.properties` |
| 3 | Flyway migration | `V1__create_campaign.sql` |
| 4 | Campaign entity | `campaign/data/Campaign.java` |
| 5 | Campaign repository | `campaign/data/CampaignRepository.java` |
| 6 | Export/Import DTO | `campaign/service/CampaignExportDto.java` |
| 7 | Service tests (red) | `campaign/service/CampaignServiceTest.java` |
| 8 | Service implementation | `campaign/service/CampaignService.java` |
| 9 | Controller tests (red) | `campaign/web/CampaignControllerTest.java` |
| 10 | CSS design system | `static/css/app.css` |
| 11 | Thymeleaf fragments | `fragments/{head,navbar,sidebar,scripts}.html` |
| 12 | List page + fragments | `campaigns/{list,_card,_form}.html`, `common/_empty-state.html` |
| 13 | Detail page | `campaigns/detail.html` |
| 14 | Controller + error handler | `campaign/web/CampaignController.java`, `common/web/GlobalExceptionHandler.java` |
| 15 | Vendored JS + VENDOR.md | `static/vendor/*.js`, `VENDOR.md` |
| 16 | Root redirect | `common/web/HomeController.java` |
| 17 | Integration smoke test | `DmhelperApplicationTests.java` |
| 18 | Final verification | Full build, manual smoke test |
