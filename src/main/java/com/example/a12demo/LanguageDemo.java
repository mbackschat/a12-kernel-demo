package com.example.a12demo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import com.mgmtp.a12.kernel.md.document.apiV2.immutable.DocumentV2;
import com.mgmtp.a12.kernel.md.document.apiV2.services.IDocumentV2Serializer;
import com.mgmtp.a12.kernel.md.model.a12internal.Computation;
import com.mgmtp.a12.kernel.md.model.a12internal.DocumentModel;
import com.mgmtp.a12.kernel.md.model.a12internal.Field;
import com.mgmtp.a12.kernel.md.model.a12internal.fieldtypes.EnumerationType;
import com.mgmtp.a12.kernel.md.model.api.IDocumentModel;
import com.mgmtp.a12.kernel.md.model.api.IRule.Severity;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ComputationBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.DocumentModelBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.FieldBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.RuleBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.EnumerationTypeBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.NumberTypeBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.StringTypeBuilder;
import com.mgmtp.a12.kernel.md.rt.api.DocumentProcessingConfig;
import com.mgmtp.a12.kernel.md.rt.api.IDocumentRtService;

/**
 * Showcase: <b>language-design surprises</b> that differ from how mainstream validators work.
 *
 * <p>Domain: a <b>Vehicle inspection</b>. Demonstrates three runnable surprises:
 * <ol>
 *   <li><b>Self-validating computations</b> — a computed field (TotalScore = Score1+Score2) also acts as a
 *       <em>validation</em> rule: if a document carries a TotalScore that disagrees with the formula, that's an
 *       error. Derived values are re-checked against their own definition — unheard-of in Zod/Joi transforms.</li>
 *   <li><b>Enumeration categories + {@code ->}</b> — enum values carry a named attribute vector; a rule reads
 *       {@code [FuelType -> Emissions]} to branch on a <em>group</em> of values rather than listing each.</li>
 *   <li><b>{@code Valid()/Invalid()} on a constructed date</b> — assemble a date from day/month/year fields and
 *       detect impossible calendar dates (e.g. 31 February).</li>
 * </ol>
 * (See SHOWCASE.md for the explainer-only surprises: bilingual DSL <em>keywords</em>, the no-negation design,
 * three-valued null semantics, and decimal-scale discipline.)
 */
public final class LanguageDemo {

    static final String DM_ID = "vehicle-inspection";
    /** Output/examples folder for this demo (named by demo, not by model id). */
    static final String FOLDER = "language";

    public static void main(String[] args) {
        DocumentModel built = build();
        Demos.writeModel(built, FOLDER, DM_ID);
        IDocumentModel model = Demos.toPublicModel(built);
        IDocumentRtService rt = Demos.dynamicRtService(Demos.resolver(model));
        IDocumentV2Serializer ser = Demos.docSerializer(model);
        DocumentProcessingConfig cfg = DocumentProcessingConfig.builder(Locale.US).build();

        // clean: electric (no emissions cert needed); TotalScore agrees with Score1+Score2.
        Demos.check(rt, ser, cfg, FOLDER, "clean inspection", "clean.json", DocumentV2.empty(DM_ID)
                .withFieldValue("Inspection[1]/FuelType", "ELECTRIC")
                .withFieldValue("Inspection[1]/Day", BigDecimal.valueOf(15)).withFieldValue("Inspection[1]/Month", BigDecimal.valueOf(3)).withFieldValue("Inspection[1]/Year", BigDecimal.valueOf(2026))
                .withFieldValue("Inspection[1]/Score1", BigDecimal.valueOf(4)).withFieldValue("Inspection[1]/Score2", BigDecimal.valueOf(5))
                .withFieldValue("Inspection[1]/TotalScore", BigDecimal.valueOf(9)));

        // surprises: diesel without an emissions cert (enum-category rule);
        //   TotalScore=99 contradicts 4+5, so the computation's own self-check fires.
        Demos.check(rt, ser, cfg, FOLDER, "surprising failures", "surprises.json", DocumentV2.empty(DM_ID)
                .withFieldValue("Inspection[1]/FuelType", "DIESEL")
                .withFieldValue("Inspection[1]/Day", BigDecimal.valueOf(31)).withFieldValue("Inspection[1]/Month", BigDecimal.valueOf(2)).withFieldValue("Inspection[1]/Year", BigDecimal.valueOf(2026))
                .withFieldValue("Inspection[1]/Score1", BigDecimal.valueOf(4)).withFieldValue("Inspection[1]/Score2", BigDecimal.valueOf(5))
                .withFieldValue("Inspection[1]/TotalScore", BigDecimal.valueOf(99)));
    }

    static DocumentModel build() {
        DocumentModel dm = DocumentModelBuilder.builderWithDefaultsForTests().id(DM_ID).build();

        // Enumeration with a category attribute vector (positionally aligned to the values).
        Field fuelType = FieldBuilder.with(dm).name("/Inspection/FuelType")
                .ft(EnumerationTypeBuilder.builder().modify(b -> b
                        .values(List.of(
                                EnumerationType.EnumValue.builder().value("PETROL").label(Demos.text("Petrol", "Benzin")).build(),
                                EnumerationType.EnumValue.builder().value("DIESEL").label(Demos.text("Diesel", "Diesel")).build(),
                                EnumerationType.EnumValue.builder().value("ELECTRIC").label(Demos.text("Electric", "Elektrisch")).build()))
                        .categories(List.of(EnumerationType.EnumCategory.builder()
                                .name("Emissions").values(List.of("COMBUSTION", "COMBUSTION", "ZERO")).build())))).build();
        Field cert = FieldBuilder.with(dm).name("/Inspection/EmissionsCert").ft(StringTypeBuilder.builder()).build();

        FieldBuilder.with(dm).name("/Inspection/Score1").ft(NumberTypeBuilder.builder()).build();
        FieldBuilder.with(dm).name("/Inspection/Score2").ft(NumberTypeBuilder.builder()).build();
        Field total = FieldBuilder.with(dm).name("/Inspection/TotalScore").ft(NumberTypeBuilder.builder()).build();

        // Date(...) operands: bounded, fraction-free integer fields (the format the kernel requires).
        Field day = FieldBuilder.with(dm).name("/Inspection/Day")
                .ft(NumberTypeBuilder.builder().modify(b -> b.maxFractionalDigits(0).minValue(BigDecimal.ONE).maxValue(BigDecimal.valueOf(31)))).build();
        FieldBuilder.with(dm).name("/Inspection/Month")
                .ft(NumberTypeBuilder.builder().modify(b -> b.maxFractionalDigits(0).minValue(BigDecimal.ONE).maxValue(BigDecimal.valueOf(12)))).build();
        FieldBuilder.with(dm).name("/Inspection/Year")
                .ft(NumberTypeBuilder.builder().modify(b -> b.maxFractionalDigits(0).minValue(BigDecimal.ZERO).maxValue(BigDecimal.valueOf(9999)))).build();

        // Self-validating computation: TotalScore = Score1 + Score2.
        ComputationBuilder.with(dm).name("/Inspection/TotalScoreComp").field(total)
                .modify(cb -> cb.computationAlternatives(List.of(
                                Computation.ComputationAlternative.builder().operation("[Score1]+[Score2]").build()))
                        .errorMessage(Demos.text("TotalScore must equal Score1 + Score2.",
                                                 "Gesamtpunktzahl muss Score1 + Score2 entsprechen.")))
                .build();

        // Enum category rule: combustion vehicles need an emissions certificate.
        RuleBuilder.with(dm).name("/Inspection/EmissionsCertRule").field(cert).modify(rb -> rb
                .errorCondition("[FuelType -> Emissions] == \"COMBUSTION\" And FieldNotFilled(EmissionsCert)")
                .errorCode("EMISSIONS_CERT_REQUIRED").severity(Severity.ERROR)
                .errorMessage(Demos.text("Combustion vehicles require an emissions certificate.",
                                         "Verbrenner benötigen eine Abgasbescheinigung."))).build();

        // Valid()/Invalid() on a date assembled from parts: catch impossible calendar dates (e.g. 31 Feb).
        RuleBuilder.with(dm).name("/Inspection/InvalidDateRule").field(day).modify(rb -> rb
                .errorCondition("Invalid(Date(Day, Month, Year))")
                .errorCode("INVALID_DATE").severity(Severity.ERROR)
                .errorMessage(Demos.text("The inspection date is not a real calendar date.",
                                         "Das Prüfdatum ist kein gültiges Kalenderdatum."))).build();

        return dm;
    }
}
