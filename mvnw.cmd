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

@echo off
for %%I in ("%~dp0.") do set "MAVEN_PROJECTBASEDIR=%%~fI"
set "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
set WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

if not exist "%WRAPPER_JAR%" (
    setlocal EnableDelayedExpansion
    set "WRAPPER_URL="
    for /f "usebackq tokens=1,* delims==" %%A in ("%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties") do (
        if "%%A"=="wrapperUrl" set "WRAPPER_URL=%%B"
    )
    if not defined WRAPPER_URL (
        echo Error: wrapperUrl is missing from .mvn\wrapper\maven-wrapper.properties. 1>&2
        endlocal
        exit /b 1
    )
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference = 'Stop'; [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object System.Net.WebClient).DownloadFile('!WRAPPER_URL!', '%WRAPPER_JAR%')"
    if errorlevel 1 (
        del "%WRAPPER_JAR%" >nul 2>&1
        echo Error: failed to download the Maven Wrapper JAR from !WRAPPER_URL!. 1>&2
        endlocal
        exit /b 1
    )
    endlocal
)

if not defined JAVA_HOME (
    set JAVA_EXE=java
) else (
    set JAVA_EXE="%JAVA_HOME%\bin\java"
)

%JAVA_EXE% -cp "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" %WRAPPER_LAUNCHER% %*
