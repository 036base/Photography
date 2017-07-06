Option Explicit
 
Const vbHide = 0             'ウィンドウを非表示
Const vbNormalFocus = 1      '通常のウィンドウ、かつ最前面のウィンドウ
Const vbMinimizedFocus = 2   '最小化、かつ最前面のウィンドウ
Const vbMaximizedFocus = 3   '最大化、かつ最前面のウィンドウ
Const vbNormalNoFocus = 4    '通常のウィンドウ、ただし、最前面にはならない
Const vbMinimizedNoFocus = 6 '最小化、ただし、最前面にはならない

Dim cmdReturn
Dim objWShell
Set objWShell = CreateObject("WScript.Shell")

'FileDownloadを起動
objWShell.Run "cmd /c FileDownload.bat", vbNormalNoFocus, false

Set objWShell = Nothing
