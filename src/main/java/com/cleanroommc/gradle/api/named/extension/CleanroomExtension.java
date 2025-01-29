package com.cleanroommc.gradle.api.named.extension;

import com.cleanroommc.gradle.api.named.Configurations;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.ExtensionAware;

public abstract class CleanroomExtension implements ExtensionAware {

    public static final String EXT_NAME = "cleanroom";

    private final NamedDomainObjectProvider<Configuration> dependencies;

    public CleanroomExtension(Project project) {
        this.dependencies = Configurations.of(project, EXT_NAME, true);
    }

    public Configuration getDependencies() {
        return dependencies.get();
    }
}
