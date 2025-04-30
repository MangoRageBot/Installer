package org.mangorage.installer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.util.PathConverter;
import org.mangorage.installer.core.UpdateChecker;
import org.mangorage.installer.core.data.*;
import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Installer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ExecutorService TASKS = Executors.newSingleThreadExecutor();

    private static final String DEPENDENCIES_PATH = "installer-data/dependencies.json";
    private static final String SERVICE_PATH = "installer-data/services.launch";
    private static final Path LIBRARIES_PATH = Path.of("libraries/").toAbsolutePath();

    public static void main(String[] args) {
        org.mangorage.installer.core.LogUtil.println("Starting Installer...");
        org.mangorage.installer.core.LogUtil.println("Arguments Supplied: " + Arrays.toString(args));

        OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();

        final OptionSpec<Void> launchArg = parser
                .accepts("launch", "Whether or not to launch the program that will be installed/updated");

        final OptionSpec<Path> manualJar = parser
                .accepts("manualJar", "Provide a path to the jar")
                .withRequiredArg()
                .withValuesSeparatedBy(";")
                .withValuesConvertedBy(new PathConverter());

        final OptionSpec<Integer> checkUpdates = parser
                .accepts("checkUpdates", "Automatically check for updates for packages which want to be checked")
                .withRequiredArg()
                .ofType(Integer.TYPE);

        var options = parser.parse(args);

        List<File> jars = options.has("manualJar") ? getManualJars(options, manualJar) : processPackages(
                options.has(checkUpdates) && options.has("launch"),
                options.has(checkUpdates) ? options.valueOf(checkUpdates) : 0
        );

        if (jars.isEmpty()) {
            throw new IllegalStateException("No JARs found to process!");
        }

        List<Dependency> dependencies = extractDependencies(jars);
        handleDependencies(dependencies);

        if (options.has("launch")) {
            org.mangorage.installer.core.LogUtil.println("Finished running installer...");
            launchJar(jars, args);
        } else {
            org.mangorage.installer.core.LogUtil.println("Finished running installer...");
            System.exit(0);
        }
    }

    private static List<File> getManualJars(OptionSet options, OptionSpec<Path> manualJarSpec) {
        return manualJarSpec.values(options)
                .stream()
                .map(Path::toFile)
                .toList();
    }

    private static List<File> processPackages(final boolean checkUpdates, final int updateFreq) {
        org.mangorage.installer.core.LogUtil.println("Processing installer/packages.json");
        File file = new File("installer/packages.json");
        if (!file.exists()) throw new IllegalStateException("packages.json not found!");

        Map<String, String> installedVersions = readInstalledVersions();
        List<File> results = new ArrayList<>();

        try (var reader = new FileReader(file)) {
            Packages packages = GSON.fromJson(reader, Packages.class);
            if (checkUpdates) UpdateChecker.startChecker(packages, updateFreq);
            Map<String, String> newVersions = new HashMap<>();

            for (Dependency dependency : packages.packages()) {
                File jar = handleDependency(dependency, installedVersions, newVersions, packages.destination());
                if (jar != null) results.add(jar);
            }

            updateInstalledVersions(newVersions);
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

    private static void updateInstalledVersions(Map<String, String> newVersions) {
        try (var writer = new FileWriter("installer/installed.json")) {
            GSON.toJson(new Installed(newVersions.entrySet()
                    .stream().map(e -> new InstalledPackage(e.getKey(), e.getValue())).toList()), writer);
        } catch (IOException e) {
            throw new RuntimeException("Error updating installed.json", e);
        }
    }

    private static File handleDependency(Dependency dependency, Map<String, String> installedVersions, Map<String, String> newVersions, String destination) {
        Maven maven = new Maven(dependency.url(), dependency.group(), dependency.artifact());
        String latestVersion = fetchLatestVersion(maven, dependency.version());
        newVersions.put(dependency.target(), latestVersion);

        if (latestVersion.equals(installedVersions.get(dependency.target()))) {
            return new File(destination, dependency.target());
        }

        org.mangorage.installer.core.LogUtil.println("Installing/updating " + dependency.target());
        return Util.downloadTo(maven, latestVersion, destination + "/" + dependency.target());
    }

    private static String fetchLatestVersion(Maven maven, String defaultVersion) {
        Future<String> future = TASKS.submit(() -> Util.downloadMetadata(maven));
        try {
            return Util.parseLatestVersion(future.get(10, TimeUnit.SECONDS), defaultVersion);
        } catch (Exception e) {
            org.mangorage.installer.core.LogUtil.println("Failed to get metadata, using default version: " + defaultVersion);
            return defaultVersion;
        }
    }

    private static List<Dependency> extractDependencies(List<File> jars) {
        org.mangorage.installer.core.LogUtil.println("Extracting dependencies from JARs");
        List<Dependency> dependencies = new ArrayList<>();

        for (File jar : jars) {
            try (var jarFile = new JarFile(jar)) {
                ZipEntry entry = jarFile.getEntry(DEPENDENCIES_PATH);
                if (entry != null) {
                    try (var reader = new InputStreamReader(jarFile.getInputStream(entry))) {
                        List<Dependency> extracted = GSON.fromJson(reader, Dependencies.class).dependencies();
                        dependencies.addAll(extracted);
                        extracted.forEach(dep -> org.mangorage.installer.core.LogUtil.println("Found dependency: " + dep));
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Error processing jar: " + jar.getName(), e);
            }
        }
        return dependencies;
    }

    private static void handleDependencies(List<Dependency> dependencies) {
        org.mangorage.installer.core.LogUtil.println("Handling dependencies...");
        org.mangorage.installer.core.LogUtil.println("Skipping dependencies already present...");
        Set<String> installedJars = new HashSet<>();
        List<File> existingJars = getExistingLibraryJars();

        for (Dependency dep : dependencies) {
            installedJars.add(dep.target());
            if (!existingJars.contains(new File(LIBRARIES_PATH.toString(), dep.target()))) {
                Util.installUrl(dep.getDownloadURL(), LIBRARIES_PATH.toString(), true);
            } else {
                org.mangorage.installer.core.LogUtil.println("Skipped -> " + dep.target());
            }
        }

        deleteUnusedDependencies(existingJars, installedJars);
    }

    private static List<File> getExistingLibraryJars() {
        return Optional.ofNullable(LIBRARIES_PATH.toFile().listFiles(file -> file.getName().endsWith(".jar")))
                .map(Arrays::asList).orElse(List.of());
    }

    private static void deleteUnusedDependencies(List<File> existingJars, Set<String> installedJars) {
        existingJars.forEach(file -> {
            if (!installedJars.contains(file.getName())) {
                org.mangorage.installer.core.LogUtil.println("Deleting unused dependency: " + file.getName());
                file.delete();
            }
        });
    }

    public static String findMainClass(List<File> files) {
        for (File file : files) {
            if (!file.getName().endsWith(".jar")) continue;

            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(zis))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (SERVICE_PATH.equals(entry.getName())) {
                        org.mangorage.installer.core.LogUtil.println("Found " + SERVICE_PATH + " in " + file.getName());
                        return reader.lines().collect(Collectors.joining("\n"));
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Error processing JAR file: " + file.getName(), e);
            }
        }
        return "";
    }

    public static void launchJar(List<File> jars, String[] args) {
        org.mangorage.installer.core.LogUtil.println("Attempting to launch....");
        String mainClass = findMainClass(jars).strip();
        if (mainClass.isEmpty()) {
            org.mangorage.installer.core.LogUtil.println("Could not find Valid Launch File from List of Jars...");
            return;
        }

        URL[] urls = jars.stream()
                .map(File::toURI)
                .map(uri -> {
                    try {
                        return uri.toURL();
                    } catch (MalformedURLException e) {
                        throw new RuntimeException("Invalid JAR file URL: " + uri, e);
                    }
                })
                .toArray(URL[]::new);

        try (URLClassLoader cl = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader())) {
            Thread.currentThread().setContextClassLoader(cl);
            Class<?> clazz = Class.forName(mainClass, true, cl);
            Method method = clazz.getDeclaredMethod("main", String[].class);
            method.invoke(null, (Object) args);
        } catch (IOException | ReflectiveOperationException e) {
            throw new RuntimeException("Failed to launch the application", e);
        } finally {
            Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
        }
    }

}
