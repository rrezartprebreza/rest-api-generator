package io.restapigen.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MainCliArgsTest {

    @Test
    void parsesGenerateZipCommandStyle() {
        CommandLine cl = new CommandLine(new Main());
        CommandLine.ParseResult result = cl.parseArgs(
                "generate-zip",
                "--file", "examples/prompts/ecommerce.txt",
                "--out", "out/custom.zip");

        Main.GenerateZipCommand cmd = (Main.GenerateZipCommand)
                result.subcommand().commandSpec().userObject();

        assertEquals(Path.of("examples/prompts/ecommerce.txt"), cmd.input.inputFile);
        assertEquals(Path.of("out/custom.zip"), cmd.outputZip);
    }

    @Test
    void parsesGenerateZipLegacyFlagStyle() {
        CommandLine cl = new CommandLine(new Main());
        cl.parseArgs("--generate-zip", "--input", "examples/prompts/blog.txt");

        Main main = cl.getCommand();

        assertTrue(main.legacyGenerateZip);
        assertEquals(Path.of("examples/prompts/blog.txt"), main.inputFile);
        assertNull(main.outputZipPath);
    }
}
