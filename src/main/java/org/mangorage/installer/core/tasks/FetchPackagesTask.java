package org.mangorage.installer.core.tasks;

import org.mangorage.installer.core.LogUtil;
import org.mangorage.installer.core.data.Maven;
import org.mangorage.installer.core.data.Util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class FetchPackagesTask {
    private static final ExecutorService TASKS = Executors.newCachedThreadPool();

    public static String fetchLatestVersion(Maven maven, String defaultVersion) {
        Future<String> future = TASKS.submit(() -> Util.downloadMetadata(maven));
        try {
            return Util.parseLatestVersion(future.get(10, TimeUnit.SECONDS), defaultVersion);
        } catch (Exception e) {
            LogUtil.println("Failed to get metadata, using default version: " + defaultVersion);
            return defaultVersion;
        }
    }

}
