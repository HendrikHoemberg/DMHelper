package dev.hendrikhoemberg.dmhelper.audio;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AudioHostileContentTest {

    @Test
    void cueNameInWidgetUsesXText() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/_cockpit-widget.html"));
        assertThat(html)
                .contains("x-text=\"cueName\"")
                .doesNotContain("x-html=\"cueName\"");
    }

    @Test
    void sourceLabelInWidgetUsesXText() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/_cockpit-widget.html"));
        assertThat(html)
                .contains("x-text=\"sourceLabel\"")
                .doesNotContain("x-html=\"sourceLabel\"");
    }

    @Test
    void errorMessageInWidgetUsesXText() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/_cockpit-widget.html"));
        assertThat(html)
                .contains("x-text=\"errorMessage\"")
                .doesNotContain("x-html=\"errorMessage\"");
    }

    @Test
    void overridePickerUsesXTextForCueNames() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/_cockpit-widget.html"));
        assertThat(html)
                .contains("x-text=\"cue.name || cue.cachedTitle || cue.cueKey\"")
                .doesNotContain("x-html");
    }

    @Test
    void listCardUsesThTextForCueName() throws IOException {
        String card = Files.readString(Path.of("src/main/resources/templates/audio/_card.html"));
        assertThat(card)
                .contains("th:text=\"${cue.name}\"")
                .doesNotContain("th:utext=\"${cue.name}\"");
    }

    @Test
    void sceneFormPickerUsesThTextForCueName() throws IOException {
        String form = Files.readString(Path.of("src/main/resources/templates/adventure/_scene-form.html"));
        assertThat(form)
                .contains("th:text=\"${c.name + ' (' + c.providerId + ' / ' + c.category + ')'}\"")
                .doesNotContain("th:utext");
    }

    @Test
    void detailPageUsesThTextForAllMetadata() throws IOException {
        String detail = Files.readString(Path.of("src/main/resources/templates/audio/detail.html"));
        assertThat(detail)
                .contains("th:text=\"${cue.name}\"")
                .contains("th:text=\"${cue.cueKey}\"")
                .contains("th:text=\"${cue.providerId}\"")
                .contains("th:text=\"${cue.providerReference}\"")
                .contains("th:text=\"${cue.category}\"")
                .contains("th:text=\"${cue.transitionPreference}\"")
                .doesNotContain("th:utext=\"${cue.name}\"")
                .doesNotContain("th:utext=\"${cue.cueKey}\"")
                .doesNotContain("th:utext=\"${cue.providerId}\"")
                .doesNotContain("th:utext=\"${cue.providerReference}\"");
    }

    @Test
    void notesUseThUtextForSanitizedMarkdown() throws IOException {
        String detail = Files.readString(Path.of("src/main/resources/templates/audio/detail.html"));
        assertThat(detail)
                .contains("th:utext=\"${markdownNotes}\"")
                .contains("<h3>Notes</h3>");
    }

    @Test
    void detailPageDoesNotRenderUnsafeHtmlForCueProviderRefs() throws IOException {
        String detail = Files.readString(Path.of("src/main/resources/templates/audio/detail.html"));
        assertThat(detail)
                .doesNotContain("<img")
                .doesNotContain("<iframe");
        int refIdx = detail.indexOf("${cue.providerReference}");
        String beforeRef = detail.substring(0, refIdx);
        int spanIdx = beforeRef.lastIndexOf("<span");
        int spanClose = detail.indexOf("</span>", refIdx);
        String spanContent = detail.substring(spanIdx, spanClose);
        assertThat(spanContent)
                .doesNotContain("<img")
                .doesNotContain("<iframe")
                .contains("th:text=");
    }

    @Test
    void formDoesNotRenderUnsafeHtmlForProviderReference() throws IOException {
        String form = Files.readString(Path.of("src/main/resources/templates/audio/form.html"));
        assertThat(form)
                .doesNotContain("<img")
                .doesNotContain("<iframe");
    }

    @Test
    void artworkUrlIsDisplayedAsTextOnly() throws IOException {
        String detail = Files.readString(Path.of("src/main/resources/templates/audio/detail.html"));
        int artworkIdx = detail.indexOf("artworkUrl");
        String afterArtwork = detail.substring(artworkIdx);
        assertThat(afterArtwork)
                .contains("th:text=\"${cue.artworkUrl}\"")
                .doesNotContain("<img");
    }

    @Test
    void validationProblemsUseXText() throws IOException {
        String form = Files.readString(Path.of("src/main/resources/templates/audio/form.html"));
        String editor = Files.readString(Path.of("src/main/resources/static/js/audio-cue-editor.js"));
        assertThat(form).contains("x-text=\"problem.message\"");
        assertThat(form).contains("x-text=\"problem.code + ': ' + problem.message\"");
        assertThat(editor).doesNotContain("innerHTML");
    }

    @Test
    void editorDoesNotUseInnerHtmlForCueData() throws IOException {
        String editor = Files.readString(Path.of("src/main/resources/static/js/audio-cue-editor.js"));
        assertThat(editor).doesNotContain("innerHTML");
    }

    @Test
    void errorMessageInWidgetIsSafeText() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/_cockpit-widget.html"));
        assertThat(html)
                .contains("x-text=\"errorMessage\"")
                .doesNotContain("x-html=\"errorMessage\"");
    }

    @Test
    void providerDoesNotCreateScriptTagFromUntrustedInput() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/audio-provider-youtube.js"));
        assertThat(js)
                .contains("tag.src = 'https://www.youtube.com/iframe_api'")
                .doesNotContain("tag.src = ref")
                .doesNotContain("tag.src = input")
                .doesNotContain("tag.src = providerReference");
    }

    @Test
    void youtubeProviderOnlyCreatesScriptWithHardcodedUrl() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/audio-provider-youtube.js"));
        int createScript = js.indexOf("createElement('script')");
        int afterCreate = js.indexOf(";", createScript);
        String scriptBlock = js.substring(createScript, afterCreate + 200);
        assertThat(scriptBlock)
                .contains("'https://www.youtube.com/iframe_api'")
                .doesNotContain("ref.id")
                .doesNotContain("providerReference");
    }

    @Test
    void audioCueServiceDoesNotLeakCredentialsInExceptions() throws IOException {
        String service = Files.readString(Path.of(
                "src/main/java/dev/hendrikhoemberg/dmhelper/audio/service/AudioCueService.java"));
        assertThat(service)
                .doesNotContain("accessToken")
                .doesNotContain("refreshToken")
                .doesNotContain("authorizationCode")
                .doesNotContain("deviceId");
    }

    @Test
    void audioCueResponseDoesNotContainCredentials() throws Exception {
        var fields = dev.hendrikhoemberg.dmhelper.audio.web.AudioCueResponse.class.getDeclaredFields();
        var names = java.util.Arrays.stream(fields)
                .map(java.lang.reflect.Field::getName)
                .toList();
        assertThat(names)
                .doesNotContain("accessToken", "refreshToken", "authorizationCode", "deviceId");
    }

    @Test
    void cueManagementMessagesArePlainText() throws IOException {
        String detail = Files.readString(Path.of("src/main/resources/templates/audio/detail.html"));
        assertThat(detail)
                .contains("x-text=\"managementMessage\"")
                .doesNotContain("x-html=\"managementMessage\"");
    }
}
