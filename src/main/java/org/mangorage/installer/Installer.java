package org.mangorage.installer;

import joptsimple.OptionParser;
import joptsimple.OptionSpec;
import joptsimple.util.PathConverter;
import org.mangorage.installer.core.Dependency;
import org.mangorage.installer.core.Maven;
import org.mangorage.installer.core.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.jar.JarFile;

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
    private static final String DEPS_PATH = "installerdata/deps.txt";
    public static void main(String[] args) {
        System.out.println("Starting Installer...");
        // Parser
        OptionParser parser = new OptionParser();

        // Option Args
        OptionSpec<String> launchArg = parser
                .accepts("launch", "Wether or not to launch the program that will be installed/updated")
                .withRequiredArg();
        OptionSpec<Path> manualJar = parser
                .accepts("manualJar", "Provide a path to the jar")
                .withRequiredArg()
                .withValuesSeparatedBy(";")
                .withValuesConvertedBy(new PathConverter());

        // Parsed args[]
        var options = parser.parse(args);
        var launch = options.has(launchArg);
        var mainClass = options.valueOf(launchArg);
        List<File> jars;

        if (options.has(manualJar)) {
            jars = options.valuesOf(manualJar).stream().map(Path::toFile).toList();
        } else {
            jars = processPackages();
        }

        if (jars.isEmpty()) {
            throw new IllegalStateException("Packages.txt was blank!");
        }

        var dependencies = processJars(jars);
        processDependencies(dependencies);

        // Launch Jar
        if (launch) launchJar(jars, mainClass, args);
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
                if (data.length == 5) {
                    // mangobot.jar;plugins/;https://s01.oss.sonatype.org/content/repositories/releases;io.github.realmangorage;mangobot
                    String jarName = data[0];
                    String jarDest = data[1];
                    String repo = data[2];
                    String groupID = data[3];
                    String artifact = data[4];

                    Maven maven = new Maven(
                            repo,
                            groupID,
                            artifact
                    );

                    String metadata = Util.downloadMetadata(maven);
                    String latestVersion = Util.parseLatestVersion(metadata);

                    newVersions.put(jarName, latestVersion);
                    if (latestVersion.equals(versions.get(jarName))) {
                        File jar = Path.of("%s/%s".formatted(jarDest, jarName)).toAbsolutePath().toFile();
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

        System.out.println("Processing dependencies for jars");
        var libs = Path.of("libs/").toAbsolutePath();
        dependencies.forEach(dep -> {
            System.out.println("Downloading dependency %s to %s".formatted(dep.jarFileName(), libs));
            Util.installUrl(dep.getDownloadURL(), libs.toString(), true);
        });
        System.out.println("Finished processing dependencies");
    }

    public static String findMainClass(String jarFile, List<File> files) {

        for (File file : files) {
            if (file.getName().equals(jarFile)) {
                try (var jf = new JarFile(file)) {
                    return jf.getManifest().getMainAttributes().getValue("Main-Class");
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        return "";
    }

    public static void launchJar(List<File> jars, String jarFile,  String[] args) {
        String mainClass = findMainClass(jarFile, jars);

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
    /*
    Old code for reference
        private static File downloadNewVersion(File jar, String version) {
        try {
            try (JarFile jarFile = new JarFile(jar)) {
                var jarsToDownload = new ArrayList<>(Util.readLinesFromInputStream(jarFile.getInputStream(jarFile.getEntry(DEPS_PATH))));
                var path = Path.of("libs/").toAbsolutePath();
                if (Files.exists(path)) {
                    var oldJars = new HashMap<String, Path>();
                    var newJars = new HashMap<String, String>();

                    jarsToDownload.forEach(a -> {
                        newJars.put(getFileName(a), a);
                    });

                    Files.walk(path).forEach(a -> {
                        if (a.toFile().isFile()) {
                            oldJars.put(a.getFileName().toString(), a);
                        }
                    });

                    oldJars.forEach((a, b) -> {
                        if (!newJars.containsKey(a)) {
                            try {
                                System.out.println("Deleting unused jar %s".formatted(a));
                                Files.delete(b);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });


                    newJars.forEach((a, b) -> {
                        if (oldJars.containsKey(a)) {
                            System.out.printf("Keeping Jar %s. Skipping Install%n", a);
                            jarsToDownload.remove(b);
                        }
                    });

                    jarsToDownload.forEach(a -> {
                        Util.installUrl(a, "libs/", true);
                    });

                } else {
                    jarsToDownload.forEach(a -> {
                        Util.installUrl(a, "libs/", true);
                    });
                }
                Util.saveVersion(version);
                return jar;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
     */

}
