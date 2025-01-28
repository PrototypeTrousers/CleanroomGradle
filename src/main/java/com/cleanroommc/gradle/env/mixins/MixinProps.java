package com.cleanroommc.gradle.env.mixins;

import com.cleanroommc.gradle.env.mcp.task.Obfuscate;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.util.List;

public class MixinProps {
    Project project;
    private final Property<String> mixinRefMap;
    /** The source set to enable mixin processing on, defaults to main. */
    public final Property<SourceSet> mixinSourceSet;

    public MixinProps(Project project) {
        this.project = project;
        this.mixinSourceSet = project.getObjects().property(SourceSet.class);
        final SourceSetContainer sourceSets = project.getExtensions().getByType(JavaPluginExtension.class)
                .getSourceSets();
        this.mixinSourceSet.convention(sourceSets.named("main"));

        final ObjectFactory objects = project.getObjects();

        this.mixinRefMap = objects.property(String.class);

        project.afterEvaluate(_p -> {
            if (this.mixinRefMap.isPresent()) {
                File tempMixinDir = FileUtils
                        .getFile(project.getLayout().getBuildDirectory().get().getAsFile(), "tmp", "mixins");
                File mixinSrg = new File(tempMixinDir, "mixins.srg");
                File mixinRefMapFile = new File(tempMixinDir, this.mixinRefMap.get());
                TaskProvider<Obfuscate> reobfJarTask = project.getTasks()
                        .named("obfuscate", Obfuscate.class);
                reobfJarTask.configure(task -> task.getExtraSrgFiles().from(mixinSrg));
                final SourceSet mixinSourceSet = this.mixinSourceSet.get();
                project.getTasks().named(mixinSourceSet.getCompileJavaTaskName(), JavaCompile.class).configure(task -> {
                    task.doFirst("createTempMixinDirectory", _t -> tempMixinDir.mkdirs());
                    ListProperty<String> reobfSrgFile = project.getObjects().listProperty(String.class);
                    reobfSrgFile.add(
                            reobfJarTask.map(Obfuscate::getDeobfuscatedJar).map(RegularFileProperty::get)
                                    .map(RegularFile::getAsFile).map(f -> "-AreobfSrgFile=" + f));
                    task.getOptions().getCompilerArgumentProviders().add(reobfSrgFile::get);
                    List<String> compilerArgs = task.getOptions().getCompilerArgs();
                    compilerArgs.add("-AoutSrgFile=" + mixinSrg);
                    compilerArgs.add("-AoutRefMapFile=" + mixinRefMapFile);
                });
                // Keep as class instead of lambda to ensure it works even if the plugin is not loaded into the
                // classpath
                // noinspection rawtypes
//                project.getPlugins().withId("org.jetbrains.kotlin.kapt", new Action<Plugin>() {
//
//                    @Override
//                    public void execute(Plugin rawPlugin) {
//                        KaptExtension kapt = project.getExtensions().getByType(KaptExtension.class);
//                        kapt.setCorrectErrorTypes(true);
//                        kapt.javacOptions(jco -> {
//                            jco.option("-AoutSrgFile=" + mixinSrg);
//                            jco.option("-AoutRefMapFile=" + mixinRefMapFile);
//                            // This is lazily evaluated by the kapt plugin
//                            Provider<String> reobfSrg = reobfJarTask.map(ReobfuscatedJar::getSrg)
//                                    .map(RegularFileProperty::get).map(RegularFile::getAsFile)
//                                    .map(f -> "-AreobfSrgFile=" + f);
//                            if (reobfSrg.isPresent()) {
//                                jco.option(reobfSrg.get());
//                            }
//                            return Unit.INSTANCE;
//                        });
//                        project.getTasks().withType(KaptTask.class).configureEach(
//                                task -> { task.doFirst("createTempMixinDirectory", _t -> tempMixinDir.mkdirs()); });
//                    }
//                });
//                project.getTasks().named(mixinSourceSet.getProcessResourcesTaskName(), ProcessResources.class)
//                        .configure(task -> {
//                            task.from(mixinRefMapFile);
//                            final String compileJava = mixinSourceSet.getCompileJavaTaskName();
//                            task.dependsOn(compileJava);
//                            final String compileScala = StringUtils.removeEnd(compileJava, "Java") + "Scala";
//                            final String compileKotlin = StringUtils.removeEnd(compileJava, "Java") + "Kotlin";
//                            project.getPlugins().withType(ScalaPlugin.class, scp -> { task.dependsOn(compileScala); });
//                            project.getPlugins()
//                                    .withId("org.jetbrains.kotlin.jvm", p -> { task.dependsOn(compileKotlin); });
//                        });
            }
        });
    }

    public Object enableMixins(Object mixinSpec, String refMapName) {
        mixinRefMap.set(refMapName);
        return mixinSpec;
    }

    public Object enableMixins(Object mixinSpec) {
        mixinRefMap.set(
                project.getExtensions().getByType(BasePluginExtension.class).getArchivesName()
                        .map(name -> String.format("mixins.%s.refmap.json", name)));
        return mixinSpec;
    }
}
