# Encounter New Full-Page Rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the new-encounter form inside the complete application shell for normal navigation while preserving its existing inline HTMX response.

**Architecture:** `EncounterController.newForm` will use the established `HX-Request` header split from `GameMapController`. A new full-page Thymeleaf template will compose the existing form fragment; no service or persistence behavior changes.

**Tech Stack:** Java 21, Spring MVC, Thymeleaf, MockMvc, JUnit 5, AssertJ

## Global Constraints

- Preserve the existing `encounter/_form :: form` response for requests with `HX-Request: true`.
- Reuse the existing form fragment and application-shell fragments.
- Add no service, database, or form-submission changes.

---

### Task 1: Request-mode-aware new encounter page

**Files:**
- Create: `src/main/resources/templates/encounter/new.html`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterController.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterControllerTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/web/FullPageRenderSmokeTest.java`

**Interfaces:**
- Consumes: HTTP header `HX-Request`, campaign model data from `CampaignModelAdvice`, and Thymeleaf fragment `encounter/_form :: form(campaignId, encounter, maps)`.
- Produces: normal-navigation view `encounter/new` and HTMX view `encounter/_form :: form`.

- [x] **Step 1: Write failing controller and full-page smoke tests**

Replace the existing broad `shouldRenderNewForm` test with:

```java
@Test
void shouldRenderNewEncounterPageForNormalNavigation() throws Exception {
    when(mapRepo.findByCampaignIdOrderBySortOrderAsc(campaignId)).thenReturn(List.of());

    mockMvc.perform(get("/campaigns/{campaignId}/encounters/new", campaignId))
            .andExpect(status().isOk())
            .andExpect(view().name("encounter/new"));
}

@Test
void shouldRenderNewEncounterFormFragmentForHtmxNavigation() throws Exception {
    when(mapRepo.findByCampaignIdOrderBySortOrderAsc(campaignId)).thenReturn(List.of());

    mockMvc.perform(get("/campaigns/{campaignId}/encounters/new", campaignId)
                    .header("HX-Request", "true"))
            .andExpect(status().isOk())
            .andExpect(view().name("encounter/_form :: form"));
}
```

Add `c + "/encounters/new"` to `FullPageRenderSmokeTest.pages()`.

- [x] **Step 2: Run the focused controller tests and verify RED**

Run:

```bash
./mvnw -Dtest=EncounterControllerTest test
```

Expected: `shouldRenderNewEncounterPageForNormalNavigation` fails because the controller returns `encounter/_form :: form`.

- [x] **Step 3: Implement the minimal request-mode split and page**

Add the request header to `EncounterController.newForm`:

```java
@RequestHeader(value = "HX-Request", defaultValue = "false") boolean htmxRequest
```

Return:

```java
return htmxRequest ? "encounter/_form :: form" : "encounter/new";
```

Create `encounter/new.html` with the standard head, navbar, application shell, “New Encounter” page header, back link to the encounter list, and:

```html
<th:block th:replace="~{encounter/_form :: form(
    campaignId=${campaignId}, encounter=${encounter}, maps=${maps})}"></th:block>
```

- [x] **Step 4: Run focused and integration tests and verify GREEN**

Run:

```bash
./mvnw -Dtest=EncounterControllerTest,FullPageRenderSmokeTest test
```

Expected: all selected tests pass and the new route renders a document ending in `</html>`.

- [ ] **Step 5: Run repository verification**

Run:

```bash
./mvnw test
git diff --check
```

Expected: the complete test suite passes and the diff has no whitespace errors.
