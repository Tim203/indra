/*
 * This file is part of indra, licensed under the MIT License.
 *
 * Copyright (c) 2020-2021 KyoriPowered
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.kyori.indra;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import net.kyori.indra.test.FunctionalTestDisplayNameGenerator;
import net.kyori.indra.test.IndraConfigCacheFunctionalTest;
import net.kyori.mammoth.test.TestContext;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.DisplayNameGeneration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayNameGeneration(FunctionalTestDisplayNameGenerator.class)
class IndraPluginFunctionalTest {

  @IndraConfigCacheFunctionalTest
  void testSimpleBuild(final TestContext ctx) throws IOException {
    ctx.copyInput("build.gradle");
    ctx.copyInput("settings.gradle");

    ctx.build("build"); // run build

    assertTrue(Files.exists(ctx.outputDirectory().resolve("build/libs/simplebuild-1.0.0-SNAPSHOT.jar")));

    // todo: check version of classfiles
    // todo: add a source file, resource, etc with utf-8 characters and confirm they compile properly
  }

  @IndraConfigCacheFunctionalTest
  void testKotlinBuildscript(final TestContext ctx) throws IOException {
    ctx.copyInput("build.gradle.kts");
    ctx.copyInput("settings.gradle.kts");

    ctx.build("build"); // run build

    assertTrue(Files.exists(ctx.outputDirectory().resolve("build/libs/kotlinbuildscript-1.0.0-SNAPSHOT.jar")));
  }

  @IndraConfigCacheFunctionalTest
  void testMultiprojectModular(final TestContext ctx) throws IOException {
    ctx.copyInput("settings.gradle");

    ctx.copyInput("subprojects/core/build.gradle");
    ctx.copyInput("subprojects/core/j8/testproject/core/InformationProvider.java", "subprojects/core/src/main/java/testproject/core/InformationProvider.java");
    ctx.copyInput("subprojects/core/j9/module-info.java", "subprojects/core/src/main/java9/module-info.java");

    ctx.copyInput("subprojects/module-consumer/build.gradle");
    ctx.copyInput("subprojects/module-consumer/ModuleConsumer.java", "subrojects/module-consumer/src/main/java/testproject/consumer/ModuleConsumer.java");
    ctx.copyInput("subprojects/module-consumer/module-info.java", "subrojects/module-consumer/src/main/java/module-info.java");

    ctx.copyInput("subprojects/multirelease-module-consumer/build.gradle");
    ctx.copyInput("subprojects/multirelease-module-consumer/j8/Main.java", "subprojects/multirelease-module-consumer/src/main/java/testproject/consumer/multirelease/Main.java");
    ctx.copyInput("subprojects/multirelease-module-consumer/j9/module-info.java", "subprojects/multirelease-module-consumer/src/main/java9/module-info.java");
    ctx.copyInput("subprojects/multirelease-module-consumer/j11/Main.java", "subprojects/multirelease-module-consumer/src/main/java11/testproject/consumer/multirelease/Main.java");

    ctx.copyInput("subprojects/non-modular-consumer/build.gradle");
    ctx.copyInput("subprojects/non-modular-consumer/Main.java", "subprojects/non-modular-consumer/src/main/java/testproject/consumer/nonmodular/Main.java");

    // The goal here is to test that the module paths are set up appropriately within the projects. We already validate multirelease jar building in another test.
    assertDoesNotThrow(() -> ctx.build("build"));
  }

  @IndraConfigCacheFunctionalTest
  void testMultirelease(final TestContext ctx) throws IOException {
    ctx.copyInput("build.gradle");
    ctx.copyInput("settings.gradle");

    ctx.copyInput("j8/pkg/Actor.java", "src/main/java/pkg/Actor.java");
    ctx.copyInput("j8/pkg/Main.java", "src/main/java/pkg/Main.java");
    ctx.copyInput("j9/pkg/Actor.java", "src/main/java9/pkg/Actor.java");
    ctx.copyInput("j17/pkg/Actor.java", "src/main/java17/pkg/Actor.java");

    final BuildResult result = ctx.build("jar");

    // First: the tasks ran
    Stream.of("compileJava", "compileJava9Java", "compileJava17Java").forEach(name -> {
      assertEquals(TaskOutcome.SUCCESS, result.task(":" + name).getOutcome());
    });

    // Second: The output jar exists and has the appropriate variants

    final Path jar = ctx.outputDirectory().resolve("build/libs/multirelease-1.0.0-SNAPSHOT.jar");
    assertTrue(Files.exists(jar));

    try (final ZipFile jf = new ZipFile(jar.toFile())) {
      try (final InputStream is = jf.getInputStream(jf.getEntry("META-INF/MANIFEST.MF"))) {
        final Manifest manifest = new Manifest(is);
        assertEquals("true", manifest.getMainAttributes().getValue("Multi-Release"), "Jar does not have Multi-Release attribute");
      }

      Stream.of("pkg/Actor.class", "META-INF/versions/9/pkg/Actor.class", "META-INF/versions/17/pkg/Actor.class").forEach(file -> {
        assertNotNull(jf.getEntry(file), () -> file + " was not found in the built archive");
      });
    }

    // TODO: test that multirelease tests work
  }
}