package dev.hendrikhoemberg.dmhelper.audio.provider;

public record AudioProviderCapabilities(
    boolean knownVideo,
    boolean knownPlaylist,
    boolean playPause,
    boolean skip,
    boolean volume,
    boolean queue,
    boolean search,
    boolean crossfade,
    boolean visiblePlayer,
    boolean initialGesture
) {
    public static final AudioProviderCapabilities NONE = new AudioProviderCapabilities(
        false, false, false, false, false, false, false, false, false, false
    );
}
