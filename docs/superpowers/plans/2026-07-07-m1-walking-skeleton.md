# M1: Walking Skeleton — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `java -jar dmhelper.jar` → create, export, and import a campaign in the browser.

**Architecture:** Spring Boot 4.1.0 monolith with server-rendered Thymeleaf + htmx views. Hibernate `ddl-auto=update` manages the schema from JPA entities (no Flyway). H2 file-mode database at `~/.dmhelper/data/`. Vendored JS libraries (htmx, Alpine, Konva) with no CDN. Backup rotation on startup.

**Tech Stack:** Spring Boot 4.1.0, Java 25, Thymeleaf + htmx + Alpine.js, Konva.js (vendored), H2 file mode, Hibernate ddl-auto=update, Jackson, JPA, JUnit 5 + Mockito

---

### Task 1: Clean up pom.xml — remove Spring Security, add H2

**Files:**
- Modify: `pom.xml`

Spring Security gates all endpoints by default and the spec's PIN gate arrives in M8 — remove it now so the walking skeleton is accessible. Add `h2` for the file-mode database.

- [ ] **Step 1: Read current pom.xml, then apply edits**

Remove these two dependencies:
```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
```
```xml
        <dependency>
            <groupId>org.thymeleaf.extras</groupId>
            <artifactId>thymeleaf-extras-springsecurity6</artifactId>
        </dependency>
```

And remove:
```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security-test</artifactId>
            <scope>test</scope>
        </dependency>
```

And remove:
```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-docker-compose</artifactId>
            <scope>runtime</scope>
            <optional>true</optional>
        </dependency>
```

Add the H2 dependency after `spring-boot-starter-data-jpa`:
```xml
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
```

- [ ] **Step 2: Remove compose.yaml**

Run: `rm -f compose.yaml`

- [ ] **Step 3: Verify compilation**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add pom.xml && git rm compose.yaml 2>/dev/null; git commit -m "chore: remove Spring Security, add H2; remove compose.yaml"
```

---

### Task 2: Configure application.properties for H2 file mode + ddl-auto=update

**Files:**
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Replace application.properties**

Replace the entire contents of `src/main/resources/application.properties` with:

```properties
spring.application.name=dmhelper
server.port=8081

spring.datasource.url=jdbc:h2:file:${user.home}/.dmhelper/data/dmhelper;AUTO_SERVER=TRUE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

spring.jpa.hibernate.ddl-auto=update
spring.jpa.open-in-view=false

spring.h2.console.enabled=false

spring.thymeleaf.cache=false
spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.suffix=.html
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/application.properties && git commit -m "feat: configure H2 file mode, ddl-auto=update, Thymeleaf"
```

---

### Task 3: Create Campaign entity

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/data/Campaign.java`

- [ ] **Step 1: Create package directories**

Run: `mkdir -p src/main/java/dev/hendrikhoemberg/dmhelper/campaign/data`

- [ ] **Step 2: Write Campaign entity**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/data/Campaign.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.data;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "campaign")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "CLOB")
    private String description;

    @Column(columnDefinition = "CLOB")
    private String settings;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSettings() { return settings; }
    public void setSettings(String settings) { this.settings = settings; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./mvnw compile`
Expected: BUILD SUCCESS (ddl-auto=update will create the campaign table in H2 on context load)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/data/Campaign.java && git commit -m "feat: add Campaign JPA entity"
```

---

### Task 4: Create CampaignRepository

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

- [ ] **Step 2: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/data/CampaignRepository.java && git commit -m "feat: add CampaignRepository"
```

---

### Task 5: Create CampaignExportDto for JSON import/export

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java`

- [ ] **Step 1: Create service package**

Run: `mkdir -p src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service`

- [ ] **Step 2: Write CampaignExportDto**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;

import java.util.List;

public record CampaignExportDto(
        int formatVersion,
        CampaignDto campaign,
        List<Object> party,
        List<Object> statBlocks,
        List<Object> handouts,
        List<Object> maps,
        List<Object> encounters,
        List<Object> notes
) {
    private static final int CURRENT_FORMAT_VERSION = 1;

    public static CampaignExportDto from(Campaign campaign) {
        return new CampaignExportDto(
                CURRENT_FORMAT_VERSION,
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
        public CampaignDto {
            if (description != null && description.isBlank()) {
                description = null;
            }
        }
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java && git commit -m "feat: add CampaignExportDto for JSON import/export"
```

---

### Task 6: Write CampaignService tests (TDD — red phase)

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(CampaignService.class)
class CampaignServiceTest {

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

- [ ] **Step 3: Run tests to verify they fail**

Run: `./mvnw test -pl . -Dtest=CampaignServiceTest`
Expected: Compilation fails — `CampaignService` class not found

- [ ] **Step 4: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignServiceTest.java && git commit -m "test: add CampaignService tests (TDD red phase)"
```

---

### Task 7: Implement CampaignService (TDD — green phase)

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`

- [ ] **Step 1: Write CampaignService**

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
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java && git commit -m "feat: implement CampaignService with CRUD, JSON export/import"
```

---

### Task 8: Create CSS design system

**Files:**
- Create: `src/main/resources/static/css/app.css`

- [ ] **Step 1: Create CSS directory**

Run: `mkdir -p src/main/resources/static/css`

- [ ] **Step 2: Write app.css**

Create `src/main/resources/static/css/app.css`:

```css
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

.app-layout {
  display: grid;
  grid-template-columns: 1fr 280px;
  min-height: calc(100vh - 49px);
}

.app-layout > main {
  padding: var(--space-lg);
  overflow-y: auto;
}

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

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: calc(var(--space-xl) * 2);
  color: var(--color-text-muted);
  text-align: center;
}

.empty-state p {
  font-size: var(--text-lg);
  margin-bottom: var(--space-md);
}

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

.badge {
  display: inline-block;
  padding: 2px 8px;
  font-size: var(--text-sm);
  border-radius: 4px;
  background: var(--color-accent);
  color: #fff;
}

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

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/css/app.css && git commit -m "feat: add CSS design system — dark theme, layout, cards, forms, buttons"
```

---

### Task 9: Create Thymeleaf fragments (head, navbar, sidebar)

**Files:**
- Create: `src/main/resources/templates/fragments/head.html`
- Create: `src/main/resources/templates/fragments/navbar.html`
- Create: `src/main/resources/templates/fragments/sidebar.html`

- [ ] **Step 1: Create directories**

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
            <input type="checkbox" id="dmModeCheckbox"
                   onclick="document.body.classList.toggle('dm-mode-off')">
            DM Mode
        </label>
        <span class="pin-display" id="pinDisplay">PIN: &#x2014;</span>
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

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/fragments/ && git commit -m "feat: add Thymeleaf fragments — head, navbar, sidebar"
```

---

### Task 10: Create campaign list page, card fragment, and form fragment

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
    <div class="card-time"
         th:text="'Created ' + ${#temporals.format(campaign.createdAt, 'yyyy-MM-dd HH:mm')}">
        Created date
    </div>
    <div class="card-actions">
        <button class="btn btn-danger"
                hx-delete="@{/campaigns/{id}(id=${campaign.id})}"
                hx-confirm="Delete this campaign?"
                hx-target="closest .card"
                hx-swap="outerHTML">
            Delete
        </button>
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

### Task 11: Create campaign detail page

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
                <a href="/campaigns" class="btn btn-ghost">&larr; Back to Campaigns</a>
            </div>

            <div class="detail-section">
                <div class="detail-meta"
                     th:text="'Created ' + ${#temporals.format(campaign.createdAt, 'yyyy-MM-dd HH:mm')}">
                    Created date
                </div>

                <div class="detail-description"
                     th:if="${campaign.description != null and !campaign.description.isBlank()}"
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
                                   onchange="this.form.requestSubmit()"
                                   style="display: none;">
                        </label>
                    </form>

                    <button class="btn btn-danger"
                            hx-delete="@{/campaigns/{id}(id=${campaign.id})}"
                            hx-confirm="Delete this campaign?"
                            hx-target="body"
                            hx-swap="outerHTML">
                        Delete Campaign
                    </button>
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

### Task 12: Write CampaignController tests (TDD — red phase)

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

- [ ] **Step 3: Run tests to verify they fail**

Run: `./mvnw test -pl . -Dtest=CampaignControllerTest`
Expected: Compilation fails — `CampaignController` class not found

- [ ] **Step 4: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignControllerTest.java && git commit -m "test: add CampaignController tests (TDD red phase)"
```

---

### Task 13: Implement CampaignController + GlobalExceptionHandler (TDD — green phase)

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/GlobalExceptionHandler.java`
- Create: `src/main/resources/templates/common/_error.html`

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

- [ ] **Step 4: Write error fragment**

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
Expected: All 9 tests pass

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java src/main/java/dev/hendrikhoemberg/dmhelper/common/web/GlobalExceptionHandler.java src/main/resources/templates/common/_error.html && git commit -m "feat: implement CampaignController and GlobalExceptionHandler"
```

---

### Task 14: Add root redirect to /campaigns

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/HomeController.java`

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

- [ ] **Step 2: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/common/web/HomeController.java && git commit -m "feat: add root redirect to /campaigns"
```

---

### Task 15: Implement database backup system

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/common/config/StartupBackupRunner.java`

Per spec §6: on every app start, copy the H2 database file to a rotating backup folder (`~/.dmhelper/backups`, keep last 10).

- [ ] **Step 1: Create config package**

Run: `mkdir -p src/main/java/dev/hendrikhoemberg/dmhelper/common/config`

- [ ] **Step 2: Write StartupBackupRunner**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/common/config/StartupBackupRunner.java`:

```java
package dev.hendrikhoemberg.dmhelper.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.Stream;

@Component
public class StartupBackupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupBackupRunner.class);
    private static final int MAX_BACKUPS = 10;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path dataDir;
    private final Path backupDir;

    public StartupBackupRunner(@Value("${user.home}") String userHome) {
        this.dataDir = Path.of(userHome, ".dmhelper", "data");
        this.backupDir = Path.of(userHome, ".dmhelper", "backups");
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!Files.isDirectory(dataDir)) {
            log.info("No data directory at {} — skipping startup backup", dataDir);
            return;
        }

        try {
            Files.createDirectories(backupDir);

            String timestamp = LocalDateTime.now().format(FMT);
            Path snapshotDir = backupDir.resolve("dmhelper-" + timestamp);
            Files.createDirectory(snapshotDir);

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDir)) {
                for (Path file : stream) {
                    Files.copy(file, snapshotDir.resolve(file.getFileName()));
                }
            }

            log.info("Database backup created at {}", snapshotDir);

            rotateBackups();
        } catch (IOException e) {
            log.error("Failed to create startup backup", e);
        }
    }

    private void rotateBackups() throws IOException {
        try (Stream<Path> dirs = Files.list(backupDir)) {
            var backups = dirs
                    .filter(Files::isDirectory)
                    .sorted(Comparator.reverseOrder())
                    .toList();

            for (int i = MAX_BACKUPS; i < backups.size(); i++) {
                deleteRecursively(backups.get(i));
                log.info("Removed old backup: {}", backups.get(i));
            }
        }
    }

    private void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            log.warn("Failed to delete {}", p, e);
                        }
                    });
        }
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/common/config/StartupBackupRunner.java && git commit -m "feat: add startup database backup rotation (keep last 10)"
```

---

### Task 16: Download and vendor htmx, Alpine.js, Konva.js

**Files:**
- Create: `src/main/resources/static/vendor/htmx.min.js`
- Create: `src/main/resources/static/vendor/alpine.min.js`
- Create: `src/main/resources/static/vendor/konva.min.js`
- Create: `VENDOR.md`

- [ ] **Step 1: Create vendor directory**

Run: `mkdir -p src/main/resources/static/vendor`

- [ ] **Step 2: Download htmx v2.0.4**

Run:
```bash
curl -L -o src/main/resources/static/vendor/htmx.min.js \
  "https://unpkg.com/htmx.org@2.0.4/dist/htmx.min.js"
```

- [ ] **Step 3: Download Alpine.js v3.14.9**

Run:
```bash
curl -L -o src/main/resources/static/vendor/alpine.min.js \
  "https://unpkg.com/alpinejs@3.14.9/dist/cdn.min.js"
```

- [ ] **Step 4: Download Konva.js v9.3.18**

Run:
```bash
curl -L -o src/main/resources/static/vendor/konva.min.js \
  "https://unpkg.com/konva@9.3.18/konva.min.js"
```

- [ ] **Step 5: Compute SHA-256 hashes**

Run:
```bash
sha256sum src/main/resources/static/vendor/htmx.min.js src/main/resources/static/vendor/alpine.min.js src/main/resources/static/vendor/konva.min.js
```

Copy the three hashes for the next step.

- [ ] **Step 6: Write VENDOR.md**

Create `VENDOR.md` (replace `<HTMX_HASH>`, `<ALPINE_HASH>`, `<KONVA_HASH>` with the actual hashes from step 5):

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

### Task 17: Configure WebMvcConfig for static resources

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/common/config/WebMvcConfig.java`

- [ ] **Step 1: Write WebMvcConfig**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/common/config/WebMvcConfig.java`:

```java
package dev.hendrikhoemberg.dmhelper.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/vendor/**")
                .addResourceLocations("classpath:/static/vendor/")
                .setCachePeriod(31536000);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/common/config/WebMvcConfig.java && git commit -m "feat: add WebMvcConfig with long-cache vendor resource handler"
```

---

### Task 18: Integration test — full app build and smoke test

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/DmhelperApplicationTests.java`

- [ ] **Step 1: Update application test**

Replace the contents of `src/test/java/dev/hendrikhoemberg/dmhelper/DmhelperApplicationTests.java` with:

```java
package dev.hendrikhoemberg.dmhelper;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"
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

Run: `./mvnw clean package -DskipTests`
Expected: BUILD SUCCESS, JAR in `target/dmhelper-0.0.1-SNAPSHOT.jar`

- [ ] **Step 4: Run the JAR and verify manually**

Run: `java -jar target/dmhelper-0.0.1-SNAPSHOT.jar`

Open `http://localhost:8081` in a browser.
- Redirects to `/campaigns`
- Shows "No campaigns yet. Create your first one!" + create form
- Create a campaign → card appears in grid
- Click card → detail page with name, description, edit form
- Click "Export JSON" → downloads `.dmcampaign.json` file
- Click "Import JSON" → upload the `.dmcampaign.json` → new campaign card appears
- Delete a campaign → card removed from grid
- DM Mode toggle changes body border color
- Stop app (Ctrl+C), restart → data persists (campaigns still there)

- [ ] **Step 5: Verify backup rotation**

Check that `~/.dmhelper/backups/` exists and contains a timestamped directory with database files.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/DmhelperApplicationTests.java && git commit -m "test: update application test with H2 mem DB; full test suite passes"
```

---

## Task Summary

| Task | Component | Key Files |
|---|---|---|
| 1 | Project cleanup | `pom.xml`, `compose.yaml` |
| 2 | Configuration | `application.properties` |
| 3 | Campaign entity | `campaign/data/Campaign.java` |
| 4 | Campaign repository | `campaign/data/CampaignRepository.java` |
| 5 | Export/Import DTO | `campaign/service/CampaignExportDto.java` |
| 6 | Service tests (red) | `campaign/service/CampaignServiceTest.java` |
| 7 | Service implementation (green) | `campaign/service/CampaignService.java` |
| 8 | CSS design system | `static/css/app.css` |
| 9 | Thymeleaf fragments | `fragments/{head,navbar,sidebar}.html` |
| 10 | List page + fragments | `campaigns/{list,_card,_form}.html`, `common/_empty-state.html` |
| 11 | Detail page | `campaigns/detail.html` |
| 12 | Controller tests (red) | `campaign/web/CampaignControllerTest.java` |
| 13 | Controller + error handler (green) | `campaign/web/CampaignController.java`, `common/web/GlobalExceptionHandler.java`, `common/_error.html` |
| 14 | Root redirect | `common/web/HomeController.java` |
| 15 | Backup system | `common/config/StartupBackupRunner.java` |
| 16 | Vendored JS + VENDOR.md | `static/vendor/*.js`, `VENDOR.md` |
| 17 | WebMvcConfig | `common/config/WebMvcConfig.java` |
| 18 | Final verification | `DmhelperApplicationTests.java`, manual smoke test |
