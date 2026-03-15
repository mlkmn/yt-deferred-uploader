package pl.mlkmn.ytdeferreduploader.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public enum ScopeTier {

    BASIC(List.of(
            "https://www.googleapis.com/auth/youtube.upload",
            "https://www.googleapis.com/auth/youtube.readonly",
            "https://www.googleapis.com/auth/drive.readonly"
    )),

    EXTENDED(List.of(
            "https://www.googleapis.com/auth/youtube.upload",
            "https://www.googleapis.com/auth/youtube",
            "https://www.googleapis.com/auth/drive"
    ));

    private final List<String> scopes;

    public boolean canInsertPlaylist() {
        return this == EXTENDED;
    }

    public boolean canTrashDriveFiles() {
        return this == EXTENDED;
    }
}
