package pl.mlkmn.ytdeferreduploader.config;

public enum AppMode {
    DEMO,
    SELF_HOSTED;

    public boolean isDemoMode() {
        return this == DEMO;
    }

    public boolean isSelfHosted() {
        return this == SELF_HOSTED;
    }
}
