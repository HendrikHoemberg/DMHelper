package dev.hendrikhoemberg.dmhelper.audio.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AudioCueTemplateContractTest {

    @Test
    void listHasCreateSearchAndScope() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/list.html"));
        assertThat(html).contains("campaignId");
        assertThat(html).contains("name=\"text\"");
        assertThat(html).contains("New Cue");
        assertThat(html).contains("createHref");
        assertThat(html).contains("campaigns");
    }

    @Test
    void detailHasCloneDependencyAwareDeleteAndManagement() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/detail.html"));
        assertThat(html).contains("Clone");
        assertThat(html).contains("Delete");
        assertThat(html).contains("audioCueManagement");
        assertThat(html).contains("deletion-impact");
    }

    @Test
    void detailUsesUtextForSanitizedNotes() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/detail.html"));
        assertThat(html).contains("th:utext=\"${markdownNotes}\"");
        assertThat(html).contains("th:text");
    }

    @Test
    void formHasEditorAndPathErrors() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/form.html"));
        assertThat(html).contains("audioCueEditor");
        assertThat(html).contains("problemsFor");
        assertThat(html).contains("data-path");
    }

    @Test
    void editorSupportsCreateUpdateCloneDeleteAndProblems() throws IOException {
        String editor = Files.readString(Path.of("src/main/resources/static/js/audio-cue-editor.js"));
        assertThat(editor).contains("audioCueEditor");
        assertThat(editor).contains("audioCueManagement");
        assertThat(editor).contains("problemsFor");
        assertThat(editor).contains("CUE_ID");
        assertThat(editor).contains("CAMPAIGN_ID");
    }

    @Test
    void artworkUrlIsTextOnlyNeverImageSrc() throws IOException {
        String form = Files.readString(Path.of("src/main/resources/templates/audio/form.html"));
        String detail = Files.readString(Path.of("src/main/resources/templates/audio/detail.html"));

        assertThat(form).doesNotContain("<img");
        assertThat(detail).doesNotContain("<img");
        assertThat(form).contains("artworkUrl");
        assertThat(detail).contains("artworkUrl");
    }
}
