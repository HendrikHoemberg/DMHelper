# P0 Runtime Reliability Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining P0 release gate by making map, encounter, dice, and presentation mutations fail visibly and recoverably, attaching a safe correlation identifier to failures, and labeling the current encounter difficulty calculation as an estimate with its source and assumptions.

**Architecture:** Add one request-correlation filter and one browser request/error helper instead of teaching every interaction its own error protocol. Map and tracker components keep ownership of domain rollback and retry callbacks, while the shared helper owns non-2xx parsing, safe reference display, and actionable toast rendering. Preserve the existing calculator for now, but make its approximation explicit in both the Java result contract and encounter UI.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring MVC, SLF4J/MDC, Thymeleaf, Alpine.js, native Fetch API, Konva, JUnit 5, AssertJ, MockMvc, Playwright 1.54, Maven Wrapper.

## Global Constraints

- Runtime features work without internet access.
- No new frontend build chain, runtime CDN, JavaScript package manager, or Java dependency is introduced.
- DM-only content remains absent from player payloads.
- User-facing failures disclose a correlation identifier, HTTP status, and actionable wording, but never a local path, stack trace, PIN, source content, or raw exception message.
- The response header is exactly `X-Correlation-ID`; the problem-detail property is exactly `correlationId`; the request attribute and MDC key are both exactly `correlationId`.
- A caller-supplied correlation identifier is accepted only when it matches `^[A-Za-z0-9._-]{8,100}$`; otherwise the server generates a lowercase UUID.
- Every map, tracker, dice, and presentation mutation treats a non-2xx response as failure. A resolved `fetch()` promise is not itself success.
- Optimistic state is restored when persistence fails. Input that has not been accepted by the server remains available for retry.
- Retryable actions expose a keyboard-operable `Retry` button in an assertive error toast.
- Expected read fallbacks may remain quiet only when the fallback is intentional and visible, such as the bundled common-condition list. User-initiated mutations may not have empty catch blocks.
- Encounter difficulty remains advisory. Until authoritative rules data replaces it, the UI uses the exact phrase `Difficulty estimate` and shows the source and assumptions.
- Existing v1/v2 campaign schemas, import/export behavior, package keys, and round-trip semantics do not change.
- Tests use an isolated home directory through `-DargLine=-Duser.home=/tmp/dmhelper-p0-reliability`.

---

## Audit Basis and Scope Boundary

The repository audit on 2026-07-16 found the following master-spec delivery state:

| Master-spec area | Evidence in the current tree | Status before this plan |
|---|---|---|
| P0 quick notes | `quicknotes.js`, campaign-scoped APIs, template-expression contract, Playwright create/promote coverage | Implemented |
| P0 destinations | `ContentDestinationRegistry`, route-contract tests, globally capped palette results | Implemented |
| P0 browser failure collector | `BrowserFailureCollector` is attached to every smoke-test page | Implemented |
| P0 asset safety | Bounded v2 reader, signature checks, normalized paths, staged UUID-named installation, rollback tests | Implemented |
| P0 visible mutation failures | Empty catch blocks remain throughout `_tracker.html` and `maps/battle.html`; `battle-map.js` accepts non-2xx responses and does not restore failed optimistic moves/modes | Open; included here |
| P0 correlation identifiers | No request filter, MDC key, response header, or error-body property exists | Open; included here |
| P0 difficulty honesty | `CombatDifficultyCalculator` uses 2014 thresholds plus a `2×` heuristic, while the UI heading is `Difficulty` | Open; included here |
| Campaign contract v1 repair | Closed schemas, shared validation pipeline, checked fixtures, unresolved-reference blocking | Implemented |
| Package v2 foundation | ZIP/JSON readers, schema, stable keys, typed catalog, migration, preview, asset staging, atomic confirmation | Implemented |
| Complete current-state round-trip | Module section adapters, semantic snapshots/comparator, three v2 fixtures, import/export/import tests | Implemented for state the application currently persists |
| Unified session cockpit | `SessionController` still redirects to `maps.get(0)/play`; the battle page has a useful shell but no cockpit route/lifecycle/session plan/end-session draft | Not implemented |
| Structured adventures and later workstreams | Linear scene baseline exists; transitions, quests, full custom compendium, complete sheets, published-map tooling, world graph, logistics depth, and advanced player presentation remain future work | Partial or unsupported |

This plan deliberately does not begin the session cockpit. Master-spec §§5 and 22 make unfinished P0 work a release blocker and prohibit it from being displaced by P1/P3 work. After this plan passes, delivery item 5, **Session cockpit**, is the next implementation plan.

No database schema change is required. Correlation IDs are request-scoped, difficulty metadata is derived, and frontend retry state remains in the browser.

## File Structure

### Files created

- `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/CorrelationIdFilter.java` — validate/generate the request correlation ID, place it in MDC/request attributes, and return it in every response.
- `src/main/resources/static/js/dm-request.js` — reject non-2xx Fetch responses, parse safe problem details, and report failures with correlation-aware retry actions.
- `src/test/java/dev/hendrikhoemberg/dmhelper/common/web/CorrelationIdFilterTest.java` — generated/accepted/rejected ID and MDC cleanup contract.
- `src/test/java/dev/hendrikhoemberg/dmhelper/common/web/GlobalExceptionHandlerTest.java` — safe problem body, HTMX fragment, correlation property, and response-header contract.
- `src/test/java/dev/hendrikhoemberg/dmhelper/config/InteractionFailureContractTest.java` — static regression guard against raw mutation fetches and empty mutation catches in the time-critical surfaces.

### Files modified

- `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/GlobalExceptionHandler.java` — attach correlation IDs, log unexpected failures, and replace unsafe exception details with stable user messages.
- `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/SafeErrorAttributes.java` — expose the safe correlation ID on framework-generated errors while continuing to strip exception/trace details.
- `src/main/resources/application.properties` — include the MDC correlation value in request-time log lines.
- `src/main/resources/templates/fragments/head.html` — load `dm-request.js` before feature scripts.
- `src/main/resources/static/js/ui-elevation.js` — support an accessible action button in error toasts.
- `src/main/resources/static/css/components.css` — style the toast action without weakening existing focus-visible rules.
- `src/main/resources/static/js/map/battle-map.js` — use the shared request helper, rollback failed optimistic map changes, and expose retry.
- `src/main/resources/templates/maps/battle.html` — surface toolbar, encounter, map-switch, and presentation failures.
- `src/main/resources/templates/encounter/_tracker.html` — route every tracker mutation through one checked mutation helper.
- `src/main/resources/templates/fragments/navbar.html` — use checked requests for both dice entry points and retain failed expressions.
- `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/CombatDifficultyCalculator.java` — return estimate metadata and accurate assumption text.
- `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/CombatDifficultyCalculatorTest.java` — verify estimate/source/assumption metadata.
- `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterControllerTest.java` — verify rendered estimate disclosure.
- `src/main/resources/templates/encounter/detail.html` — label the result as an estimate and render source/assumptions.
- `src/test/java/dev/hendrikhoemberg/dmhelper/BrowserFailureCollector.java` — allow a test to declare one expected HTTP failure while retaining fail-on-unexpected behavior.
- `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java` — force map, tracker, presentation, and dice failures; assert rollback, correlation display, and retry.
- `docs/campaign-capabilities.md` — record P0 as supported and keep the cockpit honestly unsupported.

---

### Task 1: Establish the correlation and browser-error contract

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/CorrelationIdFilter.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/common/web/CorrelationIdFilterTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/common/web/GlobalExceptionHandlerTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/GlobalExceptionHandler.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/SafeErrorAttributes.java`
- Modify: `src/main/resources/application.properties`
- Create: `src/main/resources/static/js/dm-request.js`
- Modify: `src/main/resources/templates/fragments/head.html`
- Modify: `src/main/resources/static/js/ui-elevation.js`
- Modify: `src/main/resources/static/css/components.css`

**Interfaces:**
- Produces: `CorrelationIdFilter.HEADER`, `CorrelationIdFilter.ATTRIBUTE`, `CorrelationIdFilter.current(HttpServletRequest)`.
- Produces: `window.dmRequest(url, options): Promise<Response>`; rejects with `DmRequestError(status, correlationId, detail)` for network and non-2xx failures.
- Produces: `window.reportActionFailure(summary, error, retry?)` and action-aware `window.showToast(message, type, duration, action?)`.
- Consumed by: map, tracker, dice, and presentation tasks below.

- [ ] **Step 1: Write the failing filter tests**

Create `CorrelationIdFilterTest.java` with these four tests:

```java
package dev.hendrikhoemberg.dmhelper.common.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesAndReturnsAnIdWhenTheRequestHasNone() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/roll");
        var response = new MockHttpServletResponse();
        var inside = new AtomicReference<String>();
        FilterChain chain = (req, res) -> inside.set(MDC.get(CorrelationIdFilter.ATTRIBUTE));

        filter.doFilter(request, response, chain);

        assertThat(inside.get()).matches("[0-9a-f-]{36}");
        assertThat(request.getAttribute(CorrelationIdFilter.ATTRIBUTE)).isEqualTo(inside.get());
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(inside.get());
        assertThat(MDC.get(CorrelationIdFilter.ATTRIBUTE)).isNull();
    }

    @Test
    void preservesAValidCallerId() throws Exception {
        var request = new MockHttpServletRequest("GET", "/campaigns");
        request.addHeader(CorrelationIdFilter.HEADER, "browser-test-1234");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("browser-test-1234");
    }

    @Test
    void replacesAnInvalidCallerId() throws Exception {
        var request = new MockHttpServletRequest("GET", "/campaigns");
        request.addHeader(CorrelationIdFilter.HEADER, "../../secret\nvalue");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(CorrelationIdFilter.HEADER))
                .matches("[0-9a-f-]{36}")
                .doesNotContain("secret");
    }

    @Test
    void clearsMdcWhenTheChainThrows() {
        var request = new MockHttpServletRequest("GET", "/explode");
        var response = new MockHttpServletResponse();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> filter.doFilter(request, response,
                        (req, res) -> { throw new IllegalStateException("boom"); }));

        assertThat(MDC.get(CorrelationIdFilter.ATTRIBUTE)).isNull();
    }
}
```

- [ ] **Step 2: Run the filter test and verify it fails because the filter does not exist**

Run:

```bash
./mvnw -Dtest=CorrelationIdFilterTest test -DargLine=-Duser.home=/tmp/dmhelper-p0-reliability
```

Expected: compilation failure naming `CorrelationIdFilter`.

- [ ] **Step 3: Implement the request-scoped correlation filter**

Create `CorrelationIdFilter.java`:

```java
package dev.hendrikhoemberg.dmhelper.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-ID";
    public static final String ATTRIBUTE = "correlationId";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{8,100}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String correlationId = supplied != null && SAFE_ID.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, correlationId);
        response.setHeader(HEADER, correlationId);
        try (MDC.MDCCloseable ignored = MDC.putCloseable(ATTRIBUTE, correlationId)) {
            filterChain.doFilter(request, response);
        }
    }

    public static String current(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        return value == null ? "unavailable" : value.toString();
    }
}
```

Add this property to `application.properties` so request-time logs display the MDC value:

```properties
logging.pattern.level=%5p [corr:%X{correlationId:-}]
```

- [ ] **Step 4: Write the failing error-response tests**

Create `GlobalExceptionHandlerTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.common.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void unexpectedJsonFailureIsSafeAndCorrelated() throws Exception {
        mvc.perform(get("/explode").accept(MediaType.APPLICATION_JSON)
                        .header(CorrelationIdFilter.HEADER, "test-corr-1234"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(CorrelationIdFilter.HEADER, "test-corr-1234"))
                .andExpect(jsonPath("$.title").value("Request Failed"))
                .andExpect(jsonPath("$.detail").value("The request could not be completed."))
                .andExpect(jsonPath("$.correlationId").value("test-corr-1234"))
                .andExpect(content().string(not(containsString("database-password"))));
    }

    @Test
    void htmxFailureRendersSafeReference() throws Exception {
        mvc.perform(get("/explode").header("HX-Request", "true")
                        .header(CorrelationIdFilter.HEADER, "test-corr-5678"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("common/_error"))
                .andExpect(model().attribute("message", "The request could not be completed."))
                .andExpect(model().attribute("correlationId", "test-corr-5678"));
    }

    @RestController
    static class FailingController {
        @GetMapping("/explode")
        String explode() {
            throw new IllegalStateException("database-password=/private/path");
        }
    }
}
```

- [ ] **Step 5: Run the error-response test and verify the missing handler contract**

Run:

```bash
./mvnw -Dtest=GlobalExceptionHandlerTest test -DargLine=-Duser.home=/tmp/dmhelper-p0-reliability
```

Expected: FAIL because the current advice has no generic handler or `correlationId` property.

- [ ] **Step 6: Add safe correlated error rendering**

In `GlobalExceptionHandler`, add an SLF4J logger, use a helper for all `ProblemDetail` instances, add `correlationId` to HTMX models, replace the conflict exception message with stable copy, and add the generic handler:

```java
private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

private ProblemDetail problem(HttpStatus status, String type, String title, String detail,
                              HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(type));
    problem.setTitle(title);
    problem.setProperty(CorrelationIdFilter.ATTRIBUTE, CorrelationIdFilter.current(request));
    return problem;
}

private ModelAndView htmxError(HttpStatus status, String message, HttpServletRequest request) {
    ModelAndView mav = new ModelAndView("common/_error");
    mav.addObject("message", message);
    mav.addObject(CorrelationIdFilter.ATTRIBUTE, CorrelationIdFilter.current(request));
    mav.setStatus(status);
    return mav;
}

@ExceptionHandler(Exception.class)
public Object handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Unhandled request failure", ex);
    String message = "The request could not be completed.";
    if ("true".equals(request.getHeader("HX-Request"))) {
        return htmxError(HttpStatus.INTERNAL_SERVER_ERROR, message, request);
    }
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "urn:dmhelper:request-failed",
            "Request Failed",
            message,
            request));
}
```

Refactor the existing 400, 404, and 409 handlers to call `problem(...)` or `htmxError(...)`. Use these exact safe details:

```java
// 400
"The request was not valid. Check the entered values and try again."
// 404
"The requested item could not be found. Reload and try again."
// 409
"The item changed before this request completed. Reload and try again."
```

In `SafeErrorAttributes.getErrorAttributes`, add:

```java
Object correlationId = webRequest.getAttribute(
        CorrelationIdFilter.ATTRIBUTE, WebRequest.SCOPE_REQUEST);
if (correlationId != null) {
    attributes.put(CorrelationIdFilter.ATTRIBUTE, correlationId);
}
attributes.remove("message");
```

In `common/_error.html`, render the reference without exposing exception text:

```html
<div class="alert alert-error" th:fragment="error" role="alert">
    <span th:text="${message}">The request could not be completed.</span>
    <span class="u-text-sm" th:if="${correlationId != null}"
          th:text="' Reference: ' + ${correlationId}">Reference</span>
</div>
```

- [ ] **Step 7: Write the shared checked-request helper**

Create `dm-request.js`:

```javascript
(function () {
  'use strict';

  class DmRequestError extends Error {
    constructor(message, status = 0, correlationId = null) {
      super(message);
      this.name = 'DmRequestError';
      this.status = status;
      this.correlationId = correlationId;
    }
  }

  async function responseError(response) {
    let detail = response.status === 409
      ? 'The item changed first. Reload and try again.'
      : `The server refused that request (${response.status}).`;
    let correlationId = response.headers.get('X-Correlation-ID');
    const contentType = response.headers.get('Content-Type') || '';
    if (contentType.includes('json')) {
      try {
        const body = await response.json();
        detail = body.detail || detail;
        correlationId = body.correlationId || correlationId;
      } catch (_) {
        // A malformed error body must not hide the status/header fallback.
      }
    }
    return new DmRequestError(detail, response.status, correlationId);
  }

  window.dmRequest = async function dmRequest(url, options = {}) {
    let response;
    try {
      response = await fetch(url, options);
    } catch (_) {
      throw new DmRequestError('No answer from the server. Check it is still running.');
    }
    if (!response.ok) throw await responseError(response);
    return response;
  };

  window.reportActionFailure = function reportActionFailure(summary, error, retry) {
    const reference = error?.correlationId ? ` Reference: ${error.correlationId}.` : '';
    const detail = error?.message ? ` ${error.message}` : '';
    window.showToast(
      summary + detail + reference,
      'error',
      retry ? 15000 : 7000,
      retry ? { label: 'Retry', handler: retry } : null
    );
  };

  window.DmRequestError = DmRequestError;
})();
```

Load it in `fragments/head.html` immediately after HTMX and before all feature scripts:

```html
<script th:src="@{/vendor/htmx.min.js}"></script>
<script th:src="@{/js/dm-request.js}"></script>
```

- [ ] **Step 8: Make toast retries accessible**

Replace `window.showToast` in `ui-elevation.js` with:

```javascript
window.showToast = function(message, type = 'info', duration = 3000, action = null) {
  const toast = document.createElement('div');
  toast.className = 'toast toast-' + type;
  if (type === 'error') {
    toast.setAttribute('role', 'alert');
    toast.setAttribute('aria-live', 'assertive');
  }
  const text = document.createElement('span');
  text.textContent = message;
  toast.appendChild(text);
  if (action) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'toast-action';
    button.textContent = action.label;
    button.addEventListener('click', () => {
      toast.remove();
      action.handler();
    });
    toast.appendChild(button);
  }
  toastContainer().appendChild(toast);
  requestAnimationFrame(() => toast.classList.add('show'));
  setTimeout(() => {
    if (!toast.isConnected) return;
    toast.classList.remove('show');
    toast.addEventListener('transitionend', () => toast.remove(), { once: true });
  }, duration);
};
```

Add to `components.css`:

```css
.toast-action {
    margin-left: var(--space-sm);
    border: 1px solid currentColor;
    border-radius: var(--radius);
    background: transparent;
    color: inherit;
    padding: var(--space-xs) var(--space-sm);
    font: inherit;
    font-weight: 700;
    cursor: pointer;
}
```

- [ ] **Step 9: Run the focused backend tests**

Run:

```bash
./mvnw -Dtest=CorrelationIdFilterTest,GlobalExceptionHandlerTest test -DargLine=-Duser.home=/tmp/dmhelper-p0-reliability
```

Expected: PASS, 6 tests.

- [ ] **Step 10: Commit the shared failure contract**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/common/web/CorrelationIdFilter.java src/main/java/dev/hendrikhoemberg/dmhelper/common/web/GlobalExceptionHandler.java src/main/java/dev/hendrikhoemberg/dmhelper/common/web/SafeErrorAttributes.java src/main/resources/application.properties src/main/resources/static/js/dm-request.js src/main/resources/templates/fragments/head.html src/main/resources/templates/common/_error.html src/main/resources/static/js/ui-elevation.js src/main/resources/static/css/components.css src/test/java/dev/hendrikhoemberg/dmhelper/common/web/CorrelationIdFilterTest.java src/test/java/dev/hendrikhoemberg/dmhelper/common/web/GlobalExceptionHandlerTest.java
git commit -m "feat: add correlated action failure contract"
```

---

### Task 2: Make battle-map mutations rollback and retry safely

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/InteractionFailureContractTest.java`
- Modify: `src/main/resources/static/js/map/battle-map.js`
- Modify: `src/main/resources/templates/maps/battle.html`

**Interfaces:**
- Consumes: `window.dmRequest` and `window.reportActionFailure` from Task 1.
- Produces: every user-initiated `BattleMap` mutation rejects or handles failure without committing local success state.
- Produces: `BattleMap.saveTokenMove(tokenId, x, y)` restores both token data and Konva position after failure.

- [ ] **Step 1: Add a failing static interaction contract**

Create `InteractionFailureContractTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InteractionFailureContractTest {

    private static String read(String path) throws IOException {
        return Files.readString(Path.of("src/main/resources", path));
    }

    @Test
    void timeCriticalMutationSurfacesHaveNoEmptyCatchBlocks() throws IOException {
        assertThat(read("templates/encounter/_tracker.html")).doesNotContain("catch (e) {}");
        assertThat(read("templates/maps/battle.html"))
                .doesNotContain("catch (e) {}", ".catch(() => {})");
        assertThat(read("static/js/map/battle-map.js"))
                .doesNotContain("catch (e) {}", "catch (e) { /* non-critical */ }");
    }

    @Test
    void mutationSurfacesUseCheckedRequests() throws IOException {
        assertThat(read("templates/encounter/_tracker.html"))
                .contains("window.dmRequest", "window.reportActionFailure");
        assertThat(read("templates/maps/battle.html"))
                .contains("window.dmRequest", "window.reportActionFailure");
        assertThat(read("static/js/map/battle-map.js"))
                .contains("window.dmRequest", "window.reportActionFailure");
    }
}
```

- [ ] **Step 2: Run the contract and verify it reports the existing silent paths**

Run:

```bash
./mvnw -Dtest=InteractionFailureContractTest test -DargLine=-Duser.home=/tmp/dmhelper-p0-reliability
```

Expected: FAIL naming empty catches in the tracker, battle template, and map module.

- [ ] **Step 3: Add checked request/report helpers to `BattleMap`**

Add these methods after the constructor:

```javascript
async _request(url, options = {}) {
    return window.dmRequest(url, options);
}

_failure(summary, error, retry) {
    this._setError();
    window.reportActionFailure(summary, error, retry);
}
```

Change all reads (`fetchMapDocument`, `fetchTokens`, statblock lookup, pins, and map switching) to call `this._request`. Read failures may show a non-retry toast and retain the last rendered state:

```javascript
async fetchTokens() {
    try {
        const resp = await this._request(`/api/v1/maps/${this.mapId}/tokens`);
        this.tokens = await resp.json();
    } catch (error) {
        this._failure('Could not load map tokens.', error, () => this.fetchTokens());
        return false;
    }
}
```

- [ ] **Step 4: Replace token CRUD with checked, state-safe implementations**

Use `this._request` in `createToken`, `deleteToken`, `duplicateToken`, `markDead`, `updateToken`, and `addPartyToMap`. Only update `this.tokens` and Konva nodes after the checked request returns. Wrap each public method with this exact pattern:

```javascript
async deleteToken(id) {
    try {
        await this._request(`/api/v1/tokens/${id}`, { method: 'DELETE' });
        const node = this.tokenNodes[id];
        if (node) { node.group.destroy(); delete this.tokenNodes[id]; }
        this.tokens = this.tokens.filter(t => t.id !== id);
        this.tokenLayer.batchDraw();
        if (this.selectedTokenId === id) this.deselectToken();
        this.emit('tokenupdate', { tokens: this.tokens });
        this.emit('state-changed');
    } catch (error) {
        this._failure('Could not delete the token. Nothing was changed.', error,
            () => this.deleteToken(id));
        return false;
    }
}
```

Use these exact summaries for the neighboring methods:

```text
createToken: Could not add the token. Nothing was changed.
duplicateToken: Could not duplicate the token. Nothing was changed.
markDead: Could not change the defeated state. The previous state was restored.
updateToken: Could not save the token. Your edits are still visible for retry.
addPartyToMap: Could not add the party. Nothing was changed.
createTokenFromStatblock: Could not load that statblock. Nothing was changed.
```

- [ ] **Step 5: Restore a failed optimistic token move**

Replace `saveTokenMove` with:

```javascript
async saveTokenMove(tokenId, x, y) {
    const token = this.tokens.find(t => t.id === tokenId);
    if (!token) return;
    const previous = { x: token.positionX, y: token.positionY };
    token.positionX = x;
    token.positionY = y;
    this.emit('tokenupdate', { tokens: this.tokens });
    this.emit('state-changed');
    try {
        await this._request(`/api/v1/tokens/${tokenId}/move`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ positionX: x, positionY: y }),
        });
        this._setSaved();
    } catch (error) {
        token.positionX = previous.x;
        token.positionY = previous.y;
        const node = this.tokenNodes[tokenId]?.group;
        if (node) node.position(previous);
        this.tokenLayer.batchDraw();
        this.emit('tokenupdate', { tokens: this.tokens });
        this.emit('state-changed');
        this._failure('Could not move the token. Its previous position was restored.', error,
            () => this.saveTokenMove(tokenId, x, y));
    }
}
```

- [ ] **Step 6: Restore failed map-mode changes**

Replace `setMovementMode`, `setShowGrid`, and `_patchMode` with:

```javascript
async setMovementMode(mode) {
    const previous = this.movementMode;
    this.movementMode = mode;
    this.emit('modestate', { movementMode: mode, showGrid: this.showGrid });
    try {
        await this._patchMode(mode, null);
    } catch (error) {
        this.movementMode = previous;
        this.emit('modestate', { movementMode: previous, showGrid: this.showGrid });
        this._failure('Could not change movement mode. The previous mode was restored.', error,
            () => this.setMovementMode(mode));
    }
}

async setShowGrid(show) {
    const previous = this.showGrid;
    this.showGrid = show;
    this.renderGrid();
    this.emit('modestate', { movementMode: this.movementMode, showGrid: show });
    try {
        await this._patchMode(null, show);
    } catch (error) {
        this.showGrid = previous;
        this.renderGrid();
        this.emit('modestate', { movementMode: this.movementMode, showGrid: previous });
        this._failure('Could not change grid visibility. The previous setting was restored.', error,
            () => this.setShowGrid(show));
    }
}

async _patchMode(movementMode, showGrid) {
    await this._request(`/api/v1/maps/${this.mapId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ movementMode, showGrid }),
    });
}
```

- [ ] **Step 7: Make AoE/table refresh failures visible without discarding the template**

In `syncAoEs`, retain `this.aoeNodes`, use `this._request`, and report:

```javascript
} catch (error) {
    this._failure('Could not update the player AoE overlay. The DM map was kept.', error,
        () => this.syncAoEs());
}
```

In `battle.html`, replace the raw table refresh with:

```javascript
window.dmRequest('/api/v1/table/refresh', { method: 'POST' })
    .catch(error => window.reportActionFailure(
        'The player display could not refresh.', error,
        () => window.dmRequest('/api/v1/table/refresh', { method: 'POST' })));
```

- [ ] **Step 8: Make the battle toolbar check every response**

Add inside `battleToolbar()`:

```javascript
async request(url, options = {}) {
    return window.dmRequest(url, options);
},
failure(summary, error, retry) {
    window.reportActionFailure(summary, error, retry);
},
```

Replace raw fetch calls in `newEncounter`, `activatePlannedEncounter`, `loadPlannedEncounters`, `loadActiveEncounter`, `sendToTable`, `curtain`, `loadMaps`, and `searchStatblocks` with `this.request`. Each catch calls `this.failure` with a bound retry callback. For presentation, update state only after success:

```javascript
async sendToTable() {
    try {
        await this.request('/api/v1/table/presentation', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ mode: 'MAP', ref: this.currentMapId }),
        });
        this.presentingMap = true;
    } catch (error) {
        this.presentingMap = false;
        this.failure('Could not show this map to the table.', error, () => this.sendToTable());
    }
},

async curtain() {
    const wasPresenting = this.presentingMap;
    try {
        await this.request('/api/v1/table/presentation', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ mode: 'CURTAIN', ref: '' }),
        });
        this.presentingMap = false;
    } catch (error) {
        this.presentingMap = wasPresenting;
        this.failure('Could not lower the curtain. The current presentation remains active.',
            error, () => this.curtain());
    }
},
```

Use these exact summaries elsewhere:

```text
newEncounter: Could not create the encounter.
activatePlannedEncounter: Could not activate the encounter.
loadPlannedEncounters: Could not load planned encounters.
loadActiveEncounter: Could not load the active encounter.
loadMaps: Could not load the map switcher.
searchStatblocks: Could not search statblocks.
```

- [ ] **Step 9: Re-run the static contract**

Run:

```bash
./mvnw -Dtest=InteractionFailureContractTest test -DargLine=-Duser.home=/tmp/dmhelper-p0-reliability
```

Expected: tracker assertion still FAILS; battle-map and battle-template assertions PASS. The tracker is addressed next.

- [ ] **Step 10: Commit battle-map integrity**

```bash
git add src/main/resources/static/js/map/battle-map.js src/main/resources/templates/maps/battle.html src/test/java/dev/hendrikhoemberg/dmhelper/config/InteractionFailureContractTest.java
git commit -m "fix: surface and recover battle map failures"
```

---

### Task 3: Make encounter, presentation, and dice actions checked and retryable

**Files:**
- Modify: `src/main/resources/templates/encounter/_tracker.html`
- Modify: `src/main/resources/templates/fragments/navbar.html`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/InteractionFailureContractTest.java`

**Interfaces:**
- Consumes: `window.dmRequest` and `window.reportActionFailure`.
- Produces: `combatTracker.request`, `combatTracker.mutate`, and `combatTracker.failure` as the only tracker paths for remote mutations.
- Preserves: current encounter/combatant state and dice expression until the server confirms a mutation.

- [ ] **Step 1: Add one checked tracker mutation helper**

Add these methods to the Alpine object in `_tracker.html`:

```javascript
async request(url, options = {}) {
    return window.dmRequest(url, options);
},

failure(summary, error, retry) {
    window.reportActionFailure(summary, error, retry);
},

async mutate(summary, url, options, afterSuccess) {
    try {
        const response = await this.request(url, options);
        if (afterSuccess) await afterSuccess(response);
        return true;
    } catch (error) {
        this.failure(summary, error,
            () => this.mutate(summary, url, options, afterSuccess));
        return false;
    }
},
```

- [ ] **Step 2: Convert tracker reads to checked requests with intentional fallback semantics**

Use `this.request` in `loadActiveEncounter`, `loadEncounter`, and `reloadCombatants`. A `404` from the active-encounter endpoint means no encounter and is handled without a toast; all other failures call `failure` with retry. Keep the common-condition catalog as the only quiet fallback, but distinguish network fallback from a mutation failure:

```javascript
loadConditionsCatalog() {
    this.request('/api/v1/library/conditions')
        .then(response => response.json())
        .then(data => {
            this.conditionsCatalog = data.map(c => ({
                sourceKey: c.sourceKey,
                name: c.name,
                description: c.description || '',
                defaultDuration: 1,
            }));
        })
        .catch(() => { this.conditionsCatalog = COMMON_CONDITIONS; });
},
```

- [ ] **Step 3: Route turn and initiative actions through `mutate`**

Replace the bodies of `nextTurn`, `previousTurn`, `setInitiative`, and `autoRoll` with calls shaped like:

```javascript
async nextTurn() {
    if (!this._encounterId) return;
    const roundBefore = this.encounter?.round;
    await this.mutate(
        'Could not advance the turn. Initiative was not changed.',
        `/api/v1/encounters/${this._encounterId}/next-turn`,
        { method: 'POST' },
        async response => {
            this.encounter = await response.json();
            await this.reloadCombatants();
            this.rechargePrompts = this.encounter.rechargePrompts || [];
            if (this.encounter.round !== roundBefore) this._pulseRound();
            this.dispatchTurnEvent();
            this.dispatchState();
        });
},
```

Use the exact summaries:

```text
previousTurn: Could not move to the previous turn. Initiative was not changed.
setInitiative: Could not save initiative. The previous order was kept.
autoRoll: Could not roll initiative. The previous order was kept.
```

- [ ] **Step 4: Route HP and status actions through `mutate` without optimistic success**

Convert `quickHp`, `markDefeated`, `toggleCondition`, `removeCondition`, `setHp`, `setConcentration`, and `resolveConcentrationCheck`. Do not clear `hpDelta`, `editHp`, `editTempHp`, `conditionDuration`, or spell input until `afterSuccess` has reloaded combatants. `applyHpDelta` becomes:

```javascript
async applyHpDelta(combatantId) {
    const amount = parseInt(this.hpDelta, 10);
    if (isNaN(amount) || amount === 0) return;
    const succeeded = await this.quickHp(combatantId, amount);
    if (succeeded) this.hpDelta = '';
},
```

`quickHp` returns the `mutate` result:

```javascript
async quickHp(combatantId, amount) {
    if (!this._encounterId) return false;
    return this.mutate(
        'Could not change hit points. The previous value was kept.',
        `/api/v1/combatants/${combatantId}/damage`,
        {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ amount }),
        },
        async () => {
            await this.reloadCombatants();
            this._flashHp(combatantId, amount);
            this.dispatchState();
        });
},
```

- [ ] **Step 5: Route removal, special actions, undo, and encounter end through `mutate`**

Convert `removeCombatant`, `activateLairAction`, `undo`, `endEncounter`, `useLegendaryAction`, `useLegendaryResistance`, and `resolveRecharge`. Only clear encounter, selection, or recharge-prompt state in `afterSuccess`. Use these exact summaries:

```text
removeCombatant: Could not remove the combatant. The tracker was kept unchanged.
activateLairAction: Could not record the lair action.
undo: Could not undo the last action. The tracker was kept unchanged.
endEncounter: Could not end the encounter. It remains active.
useLegendaryAction: Could not record the legendary action.
useLegendaryResistance: Could not record the legendary resistance.
resolveRecharge: Could not record the recharge check.
```

- [ ] **Step 6: Make both dice entry points preserve input and show correlated retry**

In the click-to-roll handler in `navbar.html`, replace `fetch` with `window.dmRequest`. In its catch, retain the tooltip, call `window.reportActionFailure`, and retry the same `expr`:

```javascript
} catch (error) {
    tooltip.textContent = 'Roll failed';
    window.reportActionFailure('Could not roll the dice.', error,
        () => btn.click());
}
```

In `diceRoller.roll`, replace `fetch` and the manual `resp.ok` branch with:

```javascript
const resp = await window.dmRequest('/api/v1/roll', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
});
```

Keep `this.expression`, advantage, and disadvantage unchanged in the catch, then report:

```javascript
} catch (error) {
    window.reportActionFailure('The dice roll was not saved.', error, () => this.roll());
} finally {
    this.loading = false;
}
```

Change `loadHistory` to use `window.dmRequest`; on failure retain the previous `history` array and show `Could not refresh dice history.` with a retry callback.

- [ ] **Step 7: Extend and pass the static contract**

Add to `InteractionFailureContractTest.mutationSurfacesUseCheckedRequests`:

```java
assertThat(read("templates/fragments/navbar.html"))
        .contains("window.dmRequest", "window.reportActionFailure");
```

Run:

```bash
./mvnw -Dtest=InteractionFailureContractTest test -DargLine=-Duser.home=/tmp/dmhelper-p0-reliability
```

Expected: PASS, 2 tests.

- [ ] **Step 8: Commit tracker and dice integrity**

```bash
git add src/main/resources/templates/encounter/_tracker.html src/main/resources/templates/fragments/navbar.html src/test/java/dev/hendrikhoemberg/dmhelper/config/InteractionFailureContractTest.java
git commit -m "fix: make session mutations checked and retryable"
```

---

### Task 4: Label encounter difficulty as an estimate with provenance

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/CombatDifficultyCalculator.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/CombatDifficultyCalculatorTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterControllerTest.java`
- Modify: `src/main/resources/templates/encounter/detail.html`

**Interfaces:**
- Produces: `DifficultyResult(String rating, int adjustedXp, int partyThreshold, String details, boolean estimate, String source, List<String> assumptions)`.
- Preserves: existing `rating`, `adjustedXp`, `partyThreshold`, and `details` accessors for current callers.

- [ ] **Step 1: Write failing calculator metadata assertions**

In `CombatDifficultyCalculatorTest`, add:

```java
@Test
void labelsTheCurrentCalculationAsAnEstimateWithSourceAndAssumptions() {
    PartyMember hero = pc("Fighter 5");
    CombatantDto monster = new CombatantDto(
            UUID.randomUUID(), UUID.randomUUID(), "Ogre", 0, 0,
            0, 0, 0, "MONSTER", null, false,
            null, UUID.randomUUID(), null,
            false, false, false, List.of(),
            null, false, 0, 0, 0, 0, null);
    when(statBlockRepo.findById(monster.statBlockId()))
            .thenReturn(Optional.of(statBlock("Ogre", "2", 450)));

    var result = calculator.calculate(List.of(hero), List.of(monster));

    assertThat(result.estimate()).isTrue();
    assertThat(result.source()).isEqualTo("2014 DMG encounter XP thresholds");
    assertThat(result.assumptions()).containsExactly(
            "2014 Medium thresholds stand in for 2024 Moderate thresholds.",
            "High begins at twice the proxy Moderate threshold.",
            "Monster XP uses stored XP, then the CR table, then a 200 XP fallback.");
}
```

- [ ] **Step 2: Run the calculator test and verify the new accessors are absent**

Run:

```bash
./mvnw -Dtest=CombatDifficultyCalculatorTest test -DargLine=-Duser.home=/tmp/dmhelper-p0-reliability
```

Expected: compilation failure for `estimate`, `source`, and `assumptions`.

- [ ] **Step 3: Add immutable estimate metadata**

Change the record and add constants:

```java
public record DifficultyResult(
        String rating,
        int adjustedXp,
        int partyThreshold,
        String details,
        boolean estimate,
        String source,
        List<String> assumptions) {}

private static final String ESTIMATE_SOURCE = "2014 DMG encounter XP thresholds";
private static final List<String> ESTIMATE_ASSUMPTIONS = List.of(
        "2014 Medium thresholds stand in for 2024 Moderate thresholds.",
        "High begins at twice the proxy Moderate threshold.",
        "Monster XP uses stored XP, then the CR table, then a 200 XP fallback.");
```

Return the metadata from both branches:

```java
return new DifficultyResult("N/A", 0, 0, "No party or monsters",
        true, ESTIMATE_SOURCE, ESTIMATE_ASSUMPTIONS);
```

and:

```java
return new DifficultyResult(rating, totalXp, partyThreshold,
        "Proxy threshold: " + partyThreshold + " XP, Monster total: " + totalXp + " XP",
        true, ESTIMATE_SOURCE, ESTIMATE_ASSUMPTIONS);
```

Rename `MODERATE_XP` to `PROXY_MODERATE_XP` and update the comment to state that the array is the 2014 Medium table. Keep the numerical behavior unchanged in this checkpoint.

- [ ] **Step 4: Write the failing rendered-disclosure assertion**

Update the `DifficultyResult` construction in `EncounterControllerTest` to:

```java
new DifficultyResult(
        "LOW", 0, 3000, "Test", true,
        "2014 DMG encounter XP thresholds",
        List.of(
                "2014 Medium thresholds stand in for 2024 Moderate thresholds.",
                "High begins at twice the proxy Moderate threshold.",
                "Monster XP uses stored XP, then the CR table, then a 200 XP fallback."))
```

Then add to `shouldRenderEncounterDetail`:

```java
.andExpect(content().string(containsString("Difficulty estimate")))
.andExpect(content().string(containsString("2014 DMG encounter XP thresholds")))
.andExpect(content().string(containsString("High begins at twice the proxy Moderate threshold.")));
```

- [ ] **Step 5: Render the estimate label, source, and assumptions**

Replace the difficulty block in `encounter/detail.html` with:

```html
<h2>Difficulty estimate</h2>
<div class="detail-actions u-mb-lg u-items-center">
    <th:block th:if="${difficulty.rating == 'N/A'}">
        <p class="text-muted">No party or monsters — add combatants to see an estimate.</p>
    </th:block>
    <th:block th:unless="${difficulty.rating == 'N/A'}">
        <span class="badge badge-rating"
              th:classappend="'badge-' + ${#strings.toLowerCase(difficulty.rating)}"
              th:text="'Estimate: ' + ${difficulty.rating}">Estimate: LOW</span>
        <span class="text-muted" th:text="'Monster XP: ' + ${difficulty.adjustedXp}">XP</span>
        <span class="text-muted" th:text="'Proxy threshold: ' + ${difficulty.partyThreshold}">Threshold</span>
        <span class="text-muted u-text-sm" th:text="${difficulty.details}">Details</span>
    </th:block>
</div>
<details class="detail-description u-mb-lg">
    <summary>Estimate source and assumptions</summary>
    <p th:text="${difficulty.source}">2014 DMG encounter XP thresholds</p>
    <ul>
        <li th:each="assumption : ${difficulty.assumptions}"
            th:text="${assumption}">Assumption</li>
    </ul>
</details>
```

- [ ] **Step 6: Run calculator and controller tests**

Run:

```bash
./mvnw -Dtest=CombatDifficultyCalculatorTest,EncounterControllerTest test -DargLine=-Duser.home=/tmp/dmhelper-p0-reliability
```

Expected: PASS.

- [ ] **Step 7: Commit honest difficulty labeling**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/CombatDifficultyCalculator.java src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/CombatDifficultyCalculatorTest.java src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterControllerTest.java src/main/resources/templates/encounter/detail.html
git commit -m "fix: label encounter difficulty as an estimate"
```

---

### Task 5: Prove rollback, correlation display, and retry in the browser

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/BrowserFailureCollector.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`
- Modify: `docs/campaign-capabilities.md`

**Interfaces:**
- Produces: `BrowserFailureCollector.expectHttpFailure(String method, Pattern url, int status)` for one-shot, explicitly expected failures.
- Preserves: every unexpected console error, page error, request failure, or HTTP 4xx/5xx still fails the smoke test.

- [ ] **Step 1: Add one-shot expected-failure support to the collector**

Add this record, list, and method to `BrowserFailureCollector`:

```java
private record ExpectedHttpFailure(String method, Pattern url, int status) {
    boolean matches(Response response) {
        return method.equals(response.request().method())
                && status == response.status()
                && url.matcher(response.url()).matches();
    }
}

private final List<ExpectedHttpFailure> expectedHttpFailures = new CopyOnWriteArrayList<>();

void expectHttpFailure(String method, Pattern url, int status) {
    expectedHttpFailures.add(new ExpectedHttpFailure(method, url, status));
}
```

Replace the response listener with:

```java
page.onResponse(response -> {
    if (response.status() < 400) return;
    var expected = expectedHttpFailures.stream()
            .filter(candidate -> candidate.matches(response))
            .findFirst();
    if (expected.isPresent()) {
        expectedHttpFailures.remove(expected.get());
        return;
    }
    failures.add("HTTP " + response.status() + ": "
            + response.request().method() + " " + response.url());
});
```

Extend `assertNoFailures` and `clear`:

```java
assertThat(expectedHttpFailures).as("declared HTTP failures that never occurred").isEmpty();
assertThat(failures).as("unexpected browser failures").isEmpty();
```

```java
expectedHttpFailures.clear();
failures.clear();
```

- [ ] **Step 2: Add a shared route helper for fail-once/retry-success tests**

In `CoreSessionLoopSmokeTest`, add:

```java
private void failOnce(Page page, String glob, String method, Pattern url, String correlationId) {
    browserFailures.expectHttpFailure(method, url, 503);
    AtomicBoolean failed = new AtomicBoolean();
    page.route(glob, route -> {
        if (failed.compareAndSet(false, true)) {
            route.fulfill(new Route.FulfillOptions()
                    .setStatus(503)
                    .setContentType("application/problem+json")
                    .setHeaders(Map.of("X-Correlation-ID", correlationId))
                    .setBody("{\"title\":\"Unavailable\",\"detail\":\"Try the action again.\","
                            + "\"correlationId\":\"" + correlationId + "\"}"));
        } else {
            route.resume();
        }
    });
}
```

Add imports for `Map`, `Pattern`, and `AtomicBoolean`.

- [ ] **Step 3: Add the failed-token-move rollback/retry browser test**

Append after the map/encounter setup tests:

```java
@Test
@Order(13)
void failedTokenMoveRollsBackAndRetryPersists() {
    Token before = tokenRepo.findByMapIdOrderByNameAsc(mapId).getFirst();
    int oldX = before.getPositionX();
    int oldY = before.getPositionY();
    String corr = "move-failure-1234";
    failOnce(dmPage, "**/api/v1/tokens/*/move", "PATCH",
            Pattern.compile(".*/api/v1/tokens/.+/move"), corr);

    dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId
            + "/maps/" + mapId + "/play");
    dmPage.waitForFunction("window.battleMap && window.battleMap.tokens.length > 0");
    dmPage.evaluate("([id]) => window.battleMap.saveTokenMove(id, 333, 222)",
            List.of(before.getId().toString()));

    dmPage.locator(".toast-error", new Page.LocatorOptions().setHasText(corr)).waitFor();
    assertThat(tokenRepo.findById(before.getId()).orElseThrow().getPositionX()).isEqualTo(oldX);
    assertThat(tokenRepo.findById(before.getId()).orElseThrow().getPositionY()).isEqualTo(oldY);
    assertThat(((Number) dmPage.evaluate("([id]) => window.battleMap.tokens.find(t => t.id === id).positionX",
            List.of(before.getId().toString()))).intValue()).isEqualTo(oldX);

    dmPage.locator(".toast-error .toast-action").click();
    dmPage.waitForFunction("([id]) => window.battleMap.tokens.find(t => t.id === id).positionX === 333",
            before.getId().toString());
    assertThat(tokenRepo.findById(before.getId()).orElseThrow().getPositionX()).isEqualTo(333);
}
```

- [ ] **Step 4: Add tracker and presentation failure tests**

Add these two tests:

```java
@Test
@Order(14)
void failedNextTurnKeepsTrackerStateAndRetryAdvances() {
    String corr = "turn-failure-1234";
    failOnce(dmPage, "**/api/v1/encounters/*/next-turn", "POST",
            Pattern.compile(".*/api/v1/encounters/.+/next-turn"), corr);
    dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId
            + "/maps/" + mapId + "/play");
    dmPage.waitForFunction("document.querySelector('.tracker-panel')?._x_dataStack?.[0]?.encounter");
    var before = encounterService.getById(encounterId);

    dmPage.evaluate("document.querySelector('.tracker-panel')._x_dataStack[0].nextTurn()");
    dmPage.locator(".toast-error", new Page.LocatorOptions().setHasText(corr)).waitFor();

    var unchanged = encounterService.getById(encounterId);
    assertThat(unchanged.round()).isEqualTo(before.round());
    assertThat(unchanged.activeTurnIndex()).isEqualTo(before.activeTurnIndex());

    dmPage.locator(".toast-error .toast-action").click();
    dmPage.waitForFunction("([round, turn]) => {"
                    + " const e = document.querySelector('.tracker-panel')._x_dataStack[0].encounter;"
                    + " return e.round !== round || e.activeTurnIndex !== turn; }",
            Arrays.asList(before.round(), before.activeTurnIndex()));
    var advanced = encounterService.getById(encounterId);
    assertThat(advanced.round() != before.round()
            || advanced.activeTurnIndex() != before.activeTurnIndex()).isTrue();
}

@Test
@Order(15)
void failedPresentationKeepsCurtainAndRetryShowsMap() {
    presentationService.curtain();
    String corr = "present-failure-1234";
    failOnce(dmPage, "**/api/v1/table/presentation", "PUT",
            Pattern.compile(".*/api/v1/table/presentation"), corr);
    dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId
            + "/maps/" + mapId + "/play");
    dmPage.waitForFunction("window.battleMap && window.battleMap.tokens.length > 0");

    dmPage.locator("button[title='Send current map to player view']").click();
    dmPage.locator(".toast-error", new Page.LocatorOptions().setHasText(corr)).waitFor();
    assertThat(presentationService.getCurrentState().mode()).isEqualTo("CURTAIN");

    dmPage.locator(".toast-error .toast-action").click();
    dmPage.waitForFunction("document.querySelector('.battle-container')._x_dataStack[0].presentingMap");
    assertThat(presentationService.getCurrentState().mode()).isEqualTo("MAP");
}
```

- [ ] **Step 5: Add the dice-input retention test**

Add this test:

```java
@Test
@Order(16)
void failedDiceRollKeepsExpressionAndRetryCompletes() {
    String corr = "dice-failure-1234";
    failOnce(dmPage, "**/api/v1/roll", "POST",
            Pattern.compile(".*/api/v1/roll"), corr);
    dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId);
    dmPage.locator("#diceToggle").click();
    Locator expression = dmPage.locator(".dice-panel input[type='text']");
    expression.fill("2d6+4");
    dmPage.locator(".dice-panel .dice-input-row button").click();

    dmPage.locator(".toast-error", new Page.LocatorOptions().setHasText(corr)).waitFor();
    assertThat(expression.inputValue()).isEqualTo("2d6+4");

    dmPage.locator(".toast-error .toast-action").click();
    dmPage.locator(".dice-result-total").waitFor();
    assertThat(expression.inputValue()).isEmpty();
}
```

- [ ] **Step 6: Run the browser and static reliability tests**

Run:

```bash
./mvnw -Dtest=InteractionFailureContractTest,CoreSessionLoopSmokeTest test -DargLine=-Duser.home=/tmp/dmhelper-p0-reliability
```

Expected: PASS. The collector reports no undeclared HTTP failures, console errors, page errors, or malformed requests, and consumes all four declared 503 responses.

- [ ] **Step 7: Update the capability matrix**

Replace `docs/campaign-capabilities.md` with an implementation-state matrix containing these exact rows:

```markdown
# Campaign Capabilities

| Capability | Status | Notes |
|---|---|---|
| P0 interaction reliability | `SUPPORTED` | Quick notes, destinations, browser guards, secure package assets, visible/retryable session mutations, correlated failures, and honest difficulty estimates are covered. |
| Campaign contract v1 | `SUPPORTED` | Schema, DTO, dry-run, import, references, and checked fixtures share one contract. |
| Campaign package v2 foundation | `SUPPORTED` | ZIP/JSON containers, stable keys, typed catalog, preview, migrations, staged assets, and atomic import are implemented. |
| Current persisted campaign state recovery | `SUPPORTED` | Default export/import preserves all currently persisted campaign meaning. |
| Combat log/dice recovery | `SUPPORTED` | Included by default and explicitly excludable. |
| Session cockpit | `UNSUPPORTED` | Delivery item 5; `/session` still redirects to the first map. |
| Structured scene transitions | `UNSUPPORTED` | Delivery item 6. |
| Structured quests/objectives | `UNSUPPORTED` | Delivery item 6. |
| Campaign-scoped non-statblock custom content | `UNSUPPORTED` | Delivery item 7. |
```

- [ ] **Step 8: Run the full verification suite**

Run:

```bash
./mvnw test -DargLine=-Duser.home=/tmp/dmhelper-p0-reliability
```

Expected: `BUILD SUCCESS`, no failures/errors, and a test count greater than the audited baseline of 712.

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 9: Commit the browser proof and status documentation**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/BrowserFailureCollector.java src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java docs/campaign-capabilities.md
git commit -m "test: prove runtime failure recovery"
```

---

## Spec Coverage Review

| Requirement | Covered by |
|---|---|
| Failed interactive map operations show actionable messages | Tasks 1, 2, and 5 |
| Optimistic map failures restore prior state | Task 2 steps 5–6; Task 5 step 3 |
| Tracker mutations never fail silently | Task 3 steps 1–5; Task 5 step 4 |
| Dice failures retain input and offer retry | Task 3 step 6; Task 5 step 5 |
| Presentation failure does not appear successful | Task 2 steps 7–8; Task 5 step 4 |
| Retryable actions expose retry | Task 1 step 8 and all mutation tasks |
| Server logs and user details share a correlation ID | Task 1 steps 1–6; Task 5 forced failures |
| Error details do not leak internals | Task 1 steps 4–6 |
| Difficulty is visibly an estimate with source/assumptions | Task 4 |
| Browser suite fails on unexpected console/network/HTTP problems | Existing collector retained; Task 5 adds explicit one-shot exceptions only |
| Asset traversal/collision/archive controls | Already implemented by package-v2 foundation; excluded from new code |
| Quick-note rendering and destinations | Already implemented and retained by full-suite verification |

## Type and Naming Consistency Review

- `correlationId` is identical in MDC, servlet request attributes, problem JSON, and client parsing.
- `X-Correlation-ID` is identical in the filter, tests, response parsing, and forced browser failures.
- `window.dmRequest` rejects network and non-2xx responses; no caller treats a resolved non-2xx Fetch response as success.
- `window.reportActionFailure` accepts `(summary, error, retry)` everywhere.
- `DifficultyResult` keeps the four current accessors and adds `estimate`, `source`, and `assumptions` once.
- Static guards target only time-critical mutation surfaces, so the deliberate JSON-parse fallback in `dm-request.js` and the common-condition read fallback remain legal.

After this plan is complete and verified, write the focused design/implementation plan for master-spec delivery item 5: the unified session cockpit.
