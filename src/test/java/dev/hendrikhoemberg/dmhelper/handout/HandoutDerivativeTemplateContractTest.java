package dev.hendrikhoemberg.dmhelper.handout;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HandoutDerivativeTemplateContractTest {

    @Test
    void derivativeDialogRequiresNumericCropInputs() throws IOException {
        String html = Files.readString(
                Path.of("src/main/resources/templates/handout/_derivative-dialog.html"));
        assertThat(html).contains("type=\"number\"");
        assertThat(html).contains("cropX");
        assertThat(html).contains("cropY");
        assertThat(html).contains("cropWidth");
        assertThat(html).contains("cropHeight");
    }

    @Test
    void derivativeDialogHasEditableRedactionList() throws IOException {
        String html = Files.readString(
                Path.of("src/main/resources/templates/handout/_derivative-dialog.html"));
        assertThat(html).contains("redactions");
        assertThat(html).contains("addRedaction");
        assertThat(html).contains("removeRedaction");
    }

    @Test
    void derivativeDialogHasPointerHandlers() throws IOException {
        String html = Files.readString(
                Path.of("src/main/resources/templates/handout/_derivative-dialog.html"));
        // The dialog should have pointer events for drawing crop/redaction rectangles
        assertThat(html).contains("pointerdown");
        assertThat(html).contains("pointermove");
        assertThat(html).contains("pointerup");
    }

    @Test
    void derivativeDialogCallsCanvasToBlob() throws IOException {
        String js = Files.readString(
                Path.of("src/main/resources/static/js/handout-derivative-editor.js"));
        assertThat(js).contains("canvas.toBlob");
        assertThat(js).contains("'image/png'");
    }

    @Test
    void derivativeDialogPostsMultipartFormData() throws IOException {
        String js = Files.readString(
                Path.of("src/main/resources/static/js/handout-derivative-editor.js"));
        assertThat(js).contains("FormData");
        assertThat(js).contains("append");
        assertThat(js).contains("/derivatives");
        assertThat(js).contains("window.history.replaceState(null, '', redirect)")
                .contains("window.location.reload()");
    }

    @Test
    void derivativeDialogDoesNotRequirePointerDraggingToSubmit() throws IOException {
        String html = Files.readString(
                Path.of("src/main/resources/templates/handout/_derivative-dialog.html"));
        // Numeric inputs must be authoritative - a user can type coordinates without dragging
        assertThat(html).contains("x-model");
        assertThat(html).contains("cropX");
        assertThat(html).contains("cropY");
        assertThat(html).contains("cropWidth");
        assertThat(html).contains("cropHeight");
    }

    @Test
    void listHtmlIncludesDerivativeDialog() throws IOException {
        String html = Files.readString(
                Path.of("src/main/resources/templates/handout/list.html"));
        assertThat(html).contains("_derivative-dialog");
        assertThat(html).contains("handout-derivative-editor.js");
    }

    @Test
    void derivativeDialogUsesARealViewportOverlayAndScopedPanel() throws IOException {
        String html = Files.readString(
                Path.of("src/main/resources/templates/handout/_derivative-dialog.html"));
        String css = Files.readString(
                Path.of("src/main/resources/static/css/components.css"));

        assertThat(html).contains("role=\"dialog\"", "aria-modal=\"true\"",
                "class=\"modal-panel derivative-modal-panel\"")
                .doesNotContain("class=\"modal\"");
        assertThat(css).contains(".modal-overlay {", "position: fixed;", "inset: 0;",
                ".derivative-modal-panel {");
    }

    @Test
    void cardHtmlHasEnabledDerivativeButton() throws IOException {
        String card = Files.readString(
                Path.of("src/main/resources/templates/handout/_card.html"));
        // The previously disabled "Create player derivative" button should now be enabled
        // and should open the derivative dialog
        assertThat(card).doesNotContain("Coming soon");
        assertThat(card).contains("openDerivativeDialog", "data-source-id", "data-campaign-id")
                .doesNotContain("th:onclick");
    }

    @Test
    void fragmentOwnsItsAlpineControllerAndScriptLoadsBeforeAlpineStarts() throws IOException {
        String dialog = Files.readString(
                Path.of("src/main/resources/templates/handout/_derivative-dialog.html"));
        String list = Files.readString(Path.of("src/main/resources/templates/handout/list.html"));
        String js = Files.readString(
                Path.of("src/main/resources/static/js/handout-derivative-editor.js"));

        int fragment = dialog.indexOf("th:fragment=\"dialog\"");
        assertThat(fragment).isGreaterThan(-1);
        assertThat(dialog.substring(Math.max(0, fragment - 120), fragment + 180))
                .contains("x-data=\"derivativeEditor()\"")
                .contains("x-init=\"init()\"");
        assertThat(dialog).contains("@open-derivative-dialog.window");
        assertThat(list).contains("handout-derivative-editor.js}")
                .doesNotContain("handout-derivative-editor.js}\" defer");
        assertThat(js).doesNotContain(".__x");
        assertThat(js).contains("window.dispatchEvent(new CustomEvent('open-derivative-dialog'");
    }

    @Test
    void defaultRedactionAlwaysHasPositiveDimensions() throws IOException {
        String js = Files.readString(
                Path.of("src/main/resources/static/js/handout-derivative-editor.js"));
        assertThat(js).contains("Math.max(1, Math.round(this.cropWidth * 0.3))")
                .contains("Math.max(1, Math.round(this.cropHeight * 0.1))");
    }
}
