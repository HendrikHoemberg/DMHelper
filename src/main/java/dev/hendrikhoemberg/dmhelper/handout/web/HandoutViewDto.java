package dev.hendrikhoemberg.dmhelper.handout.web;

import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import java.util.UUID;

public record HandoutViewDto(
        UUID id,
        String title,
        String contentType,
        boolean presented
) {
    public static HandoutViewDto from(Handout handout) {
        return new HandoutViewDto(handout.getId(), handout.getTitle(), handout.getContentType(),
                handout.isPresented());
    }
}
