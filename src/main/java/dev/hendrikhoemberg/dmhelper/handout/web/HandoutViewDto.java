package dev.hendrikhoemberg.dmhelper.handout.web;

import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import java.util.UUID;

public record HandoutViewDto(
        UUID id,
        String title,
        String contentType,
        String safetyClassification,
        boolean presentable,
        boolean presented,
        UUID sourceHandoutId
) {
    public static HandoutViewDto from(Handout handout) {
        return new HandoutViewDto(handout.getId(), handout.getTitle(), handout.getContentType(),
                handout.getSafetyClassification().name(), handout.isPresentable(),
                handout.isPresented(), handout.getSourceHandout() == null
                        ? null : handout.getSourceHandout().getId());
    }
}
