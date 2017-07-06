@echo off

rem カレントディレクトリ移動
cd /D %~dp0

rem FileDownload実行
java -jar FileDownload.jar

exit /b 0

