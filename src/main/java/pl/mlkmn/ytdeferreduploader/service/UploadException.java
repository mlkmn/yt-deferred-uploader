package pl.mlkmn.ytdeferreduploader.service;

import lombok.Getter;

@Getter
public class UploadException extends RuntimeException {

    private final boolean permanent;
    private final boolean quotaExhausted;

    public UploadException(String message, Throwable cause, boolean permanent) {
        this(message, cause, permanent, false);
    }

    public UploadException(String message, Throwable cause, boolean permanent, boolean quotaExhausted) {
        super(message, cause);
        this.permanent = permanent;
        this.quotaExhausted = quotaExhausted;
    }
}
