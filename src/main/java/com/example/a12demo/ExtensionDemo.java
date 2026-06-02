package com.example.a12demo;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mgmtp.a12.kernel.core.customfieldtype.ICustomFieldTypeCheckError;
import com.mgmtp.a12.kernel.core.customfieldtype.ICustomFieldTypeFactory;
import com.mgmtp.a12.kernel.core.customfieldtype.ICustomFieldTypeValidationParam;
import com.mgmtp.a12.kernel.core.customfieldtype.ICustomFieldValidator;
import com.mgmtp.a12.kernel.md.document.apiV2.DocumentMultiPointer;
import com.mgmtp.a12.kernel.md.document.apiV2.DocumentPointer;
import com.mgmtp.a12.kernel.md.document.apiV2.PartiallyKnownDocumentMultiPointer;
import com.mgmtp.a12.kernel.md.document.apiV2.immutable.DocumentV2;
import com.mgmtp.a12.kernel.md.document.apiV2.services.IDocumentV2Serializer;
import com.mgmtp.a12.kernel.md.model.a12internal.DocumentModel;
import com.mgmtp.a12.kernel.md.model.a12internal.Field;
import com.mgmtp.a12.kernel.md.model.api.IDocumentModel;
import com.mgmtp.a12.kernel.md.model.api.IRule.Severity;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.DocumentModelBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.FieldBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.RuleBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.CustomFieldTypeBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.NumberTypeBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.StringTypeBuilder;
import com.mgmtp.a12.kernel.md.rt.api.DocumentProcessingConfig;
import com.mgmtp.a12.kernel.md.rt.api.ICustomCondition;
import com.mgmtp.a12.kernel.md.rt.api.ICustomConditionFactory;
import com.mgmtp.a12.kernel.md.rt.api.IDocumentRtService;

/**
 * Showcase: <b>extending the kernel from your own code</b>, plus the runtime's "free" type checking.
 *
 * <p>Domain: a <b>Product catalog</b> entry. Three things the DSL alone can't express are wired in via the
 * processing config:
 * <ol>
 *   <li><b>Custom condition</b> — an EAN-13 barcode <em>checksum</em> (mod-10) implemented in Java and called
 *       from a rule as {@code CustomCondition Ean13Invalid}. Algorithmic checks like this are the DSL's escape hatch.</li>
 *   <li><b>Custom field validator</b> — a field of a project-defined type {@code HexColor} validated by a Java
 *       {@link ICustomFieldValidator}; the kernel runs it during normal validation.</li>
 *   <li><b>Formal (type) errors for free</b> — feeding a fractional value into an integer field yields a
 *       built-in formal error (no rule needed), distinct from rule errors (its rule path is empty).</li>
 * </ol>
 */
public final class ExtensionDemo {

    static final String DM_ID = "product-catalog";
    /** Output/examples folder for this demo (named by demo, not by model id). */
    static final String FOLDER = "extension";

    public static void main(String[] args) {
        DocumentModel built = build();
        Demos.writeModel(built, FOLDER, DM_ID);
        IDocumentModel model = Demos.toPublicModel(built);
        IDocumentRtService rt = Demos.dynamicRtService(Demos.resolver(model));
        IDocumentV2Serializer ser = Demos.docSerializer(model);

        // The processing config carries the extension points: a custom condition + a custom field validator.
        DocumentProcessingConfig cfg = DocumentProcessingConfig.builder(Locale.US)
                .customConditionFactory(ICustomConditionFactory.fromMap(Map.of(
                        "Ean13Invalid", new Ean13ChecksumCondition("/Product/Barcode"))))
                .customFieldTypeFactory(ICustomFieldTypeFactory.fromMap(Map.of(
                        "HexColor", new HexColorValidator())))
                .build();

        Demos.check(rt, ser, cfg, FOLDER, "well-formed product", "valid.json", DocumentV2.empty(DM_ID)
                .withFieldValue("Product[1]/Barcode", "4006381333931")   // valid EAN-13 checksum
                .withFieldValue("Product[1]/Color", "#1A2B3C")           // valid hex colour
                .withFieldValue("Product[1]/StockUnits", BigDecimal.valueOf(100)));

        Demos.check(rt, ser, cfg, FOLDER, "malformed product", "malformed.json", DocumentV2.empty(DM_ID)
                .withFieldValue("Product[1]/Barcode", "4006381333930")   // last digit wrong -> checksum fails
                .withFieldValue("Product[1]/Color", "teal")              // not a hex colour
                .withFieldValue("Product[1]/StockUnits", new BigDecimal("2.5")));  // fractional -> formal error
    }

    static DocumentModel build() {
        DocumentModel dm = DocumentModelBuilder.builderWithDefaultsForTests().id(DM_ID).build();

        Field barcode = FieldBuilder.with(dm).name("/Product/Barcode").ft(StringTypeBuilder.builder()).build();
        // A field of a project-defined custom type; the validator registered under "HexColor" runs against it.
        FieldBuilder.with(dm).name("/Product/Color")
                .ft(CustomFieldTypeBuilder.builder().modify(b -> b.name("HexColor"))).build();
        // Integer field (no fractional digits) — fractional input becomes a formal error.
        FieldBuilder.with(dm).name("/Product/StockUnits").ft(NumberTypeBuilder.builder()).build();

        // Rule delegating to the Java custom condition. (FieldFilled also satisfies "error field referenced".)
        RuleBuilder.with(dm).name("/Product/BarcodeRule").field(barcode).modify(rb -> rb
                .errorCondition("FieldFilled(Barcode) And CustomCondition Ean13Invalid")
                .errorCode("BARCODE_CHECKSUM").severity(Severity.ERROR)
                .errorMessage(Demos.text("Barcode failed its EAN-13 checksum.",
                                         "Barcode hat die EAN-13-Prüfsumme nicht bestanden."))).build();

        return dm;
    }

    /** Custom condition: returns true (rule fires) when the barcode field is not a valid EAN-13. */
    static final class Ean13ChecksumCondition implements ICustomCondition {
        private final String fieldPath;

        Ean13ChecksumCondition(String fieldPath) {
            this.fieldPath = fieldPath;
        }

        @Override
        public boolean check(DocumentV2 document, Set<? extends DocumentMultiPointer> relevantEntities,
                Set<DocumentPointer> formallyIncorrectEntities, PartiallyKnownDocumentMultiPointer errorEntityInstance) {
            Object value = document.fieldValue(fieldPath);
            if (value == null) {
                return false; // not filled — leave "is it present?" to a separate rule
            }
            return !isValidEan13(value.toString());
        }

        private static boolean isValidEan13(String code) {
            if (!code.matches("\\d{13}")) {
                return false;
            }
            int sum = 0;
            for (int i = 0; i < 12; i++) {
                int digit = code.charAt(i) - '0';
                sum += (i % 2 == 0) ? digit : digit * 3; // positions alternate weight 1 / 3
            }
            int check = (10 - (sum % 10)) % 10;
            return check == (code.charAt(12) - '0');
        }
    }

    /** Custom field validator for a project-defined "HexColor" type. */
    static final class HexColorValidator implements ICustomFieldValidator {
        @Override
        public Optional<ICustomFieldTypeCheckError> validate(String value, ICustomFieldTypeValidationParam valParam,
                boolean isDisplayValue) {
            if (value == null || value.isEmpty() || value.matches("#[0-9A-Fa-f]{6}")) {
                return Optional.empty();
            }
            boolean de = "de".equals(valParam.getErrorMsgLocale().getLanguage());
            return Optional.of(new CheckError("HEX_COLOR_INVALID",
                    de ? "Farbe muss ein Hex-Code wie #1A2B3C sein." : "Color must be a hex code like #1A2B3C."));
        }
    }

    /** Minimal {@link ICustomFieldTypeCheckError}: an error key (becomes the message code) + a display message. */
    static final class CheckError implements ICustomFieldTypeCheckError {
        private final String key;
        private final String message;

        CheckError(String key, String message) {
            this.key = key;
            this.message = message;
        }

        @Override public String getErrorKey() { return key; }
        @Override public String getErrorMessage() { return message; }
    }
}
