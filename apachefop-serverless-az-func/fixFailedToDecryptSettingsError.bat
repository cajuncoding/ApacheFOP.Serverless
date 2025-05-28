@echo off
echo ************************************************************************************************************
echo Ensure that the IsEncrypted flag is set to 'false' int he Root of the local.settings.json file!
echo See for more info: https://archicode.be/index.php/2024/05/15/issue-when-debugging-azure-function-locally/
echo ************************************************************************************************************
@echo on
call func settings encrypt
call func settings decrypt
cmd /k