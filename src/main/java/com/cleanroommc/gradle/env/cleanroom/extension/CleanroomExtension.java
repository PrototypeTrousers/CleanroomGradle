package com.cleanroommc.gradle.env.cleanroom.extension;

import com.cleanroommc.gradle.api.named.Configurations;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

public class CleanroomExtension {
    private final NamedDomainObjectProvider<Configuration> dependencies;

    public CleanroomExtension(Project project) {
        this.dependencies = Configurations.of(project, "cleanroom", true);
    }

    public Configuration getDependencies() {
        return dependencies.get();
    }
}
