package com.awesomebase.photography.google;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.googleapis.media.MediaHttpDownloaderProgressListener;

/**
 * The File Download Progress Listener.
 *
 * @author
 */
public class FileDownloadProgressListener implements MediaHttpDownloaderProgressListener {

	private final Logger _logger = LogManager.getLogger();

	@Override
	public void progressChanged(MediaHttpDownloader downloader) {
		switch (downloader.getDownloadState()) {
		case MEDIA_IN_PROGRESS:
			_logger.debug("Download is in progress: " + downloader.getProgress());
			break;
		case MEDIA_COMPLETE:
			_logger.debug("Download is Complete.");
			break;
		default:
			break;
		}
	}
}
