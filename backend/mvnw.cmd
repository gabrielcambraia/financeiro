@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup batch script
@REM ----------------------------------------------------------------------------
@IF "%__MVNW_ARG0_NAME__%"=="" (SET "MVN_CMD=mvn.cmd") ELSE (SET "MVN_CMD=%__MVNW_ARG0_NAME__%")
@SET MAVEN_PROJECTBASEDIR=%~dp0
@SET WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar"
@SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain
@SET DOWNLOAD_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"

@IF EXIST %WRAPPER_JAR% (
    @SET MVNW_CMD="%JAVA_HOME%\bin\java.exe"
) ELSE (
    @IF NOT "%JAVA_HOME%"=="" (
        @SET MVNW_CMD="%JAVA_HOME%\bin\java.exe"
    ) ELSE (
        @SET MVNW_CMD=java
    )
)

@IF NOT EXIST %WRAPPER_JAR% (
    @ECHO Downloading Maven Wrapper JAR...
    @%MVNW_CMD% -classpath "%MAVEN_PROJECTBASEDIR%.mvn/wrapper" org.apache.maven.wrapper.MavenWrapperMain %* 2>NUL
    @IF ERRORLEVEL 1 (
        @ECHO Falling back to curl download...
        @curl -o .mvn\wrapper\maven-wrapper.jar %DOWNLOAD_URL% 2>NUL
        @IF ERRORLEVEL 1 (
            @ECHO Falling back to PowerShell download...
            @powershell -Command "Invoke-WebRequest -Uri %DOWNLOAD_URL% -OutFile '.mvn\wrapper\maven-wrapper.jar'"
        )
    )
)

@%MVNW_CMD% -classpath "%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" %WRAPPER_LAUNCHER% %*
