package com.cleanroommc.gradle.api.named.extension;

import com.cleanroommc.gradle.api.named.Configurations;
import com.cleanroommc.gradle.api.named.SourceSets;
import com.cleanroommc.gradle.api.named.dependency.Dependencies;
import com.cleanroommc.gradle.api.os.Platform;
import com.cleanroommc.gradle.env.cleanroom.CleanroomTasks;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.ExtensionAware;

public abstract class CleanroomExtension implements ExtensionAware {

    public static final String EXT_NAME = "cleanroom";
    private final Project project;

    private NamedDomainObjectProvider<Configuration> config;

    public CleanroomExtension(Project project) {
        this.project = project;
        this.config = Configurations.of(project, EXT_NAME);
    }

    public void implementation(String notation) {
        Dependencies.add(project, config, notation);
    }

    public void compileOnly(String notation) { implementation(notation); } //compileOnly

    public NamedDomainObjectProvider<Configuration> config() {
        return config;
    }
}
