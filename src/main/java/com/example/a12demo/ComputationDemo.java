package com.example.a12demo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import com.mgmtp.a12.kernel.md.document.apiV2.immutable.DocumentV2;
import com.mgmtp.a12.kernel.md.document.apiV2.services.IDocumentV2Serializer;
import com.mgmtp.a12.kernel.md.model.a12internal.Computation;
import com.mgmtp.a12.kernel.md.model.a12internal.DocumentModel;
import com.mgmtp.a12.kernel.md.model.a12internal.Field;
import com.mgmtp.a12.kernel.md.model.api.IDocumentModel;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ComputationBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.DocumentModelBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.FieldBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.GroupBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.NumberTypeBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.StringTypeBuilder;
import com.mgmtp.a12.kernel.md.rt.api.DocumentProcessingConfig;
import com.mgmtp.a12.kernel.md.rt.api.IComputedFieldInstance;
import com.mgmtp.a12.kernel.md.rt.api.IDocumentComputationResult;
import com.mgmtp.a12.kernel.md.rt.api.IDocumentRtService;

/**
 * Showcase: <b>computed / derived fields</b> — the other half of the kernel's mission. Computations are
 * declared <em>in the model as data</em> (a {@code Precondition | Calculation} table), evaluated by the same
 * generated code as validation, and applied back onto the immutable document.
 *
 * <p>Domain: a weekly <b>Timesheet</b> with a repeating list of <b>Days</b>. Three computations of increasing
 * interest:
 * <ol>
 *   <li><b>per-row</b> {@code DayPay = Hours · Rate};</li>
 *   <li><b>cross-level aggregate</b> {@code WeekPay = Σ DayPay} (depends on the computed day totals);</li>
 *   <li><b>tiered / conditional</b> {@code Bonus} — mutually-exclusive preconditions select 0 % / 5 % / 10 %.</li>
 * </ol>
 */
public final class ComputationDemo {

    static final String DM_ID = "timesheet";
    /** Output/examples folder for this demo (named by demo, not by model id). */
    static final String FOLDER = "computation";

    public static void main(String[] args) {
        DocumentModel built = build();
        Demos.writeModel(built, FOLDER, DM_ID);
        IDocumentModel model = Demos.toPublicModel(built);
        IDocumentRtService rt = Demos.dynamicRtService(Demos.resolver(model));
        IDocumentV2Serializer ser = Demos.docSerializer(model);
        DocumentProcessingConfig cfg = DocumentProcessingConfig.builder(Locale.US).build();

        // Only the inputs are supplied; DayPay / WeekPay / Bonus are left for the kernel to compute.
        DocumentV2 input = DocumentV2.empty(DM_ID)
                .withFieldValue("Sheet[1]/Employee", "Dana Lee")
                .withFieldValue("Sheet[1]/Days[1]/Day", "Mon").withFieldValue("Sheet[1]/Days[1]/Hours", BigDecimal.valueOf(8)).withFieldValue("Sheet[1]/Days[1]/Rate", new BigDecimal("50.00"))
                .withFieldValue("Sheet[1]/Days[2]/Day", "Tue").withFieldValue("Sheet[1]/Days[2]/Hours", BigDecimal.valueOf(8)).withFieldValue("Sheet[1]/Days[2]/Rate", new BigDecimal("80.00"));
        Demos.writeDoc(ser, FOLDER, "input.json", input);

        System.out.println("\n=== inputs (DayPay / WeekPay / Bonus empty) ===");
        System.out.println("  Mon: 8 h @ 50.00    Tue: 8 h @ 80.00");

        IDocumentComputationResult comp = rt.compute(input, cfg);
        DocumentV2 computed = comp.applyTo(input);   // immutable: returns a NEW document carrying the derived values
        Demos.writeDoc(ser, FOLDER, "computed.json", computed);

        System.out.println("\n=== computed field instances (engine output) ===");
        for (IComputedFieldInstance cfi : comp.getComputedFieldInstancesWithoutErrors()) {
            System.out.printf("  %-30s = %s%n", cfi.pointer(), cfi.getValueV2());
        }

        System.out.println("\n=== read back from the applied document ===");
        System.out.println("  Days[1]/DayPay = " + computed.fieldValue("Sheet[1]/Days[1]/DayPay") + "   (8 · 50.00)");
        System.out.println("  Days[2]/DayPay = " + computed.fieldValue("Sheet[1]/Days[2]/DayPay") + "   (8 · 80.00)");
        System.out.println("  WeekPay        = " + computed.fieldValue("Sheet[1]/WeekPay") + "  (Σ day pay = 1040.00)");
        System.out.println("  Bonus          = " + computed.fieldValue("Sheet[1]/Bonus") + "   (>=1000 tier -> 10% of 1040.00)");
    }

    static DocumentModel build() {
        DocumentModel dm = DocumentModelBuilder.builderWithDefaultsForTests().id(DM_ID).build();

        FieldBuilder.with(dm).name("/Sheet/Employee").ft(StringTypeBuilder.builder()).build();
        Field weekPay = FieldBuilder.with(dm).name("/Sheet/WeekPay")
                .ft(NumberTypeBuilder.builder().modify(b -> b.maxFractionalDigits(2))).build();
        Field bonus = FieldBuilder.with(dm).name("/Sheet/Bonus")
                .ft(NumberTypeBuilder.builder().modify(b -> b.maxFractionalDigits(2))).build();

        GroupBuilder.with(dm).name("/Sheet/Days").repeat(7).build();
        FieldBuilder.with(dm).name("/Sheet/Days/Day").ft(StringTypeBuilder.builder()).build();
        FieldBuilder.with(dm).name("/Sheet/Days/Hours").ft(NumberTypeBuilder.builder()).build();
        FieldBuilder.with(dm).name("/Sheet/Days/Rate")
                .ft(NumberTypeBuilder.builder().modify(b -> b.maxFractionalDigits(2))).build();
        Field dayPay = FieldBuilder.with(dm).name("/Sheet/Days/DayPay")
                .ft(NumberTypeBuilder.builder().modify(b -> b.maxFractionalDigits(2))).build();

        // 1) per-row: DayPay = Hours * Rate. (.field(f) seeds a placeholder alternative we replace via modify.)
        computation(dm, "/Sheet/Days/DayPayComp", dayPay, alt(null, "[Hours]*[Rate]"));

        // 2) cross-level aggregate: WeekPay = Σ of the (computed) day pays.
        computation(dm, "/Sheet/WeekPayComp", weekPay, alt(null, "Sum(Days*/DayPay)"));

        // 3) tiered/conditional bonus: mutually-exclusive preconditions pick the rate.
        computation(dm, "/Sheet/BonusComp", bonus,
                alt("[WeekPay] >= 1000", "RoundDown([WeekPay] * 0.10, 2)"),
                alt("[WeekPay] >= 500 And [WeekPay] < 1000", "RoundDown([WeekPay] * 0.05, 2)"),
                alt("[WeekPay] < 500", "0.00"));

        return dm;
    }

    private static Computation.ComputationAlternative alt(String precondition, String operation) {
        Computation.ComputationAlternative.ComputationAlternativeBuilder b =
                Computation.ComputationAlternative.builder().operation(operation);
        if (precondition != null) {
            b.precondition(precondition);
        }
        return b.build();
    }

    private static void computation(DocumentModel dm, String name, Field computedField,
            Computation.ComputationAlternative... alternatives) {
        ComputationBuilder.with(dm).name(name).field(computedField)
                .modify(cb -> cb.computationAlternatives(List.of(alternatives)))
                .build();
    }
}
