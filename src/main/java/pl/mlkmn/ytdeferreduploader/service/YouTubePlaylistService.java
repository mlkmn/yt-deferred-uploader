package pl.mlkmn.ytdeferreduploader.service;

import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.Playlist;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface YouTubePlaylistService {

    Optional<Channel> getChannel();

    List<Playlist> getUserPlaylists();

    void addVideoToPlaylist(String playlistId, String videoId) throws IOException;
}
