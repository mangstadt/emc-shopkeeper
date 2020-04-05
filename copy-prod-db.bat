set DEVDIR=dev-profiles\default
rmdir %DEVDIR% /S /Q
robocopy %USERPROFILE%\.emc-shopkeeper\default %DEVDIR% /E /XD cache db-backups
powershell -Command "(gc %DEVDIR%\settings.properties) -replace 'backup.enabled=true', 'backup.enabled=false' | Out-File -encoding ASCII %DEVDIR%\settings.properties"
