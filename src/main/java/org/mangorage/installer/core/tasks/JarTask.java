package org.mangorage.installer.core.tasks;

import org.mangorage.installer.core.LogUtil;

import java.io.File;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class JarTask {
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
                ModuleFinder.of(
                        Path.of("boot")
                ),
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
