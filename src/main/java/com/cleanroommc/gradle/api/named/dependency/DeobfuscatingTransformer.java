package com.cleanroommc.gradle.api.named.dependency;

import org.apache.tools.ant.types.resources.FileProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.initialization.transform.services.CacheInstrumentationDataBuildService;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.jvm.toolchain.JavaLauncher;

import java.io.File;

public abstract class DeobfuscatingTransformer implements TransformAction<DeobfuscatingTransformer.Parameters> {

    public interface Parameters extends TransformParameters {
        @InputFile
        RegularFileProperty getTransformerTool();

//        @Internal
//        Provider<JavaLauncher> getJavaLauncher();
//
//        @Internal
//        Provider<Project> getProject();

    }

    @InputArtifact
    public abstract Provider<FileSystemLocation> getInputArtifact();



    @Override
    public void transform(TransformOutputs outputs) {
        File input = getInputArtifact().get().getAsFile();
        File outputFile = outputs.file(input.getName() + ".transformed");
        // Do something to generate output from input


//        //File deobfuscatorJar = getParameters().getTransformerTool();
//
//        JavaLauncher javaLauncher = getParameters().getJavaLauncher().get();
//
//        javaLauncher.getExecutablePath().getAsFile(); // Ensure toolchain is resolved
//
//        getParameters().getProject().get().javaexec(javaExecSpec -> {
//            javaExecSpec.setExecutable(javaLauncher.getExecutablePath().getAsFile());
//            javaExecSpec.args("-jar", deobfuscatorJar.getAbsolutePath(),
//                    "--input", input.getAbsolutePath(),
//                    "--output", outputFile.getAbsolutePath());
//        });


    }
}
