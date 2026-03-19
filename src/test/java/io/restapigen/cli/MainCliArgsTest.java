package io.restapigen.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MainCliArgsTest {
    @Test
    void parsesGenerateZipCommandStyle() {
        Main.CliArgs args = Main.CliArgs.parse(new String[]{
                "generate-zip",
                "--file", "examples/prompts/ecommerce.txt",
                "--out", "out/custom.zip"
        });

        assertEquals(Main.CliMode.GENERATE_ZIP, args.mode);
        assertEquals(Path.of("examples/prompts/ecommerce.txt"), args.inputFile);
        assertEquals(Path.of("out/custom.zip"), args.outputZipPath);
    }

    @Test
    void parsesGenerateZipLegacyFlagStyle() {
        Main.CliArgs args = Main.CliArgs.parse(new String[]{
                "--generate-zip",
                "--input", "examples/prompts/blog.txt"
        });

        assertEquals(Main.CliMode.GENERATE_ZIP, args.mode);
        assertEquals(Path.of("examples/prompts/blog.txt"), args.inputFile);
        assertNull(args.outputZipPath);
    }
}
