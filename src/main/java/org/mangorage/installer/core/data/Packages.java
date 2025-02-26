package org.mangorage.installer.core.data;

import java.util.List;

public record Packages(
        String destination,
        List<Dependency> packages
) {}
