package org.mangorage.installer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.util.PathConverter;
import org.mangorage.installer.core.LogUtil;
import org.mangorage.installer.core.data.Dependency;
import org.mangorage.installer.core.tasks.ExtractDependenciesTask;
import org.mangorage.installer.core.tasks.HandleDependenciesTask;
import org.mangorage.installer.core.tasks.JarTask;
import org.mangorage.installer.core.tasks.ProcessPackagesTask;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class Installer {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws InterruptedException {
        LogUtil.println("Starting Installer in 30 seconds...");
        LogUtil.println("Arguments Supplied: " + Arrays.toString(args));

        //Thread.sleep(30_000); // Sleep for 30 seconds

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

        List<File> jars = options.has(manualJar) ? getManualJars(options, manualJar) : ProcessPackagesTask.processPackages(
                options.has(checkUpdates) && options.has(launchArg),
                options.has(checkUpdates) ? options.valueOf(checkUpdates) : 0
        );

        if (jars.isEmpty()) {
            throw new IllegalStateException("No JARs found to process!");
        }

        List<Dependency> dependencies = ExtractDependenciesTask.extractDependencies(jars);
        HandleDependenciesTask.handleDependencies(dependencies);

        if (options.has(launchArg)) {
            LogUtil.println("Finished running installer...");
            JarTask.launchJar(jars, args);
        } else {
            LogUtil.println("Finished running installer...");
            System.exit(0);
        }
    }

    static List<File> getManualJars(OptionSet options, OptionSpec<Path> manualJarSpec) {
        return manualJarSpec.values(options)
                .stream()
                .map(Path::toFile)
                .toList();
    }
}
