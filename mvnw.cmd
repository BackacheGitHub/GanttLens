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

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM
@REM Optional ENV vars
@REM   MVNW_REPOURL - repo url base for downloading maven distribution
@REM   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
@REM   MVNW_VERBOSE - true: enable verbose log; others: silence the output
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET __MVNW_CMD__=
@SET __MVNW_ERROR__=
@SET __MVNW_PSMODULEP_SAVE=%PSModulePath%
@SET PSModulePath=
@FOR /F "usebackq tokens=1* delims==" %%A IN ("%~dp0\.mvn\wrapper\maven-wrapper.properties") DO @(
    IF "%%~A"=="wrapperUrl" SET "WRAPPER_URL=%%~B"
    IF "%%~A"=="distributionUrl" SET "MVNW_DISTRIBUTION_URL=%%~B"
)
@IF "%WRAPPER_URL%"=="" SET "WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"
@IF "%MVNW_DISTRIBUTION_URL%"=="" SET "MVNW_DISTRIBUTION_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip"
@IF "%MVNW_VERBOSE%"=="true" (
    echo [INFO] DistributionUrl: %MVNW_DISTRIBUTION_URL%
    echo [INFO] WrapperUrl: %WRAPPER_URL%
)

@SET "MVNW_WrapperJAR=%~dp0\.mvn\wrapper\maven-wrapper.jar"
@SET "MVNW_WrapperLauncher=org.apache.maven.wrapper.MavenWrapperMain"

@REM Extension to allow automatically downloading the maven-wrapper.jar from Maven-central
@REM This allows using the maven wrapper in projects that prohibit checking in binary data.
@IF EXIST "%MVNW_WrapperJAR%" (
    IF "%MVNW_VERBOSE%"=="true" (
        echo [INFO] Found .mvn\wrapper\maven-wrapper.jar
    )
) ELSE (
    IF "%MVNW_VERBOSE%"=="true" (
        echo [INFO] Couldn't find .mvn\wrapper\maven-wrapper.jar, downloading it ...
    )
    powershell -Command "&{"^
		"$webclient = new-object System.Net.WebClient;"^
		"if ([bool]$(Test-Path -Path Env:MVNW_USERNAME)) {"^
			"$webclient.Credentials = New-Object System.Net.NetworkCredential($env:MVNW_USERNAME, $env:MVNW_PASSWORD);"^
		"}"^
		"[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $webclient.DownloadFile('%WRAPPER_URL%', '%MVNW_WrapperJAR%')"^
		"}"
    IF "%MVNW_VERBOSE%"=="true" (
        echo [INFO] Finished downloading .mvn\wrapper\maven-wrapper.jar
    )
)
@REM End of extension

@SET "MAVEN_OPTS=%MAVEN_OPTS%"
@SET "MVNW_CMD_LINE_ARGS=%*"

@REM Find java.exe
@IF DEFINED JAVA_HOME goto findJavaFromJavaHome

@SET JAVA_EXE=java.exe
@%JAVA_EXE% -version >NUL 2>&1
@IF "%ERRORLEVEL%" == "0" goto execute

@IF "%MVNW_VERBOSE%"=="true" (
    echo [ERROR] JAVA_HOME is not set and no 'java' command could be found in your PATH.
    echo.
    echo [ERROR] Please set the JAVA_HOME variable in your environment to match the
    echo [ERROR] location of your Java installation.
)

@GOTO error

:findJavaFromJavaHome
@SET JAVA_HOME=%JAVA_HOME:"=%
@SET JAVA_HOME=%JAVA_HOME:\=/%
@FOR %%I IN ("%JAVA_HOME%/bin/java.exe") DO @SET "JAVA_EXE=%%I"

@IF EXIST "%JAVA_EXE%" goto execute

@IF "%MVNW_VERBOSE%"=="true" (
    echo [ERROR] JAVA_HOME is set to an invalid directory: %JAVA_HOME%
    echo.
    echo [ERROR] Please set the JAVA_HOME variable in your environment to match the
    echo [ERROR] location of your Java installation.
)

@GOTO error

:execute
@REM Setup the command line

@SET "MAVEN_CMD_LINE_ARGS=%*"

@IF "%MVNW_VERBOSE%"=="true" (
    echo [INFO] Executing: "%JAVA_EXE%" %MAVEN_OPTS% %MAVEN_DEBUG_OPTS% -classpath "%MVNW_WrapperJAR%" "-Dmaven.multiModuleProjectDirectory=%~dp0" %MVNW_WrapperLauncher% %MAVEN_CMD_LINE_ARGS%
)

"%JAVA_EXE%" ^
  %MAVEN_OPTS% ^
  %MAVEN_DEBUG_OPTS% ^
  -classpath "%MVNW_WrapperJAR%" ^
  "-Dmaven.multiModuleProjectDirectory=%~dp0" ^
  %MVNW_WrapperLauncher% ^
  %MAVEN_CMD_LINE_ARGS%
@IF ERRORLEVEL 1 goto error
@goto end

:error
@SET ERROR_CODE=1

:end
@endLocal & SET "MAVEN_CMD_LINE_ARGS=%MAVEN_CMD_LINE_ARGS%" & SET "JAVA_EXE=%JAVA_EXE%" & SET ERROR_CODE=%ERROR_CODE%

@IF NOT "%MVNW_VERBOSE%"=="true" goto mvnwFooter
@IF "%MVNW_CMD_LINE_ARGS%"=="" goto mvnwFooter
@IF "%ERROR_CODE%"=="0" echo [INFO] Build success.
@IF NOT "%ERROR_CODE%"=="0" echo [ERROR] Build failed with error code: %ERROR_CODE%

:mvnwFooter
@SET PSModulePath=%__MVNW_PSMODULEP_SAVE%
@IF "%MVNW_VERBOSE%"=="true" (
    echo [INFO] Finished with exit code %ERROR_CODE%
)
@IF "%__MVNW_ARG0_NAME__%"=="mvnw" (goto :eof)
@GOTO :eof
