package dev.hendrikhoemberg.dmhelper.audio.provider;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioReferenceKind;

import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;

public class YouTubeProviderAdapter implements AudioProviderAdapter {

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Pattern PLAYLIST_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{13,}$");

    private static final Set<String> ALLOWED_HOSTS = Set.of(
        "www.youtube.com", "youtube.com", "youtu.be", "m.youtube.com"
    );

    private static final Set<String> PLAYLIST_PREFIXES = Set.of("PL", "RD", "UU", "FL", "OL", "TL", "LL");

    private static final AudioProviderCapabilities YOUTUBE_CAPABILITIES = new AudioProviderCapabilities(
        true, true, true, true, true, true, false, false, true, true
    );

    @Override
    public AudioProviderId id() {
        return AudioProviderId.YOUTUBE;
    }

    @Override
    public AudioAuthMode authMode() {
        return AudioAuthMode.NONE;
    }

    @Override
    public AudioProviderCapabilities capabilities() {
        return YOUTUBE_CAPABILITIES;
    }

    @Override
    public ParsedAudioReference parseReference(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Input must not be blank");
        }

        if (looksLikeUrl(input)) {
            return parseUrl(input);
        }

        return parseRawId(input);
    }

    @Override
    public AudioProviderAvailability availability() {
        return AudioProviderAvailability.AVAILABLE;
    }

    @Override
    public void clearCredentials() {
    }

    private boolean looksLikeUrl(String input) {
        return input.startsWith("https://") || input.startsWith("http://");
    }

    private ParsedAudioReference parseUrl(String input) {
        if (input.startsWith("http://")) {
            throw new IllegalArgumentException("HTTPS only");
        }

        URI uri = URI.create(input);
        String host = uri.getHost();

        if (host == null || !ALLOWED_HOSTS.contains(host)) {
            throw new IllegalArgumentException("Unsupported host: " + host);
        }

        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("User info not allowed in URL");
        }

        String path = uri.getPath() != null ? uri.getPath() : "";
        String query = uri.getRawQuery() != null ? uri.getRawQuery() : "";

        if ("youtu.be".equals(host)) {
            return parseShortUrl(path);
        }

        if (path.equals("/watch")) {
            return parseWatchQuery(query);
        }

        if (path.startsWith("/embed/")) {
            return parseEmbedPath(path);
        }

        if (path.equals("/playlist")) {
            return parsePlaylistQuery(query);
        }

        throw new IllegalArgumentException("Unrecognized YouTube URL shape: " + input);
    }

    private ParsedAudioReference parseShortUrl(String path) {
        String id = path.startsWith("/") ? path.substring(1) : path;
        if (id.isBlank()) {
            throw new IllegalArgumentException("Blank YouTube ID in short URL");
        }
        return validateAndCreateVideo(id);
    }

    private ParsedAudioReference parseWatchQuery(String query) {
        String v = extractQueryParam(query, "v");
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing video ID in watch URL");
        }
        return validateAndCreateVideo(v);
    }

    private ParsedAudioReference parseEmbedPath(String path) {
        String id = path.substring("/embed/".length());
        int slash = id.indexOf('/');
        if (slash != -1) {
            id = id.substring(0, slash);
        }
        if (id.isBlank()) {
            throw new IllegalArgumentException("Blank video ID in embed URL");
        }
        return validateAndCreateVideo(id);
    }

    private ParsedAudioReference parsePlaylistQuery(String query) {
        String list = extractQueryParam(query, "list");
        if (list == null || list.isBlank()) {
            throw new IllegalArgumentException("Missing playlist ID in playlist URL");
        }
        return validateAndCreatePlaylist(list);
    }

    private ParsedAudioReference parseRawId(String input) {
        if (PLAYLIST_PREFIXES.stream().anyMatch(input::startsWith)) {
            return validateAndCreatePlaylist(input);
        }
        return validateAndCreateVideo(input);
    }

    private ParsedAudioReference validateAndCreateVideo(String id) {
        if (!VIDEO_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid video ID: " + id);
        }
        return new ParsedAudioReference(AudioReferenceKind.VIDEO, id);
    }

    private ParsedAudioReference validateAndCreatePlaylist(String id) {
        if (!PLAYLIST_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid playlist ID: " + id);
        }
        return new ParsedAudioReference(AudioReferenceKind.PLAYLIST, id);
    }

    private String extractQueryParam(String query, String param) {
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            String key = eq != -1 ? pair.substring(0, eq) : pair;
            if (key.equals(param)) {
                return eq != -1 ? pair.substring(eq + 1) : "";
            }
        }
        return null;
    }
}
