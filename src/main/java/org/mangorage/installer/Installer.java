package org.mangorage.installer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.util.PathConverter;
import org.mangorage.installer.core.LogUtil;
import org.mangorage.installer.core.UpdateChecker;
import org.mangorage.installer.core.data.*;
import java.io.*;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

public class Installer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ExecutorService TASKS = Executors.newSingleThreadExecutor();

    private static final String DEPENDENCIES_PATH = "installer-data/dependencies.json";
    private static final Path LIBRARIES_PATH = Path.of("libraries/").toAbsolutePath();

    public static void main(String[] args) throws InterruptedException {
        LogUtil.println("Starting Installer in 30 seconds...");
        LogUtil.println("Arguments Supplied: " + Arrays.toString(args));

        Thread.sleep(30_000); // Sleep for 30 seconds

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
            LogUtil.println("Finished running installer...");
            launchJar(jars, args);
        } else {
            LogUtil.println("Finished running installer...");
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
                File jar = handleDependency(dependency, installedVersions, newVersions, dependency.getDestination(packages.destination()));
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

        LogUtil.println("Installing/updating " + dependency.target());
        return Util.downloadTo(maven, latestVersion, destination + "/" + dependency.target());
    }

    private static String fetchLatestVersion(Maven maven, String defaultVersion) {
        Future<String> future = TASKS.submit(() -> Util.downloadMetadata(maven));
        try {
            return Util.parseLatestVersion(future.get(10, TimeUnit.SECONDS), defaultVersion);
        } catch (Exception e) {
            LogUtil.println("Failed to get metadata, using default version: " + defaultVersion);
            return defaultVersion;
        }
    }

    private static List<Dependency> extractDependencies(List<File> jars) {
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

    private static void handleDependencies(List<Dependency> dependencies) {
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

        deleteUnusedDependencies(existingJars, installedJars);
    }

    private static List<File> getExistingLibraryJars() {
        return Optional.ofNullable(LIBRARIES_PATH.toFile().listFiles(file -> file.getName().endsWith(".jar")))
                .map(Arrays::asList).orElse(List.of());
    }

    private static void deleteUnusedDependencies(List<File> existingJars, Set<String> installedJars) {
        existingJars.forEach(file -> {
            if (!installedJars.contains(file.getName())) {
                LogUtil.println("Deleting unused dependency: " + file.getName());
                file.delete();
            }
        });
    }

    public static String findMainClass(File file) {
        try (JarFile jar = new JarFile(file)) {
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String mainClass = manifest.getMainAttributes().getValue("Main-Class");
                if (mainClass != null) {
                    LogUtil.println("Found Main-Class: " + mainClass);
                    return mainClass;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read JAR manifest from " + file.getName(), e);
        }
        return "";
    }

    public static void launchJar(List<File> jars, String[] args) {
        LogUtil.println("Attempting to launch....");
        File bootJar = new File("boot/boot.jar");
        String mainClass = findMainClass(bootJar);

        if (mainClass.isEmpty()) {
            LogUtil.println("Could not find Valid Launch File from List of Jars...");
            return;
        }

        final var moduleCfg = Configuration.resolve(
                ModuleFinder.of(Path.of("boot")),
                List.of(
                        ModuleLayer.boot().configuration()
                ),
                ModuleFinder.of(),
                Set.of("org.mangorage.bootstrap")
        );

        try {
            final var moduleCl = new URLClassLoader(new URL[]{bootJar.toURI().toURL()}, Thread.currentThread().getContextClassLoader());

            final var moduleLayerController = ModuleLayer.defineModules(moduleCfg, List.of(ModuleLayer.boot()), s -> moduleCl);
            final var moduleLayer = moduleLayerController.layer();

            Thread.currentThread().setContextClassLoader(moduleCl);

            callMain(mainClass, args, moduleLayer.findModule("org.mangorage.bootstrap").get());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public static void callMain(String className, String[] args, Module module) {
        try {
            Class<?> clazz = Class.forName(className, false, module.getClassLoader());
            Method mainMethod = clazz.getMethod("main", String[].class);

            // Make sure it's static and public
            if (!java.lang.reflect.Modifier.isStatic(mainMethod.getModifiers())) {
                throw new IllegalStateException("Main method is not static, are you high?");
            }

            // Invoke the main method with a godawful cast
            mainMethod.invoke(null, (Object) args);
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException("Couldn't reflectively call main because something exploded.", e);
        }
    }

}
