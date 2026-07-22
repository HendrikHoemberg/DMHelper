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
    void cardHtmlHasEnabledDerivativeButton() throws IOException {
        String card = Files.readString(
                Path.of("src/main/resources/templates/handout/_card.html"));
        // The previously disabled "Create player derivative" button should now be enabled
        // and should open the derivative dialog
        assertThat(card).doesNotContain("Coming soon");
        assertThat(card).contains("openDerivativeDialog");
    }
}
