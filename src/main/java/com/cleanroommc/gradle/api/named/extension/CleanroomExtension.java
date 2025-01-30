package com.cleanroommc.gradle.api.named.extension;

import com.cleanroommc.gradle.api.named.Configurations;
import com.cleanroommc.gradle.api.named.attribute.ObfuscationAttribute;
import com.cleanroommc.gradle.api.named.dependency.Dependencies;
import com.cleanroommc.gradle.api.named.dependency.DependencyDeobfuscationTransform;
import com.cleanroommc.gradle.env.mcp.MCPTasks;
import com.cleanroommc.gradle.env.mcp.task.GenSrgMappingsTask;
import com.cleanroommc.gradle.utils.Utilities;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFile;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.SetProperty;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

public abstract class CleanroomExtension implements ExtensionAware {

    public static final String EXT_NAME = "cleanroom";
    private final Project project;

    private final ConfigurableFileCollection depFilesToDeobf;
    private final SetProperty<String> depModulesToDeobf;

    private NamedDomainObjectProvider<Configuration> config;

    public static final Attribute<Boolean> DEOBFUSCATOR_TRANSFORMED = Attribute
            .of("rfgDeobfuscatorTransformed", Boolean.class);

    public static class DeobfuscatorTransformerCompatRules implements AttributeCompatibilityRule<Boolean> {

        @Override
        public void execute(CompatibilityCheckDetails<Boolean> details) {
            if (details.getProducerValue() == null) {
                details.compatible();
            } else {
                details.incompatible();
            }
        }
    }

    public CleanroomExtension(Project project, MCPTasks mcpTasks) {
        this.project = project;
        this.config = Configurations.of(project, EXT_NAME);

        final ObjectFactory objects = project.getObjects();
        this.depFilesToDeobf = objects.fileCollection();
        this.depModulesToDeobf = objects.setProperty(String.class);

        project.getDependencies().getAttributesSchema().attribute(DEOBFUSCATOR_TRANSFORMED, ams -> {
            ams.getCompatibilityRules().add(DeobfuscatorTransformerCompatRules.class);
            ams.getDisambiguationRules().pickFirst(Comparator.nullsFirst(Comparator.naturalOrder()));
        });

        // Dependency deobfuscation utilities, see comment on deobfuscate
        project.getDependencies().registerTransform(DependencyDeobfuscationTransform.class, spec -> {
            spec.getFrom().attribute(DEOBFUSCATOR_TRANSFORMED, Boolean.FALSE);
            spec.getTo().attribute(DEOBFUSCATOR_TRANSFORMED, Boolean.TRUE);
            final DependencyDeobfuscationTransform.Parameters params = spec.getParameters();
            params.getFieldsCsv()
                    .set(mcpTasks.genSrgMappings().flatMap(GenSrgMappingsTask::getFieldsCsv));
            params.getMethodsCsv()
                    .set(mcpTasks.genSrgMappings().flatMap(GenSrgMappingsTask::getMethodsCsv));
            params.getFilesToDeobf().from(depFilesToDeobf);
            params.getModulesToDeobf().set(depModulesToDeobf);
        });

        project.afterEvaluate(_p -> {
            project.getDependencies().getArtifactTypes().getByName("jar").getAttributes()
                    .attribute(DEOBFUSCATOR_TRANSFORMED, Boolean.FALSE);
            project.getConfigurations().configureEach(cfg -> {
                // Don't add the deobfuscator-transformed attribute to published variants
                if (cfg.isCanBeConsumed() && !cfg.isCanBeResolved()) {
                    return;
                }
                if (cfg.getName().endsWith("Elements") || cfg.getName().endsWith("ElementsForTest")) {
                    return;
                }
                ObfuscationAttribute requiredObfuscation = cfg.getAttributes()
                        .getAttribute(ObfuscationAttribute.OBFUSCATION_ATTRIBUTE);
                if (requiredObfuscation.getName().equals(ObfuscationAttribute.MCP)) {
                    cfg.getAttributes().attribute(DEOBFUSCATOR_TRANSFORMED, Boolean.TRUE);
                }
            });
        });
    }

    public void implementation(String notation) {
        deobfuscate(notation);
        Dependencies.add(project, config, notation);
    }

    public Object deobfuscate(Object depSpec) {
        if (depSpec instanceof CharSequence) {
            depModulesToDeobf.add(depSpec.toString());
        } else if (depSpec instanceof Map<?, ?> depMap) {
            final String group = Utilities.getMapStringOrBlank(depMap, "group");
            final String module = Utilities.getMapStringOrBlank(depMap, "name");
            final String version = Utilities.getMapStringOrBlank(depMap, "version");
            final String classifier = Utilities.getMapStringOrBlank(depMap, "classifier");
            String gmv = group + ":" + module + ":" + version;
            if (StringUtils.isNotBlank(classifier)) {
                gmv += ":" + classifier;
            }
            depModulesToDeobf.add(gmv);
        } else if (depSpec instanceof File || depSpec instanceof RegularFile
                || depSpec instanceof Path
                || depSpec instanceof URI
                || depSpec instanceof URL
                || depSpec instanceof FileTree
                || depSpec instanceof FileCollection) {
            depFilesToDeobf.from(depSpec);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported dependency type " + depSpec.getClass() + " for RFG deobfuscation");
        }
        return depSpec;
    }

    public void compileOnly(String notation) {
        implementation(notation);
    } //compileOnly

    public NamedDomainObjectProvider<Configuration> config() {
        return config;
    }
}
