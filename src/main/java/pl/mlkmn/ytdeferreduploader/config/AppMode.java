package pl.mlkmn.ytdeferreduploader.config;

import java.util.List;

/**
 * Controls which features and OAuth scopes are available.
 *
 * <ul>
 *   <li><b>HOSTED</b> — public hosted instance; uses only non-sensitive/sensitive scopes
 *       (no CASA audit required). Drive access via Google Picker only.</li>
 *   <li><b>SELF_HOSTED</b> — operator's own Google Cloud project; full feature set
 *       with restricted scopes.</li>
 * </ul>
 */
public enum AppMode {

    HOSTED(
            List.of(
                    "https://www.googleapis.com/auth/youtube.upload",
                    "https://www.googleapis.com/auth/youtube.readonly",
                    "https://www.googleapis.com/auth/drive.file"
            )
    ),
    SELF_HOSTED(
            List.of(
                    "https://www.googleapis.com/auth/youtube.upload",
                    "https://www.googleapis.com/auth/youtube",
                    "https://www.googleapis.com/auth/drive"
            )
    );

    private final List<String> scopes;

    AppMode(List<String> scopes) {
        this.scopes = scopes;
    }

    public List<String> getScopes() {
        return scopes;
    }

    /** Whether Drive folder polling is available (SELF_HOSTED only). */
    public boolean canPollDrive() {
        return this == SELF_HOSTED;
    }

    /** Whether videos can be inserted into playlists (SELF_HOSTED only). */
    public boolean canInsertPlaylist() {
        return this == SELF_HOSTED;
    }

    /** Whether Drive files can be trashed after upload (SELF_HOSTED only). */
    public boolean canTrashDriveFiles() {
        return this == SELF_HOSTED;
    }

    /** Whether playlists can be listed from YouTube (SELF_HOSTED only). */
    public boolean canListPlaylists() {
        return this == SELF_HOSTED;
    }

    /** Whether the Google Picker is used for file selection (HOSTED only). */
    public boolean usesGooglePicker() {
        return this == HOSTED;
    }
}
