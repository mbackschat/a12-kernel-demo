package com.example.a12demo.purchaseorder;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.mgmtp.a12.kernel.md.document.api.services.DocumentDeserializationConfig;
import com.mgmtp.a12.kernel.md.document.apiV2.immutable.DocumentV2;
import com.mgmtp.a12.kernel.md.document.apiV2.services.IDocumentV2Serializer;
import com.mgmtp.a12.kernel.md.facade.DocumentModelServiceFactory;
import com.mgmtp.a12.kernel.md.facade.DocumentRtServiceFactory;
import com.mgmtp.a12.kernel.md.facade.DocumentServiceFactory;
import com.mgmtp.a12.kernel.md.model.api.IDocumentModel;
import com.mgmtp.a12.kernel.md.model.api.services.IDocumentModelResolver;
import com.mgmtp.a12.kernel.md.rt.api.DocumentProcessingConfig;
import com.mgmtp.a12.kernel.md.rt.api.IDocumentDynamicServiceConfig;
import com.mgmtp.a12.kernel.md.rt.api.IDocumentRtService;
import com.mgmtp.a12.kernel.md.rt.api.IDocumentValidationResult;
import com.mgmtp.a12.kernel.md.rt.api.ILabelProvider;
import com.mgmtp.a12.kernel.md.rt.api.IMessage;
import com.mgmtp.a12.kernel.md.rt.api.IModelCode;
import com.mgmtp.a12.kernel.md.rt.api.IModelCodeCache;

/**
 * Consumption side of the demo — the everyday "load a pre-built model + documents and validate" path.
 *
 * <p>Reads the DocumentModel and the sample documents that {@link BuilderMain} wrote under {@code output/},
 * then validates each document (dynamic path) and prints the errors &amp; warnings. This is fully decoupled
 * from how the model was authored: it only consumes JSON files via the public kernel API.
 */
public class ValidatorMain {

    static final Path OUT = Path.of("output/purchase-order");
    private static final String DM_ID = PurchaseOrderModel.DM_ID; // a real app references its model by a known id

    public static void main(String[] args) throws Exception {
        Path modelFile = OUT.resolve("purchase-order.dm.json");
        if (!Files.exists(modelFile)) {
            System.err.println("No model at " + modelFile.toAbsolutePath() + " — run the builder first (e.g. `gradle buildModels`).");
            System.exit(2);
        }

        // --- 1. Load the (pre-built, reusable) DocumentModel from JSON. ---
        IDocumentModel model;
        try (Reader r = Files.newBufferedReader(modelFile)) {
            model = new DocumentModelServiceFactory().createDocumentModelSerializer().deserialize(r);
        }
        IDocumentModelResolver resolver = id -> model;

        // --- 2. Set up the dynamic validation runtime (generate + compile validation code, cached). ---
        IModelCodeCache cache = new IModelCodeCache() {
            private final Map<String, IModelCode> codes = new ConcurrentHashMap<>();
            @Override public IModelCode getModelCode(String id) { return codes.get(id); }
            @Override public void addModelCode(String id, IModelCode code) { codes.put(id, code); }
        };
        IDocumentDynamicServiceConfig serviceConfig = new IDocumentDynamicServiceConfig() {
            @Override public IModelCodeCache getCache() { return cache; }
            @Override public Optional<String> getVariant() { return Optional.empty(); }
            @Override public Optional<ILabelProvider> getLabelProvider() { return Optional.empty(); }
        };
        IDocumentRtService rt = new DocumentRtServiceFactory(resolver).createDocumentRtService(serviceConfig);
        DocumentProcessingConfig cfg = DocumentProcessingConfig.builder(Locale.US).build(); // must be a model-supported locale

        IDocumentV2Serializer docSerializer = new DocumentServiceFactory(resolver).createDocumentV2Serializer();
        DocumentDeserializationConfig deserCfg = DocumentDeserializationConfig.builder().build(); // JSON by default

        // --- 3. Load each document from JSON and validate it. ---
        for (String name : List.of("valid", "invalid", "large")) {
            Path file = OUT.resolve(name + ".json");
            if (!Files.exists(file)) {
                System.out.println("(skipping missing " + name + ".json)");
                continue;
            }
            DocumentV2 doc;
            try (Reader r = Files.newBufferedReader(file)) {
                doc = docSerializer.deserializeV2(r, DM_ID, deserCfg, n -> System.err.println("doc-load: " + n));
            }
            report(name, rt.validateFull(doc, cfg));
        }
    }

    private static void report(String name, IDocumentValidationResult result) {
        System.out.println();
        System.out.println("=== " + name + " ===");
        System.out.println(result.noErrorOccurred() ? "  result: VALID (no errors)" : "  result: INVALID (errors present)");
        if (result.getMessages().isEmpty()) {
            System.out.println("  (no messages)");
            return;
        }
        for (IMessage m : result.getMessages()) {
            System.out.printf("  [%-7s] %-22s %s%s%n",
                    m.getSeverity(),
                    m.getErrorCode(),
                    m.getErrorText(),
                    m.getRulePath().map(p -> "   (rule: " + p + ")").orElse(""));
        }
    }
}
