/*
 * Copyright (c) 2023. MangoRage
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.mangorage.installer;

import joptsimple.OptionParser;
import joptsimple.OptionSpec;
import joptsimple.OptionSpecBuilder;
import joptsimple.util.PathConverter;
import org.mangorage.installer.api.Maven;
import org.mangorage.installer.api.Package;
import org.mangorage.installer.api.Version;

import java.io.File;
import java.io.IOException;
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
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Logger;

import static org.mangorage.installer.PackageReader.getPackage;
import static org.mangorage.installer.api.Package.EMPTY;

public class Installer {
    public static final Logger LOGGER = Logger.getLogger(Installer.class.getName());

    private static final Package _package = getPackage();
    private static final Version VERSION = Util.getVersion();

    private static final String DEPS_PATH = "installerdata/deps.txt";

    // -launch
    // -manualJar jar
    // -manualJarVersion version

    public static void main(String[] args) {
        System.out.println("Starting Installer...");
        // Parser
        OptionParser parser = new OptionParser();

        // Option Args
        OptionSpec<Void> launchArg = parser
                .accepts("launch", "Wether or not to launch the program that will be installed/updated");
        OptionSpec<Path> manualJar = parser
                .accepts("manualJar", "Provide a path to the jar")
                .withRequiredArg()
                .withValuesConvertedBy(new PathConverter());
        OptionSpec<String> manualVersion = parser
                .accepts("manualJarVersion")
                .requiredIf(manualJar)
                .withRequiredArg();

        // Parsed args[]
        var options = parser.parse(args);
        var launch = options.has(launchArg);


        if (!options.has(manualJar)) {
            if (_package == EMPTY) {
                LOGGER.info("Please configure package.json");
                return;
            }

            Maven MAVEN = _package.maven();
            String metadata = Util.downloadMetadata(MAVEN);

            if (metadata == null) {
                LOGGER.info("Unable to download metadata...");
                return;
            }

            String latestVersion = Util.parseLatestVersion(metadata);
            if (latestVersion == null) {
                LOGGER.info("Unable to parse latest version...");
                return;
            }


            if (VERSION != null) {
                LOGGER.info("Checking for Updates...");

                var oldVersion = VERSION.version();
                File jar = null;
                if (!latestVersion.equals(oldVersion)) {
                    LOGGER.info("Found latest Version: " + latestVersion);
                    LOGGER.info("Downloading update for %s...".formatted(_package.packageName()));
                    jar = downloadNewVersion(MAVEN, latestVersion);
                    LOGGER.info("Downloaded update for %s!".formatted(_package.packageName()));
                } else {
                    LOGGER.info("No new Version found for %s!".formatted(_package.packageName()));
                }
                if (launch && jar != null) launchJar(jar, args);
            } else {
                LOGGER.info("Installing %s...".formatted(_package.packageName()));
                var jar = downloadNewVersion(MAVEN, latestVersion);
                LOGGER.info("Installed Everything for %s!".formatted(_package.packageName()));
                if (launch && jar != null) launchJar(jar, args);
            }
        } else {
            File jarFile = options.valueOf(manualJar).toFile();
            LOGGER.info("Installing libraries for %s".formatted(jarFile.getName()));
            downloadNewVersion(jarFile, options.valueOf(manualVersion));
            if (launch) launchJar(jarFile, args);
        }

    }

    private static String getFileName(String s) {
        String[] parts = s.split("/");
        return parts[parts.length - 1];
    }


    private static File downloadNewVersion(Maven MAVEN, String version) {
        String dest = _package.packageDest().isBlank() ? "libs" : _package.packageDest();
        var jar = Util.downloadTo(MAVEN, version, "%s/%s.jar".formatted(dest, _package.packageName()));
        return downloadNewVersion(jar, version);
    }

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

    private static void launchJar(File jarFile, String[] args) {

        try {
            URL jarFileURL = jarFile.toURI().toURL();

            LOGGER.info("Launching %s".formatted(jarFile.getName()));

            try (JarFile jar = new JarFile(jarFile)) {
                Manifest manifest = jar.getManifest();
                if (manifest == null) throw new Exception("No manifest found");
                Attributes attributes = manifest.getMainAttributes();
                String mainClass = attributes.getValue("Main-Class");
                if (mainClass == null) throw new Exception("No Main Class found");

                try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jarFileURL}, Installer.class.getClassLoader())) {
                    ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
                    Thread.currentThread().setContextClassLoader(classLoader);
                    try {
                        Class<?> clazz = Class.forName(mainClass, false, classLoader);
                        Method method = clazz.getDeclaredMethod("main", String[].class);
                        method.invoke(null, (Object) Arrays.copyOfRange(args, 1, args.length)); // Pass through the args...
                    } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
                             InvocationTargetException exception) {
                        throw new RuntimeException(exception);
                    } finally {
                        System.err.println("Finished");
                        Thread.currentThread().setContextClassLoader(oldCl);
                    }
                }

            } catch (Exception exception) {
                LOGGER.info("Unable to launch Jar. Reason: %s".formatted(exception.getMessage()));
                exception.printStackTrace();
            }
        } catch (MalformedURLException exception) {
            LOGGER.info("Unable to launch Jar. Reason: %s".formatted(exception.getMessage()));
            exception.printStackTrace();
        }
    }
}
