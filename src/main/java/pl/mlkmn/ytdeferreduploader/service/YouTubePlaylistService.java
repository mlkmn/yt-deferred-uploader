package pl.mlkmn.ytdeferreduploader.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemSnippet;
import com.google.api.services.youtube.model.PlaylistListResponse;
import com.google.api.services.youtube.model.ResourceId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubePlaylistService {

    private static final String APPLICATION_NAME = "yt-deferred-uploader";

    private final YouTubeCredentialService credentialService;
    private final NetHttpTransport httpTransport;
    private final GsonFactory jsonFactory;

    public Optional<Channel> getChannel() {
        Credential credential = credentialService.getCredential().orElse(null);
        if (credential == null) {
            return Optional.empty();
        }

        try {
            ChannelListResponse response = buildClient(credential).channels()
                    .list(List.of("snippet"))
                    .setMine(true)
                    .setMaxResults(1L)
                    .execute();

            if (response.getItems() != null && !response.getItems().isEmpty()) {
                return Optional.of(response.getItems().getFirst());
            }
        } catch (IOException e) {
            log.error("Failed to fetch channel info: error={}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<Playlist> getUserPlaylists() {
        Credential credential = credentialService.getCredential().orElse(null);
        if (credential == null) {
            return Collections.emptyList();
        }

        YouTube youtube = buildClient(credential);
        List<Playlist> allPlaylists = new ArrayList<>();
        String pageToken = null;

        try {
            do {
                PlaylistListResponse response = youtube.playlists()
                        .list(List.of("snippet"))
                        .setMine(true)
                        .setMaxResults(50L)
                        .setPageToken(pageToken)
                        .execute();

                if (response.getItems() != null) {
                    allPlaylists.addAll(response.getItems());
                }
                pageToken = response.getNextPageToken();
            } while (pageToken != null);

            log.info("Fetched {} playlists from YouTube", allPlaylists.size());
        } catch (IOException e) {
            log.error("Failed to fetch playlists: error={}", e.getMessage(), e);
        }

        return allPlaylists;
    }

    public void addVideoToPlaylist(String playlistId, String videoId) throws IOException {
        Credential credential = credentialService.getCredential()
                .orElseThrow(() -> new IOException("YouTube account not connected"));

        YouTube youtube = buildClient(credential);

        PlaylistItem item = new PlaylistItem();
        PlaylistItemSnippet snippet = new PlaylistItemSnippet();
        snippet.setPlaylistId(playlistId);
        snippet.setResourceId(new ResourceId()
                .setKind("youtube#video")
                .setVideoId(videoId));
        item.setSnippet(snippet);

        youtube.playlistItems()
                .insert(List.of("snippet"), item)
                .execute();

        log.info("Video added to playlist: videoId={}, playlistId={}", videoId, playlistId);
    }

    private YouTube buildClient(Credential credential) {
        return new YouTube.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}
