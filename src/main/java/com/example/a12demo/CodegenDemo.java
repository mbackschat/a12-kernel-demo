package com.example.a12demo;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.mgmtp.a12.kernel.md.facade.DocumentModelServiceFactory;
import com.mgmtp.a12.kernel.md.model.api.IDocumentModel;
import com.mgmtp.a12.kernel.md.model.api.services.IDocumentModelService;
import com.mgmtp.a12.kernel.md.model.api.services.IValidationCodeGeneratorConfig;
import com.mgmtp.a12.kernel.md.model.api.services.IValidationCodeGeneratorConfig.JsCodeGenConfig;
import com.mgmtp.a12.kernel.md.model.api.services.IValidationCodeGeneratorConfig.ProgrammingLanguage;
import com.mgmtp.a12.model.notification.RankedNotification;

/**
 * Showcase: the kernel's headline pipeline — <b>compile a model (DSL rules + structure) into standalone
 * validation source code</b>. The same model is emitted as both <b>Java</b> and <b>JavaScript</b>, illustrating
 * "author the rules once as data, run them everywhere" — the structural edge no single-language validator has.
 *
 * <p>Reuses the fresh {@code Playlist} model from {@link IterationDemo}. Here we only <em>generate</em> the code
 * (each call returns a ZIP of sources + a build script) and list what's inside; compiling/running the generated
 * code is a separate build step. (Note the kernel enum is {@code JAVASCRIPT}; the {@code WRAPPED_IN_FUNCTION_EXTENDED}
 * option additionally emits a {@code .d.ts} typings file for the TypeScript target.)
 */
public final class CodegenDemo {

    // Reuses IterationDemo's model, so the generated ZIPs land under that demo's folder.
    static final Path OUT = Path.of("output").resolve(IterationDemo.FOLDER).resolve("generated");

    public static void main(String[] args) throws Exception {
        IDocumentModel model = Demos.toPublicModel(IterationDemo.build());
        IDocumentModelService svc = new DocumentModelServiceFactory().createDocumentModelService();
        Files.createDirectories(OUT);

        generate(svc, model, "validator-java.zip",
                () -> ProgrammingLanguage.JAVA, new Properties());

        generate(svc, model, "validator-js.zip", new IValidationCodeGeneratorConfig() {
            @Override public ProgrammingLanguage getProgrammingLanguage() { return ProgrammingLanguage.JAVASCRIPT; }
            @Override public JsCodeGenConfig getJsCodeGenConfig() { return JsCodeGenConfig.WRAPPED_IN_FUNCTION_EXTENDED; }
        }, null);

        System.out.println("\nGenerated validation code written to: " + OUT.toAbsolutePath());
    }

    private static void generate(IDocumentModelService svc, IDocumentModel model, String zipName,
            IValidationCodeGeneratorConfig cfg, Properties props) throws Exception {
        List<RankedNotification> notes = new ArrayList<>();
        byte[] zip = svc.generateValidationCode(model, cfg, props, notes::add);
        Path target = OUT.resolve(zipName);
        Files.write(target, zip);

        System.out.println("\n=== " + cfg.getProgrammingLanguage() + " -> " + zipName + " (" + zip.length + " bytes) ===");
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e = zis.getNextEntry(); e != null; e = zis.getNextEntry()) {
                if (!e.isDirectory()) {
                    System.out.println("    " + e.getName());
                }
            }
        }
        if (!notes.isEmpty()) {
            System.out.println("    (" + notes.size() + " generator notification(s))");
        }
    }
}
