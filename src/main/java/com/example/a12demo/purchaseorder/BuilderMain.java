package com.example.a12demo.purchaseorder;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import com.mgmtp.a12.kernel.core.tool.a12internal.api.error.IProblemReporter;
import com.mgmtp.a12.kernel.md.document.api.services.DocumentSerializationConfig;
import com.mgmtp.a12.kernel.md.document.apiV2.immutable.DocumentV2;
import com.mgmtp.a12.kernel.md.document.apiV2.services.IDocumentV2Serializer;
import com.mgmtp.a12.kernel.md.facade.DocumentModelServiceFactory;
import com.mgmtp.a12.kernel.md.facade.DocumentServiceFactory;
import com.mgmtp.a12.kernel.md.model.a12internal.DocumentModel;
import com.mgmtp.a12.kernel.md.model.api.IDocumentModel;
import com.mgmtp.a12.kernel.md.model.api.services.IDocumentModelResolver;
import com.mgmtp.a12.kernel.md.serializer.model.a12internal.services.DocumentModelSerializer;

/**
 * Authoring side of the demo — the "create models upfront, so they can be re-used" workflow.
 *
 * <p>Builds the Purchase-Order {@link DocumentModel} in code, plus three sample documents, and writes
 * them all out as JSON under {@code output/}. It does <b>not</b> validate — that is {@link ValidatorMain}'s
 * job, which simply loads these files. (In a real project the model JSON would live in version control
 * and be deployed alongside the app; documents come from forms / data sources.)
 */
public class BuilderMain {

    static final Path OUT = Path.of("output/purchase-order");

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUT);

        // --- 1. Build the model and write it as canonical DocumentModel JSON. ---
        DocumentModel built = PurchaseOrderModel.build();
        StringWriter modelWriter = new StringWriter();
        IProblemReporter reporter = problem -> System.err.println("model problem: " + problem);
        new DocumentModelSerializer().serialize(built, modelWriter, reporter);
        String modelJson = modelWriter.toString();
        Files.writeString(OUT.resolve("purchase-order.dm.json"), modelJson);
        System.out.println("wrote purchase-order.dm.json");

        // Bridge to the public IDocumentModel (needed by the document serializer's resolver).
        IDocumentModel model = new DocumentModelServiceFactory()
                .createDocumentModelSerializer().deserialize(new StringReader(modelJson));
        IDocumentModelResolver resolver = id -> model;
        IDocumentV2Serializer docSerializer = new DocumentServiceFactory(resolver).createDocumentV2Serializer();
        DocumentSerializationConfig serCfg = DocumentSerializationConfig.builder().build();

        // --- 2. Build sample documents and write them out. ---
        // Values: String, BigDecimal (numbers), Boolean, dates as ISO "yyyy-MM-dd" strings.
        write(docSerializer, serCfg, "valid", DocumentV2.empty(PurchaseOrderModel.DM_ID)
                .withFieldValue("Order[1]/CustomerName", "ACME Corp")
                .withFieldValue("Order[1]/OrderDate", LocalDate.of(2026, 1, 10).toString())
                .withFieldValue("Order[1]/DeliveryDate", LocalDate.of(2026, 1, 20).toString())
                .withFieldValue("Order[1]/Quantity", BigDecimal.valueOf(5))
                .withFieldValue("Order[1]/UnitPrice", new BigDecimal("9.99"))
                .withFieldValue("Order[1]/Express", Boolean.FALSE));

        write(docSerializer, serCfg, "invalid", DocumentV2.empty(PurchaseOrderModel.DM_ID)
                // CustomerName missing -> CUSTOMER_REQUIRED
                .withFieldValue("Order[1]/OrderDate", LocalDate.of(2026, 1, 20).toString())
                .withFieldValue("Order[1]/DeliveryDate", LocalDate.of(2026, 1, 10).toString()) // before order
                .withFieldValue("Order[1]/Quantity", BigDecimal.valueOf(-3)));                  // not positive

        write(docSerializer, serCfg, "large", DocumentV2.empty(PurchaseOrderModel.DM_ID)
                .withFieldValue("Order[1]/CustomerName", "BigBuyer Ltd")
                .withFieldValue("Order[1]/OrderDate", LocalDate.of(2026, 1, 10).toString())
                .withFieldValue("Order[1]/DeliveryDate", LocalDate.of(2026, 2, 10).toString())
                .withFieldValue("Order[1]/Quantity", BigDecimal.valueOf(5000)));                 // > 1000 -> warning

        System.out.println("Model + 3 documents written to: " + OUT.toAbsolutePath());
        System.out.println("Now run the validator (e.g. `gradle validate`).");
    }

    private static void write(IDocumentV2Serializer serializer, DocumentSerializationConfig cfg,
            String name, DocumentV2 doc) throws Exception {
        StringWriter sw = new StringWriter();
        serializer.serializeV2(doc, sw, cfg);
        Files.writeString(OUT.resolve(name + ".json"), sw.toString());
        System.out.println("wrote " + name + ".json");
    }
}
