package org.mangorage.installer.core.types;

import org.mangorage.installer.core.Dependency;

import java.util.List;

public record Packages(
        String destination,
        List<Dependency> packages
) {}
