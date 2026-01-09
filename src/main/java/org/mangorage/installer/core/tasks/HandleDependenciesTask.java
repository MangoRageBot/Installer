package org.mangorage.installer.core.tasks;

import org.mangorage.installer.core.LogUtil;
import org.mangorage.installer.core.data.Dependency;
import org.mangorage.installer.core.data.Maven;
import org.mangorage.installer.core.data.Package;
import org.mangorage.installer.core.data.Util;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class HandleDependenciesTask {
    private static final Path LIBRARIES_PATH = Path.of("libraries/").toAbsolutePath();

    public static void handleDependencies(List<Dependency> dependencies) {
        LogUtil.println("Handling dependencies...");
        LogUtil.println("Skipping dependencies already present...");

        for (Dependency dependency : dependencies) {
            dependency.download(LIBRARIES_PATH);
        }
    }

    public static File handlePackage(Package dependency, Map<String, String> installedVersions, Map<String, String> newVersions, String destination) {
        Maven maven = new Maven(dependency.url(), dependency.group(), dependency.artifact());
        String latestVersion = FetchPackagesTask.fetchLatestVersion(maven, dependency.version());
        newVersions.put(dependency.target(), latestVersion);

        if (latestVersion.equals(installedVersions.get(dependency.target()))) {
            return new File(destination, dependency.target());
        }

        LogUtil.println("Installing/Updating " + dependency.target());


        return Util.downloadTo(maven, latestVersion, destination + "/" + dependency.target());
    }

    private static List<File> getExistingLibraryJars() {
        return Optional.ofNullable(LIBRARIES_PATH.toFile().listFiles(file -> file.getName().endsWith(".jar")))
                .map(Arrays::asList).orElse(List.of());
    }
}
