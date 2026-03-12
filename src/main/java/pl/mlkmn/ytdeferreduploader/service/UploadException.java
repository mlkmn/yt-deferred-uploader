package pl.mlkmn.ytdeferreduploader.service;

public class UploadException extends RuntimeException {

    private final boolean permanent;

    public UploadException(String message, Throwable cause, boolean permanent) {
        super(message, cause);
        this.permanent = permanent;
    }

    public boolean isPermanent() {
        return permanent;
    }
}
