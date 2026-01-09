package org.mangorage.installer.core.tasks;

import org.mangorage.installer.core.LogUtil;
import org.mangorage.installer.core.UpdateChecker;
import org.mangorage.installer.core.data.Dependency;
import org.mangorage.installer.core.data.Installed;
import org.mangorage.installer.core.data.InstalledPackage;
import org.mangorage.installer.core.data.Packages;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.mangorage.installer.Installer.GSON;

public final class ProcessPackagesTask {
    public static List<File> processPackages(final boolean checkUpdates, final int updateFreq) {
        LogUtil.println("Processing installer/packages.json");
        File file = new File("installer/packages.json");
        if (!file.exists()) throw new IllegalStateException("packages.json not found!");

        Map<String, String> installedVersions = readInstalledVersions();
        List<File> results = new ArrayList<>();

        try (var reader = new FileReader(file)) {
            Packages packages = GSON.fromJson(reader, Packages.class);
            if (checkUpdates) UpdateChecker.startChecker(packages, updateFreq);
            Map<String, String> newVersions = new HashMap<>();

            for (Dependency dependency : packages.packages()) {
                File jar = HandleDependenciesTask.handleDependency(dependency, installedVersions, newVersions, dependency.getDestination(packages.destination()));
                if (jar != null) results.add(jar);
            }

            UpdateInstalledVersionsTask.updateInstalledVersions(newVersions);
        } catch (IOException e) {
            throw new RuntimeException("Error processing packages.json", e);
        }

        return results;
    }

    private static Map<String, String> readInstalledVersions() {
        File installedFile = new File("installer/installed.json");
        if (!installedFile.exists()) return new HashMap<>();

        try (var reader = new FileReader(installedFile)) {
            return GSON.fromJson(reader, Installed.class).installed()
                    .stream().collect(Collectors.toMap(InstalledPackage::id, InstalledPackage::version));
        } catch (IOException e) {
            throw new RuntimeException("Error reading installed.json", e);
        }
    }
}
