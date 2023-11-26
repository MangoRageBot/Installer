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

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;
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
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class Installer {
    public static final Logger LOGGER = Logger.getLogger(Installer.class.getName());
    private static final Package EMPTY = new Package("EXAMPLE_PACKAGE", "", Maven.EMPTY);


    private static Package getPackage() {
        File packageUrl = new File("installer/package.json");
        if (!packageUrl.exists()) {
            System.out.println(packageUrl.getAbsolutePath());
            Util.saveObjectToFile(EMPTY, "installer", "package.json");
            return EMPTY;
        } else {
            return Util.loadJsonToObject(packageUrl, Package.class);
        }
    }

    private static final Package _package = getPackage();
    private static final Version VERSION = Util.getVersion();

    // Where they are located in the JAR
    private static final String IVY_SETTINGS_PATH = "installerdata/ivysettings.xml";
    private static final String DEPS_JSON_PATH = "installerdata/dependencies.json";


    public static void main(String[] args) {
        if (_package == EMPTY) {
            LOGGER.info("Please configure package.json");
            return;
        }

        boolean launch = args.length == 1 && args[0].equalsIgnoreCase("-launch");

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

            ComparableVersion oldVersion = new ComparableVersion(VERSION.version());
            ComparableVersion newVersion = new ComparableVersion(latestVersion);

            if (newVersion.compareTo(oldVersion) > 0) {
                LOGGER.info("Found latest Version: " + newVersion);
                LOGGER.info("Downloading update for %s...".formatted(_package.packageName()));
                downloadNewVersion(MAVEN, latestVersion);
                LOGGER.info("Downloaded update for %s!".formatted(_package.packageName()));
            } else {
                LOGGER.info("No new Version found for %s!".formatted(_package.packageName()));
            }
            if (launch) launchJar(args);
        } else {
            LOGGER.info("Installing %s...".formatted(_package.packageName()));
            downloadNewVersion(MAVEN, latestVersion);
            LOGGER.info("Installed Everything for %s!".formatted(_package.packageName()));
            if (launch) launchJar(args);
        }
    }


    private static void downloadNewVersion(Maven MAVEN, String version) {
        try {
            FileUtils.deleteDirectory(new File("libs"));

            String dest = _package.packageDest().isBlank() ? "libs" : _package.packageDest();
            var jar = Util.downloadTo(MAVEN, version, new File("%s/%s.jar".formatted(dest, _package.packageName())));

            try (JarFile jarFile = new JarFile(jar)) {
                FileUtils.copyInputStreamToFile(
                        jarFile.getInputStream(jarFile.getEntry(IVY_SETTINGS_PATH)),
                        new File("installer/temp/ivysettings.xml")
                );

                FileUtils.copyInputStreamToFile(
                        jarFile.getInputStream(jarFile.getEntry(DEPS_JSON_PATH)),
                        new File("installer/temp/dependencies.json")
                );

                File ivySettings = new File("installer/temp/ivysettings.xml");
                File dependencies = new File("installer/temp/dependencies.json");


                CoreInstaller.install(ivySettings, dependencies);

                FileUtils.deleteDirectory(new File("installer/temp/"));
                Util.saveVersion(version);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void addJars(List<URL> urlList, Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            files
                    .filter(path -> path.toFile().getName().endsWith(".jar"))
                    .forEach(path -> {
                        try {
                            urlList.add(path.toAbsolutePath().toUri().toURL());
                        } catch (MalformedURLException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private static void launchJar(String[] args) {

        String dest = _package.packageDest().isBlank() ? "libs" : _package.packageDest();

        try {
            File jarFile = new File("%s/%s.jar".formatted(dest, _package.packageName()));
            URL jarFileURL = jarFile.toURI().toURL();

            LOGGER.info("Launching %s".formatted(jarFile.getName()));

            List<URL> urls = new ArrayList<>();
            List<Path> directories = List.of(Path.of("libs/"));

            directories.forEach(directory -> {
                try {
                    addJars(urls, directory);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });




            try (JarFile jar = new JarFile("%s/%s.jar".formatted(dest, _package.packageName()))) {
                Manifest manifest = jar.getManifest();
                if (manifest == null) throw new Exception("No manifest found");
                Attributes attributes = manifest.getMainAttributes();
                String mainClass = attributes.getValue("Main-Class");
                if (mainClass == null) throw new Exception("No Main Class found");
                urls.add(jarFileURL);

                try (URLClassLoader classLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]), Installer.class.getClassLoader())) {
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
