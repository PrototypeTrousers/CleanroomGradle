package com.cleanroommc.gradle.test.vanilla

import com.cleanroommc.gradle.TestFoundation
import com.cleanroommc.gradle.task.ManifestTasks
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

import java.nio.file.Path

@Disabled
class GatherAndReadManifestTest extends TestFoundation {

    @Override
    void appendBuildScript(Path buildFile) {
        buildFile <<
                """
                task gatherAndReadManifestTest {
                    dependsOn '${ManifestTasks.PREPARE_NEEDED_MANIFESTS}'
                    doLast {
                        def file = project.tasks.getByName('${ManifestTasks.GATHER_MANIFEST}').getDest()
                        assert file.exists() : "Manifest does not exist: \${file}"
                        def output = project.tasks.getByName('${ManifestTasks.PREPARE_NEEDED_MANIFESTS}').output
                        assert output != null : "Manifest json not parsed"
                    }
                }
                """
    }

    @Test
    void test() {
        createTest(gradleRunner -> {
            gradleRunner.setTaskName('gatherAndReadManifestTest')
            gradleRunner.configureTests(tests -> {
                tests.isSuccessful()
            })
        })
    }

}
