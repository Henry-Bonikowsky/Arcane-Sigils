@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------
@REM
@REM   Copyright (c) 2017 Apache Software Foundation
@REM
@REM   Licensed under the Apache License, Version 2.0 (the "License");
@REM   you may not use this file except in compliance with the License.
@REM   You may obtain a copy of the License at
@REM
@REM       http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM   Unless required by applicable law or agreed to in writing, software
@REM   distributed under the License is distributed on an "AS IS" BASIS,
@REM   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM   See the License for the specific language governing permissions and
@REM   limitations under the License.
@REM

@if "%DEBUG%" == "" @echo off
@REM enable echoing by setting the variable

if "%OS%"=="Windows_NT" setlocal

set DIR=%~dp0
if not "%MAVEN_BASEDIR%"=="" goto basedir
set MAVEN_BASEDIR=%DIR:~0,-1%

:basedir
if exist "%MAVEN_BASEDIR%\.mvn\jvm.config" (
  for /f "delims=" %%a in ('type "%MAVEN_BASEDIR%\.mvn\jvm.config"') do set JVM_CONFIG_MAVEN_PROPS=!JVM_CONFIG_MAVEN_PROPS! %%a
)

set MAVEN_JAVA_EXE=%JAVA_HOME%\bin\java.exe

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo.
  echo Error: JAVA_HOME environment variable is not set.
  echo.
  goto error
)

if exist "%MAVEN_BASEDIR%\.mvn\wrapper\maven-wrapper.jar" (
    set WRAPPER_JAR="%MAVEN_BASEDIR%\.mvn\wrapper\maven-wrapper.jar"
) else (
    set WRAPPER_JAR=""
)
if "%MVNW_VERBOSE%" == "true" (
  echo Couldn't find "%MAVEN_BASEDIR%\.mvn\wrapper\maven-wrapper.jar", downloading it ...
  echo Downloading from: %WRAPPER_URL%
)

powershell -Command "&{"^
	"$webclient = new-object System.Net.WebClient;"^
	"if (-not ([string]::IsNullOrEmpty('%MVNW_USERNAME%') -and [string]::IsNullOrEmpty('%MVNW_PASSWORD%'))) {"^
	"$webclient.Credentials = new-object System.Net.NetworkCredential('%MVNW_USERNAME%', '%MVNW_PASSWORD%');"^
	"}"^
	"[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $webclient.DownloadFile('%WRAPPER_URL%', '%WRAPPER_JAR%')"^
	"}"
if "%MVNW_VERBOSE%" == "true" (
  echo Finished downloading %WRAPPER_JAR%
)
if not exist "%MAVEN_BASEDIR%\.mvn\wrapper\maven-wrapper.jar" (
    echo.
    echo Error: downloading wrapper failed!
    echo.
    goto error
)

setlocal enabledelayedexpansion
for /F "usebackq delims=" %%F in ("%MAVEN_BASEDIR%\.mvn\wrapper\maven-wrapper.properties") do (
    if "%%~A"=="wrapperUrl" (
        set WRAPPER_URL=%%~B
    )
    if "%%~A"=="distributionUrl" (
        set DISTRIBUTION_URL=%%~B
    )
)

"%MAVEN_JAVA_EXE%" %JVM_CONFIG_MAVEN_PROPS% !MAVEN_OPTS! !MAVEN_DEBUG_OPTS! -classpath "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%MAVEN_BASEDIR%" org.apache.maven.wrapper.MavenWrapperMain %*
if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

:end
@endlocal & set ERROR_CODE=%ERROR_CODE%

if not "%MVNW_VERBOSE%" == "" @echo on
exit /B %ERROR_CODE%
