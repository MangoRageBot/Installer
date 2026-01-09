package org.mangorage.installer.core.tasks;

import org.mangorage.installer.core.LogUtil;
import org.mangorage.installer.core.data.Dependency;
import org.mangorage.installer.core.data.Maven;
import org.mangorage.installer.core.data.Util;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class HandleDependenciesTask {
    private static final Path LIBRARIES_PATH = Path.of("libraries/").toAbsolutePath();

    public static void handleDependencies(List<Dependency> dependencies) {
        LogUtil.println("Handling dependencies...");
        LogUtil.println("Skipping dependencies already present...");
        Set<String> installedJars = new HashSet<>();
        List<File> existingJars = getExistingLibraryJars();

        for (Dependency dep : dependencies) {
            installedJars.add(dep.target());
            if (!existingJars.contains(new File(LIBRARIES_PATH.toString(), dep.target()))) {
                Util.installUrl(dep.getDownloadURL(), LIBRARIES_PATH.toString(), true);
            } else {
                LogUtil.println("Skipped -> " + dep.target());
            }
        }
    }

    public static File handleDependency(Dependency dependency, Map<String, String> installedVersions, Map<String, String> newVersions, String destination) {
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
