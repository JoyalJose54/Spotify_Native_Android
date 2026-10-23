Set WshShell = CreateObject("WScript.Shell")
Dim fso, scriptDir, domainFile, domain, pyCmd, ngrokExe
Set fso = CreateObject("Scripting.FileSystemObject")
scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)

domain = "diary-lugged-petticoat.ngrok-free.dev"
domainFile = scriptDir & "\ngrok_domain.txt"
If fso.FileExists(domainFile) Then
    Dim txt
    Set txt = fso.OpenTextFile(domainFile, 1)
    domain = Replace(Replace(Trim(txt.ReadLine), Chr(13), ""), Chr(10), "")
    txt.Close
End If

' Determine reliable Python executable
pyCmd = "python"
If fso.FileExists("C:\Python313\python.exe") Then
    pyCmd = """C:\Python313\python.exe"""
End If

' Determine reliable ngrok executable
ngrokExe = scriptDir & "\ngrok.exe"
If Not fso.FileExists(ngrokExe) Then
    ngrokExe = "ngrok"
End If

' 1. Start Python backend silently
WshShell.CurrentDirectory = scriptDir & "\cloud_functions"
WshShell.Run "cmd /c " & pyCmd & " main.py", 0, False

' 2. Wait 2 seconds and launch Ngrok tunnel silently
WScript.Sleep 2000
WshShell.CurrentDirectory = scriptDir
WshShell.Run """" & ngrokExe & """ http --url=" & domain & " 8080 --log=""" & scriptDir & "\ngrok.log""", 0, False

Set WshShell = Nothing
Set fso = Nothing
