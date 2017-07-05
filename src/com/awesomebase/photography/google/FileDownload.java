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
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;

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
	// 【TODO】
	//  https://developers.google.com/drive/v3/web/search-parameters
	//    ・タイマーでダウンロードを実行する
	//    ・前回実行日時以降に更新されたファアイルのみ対象とする
	//
	//
	//---------------------------------------------------------------------------------------------

	private static final Logger _logger = LogManager.getLogger();
	private static final Properties _properties = new Properties();

	/** ダウンロードフォルダ */
	private static String _downloadDir;

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
			_logger.info("FileDownload main...");

			// 設定ファイル読み込み
			_properties.load((new InputStreamReader(new FileInputStream("conf/photography.properties"), "UTF-8")));

			// ダウンロードフォルダ
			_downloadDir = _properties.getProperty("dir_download");

			// 承認された新しいAPIクライアントサービスを生成
			Drive service = getDriveService();

			List<File> files = retrieveAllFiles(service);
			if (files == null || files.size() == 0) {
				_logger.info("No files found.");
			} else {
				for (File file : files) {
					_logger.debug(String.format("Get File: Name[%s] ID[%s] MimeType[%s] ModifiedTime[%s]", file.getName(), file.getId(), file.getMimeType(), file.getModifiedTime()));
					// JPEGファイルのみダウンロード
					if ("image/jpeg".equals(file.getMimeType())) {
						fileDownload(service, file);
					}
				}
			}

		} catch (Exception e) {
			_logger.error("*** System Error!! ***", e);
		}
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
	 * 承認されたドライブクライアントサービスの取得
	 * @return
	 * @throws IOException
	 */
	public static Drive getDriveService() throws IOException {
		Credential credential = authorize();
		return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
	}

	/**
	 * ファイルリソースのリストを取得する
	 * @param service
	 * @return
	 * @throws IOException
	 */
	private static List<File> retrieveAllFiles(Drive service) throws IOException {
		List<File> result = new ArrayList<File>();

		// RootフォルダID
		String rootId = service.files().get("root").setFields("id").execute().getId();
		_logger.debug(String.format("Root Folder ID [%s]", rootId));

		// 取得項目
		String files = "nextPageToken, files(id, name, kind, size, mimeType, lastModifyingUser, modifiedTime, iconLink, owners, folderColorRgb, shared, webViewLink, webContentLink)";
		// 取得条件
		StringBuilder query = new StringBuilder();
		query.append("'" + rootId + "' in parents");// 親フォルダを指定(root)
		query.append(" and ");
		query.append("mimeType = 'image/jpeg' and mimeType != 'application/vnd.google-apps.folder'");// MIMEタイプを指定
		query.append(" and ");
		query.append("trashed = false");//削除されていない

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
	 * Googleドライブからファイルをダウンロード
	 * @param service
	 * @param file
	 * @throws IOException
	 */
	public static void fileDownload(Drive service, File file) throws IOException {

		String fileName = _downloadDir + "/" + file.getName();

		// ダウンロード済みの場合は処理を抜ける
		java.io.File chk = new java.io.File(fileName);
		if (chk.exists()) {
			return;
		}

		_logger.info("Download File:" + file.getName());

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		com.google.api.services.drive.Drive.Files.Get request = service.files().get(file.getId());
		request.getMediaHttpDownloader().setProgressListener(new FileDownloadProgressListener());
		request.executeMediaAndDownloadTo(outputStream);

		FileOutputStream output = new FileOutputStream(fileName);
		outputStream.writeTo(output);
		output.flush();
		output.close();
		outputStream.close();

		_logger.info("Save is Complete.");

	}

	/**
	 * 現在日時を取得
	 * @return
	 */
	private static String getCurrentDateFormat() {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
		format.setTimeZone(TimeZone.getTimeZone("UTC"));

		return format.format(Calendar.getInstance().getTime());
	}

}
