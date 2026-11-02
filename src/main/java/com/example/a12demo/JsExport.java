package com.example.a12demo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.example.a12demo.purchaseorder.PurchaseOrderModel;
import com.mgmtp.a12.kernel.md.document.api.services.DocumentDeserializationConfig;
import com.mgmtp.a12.kernel.md.document.apiV2.immutable.DocumentV2;
import com.mgmtp.a12.kernel.md.document.apiV2.services.IDocumentV2Serializer;
import com.mgmtp.a12.kernel.md.facade.DocumentModelServiceFactory;
import com.mgmtp.a12.kernel.md.model.api.IDocumentModel;
import com.mgmtp.a12.kernel.md.model.api.services.IValidationCodeGeneratorConfig;
import com.mgmtp.a12.kernel.md.rt.api.DocumentProcessingConfig;
import com.mgmtp.a12.kernel.md.rt.api.IDocumentRtService;
import com.mgmtp.a12.kernel.md.rt.api.IDocumentValidationResult;
import com.mgmtp.a12.kernel.md.rt.api.IMessage;

/**
 * Prepares the TypeScript parity showcase in {@code ts/}: compiles models that other demos persisted under
 * {@code output/} to plain JavaScript, and records what the Java runtime reports for their sample documents.
 *
 * <p>The TypeScript kernel has no code generator of its own (Developer guide → <i>Generate validation code from
 * document model</i>: "There is no such an API provided by Kernel in TypeScript"), so Java compiles every model
 * the Node side runs. Only {@link IValidationCodeGeneratorConfig.JsCodeGenConfig#STANDARD} output can be loaded
 * by the TypeScript {@code GeneratedCodeAccessorFactory.createScriptAccessor}; {@code CodegenDemo}'s
 * {@code WRAPPED_IN_FUNCTION_EXTENDED} variant (a UMD module plus typings) is rejected there.
 *
 * <p>Writes to {@code output/typescript/generated/}: one {@code <model-id>.js} per model, and
 * {@code java-results.tsv}, the hand-off file {@code ts/src/parity.ts} reads. It is tab-separated, one record per
 * line, so the TypeScript side needs no parser library:
 * <pre>
 * document  &lt;demo&gt;/&lt;file&gt;.json  &lt;model-id&gt;  &lt;noErrorOccurred&gt;
 * message   &lt;severity&gt;  &lt;messageType&gt;  &lt;errorCode&gt;  &lt;field&gt;  &lt;rule path&gt;  &lt;text&gt;
 * compute   &lt;demo&gt;/&lt;input&gt;.json  &lt;model-id&gt;  &lt;demo&gt;/&lt;computed&gt;.json
 * </pre>
 * {@code message} records belong to the preceding {@code document}.
 */
public final class JsExport {

    static final Path GENERATED = Demos.OUT.resolve("typescript").resolve("generated");

    /** A persisted model and the documents to validate against it, all as written by the owning demo. */
    private record Case(String folder, String dmId, List<String> documents) {
    }

    private static final List<Case> CASES = List.of(
            new Case("purchase-order", PurchaseOrderModel.DM_ID, List.of("valid", "invalid", "large")),
            new Case(IterationDemo.FOLDER, IterationDemo.DM_ID, List.of("balanced", "repeated-title", "too-long")),
            new Case(ComputationDemo.FOLDER, ComputationDemo.DM_ID, List.of("input")));

    private JsExport() {
    }

    public static void main(String[] args) throws IOException {
        Files.createDirectories(GENERATED);
        DocumentProcessingConfig cfg = DocumentProcessingConfig.builder(Locale.US).build();
        List<String> records = new ArrayList<>();

        for (Case c : CASES) {
            IDocumentModel model = loadModel(Demos.OUT.resolve(c.folder()).resolve(c.dmId() + ".dm.json"));
            Path js = GENERATED.resolve(c.dmId() + ".js");
            Files.writeString(js, generateJs(model, c.dmId()));
            System.out.println("compiled " + c.folder() + "/" + c.dmId() + ".dm.json -> " + js);

            IDocumentRtService rt = Demos.dynamicRtService(Demos.resolver(model));
            IDocumentV2Serializer serializer = Demos.docSerializer(model);
            for (String name : c.documents()) {
                String file = c.folder() + "/" + name + ".json";
                IDocumentValidationResult result = rt.validateFull(loadDocument(serializer, c.dmId(), file), cfg);
                records.add(record("document", file, c.dmId(), String.valueOf(result.noErrorOccurred())));
                for (IMessage m : result.getMessages()) {
                    records.add(record("message", m.getSeverity().toString(), m.getMessageType().toString(),
                            m.getErrorCode(), Demos.pretty(m.getErrorFieldPointer()), m.getRulePath().orElse(""),
                            m.getErrorText()));
                }
            }
        }
        records.add(record("compute", ComputationDemo.FOLDER + "/input.json", ComputationDemo.DM_ID,
                ComputationDemo.FOLDER + "/computed.json"));

        Path tsv = GENERATED.resolve("java-results.tsv");
        Files.write(tsv, records, StandardCharsets.UTF_8);
        System.out.println("recorded the Java results -> " + tsv);
    }

    private static IDocumentModel loadModel(Path file) throws IOException {
        try (Reader r = Files.newBufferedReader(file)) {
            return new DocumentModelServiceFactory().createDocumentModelSerializer().deserialize(r);
        }
    }

    private static DocumentV2 loadDocument(IDocumentV2Serializer serializer, String dmId, String file)
            throws IOException {
        try (Reader r = Files.newBufferedReader(Demos.OUT.resolve(file))) {
            return serializer.deserializeV2(r, dmId, DocumentDeserializationConfig.builder().build(),
                    n -> System.err.println("doc-load: " + n));
        }
    }

    /** Compiles the model to STANDARD JavaScript and returns the single {@code .js} file of the generated ZIP. */
    private static String generateJs(IDocumentModel model, String dmId) throws IOException {
        IValidationCodeGeneratorConfig standardJs = new IValidationCodeGeneratorConfig() {
            @Override public ProgrammingLanguage getProgrammingLanguage() { return ProgrammingLanguage.JAVASCRIPT; }
            @Override public JsCodeGenConfig getJsCodeGenConfig() { return JsCodeGenConfig.STANDARD; }
        };
        byte[] zip = new DocumentModelServiceFactory().createDocumentModelService()
                .generateValidationCode(model, standardJs, null, n -> System.err.println("codegen: " + n));
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e = zis.getNextEntry(); e != null; e = zis.getNextEntry()) {
                if (e.getName().endsWith(".js")) {
                    return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalStateException("the generated ZIP contains no .js file for model " + dmId);
    }

    /** One TSV line; tabs and line breaks inside a value would break the format, so they become spaces. */
    private static String record(String... values) {
        List<String> cleaned = new ArrayList<>();
        for (String v : values) {
            cleaned.add(v.replaceAll("[\\t\\r\\n]+", " "));
        }
        return String.join("\t", cleaned);
    }
}
