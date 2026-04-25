package pl.mlkmn.ytdeferreduploader.service;

import pl.mlkmn.ytdeferreduploader.model.UploadJob;

import java.io.InputStream;

public interface YouTubeUploadService {

    String upload(UploadJob job);

    String uploadFromStream(UploadJob job, InputStream inputStream, long contentLength, String mimeType);
}
