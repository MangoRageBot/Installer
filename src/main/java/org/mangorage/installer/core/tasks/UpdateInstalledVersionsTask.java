package org.mangorage.installer.core.tasks;

import org.mangorage.installer.core.data.Installed;
import org.mangorage.installer.core.data.InstalledPackage;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

import static org.mangorage.installer.Installer.GSON;

public final class UpdateInstalledVersionsTask {
    public static void updateInstalledVersions(Map<String, String> newVersions) {
        try (var writer = new FileWriter("installer/installed.json")) {
            GSON.toJson(new Installed(newVersions.entrySet()
                    .stream().map(e -> new InstalledPackage(e.getKey(), e.getValue())).toList()), writer);
        } catch (IOException e) {
            throw new RuntimeException("Error updating installed.json", e);
        }
    }
}
