package com.cleanroommc.gradle.env.mcp.task;

import com.cleanroommc.gradle.api.named.task.JarTransformer;
import com.cleanroommc.gradle.api.named.task.type.MavenJarExec;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.*;

public abstract class Obfuscate extends MavenJarExec implements JarTransformer {

    @OutputFile
    public abstract RegularFileProperty getObfuscatedJar();

    @InputFile
    public abstract RegularFileProperty getSrgMappingFile();

    @InputFile
    @Optional
    public abstract RegularFileProperty getAccessTransformerFile();

    @InputFile
    public abstract RegularFileProperty getDeobfuscatedJar();

    @Input
    public abstract ListProperty<String> getExtraSrgEntries();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getExtraSrgFiles();

    public Obfuscate() {
        super("obfuscate", "net.md-5:SpecialSource:1.11.3");
        this.getMainClass().set("net.md_5.specialsource.SpecialSource");
        getExtraSrgFiles().forEach(ex -> this.args("--srg-in", ex));
        this.args("--in-jar", getDeobfuscatedJar(),
                "--out-jar", getObfuscatedJar(),
                "--srg-in", getSrgMappingFile(),
                "--kill-source");
        this.setup(false);
    }

    @Override
    protected void beforeExec() {
        if (this.getAccessTransformerFile().isPresent() && this.getAccessTransformerFile().get().getAsFile().exists()) {
            this.args("--access-transformer", this.getAccessTransformerFile());
        }
    }
}
