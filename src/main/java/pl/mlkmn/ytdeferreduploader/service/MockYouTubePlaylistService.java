package pl.mlkmn.ytdeferreduploader.service;

import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelSnippet;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistSnippet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.mode", havingValue = "DEMO")
public class MockYouTubePlaylistService implements YouTubePlaylistService {

    private static final Channel DEMO_CHANNEL = new Channel()
            .setSnippet(new ChannelSnippet().setTitle("Demo Channel"));

    private static final List<Playlist> CANNED_PLAYLISTS = List.of(
            buildPlaylist("demo-pl-1", "Family videos"),
            buildPlaylist("demo-pl-2", "Travel"),
            buildPlaylist("demo-pl-3", "Tutorials")
    );

    @Override
    public Optional<Channel> getChannel() {
        return Optional.of(DEMO_CHANNEL);
    }

    @Override
    public List<Playlist> getUserPlaylists() {
        return CANNED_PLAYLISTS;
    }

    @Override
    public void addVideoToPlaylist(String playlistId, String videoId) {
        log.info("[DEMO] Pretend-adding video to playlist: videoId={}, playlistId={}", videoId, playlistId);
    }

    private static Playlist buildPlaylist(String id, String title) {
        Playlist pl = new Playlist();
        pl.setId(id);
        PlaylistSnippet snippet = new PlaylistSnippet();
        snippet.setTitle(title);
        pl.setSnippet(snippet);
        return pl;
    }
}
