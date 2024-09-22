package org.mangorage.installer;

import joptsimple.OptionParser;
import joptsimple.OptionSpec;
import joptsimple.util.PathConverter;
import org.mangorage.installer.core.Dependency;
import org.mangorage.installer.core.Maven;
import org.mangorage.installer.core.Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Two Things
 *
 * Main:
 *   - Install all plugins/addons
 *   - Go thru their dependencies and get them all into a list
 *   - Filter thru Dependencies to prevent duplicate dependencys
 *   - Install the Dependencies
 *
 *  ManualJar
 *   - Skip the Installing process, they already exist
 *   - Go thru their dependencies and get them all into a list
 *   - Filter thru Dependencies to prevent duplicate dependencys
 *   - Install the Dependencies
 *
 *
 *
 *   Plugin/Addon format (plugins.txt)
 *    - Repo
 *    - GroupID
 *    - Artifact
 *    - fileName
 *    We fetch the latest always
 *
 *
 */
public class Installer {
    private static final ExecutorService TASKS = Executors.newSingleThreadExecutor();
    private static final String DEPS_PATH = "installerdata/deps.txt";
    public static final String SERVICE_PATH = "installerdata/services.launch";

    public static void main(String[] args) {
        System.out.println("Starting Installer...");
        System.out.println("Arguments Supplied: %s".formatted(List.of(args)));
        // Parser
        OptionParser parser = new OptionParser();

        // Option Args
        OptionSpec<Void> launchArg = parser
                .accepts("launch", "Wether or not to launch the program that will be installed/updated");

        OptionSpec<Path> manualJar = parser
                .accepts("manualJar", "Provide a path to the jar")
                .withRequiredArg()
                .withValuesSeparatedBy(";")
                .withValuesConvertedBy(new PathConverter());

        // Parsed args[]
        var options = parser.parse(args);
        var launch = options.has(launchArg);
        List<File> jars;

        if (options.has(manualJar)) {
            jars = options.valuesOf(manualJar).stream().map(Path::toFile).toList();
        } else {
            jars = processPackages();
        }

        if (jars.isEmpty()) {
            throw new IllegalStateException("packages.txt was blank!");
        }

        var dependencies = processJars(jars);
        processDependencies(dependencies);

        // Launch Jar
        if (launch) launchJar(jars, args);
    }

    public static void checkInstalled() {
        // Handle checking the installed packages... to ensure they still exist, otherwise update...
        // TODO: finish
    }


    public static List<File> processPackages() {
        System.out.println("Processing installer/packages.txt");
        File file = new File("installer/packages.txt");
        if (!file.exists())
            throw new IllegalStateException("installer/packages.txt not found!");

        HashMap<String, String> versions = new HashMap<>();
        HashMap<String, String> newVersions = new HashMap<>();

        File installed = new File("installer/installed.txt");
        if (installed.exists()) {
            // Handle
            try (var is = new FileInputStream(installed)) {
                var list = Util.readLinesFromInputStream(is);
                list.forEach(l -> {
                    String[] versionInfo = l.split("=");
                    if (versionInfo.length == 2) {
                        versions.put(versionInfo[0], versionInfo[1]);
                    }
                });
                installed.delete();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }


        ArrayList<File> results = new ArrayList<>();

        try (var is = new FileInputStream(file)) {
            Util.readLinesFromInputStream(is).forEach(line -> {
                String[] data = line.split(";");
                if (data.length == 6) {
                    // 1.0.+;mangobot.jar;plugins/;https://s01.oss.sonatype.org/content/repositories/releases;io.github.realmangorage;mangobot
                    String versionRange = data[0];
                    String jarName = data[1];
                    String jarDest = data[2];
                    String repo = data[3];
                    String groupID = data[4];
                    String artifact = data[5];

                    Maven maven = new Maven(
                            repo,
                            groupID,
                            artifact
                    );

                    Future<String> metadataFuture = TASKS.submit(() -> Util.downloadMetadata(maven));
                    String metadata = null;
                    Path path = Path.of("%s/%s".formatted(jarDest, jarName));
                    try {
                        metadata = metadataFuture.get(10, TimeUnit.SECONDS);
                    } catch (InterruptedException | ExecutionException | TimeoutException e) {
                        System.out.println("Failed to get metadata file. Attempting to move onto next item...");
                        File jar = path.toAbsolutePath().toFile();
                        results.add(jar);
                    } finally {
                        String latestVersion = Util.parseLatestVersion(metadata, versionRange);

                        newVersions.put(jarName, latestVersion);
                        if (latestVersion.equals(versions.get(jarName))) {
                            File jar = path.toAbsolutePath().toFile();
                            results.add(jar);
                            System.out.println("Found no updates for %s".formatted(jarName));
                        } else {
                            System.out.println("Found updates for %s and installing libraries".formatted(jarName));
                            File dest = new File(jarDest);
                            try {
                                Files.createDirectories(dest.toPath());
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }

                            File jar = Util.downloadTo(maven, latestVersion, "%s/%s".formatted(dest.getAbsolutePath(), jarName));
                            results.add(jar);
                        }
                    }
                }
            });

            try (var fileIS = new FileWriter(installed)) {
                newVersions.forEach((name, version) -> {
                    try {
                        fileIS.append("%s=%s".formatted(name, version)).append("\n");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return results;
    }

    public static List<Dependency> processJars(List<File> jars) {
        System.out.println("Processing Jars");
        ArrayList<Dependency> deps = new ArrayList<>();

        jars.forEach(jar -> {
            System.out.println("Processing Jar %s".formatted(jar.getName()));
            try (var jf = new JarFile(jar)) {
                var jarsToDownload = new ArrayList<>(Util.readLinesFromInputStream(jf.getInputStream(jf.getEntry(DEPS_PATH))));
                // Handle jarsToDownload
                jarsToDownload.forEach(dep -> {
                    var depData = dep.split(" ");
                    // String repository, String groupId, String artifactId, String version, String jarFileName
                    if (depData.length == 5) {
                        Dependency dependency = new Dependency(
                                depData[0],
                                depData[1],
                                depData[2],
                                depData[3],
                                depData[4]
                        );

                        System.out.println("Found dependency %s version %s".formatted(dependency.artifactId(), dependency.version()));
                        deps.add(dependency);
                    }
                });
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });

        return deps;
    }

    public static void processDependencies(List<Dependency> dependencies) {
        // TODO: Update to allow for checking old vs new Deps if we need to actually install them!
        // TODO: Due to them possibly already existing...
        // TODO: Remove Unused Jars

        System.out.println("Processing dependencies for jars");
        var libs = Path.of("libs/").toAbsolutePath();

        ArrayList<File> currentJarsFiles = new ArrayList<>();
        ArrayList<String> currentJars = new ArrayList<>();
        ArrayList<String> installedJars = new ArrayList<>();

        var libsFolder = libs.toFile();
        if (libsFolder.exists() && libsFolder.isDirectory()) {
            var files = libsFolder.listFiles();
            if (files != null) {
                currentJarsFiles.addAll(
                        Arrays.stream(files)
                                .filter(file -> file.getName().endsWith(".jar"))
                                .toList()
                );
                currentJars.addAll(currentJarsFiles.stream().map(File::getName).toList());
            }
        }

        dependencies.forEach(dep -> {
            if (currentJars.contains(dep.jarFileName())) {
                System.out.println("Skipping Dependency Install %s, already Exists!".formatted(dep.jarFileName()));
            } else {
                System.out.println("Downloading dependency %s to %s".formatted(dep.jarFileName(), libs));
                Util.installUrl(dep.getDownloadURL(), libs.toString(), true);
            }
            installedJars.add(dep.jarFileName());
        });


        currentJarsFiles.forEach(a -> {
            if (!installedJars.contains(a.getName())) {
                System.out.println("Deleting unused Library %s".formatted(a.getName()));
                a.delete();
            }
        });


        System.out.println("Finished processing dependencies");
    }

    public static String findMainClass(List<File> files) {
        try {
            for (File file : files) {
                if (file.getName().endsWith(".jar")) {
                    try (FileInputStream fis = new FileInputStream(file);
                         ZipInputStream zis = new ZipInputStream(fis)) {

                        ZipEntry entry;
                        while ((entry = zis.getNextEntry()) != null) {
                            if (entry.getName().equals(SERVICE_PATH)) {
                                System.out.println("Found " + SERVICE_PATH + " in " + file.getName());
                                StringBuilder content = new StringBuilder();
                                try (BufferedReader reader = new BufferedReader(new InputStreamReader(zis))) {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        content.append(line).append("\n");
                                    }
                                }
                                return content.toString(); // Return the contents as a String
                            }
                            zis.closeEntry();
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return "";
    }

    public static void launchJar(List<File> jars,  String[] args) {
        String mainClass = findMainClass(jars);
        if (mainClass.isEmpty()) {
            System.out.println("Could not find Valid Launch File from List of Jars...");
            return;
        }

        ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
        URL[] urls = jars.stream().map(jar -> {
            try {
                return jar.toURI().toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }).toList().toArray(new URL[jars.size()]);
        try (var cl = new URLClassLoader(urls, Installer.class.getClassLoader())) {
            Thread.currentThread().setContextClassLoader(cl);
            Class<?> clazz = Class.forName(mainClass, false, cl);
            Method method = clazz.getDeclaredMethod("main", String[].class);
            method.invoke(null, (Object) args); // Pass through the args...
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(oldCl);
        }
    }

}
