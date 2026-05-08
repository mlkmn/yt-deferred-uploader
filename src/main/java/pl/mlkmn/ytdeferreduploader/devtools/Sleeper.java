package pl.mlkmn.ytdeferreduploader.devtools;

@FunctionalInterface
public interface Sleeper {
    void sleep(long millis) throws InterruptedException;
}
