package org.mangorage.installer.core;

import org.mangorage.installer.core.data.Dependency;

import java.io.File;
import java.util.List;

public record ProcessedPackage(File file, List<Dependency> dependencies) {
    public ProcessedPackage(File file) {
        this(file, List.of());
    }
}
