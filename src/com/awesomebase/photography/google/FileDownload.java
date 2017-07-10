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
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.awesomebase.photography.image.ConvertImage;
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

	/** ダウンロードフォルダ */
	private static String _downloadDir;
	/** ダウンロード実行間隔 */
	private static long _downloadInterval;

	/** ルートフォルダID */
	private static String _rootFolderID;
	/** バックアップフォルダ名 */
	private static String _backupFolderName;
	/** バックアップフォルダD */
	private static String _backupFolderID;

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


	/** MimeType : folder */
	private static final String MIME_TYPE_FOLDER = "application/vnd.google-apps.folder";
	/** MimeType : jpeg */
	private static final String MIME_TYPE_JPEG = "image/jpeg";
	/** MimeType : png */
	private static final String MIME_TYPE_PNG = "image/png";


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
			_logger.info("{} start...", Thread.currentThread().getStackTrace()[1].getClassName());

			// 設定ファイル読み込み
			_properties.load((new InputStreamReader(new FileInputStream("conf/photography.properties"), "UTF-8")));
			// 設定値の読み込み
			_downloadDir = _properties.getProperty("download_dir");
			_downloadInterval = Long.parseLong(_properties.getProperty("download_interval"));

			_logger.info("Download Folder:" + _downloadDir);
			_logger.info("Download Interval:" + _downloadInterval);

			// 承認された新しいAPIクライアントサービスを生成
			Drive service = getDriveService();

			// ルートフォルダID取得
			_rootFolderID = service.files().get("root").setFields("id").execute().getId();
			_logger.debug("Root Folder ID: " + _rootFolderID);

			// バックアップフォルダ作成
			createBackupFolder(service);

			// スケジュール実行
			ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
			scheduledExecutorService.scheduleAtFixedRate(() -> {
				try {
					// ファイルダウンロード
					List<File> files = fileDownload(service);

					// 画像変換
					ConvertImage.convert(files);

				} catch (Exception e) {
					_logger.error("*** System Error!! ***", e);
					// スケジュール終了
					scheduledExecutorService.shutdown();
					_logger.info("Scheduled Executor Service Shutdown.");
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

		_logger.debug("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());

		return credential;
	}

	/**
	 * Googleドライブからファイルをダウンロード
	 * @throws IOException
	 */
	public static List<File> fileDownload(Drive service) throws IOException {

		// 取得項目
		String fields = "nextPageToken, files(name, id, mimeType, modifiedTime, parents)";
		// 取得条件
		StringBuilder query = new StringBuilder();
		query.append(String.format("'%s' in parents", _rootFolderID));// 親フォルダを指定(root)
		query.append(" and ");
		query.append(String.format("(mimeType = '%s' or mimeType = '%s')", MIME_TYPE_JPEG, MIME_TYPE_PNG));// MIMEタイプを指定（JPEG or PNG）
		query.append(" and ");
		query.append("trashed = false");//削除されていない

		// ファイル一覧を取得
		List<File> files = getDriveFiles(service, fields, query.toString());
		if (files == null || files.size() == 0) {
			_logger.info("No files found.");
		} else {
			for (File file : files) {
				_logger.debug(String.format("Get File: Name[%s] ID[%s] MimeType[%s] ModifiedTime[%s]", file.getName(), file.getId(), file.getMimeType(), file.getModifiedTime()));

				// ダウンロード
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				com.google.api.services.drive.Drive.Files.Get request = service.files().get(file.getId());
				request.getMediaHttpDownloader().setProgressListener(new FileDownloadProgressListener());
				request.executeMediaAndDownloadTo(outputStream);

				// 保存
				FileOutputStream output = new FileOutputStream(_downloadDir + "/" + file.getName());
				outputStream.writeTo(output);
				output.flush();
				output.close();
				outputStream.close();

				_logger.info("Save is Complete: " + file.getName());

				// バックアップフォルダへ移動
				fileBackup(service, file);
			}
		}
		return files;
	}

	/**
	 * バックアップフォルダへ移動
	 * @param service
	 * @param file
	 */
	private static void fileBackup(Drive service, File file) throws IOException {

		// 既存の親フォルダを取得
		StringBuilder previousParents = new StringBuilder();
		for (String parent : file.getParents()) {
			previousParents.append(parent);
			previousParents.append(',');
		}
		// 親フォルダを変更してファイルを更新
		service.files().update(file.getId(), null)
				.setAddParents(_backupFolderID)
				.setRemoveParents(previousParents.toString())
				.setFields("id, name, mimeType, modifiedTime, parents")
				.execute();

		_logger.debug("Backup to: " + _backupFolderName);
	}

	/**
	 * Googleドライブ上にバックアップフォルダ作成
	 * @param service
	 * @return
	 * @throws IOException
	 */
	private static void createBackupFolder(Drive service) throws IOException {

		// バックアップフォルダ名（年月単位）
		String bkFolderName = (new SimpleDateFormat("yyyyMM")).format(Calendar.getInstance().getTime());

		// 取得項目
		String fields = "nextPageToken, files(name, id)";
		// 取得条件
		StringBuilder query = new StringBuilder();
		query.append(String.format("'%s' in parents", _rootFolderID));// 親フォルダを指定(root)
		query.append(" and ");
		query.append("name = '" + bkFolderName + "'");//名前を指定（年月）
		query.append(" and ");
		query.append(String.format("mimeType = '%s'", MIME_TYPE_FOLDER) );// MIMEタイプを指定（フォルダ）
		query.append(" and ");
		query.append("trashed = false");//削除されていない

		// ファイル一覧を取得
		List<File> files = getDriveFiles(service, fields, query.toString());
		if (files == null || files.size() == 0) {
			// バックアップフォルダ作成
			File fileMetadata = new File();
			fileMetadata.setName(bkFolderName);
			fileMetadata.setMimeType(MIME_TYPE_FOLDER);
			fileMetadata.setParents(Collections.singletonList(_rootFolderID));
			File file = service.files().create(fileMetadata).setFields("name, id, mimeType, modifiedTime, parents").execute();
			_backupFolderName = file.getName();
			_backupFolderID = file.getId();
			_logger.debug(String.format("Create Backup Folder: %s [%s]", file.getName(), file.getId()));
		} else {
			// バックアップフォルダ名、ID取得
			for (File file : files) {
				_logger.debug(String.format("Get Backup Folder: %s [%s]", file.getName(), file.getId()));
				_backupFolderName = file.getName();
				_backupFolderID = file.getId();
				break;
			}
		}
	}

	/**
	 * Googleドライブ上のファイルリストを取得する
	 * @param service
	 * @param fields
	 * @param query
	 * @return
	 * @throws IOException
	 */
	private static List<File> getDriveFiles(Drive service, String fields, String query) throws IOException {

		_logger.debug("Get DriveFiles Fields:" + fields);
		_logger.debug("Get DriveFiles Query:" + query);

		List<File> result = new ArrayList<File>();

		// ファイル一覧を取得
		com.google.api.services.drive.Drive.Files.List request = service.files().list()
				.setPageSize(10)	// ページングサイズ
				.setFields(fields)	// 取得項目
				.setQ(query);		// 取得条件
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

}
