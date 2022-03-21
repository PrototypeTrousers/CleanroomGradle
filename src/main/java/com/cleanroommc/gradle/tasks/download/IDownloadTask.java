package com.cleanroommc.gradle.tasks.download;

import com.cleanroommc.gradle.Constants;
import groovy.lang.Closure;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public interface IDownloadTask {

    String USER_AGENT = Constants.USER_AGENT;

    @TaskAction
    void downloadAndGet() throws IOException;

    URL getUrl() throws MalformedURLException;

    void setUrl(Closure<String> url);

    File getOutputFile();

    void setOutputFile(Closure<File> outputFile);

    void setToDieWhenError();

    void checkAgainst(Closure<String> hash, String hashFunc, Closure<Long> size);

}
