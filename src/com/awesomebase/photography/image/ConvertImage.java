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
	 * @param driveFiles
	 * @throws Exception
	 */
	public static void convert(List<com.google.api.services.drive.model.File> driveFiles) throws Exception {

		// 設定ファイル読み込み
		_properties.load((new InputStreamReader(new FileInputStream("conf/photography.properties"), "UTF-8")));
		_resizeWidth = Integer.parseInt(_properties.getProperty("resize_width"));

		//_logger.debug("Convert Files: " + driveFiles.stream().map(com.google.api.services.drive.model.File::getName).collect(Collectors.toList()).toString());

		if (driveFiles != null && driveFiles.size() > 0) {
			for (com.google.api.services.drive.model.File driveFile : driveFiles) {

				// JPEGファイルを読み込む
				File jpgFile = new File(_properties.getProperty("download_dir") + "/" + driveFile.getName());
				BufferedImage bufimg = ImageIO.read(jpgFile);

				// リサイズ
				bufimg = resize(bufimg);

				// PNGファイルに出力
				File pngFile = new File(_properties.getProperty("convert_dir") + "/" + jpgFile.getName().replace(".jpg", ".png"));
				ImageIO.write(bufimg, "png", pngFile);

				_logger.info("Resize and Convert to PNG: " + pngFile.getAbsolutePath());
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
