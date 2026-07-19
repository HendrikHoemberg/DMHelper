package dev.hendrikhoemberg.dmhelper.audio.provider;

public final class AudioProviderId {

    public static final AudioProviderId YOUTUBE = new AudioProviderId("youtube");
    public static final AudioProviderId SPOTIFY = new AudioProviderId("spotify");
    public static final AudioProviderId UNKNOWN = new AudioProviderId("unknown");

    private final String id;

    private AudioProviderId(String id) {
        this.id = id;
    }

    public static AudioProviderId of(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Provider ID must not be blank");
        }
        return new AudioProviderId(id.toLowerCase());
    }

    public String id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof AudioProviderId that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}
