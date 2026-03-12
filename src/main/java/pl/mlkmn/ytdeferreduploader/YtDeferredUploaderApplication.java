package pl.mlkmn.ytdeferreduploader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class YtDeferredUploaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(YtDeferredUploaderApplication.class, args);
    }
}
