package com.awesomebase.photography.image;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Properties;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 画像変換を行うクラス
 *
 * @author
 *
 */
public class ConvertImage {

	private static final Logger _logger = LogManager.getLogger();
	private static final Properties _properties = new Properties();

	// リサイズ画像幅
	private static int _resizeWidth = 300;

	/**
	 * 画像変換
	 * @param downloadFiles
	 * @throws Exception
	 */
	public static void convert(List<com.google.api.services.drive.model.File> downloadFiles) throws Exception {

		// 設定ファイル読み込み
		_properties.load((new InputStreamReader(new FileInputStream("conf/photography.properties"), "UTF-8")));
		_resizeWidth = Integer.parseInt(_properties.getProperty("resize_width"));

		//_logger.debug("Convert Files: " + downloadFiles.stream().map(com.google.api.services.drive.model.File::getName).collect(Collectors.toList()).toString());

		if (downloadFiles != null && downloadFiles.size() > 0) {

			_logger.info("Convert Folder: " + _properties.getProperty("convert_dir"));
			for (com.google.api.services.drive.model.File driveFile : downloadFiles) {
				// ファイル読み込み
				File inFile = new File(_properties.getProperty("download_dir") + "/" + driveFile.getName());
				BufferedImage bufimg = ImageIO.read(inFile);

				// リサイズ
				bufimg = resize(bufimg);

				// ファイル出力  ※「JPEG」の場合は「PNG」に変換される
				File outFile = new File(_properties.getProperty("convert_dir") + "/" + inFile.getName().replace(".jpg", ".png"));
				ImageIO.write(bufimg, "png", outFile);

				_logger.info("Resize and Convert: " + outFile.getName());
			}

		}
	}

	/**
	 * 画像リサイズ
	 * @param bufimg
	 * @return
	 */
	private static BufferedImage resize(BufferedImage bufimg) {
		BufferedImage resizeImg = null;
		AffineTransformOp xform = null;

		int width = bufimg.getWidth();    // オリジナル画像の幅
		int height = bufimg.getHeight();  // オリジナル画像の高さ

		int new_height = _resizeWidth * height / width;
		int new_width = _resizeWidth;

		// 画像変換
		xform = new AffineTransformOp(AffineTransform.getScaleInstance((double) new_width / width, (double) new_height / height), AffineTransformOp.TYPE_BILINEAR);
		resizeImg = new BufferedImage(new_width, new_height, bufimg.getType());
		xform.filter(bufimg, resizeImg);

		return resizeImg;
	}

}
