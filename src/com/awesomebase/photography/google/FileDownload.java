package com.awesomebase.photography.google;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

/**
 * Googleドライブからファイルをダウンロードするクラス
 *
 * @author
 *
 */
public class FileDownload {

	//---------------------------------------------------------------------------------------------
	// 【備考】
	//
	//  初回実行時はブラウザが起動し、Googleアカウントの選択、認証処理が必要。
	//
	//
	//---------------------------------------------------------------------------------------------

	private static final Logger _logger = LogManager.getLogger();
	private static final Properties _properties = new Properties();

	private static SimpleDateFormat _fmtDateTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

	/** ダウンロードフォルダ */
	private static String _downloadDir;
	/** ダウンロード実行間隔 */
	private static long _downloadInterval;
	/** 前回ダウンロード実行日時 */
	private static String _lastExecDateTime = "2000-01-01T00:00:00.000Z";

	/** アプリケーション名 */
	private static final String APPLICATION_NAME = "AwesomeBase Photography File Download";

	/** アプリケーションのユーザー資格情報を格納するフォルダ */
	private static final java.io.File DATA_STORE_DIR = new java.io.File("conf");

	/** FileDataStoreFactoryのグローバルインスタンス */
	private static FileDataStoreFactory DATA_STORE_FACTORY;

	/** JsonFactoryのグローバルインスタンス */
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

	/** HttpTransportのグローバルインスタンス */
	private static HttpTransport HTTP_TRANSPORT;

	/** ドライブAPIで使用できるOAuth 2.0スコープ */
	private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE);


	static {
		try {
			HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
		} catch (Throwable t) {
			_logger.error("*** System Error!! ***", t);
			System.exit(1);
		}
	}


	public static void main(String[] args) throws IOException {
		try {
			_logger.info("FileDownload main...START");

			// 設定ファイル読み込み
			_properties.load((new InputStreamReader(new FileInputStream("conf/photography.properties"), "UTF-8")));

			// 設定値の読み込み
			_downloadDir = _properties.getProperty("download_dir");
			_downloadInterval = Long.parseLong(_properties.getProperty("download_interval"));


			// 承認された新しいAPIクライアントサービスを生成
			Drive service = getDriveService();

			// スケジュール実行
			ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
			scheduledExecutorService.scheduleAtFixedRate(() -> {
				try {
					// ファイルダウンロード
					fileDownload(service);
				} catch (IOException e) {
					_logger.error("*** System Error!! ***", e);
					// スケジュール終了
					scheduledExecutorService.shutdown();
				}
			}, 1, _downloadInterval, TimeUnit.SECONDS);

			// ※shutdownを実行しない限り動き続ける
			// scheduledExecutorService.shutdown();

		} catch (Exception e) {
			_logger.error("*** System Error!! ***", e);
		}
	}

	/**
	 * 承認されたドライブクライアントサービスの取得
	 * @return
	 * @throws IOException
	 */
	public static Drive getDriveService() throws IOException {
		Credential credential = authorize();
		return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
	}

	/**
	 * 承認された資格証明ファイルを生成
	 * @return
	 * @throws IOException
	 */
	public static Credential authorize() throws IOException {

		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(new FileInputStream("conf/client_secret.json")));

		// ユーザー認証要求
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES).setDataStoreFactory(DATA_STORE_FACTORY).setAccessType("offline").build();
		Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");

		_logger.info("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());

		return credential;
	}

	/**
	 * Googleドライブからファイルをダウンロード
	 * @throws IOException
	 */
	public static void fileDownload(Drive service) throws IOException {

		// 今回実行日時を取得
		String execDateTime = getCurrentDateFormat();

		// ファイル一覧を取得
		List<File> files = retrieveAllFiles(service);
		if (files == null || files.size() == 0) {
			_logger.info("No files found.");
		} else {
			for (File file : files) {
				_logger.debug(String.format("Get File: Name[%s] ID[%s] MimeType[%s] ModifiedTime[%s]", file.getName(), file.getId(), file.getMimeType(), file.getModifiedTime()));

				// JPEGファイルのみダウンロード
				if ("image/jpeg".equals(file.getMimeType())) {
					String fileName = _downloadDir + "/" + file.getName();

					// ダウンロード済みの場合は処理を抜ける
					java.io.File chk = new java.io.File(fileName);
					if (chk.exists()) {
						continue;
					}

					ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
					com.google.api.services.drive.Drive.Files.Get request = service.files().get(file.getId());
					request.getMediaHttpDownloader().setProgressListener(new FileDownloadProgressListener());
					request.executeMediaAndDownloadTo(outputStream);

					FileOutputStream output = new FileOutputStream(fileName);
					outputStream.writeTo(output);
					output.flush();
					output.close();
					outputStream.close();

					_logger.info("Save is Complete: " + file.getName());
				}

			}
		}

		// ダウンロード実行日時を更新
		_lastExecDateTime = execDateTime;
	}

	/**
	 * Googleドライブ上のファイルリストを取得する
	 * @param service
	 * @return
	 * @throws IOException
	 */
	private static List<File> retrieveAllFiles(Drive service) throws IOException {
		List<File> result = new ArrayList<File>();

		// RootフォルダID
		String rootId = service.files().get("root").setFields("id").execute().getId();
		_logger.debug("Root Folder ID: " + rootId);
		_logger.debug("Last Exec DateTime: " + _lastExecDateTime);

		// 取得項目
		String files = "nextPageToken, files(id, name, kind, size, mimeType, lastModifyingUser, modifiedTime, iconLink, owners, folderColorRgb, shared, webViewLink, webContentLink)";
		// 取得条件
		StringBuilder query = new StringBuilder();
		query.append("'" + rootId + "' in parents");// 親フォルダを指定(root)
		query.append(" and ");
		query.append("mimeType = 'image/jpeg' and mimeType != 'application/vnd.google-apps.folder'");// MIMEタイプを指定
		query.append(" and ");
		query.append("trashed = false");//削除されていない
		query.append(" and ");
		query.append("modifiedTime >= '" + _lastExecDateTime + "'");//更新日時が前回実行日時以降

		// ファイル一覧を取得
		com.google.api.services.drive.Drive.Files.List request = service.files().list()
				.setPageSize(10)			// ページングサイズを指定
				.setFields(files)			// 取得する項目を指定
				.setQ(query.toString());	// 取得条件;

		do {
			try {
				FileList fileList = request.execute();
				result.addAll(fileList.getFiles());
				request.setPageToken(fileList.getNextPageToken());
			} catch (IOException e) {
				_logger.error("*** System Error!! ***", e);
				request.setPageToken(null);
			}
		} while (request.getPageToken() != null && request.getPageToken().length() > 0);

		return result;
	}

	/**
	 * 現在日時を取得
	 * @return
	 */
	private static String getCurrentDateFormat() {
		//_fmtDateTime.setTimeZone(TimeZone.getTimeZone("UTC"));
		return _fmtDateTime.format(Calendar.getInstance().getTime());
	}

}
