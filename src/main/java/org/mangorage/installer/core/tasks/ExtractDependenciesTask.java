package org.mangorage.installer.core.tasks;

import org.mangorage.installer.core.LogUtil;
import org.mangorage.installer.core.data.Dependencies;
import org.mangorage.installer.core.data.Dependency;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import static org.mangorage.installer.Installer.GSON;

public final class ExtractDependenciesTask {
    private static final String DEPENDENCIES_PATH = "installer-data/dependencies.json";

    public static List<Dependency> extractDependencies(List<File> jars) {
        LogUtil.println("Extracting dependencies from JARs");
        List<Dependency> dependencies = new ArrayList<>();

        for (File jar : jars) {
            try (var jarFile = new JarFile(jar)) {
                ZipEntry entry = jarFile.getEntry(DEPENDENCIES_PATH);
                if (entry != null) {
                    try (var reader = new InputStreamReader(jarFile.getInputStream(entry))) {
                        List<Dependency> extracted = GSON.fromJson(reader, Dependencies.class).dependencies();
                        dependencies.addAll(extracted);
                        extracted.forEach(dep -> LogUtil.println("Found dependency: " + dep));
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Error processing jar: " + jar.getName(), e);
            }
        }
        return dependencies;
    }
}
