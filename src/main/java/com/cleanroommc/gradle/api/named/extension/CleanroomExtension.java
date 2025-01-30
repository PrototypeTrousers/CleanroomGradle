package com.cleanroommc.gradle.api.named.extension;

import com.cleanroommc.gradle.api.named.Configurations;
import com.cleanroommc.gradle.api.named.SourceSets;
import com.cleanroommc.gradle.api.named.dependency.DeobfuscatingTransformer;
import com.cleanroommc.gradle.api.named.dependency.Dependencies;
import com.cleanroommc.gradle.api.os.Platform;
import com.cleanroommc.gradle.env.cleanroom.CleanroomTasks;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.artifacts.ArtifactAttributes;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Provider;

import static org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE;

public abstract class CleanroomExtension implements ExtensionAware {

    public static final String EXT_NAME = "cleanroom";
    private final Project project;

    private NamedDomainObjectProvider<Configuration> config;

    public CleanroomExtension(Project project) {
        this.project = project;
        this.config = Configurations.of(project, EXT_NAME);

        Provider<RegularFile> deobfuscatorJar = project.getLayout().file(
                project.getConfigurations().detachedConfiguration(
                        project.getDependencies().create("net.md-5:SpecialSource:1.11.3")
                ).getElements().map(files -> files.iterator().next().getAsFile())
        );

        project.getConfigurations().named("cleanroom", config -> {
            config.getAttributes().attribute(ARTIFACT_TYPE_ATTRIBUTE, "deobfuscablejar");
        });

        project.getDependencies().registerTransform(DeobfuscatingTransformer.class, parametersTransformSpec -> {
            parametersTransformSpec.getFrom().attribute(ARTIFACT_TYPE_ATTRIBUTE, "deobfuscablejar");
            parametersTransformSpec.getTo().attribute(ARTIFACT_TYPE_ATTRIBUTE, "jar");
            parametersTransformSpec.parameters(param -> {
                param.getTransformerTool().set(deobfuscatorJar);
            });
        });
    }

    public void implementation(String notation) {
        Dependencies.add(project, config, notation);
    }

    public void compileOnly(String notation) {
        implementation(notation);
    } //compileOnly

    public NamedDomainObjectProvider<Configuration> config() {
        return config;
    }
}
