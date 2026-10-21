Set WshShell = CreateObject("WScript.Shell")
Dim fso, scriptDir, domainFile, domain, pyCmd
Set fso = CreateObject("Scripting.FileSystemObject")
scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)

domain = "your-subdomain.ngrok-free.dev"
domainFile = scriptDir & "\ngrok_domain.txt"
If fso.FileExists(domainFile) Then
    Dim txt
    Set txt = fso.OpenTextFile(domainFile, 1)
    domain = Trim(txt.ReadLine)
    txt.Close
End If

' Determine reliable Python executable
pyCmd = "python"
If fso.FileExists("C:\Python313\python.exe") Then
    pyCmd = """C:\Python313\python.exe"""
End If

' 1. Start Python backend silently
WshShell.CurrentDirectory = scriptDir & "\cloud_functions"
WshShell.Run "cmd /c " & pyCmd & " main.py", 0, False

' 2. Wait 2 seconds and launch Tunnel silently
WScript.Sleep 2000

' Try Cloudflare tunnel if available
If fso.FileExists("C:\Program Files (x86)\cloudflared\cloudflared.exe") Then
    WshShell.Run """C:\Program Files (x86)\cloudflared\cloudflared.exe"" tunnel --url http://127.0.0.1:8080", 0, False
End If

' Also launch Ngrok tunnel if ngrok is configured
WshShell.Run "cmd /c ngrok http --domain=" & domain & " 8080", 0, False

Set WshShell = Nothing
Set fso = Nothing
