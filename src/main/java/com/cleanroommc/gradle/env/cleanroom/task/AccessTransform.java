package com.cleanroommc.gradle.env.cleanroom.task;

import com.cleanroommc.gradle.api.named.task.JarTransformer;
import com.cleanroommc.gradle.api.named.task.type.MavenJarExec;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;

public abstract class AccessTransform extends MavenJarExec implements JarTransformer {
    @InputFile
    public abstract RegularFileProperty getPreAccessTransformedJar();

    @InputFile
    public abstract RegularFileProperty getAccessFile();

    @OutputFile
    public abstract RegularFileProperty getPostAccessTransformedJar();

    public AccessTransform() {
        super("accessTransform", "net.minecraftforge:accesstransformers:8.2.1");
        this.getMainClass().set("net.minecraftforge.accesstransformer.TransformerProcessor");
        this.args("--inJar", this.getPreAccessTransformedJar(),
                "--outJar", this.getPostAccessTransformedJar(),
                "--atFile", this.getAccessFile());
        this.setup(false);
    }

}
