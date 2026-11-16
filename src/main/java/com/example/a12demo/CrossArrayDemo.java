package com.example.a12demo;

import java.math.BigDecimal;
import java.util.Locale;

import com.mgmtp.a12.kernel.md.document.apiV2.immutable.DocumentV2;
import com.mgmtp.a12.kernel.md.document.apiV2.services.IDocumentV2Serializer;
import com.mgmtp.a12.kernel.md.model.a12internal.DocumentModel;
import com.mgmtp.a12.kernel.md.model.a12internal.Field;
import com.mgmtp.a12.kernel.md.model.api.IDocumentModel;
import com.mgmtp.a12.kernel.md.model.api.IRule.Severity;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.DocumentModelBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.FieldBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.GroupBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.RuleBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.DateTypeBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.NumberTypeBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.StringTypeBuilder;
import com.mgmtp.a12.kernel.md.rt.api.DocumentProcessingConfig;
import com.mgmtp.a12.kernel.md.rt.api.IDocumentRtService;

/**
 * Showcase: <b>cross-array logic</b> — the "a validator does SQL-like queries" category. Each of these is a
 * declarative one-liner; in code-first validators (Zod/Joi) they're hand-written loops, and JSON Schema can't
 * express them at all.
 *
 * <p>Domain: <b>shipment routing</b>, all under one {@code Shipment} group (a rule must belong to a group):
 * <ol>
 *   <li><b>Parallel iteration</b> — {@code Demand} and {@code Capacity}, two repeating groups joined by a shared
 *       {@code Warehouse} index field. Per warehouse: both-or-neither ({@code UNMATCHED}); demand ≤ capacity
 *       ({@code OVER_CAPACITY}). An outer join inferred from the key.</li>
 *   <li><b>Row-order check</b> — {@code CurrentRepetition} compares each {@code Stop} to all its predecessors, so
 *       the stop ETAs must be chronologically ordered ({@code OUT_OF_ORDER}).</li>
 *   <li><b>Correlated filter</b> — the {@code $}-operator pins the current stop while filtering the {@code Holidays}
 *       array, flagging a stop whose ETA lands on a holiday ({@code DELIVERY_ON_HOLIDAY}) — a correlated subquery.</li>
 * </ol>
 */
public final class CrossArrayDemo {

    static final String DM_ID = "shipment-routing";
    /** Output/examples folder for this demo (named by demo, not by model id). */
    static final String FOLDER = "cross-array";

    public static void main(String[] args) {
        DocumentModel built = build();
        Demos.writeModel(built, FOLDER, DM_ID);
        IDocumentModel model = Demos.toPublicModel(built);
        IDocumentRtService rt = Demos.dynamicRtService(Demos.resolver(model));
        IDocumentV2Serializer ser = Demos.docSerializer(model);
        DocumentProcessingConfig cfg = DocumentProcessingConfig.builder(Locale.US).build();

        // valid: warehouse balanced; stops in date order; no stop on a holiday.
        Demos.check(rt, ser, cfg, FOLDER, "clean routing", "clean.json", DocumentV2.empty(DM_ID)
                .withFieldValue("Shipment[1]/Demand[1]/Warehouse", "BER").withFieldValue("Shipment[1]/Demand[1]/Units", BigDecimal.valueOf(100))
                .withFieldValue("Shipment[1]/Capacity[1]/Warehouse", "BER").withFieldValue("Shipment[1]/Capacity[1]/Units", BigDecimal.valueOf(150))
                .withFieldValue("Shipment[1]/Stops[1]/Location", "BER").withFieldValue("Shipment[1]/Stops[1]/Eta", "2026-03-10")
                .withFieldValue("Shipment[1]/Stops[2]/Location", "LEJ").withFieldValue("Shipment[1]/Stops[2]/Eta", "2026-03-12")
                .withFieldValue("Shipment[1]/Holidays[1]/HolidayDate", "2026-12-25"));

        // problems: HAM has demand but no capacity (UNMATCHED); CGN demands 200 vs 120 capacity (OVER_CAPACITY);
        //   Stops[2] ETA precedes Stops[1] (OUT_OF_ORDER); Stops[3] ETA is a holiday (DELIVERY_ON_HOLIDAY).
        Demos.check(rt, ser, cfg, FOLDER, "many cross-array problems", "problems.json", DocumentV2.empty(DM_ID)
                .withFieldValue("Shipment[1]/Demand[1]/Warehouse", "BER").withFieldValue("Shipment[1]/Demand[1]/Units", BigDecimal.valueOf(100))
                .withFieldValue("Shipment[1]/Demand[2]/Warehouse", "HAM").withFieldValue("Shipment[1]/Demand[2]/Units", BigDecimal.valueOf(50))
                .withFieldValue("Shipment[1]/Demand[3]/Warehouse", "CGN").withFieldValue("Shipment[1]/Demand[3]/Units", BigDecimal.valueOf(200))
                .withFieldValue("Shipment[1]/Capacity[1]/Warehouse", "BER").withFieldValue("Shipment[1]/Capacity[1]/Units", BigDecimal.valueOf(150))
                .withFieldValue("Shipment[1]/Capacity[2]/Warehouse", "CGN").withFieldValue("Shipment[1]/Capacity[2]/Units", BigDecimal.valueOf(120))
                .withFieldValue("Shipment[1]/Stops[1]/Location", "BER").withFieldValue("Shipment[1]/Stops[1]/Eta", "2026-03-20")
                .withFieldValue("Shipment[1]/Stops[2]/Location", "HAM").withFieldValue("Shipment[1]/Stops[2]/Eta", "2026-03-10")  // before Stops[1]
                .withFieldValue("Shipment[1]/Stops[3]/Location", "CGN").withFieldValue("Shipment[1]/Stops[3]/Eta", "2026-12-25")  // a holiday
                .withFieldValue("Shipment[1]/Holidays[1]/HolidayDate", "2026-12-25"));
    }

    static DocumentModel build() {
        DocumentModel dm = DocumentModelBuilder.builderWithDefaultsForTests().id(DM_ID).build();
        GroupBuilder.with(dm).name("/Shipment").build();

        // Two repeating groups joined by the Warehouse index field (enables parallel iteration).
        GroupBuilder.with(dm).name("/Shipment/Demand").repeat(50).indexField("Warehouse").build();
        Field demandUnits = FieldBuilder.with(dm).name("/Shipment/Demand/Units").ft(NumberTypeBuilder.builder()).build();
        FieldBuilder.with(dm).name("/Shipment/Demand/Warehouse").ft(StringTypeBuilder.builder()).build();
        GroupBuilder.with(dm).name("/Shipment/Capacity").repeat(50).indexField("Warehouse").build();
        FieldBuilder.with(dm).name("/Shipment/Capacity/Units").ft(NumberTypeBuilder.builder()).build();
        FieldBuilder.with(dm).name("/Shipment/Capacity/Warehouse").ft(StringTypeBuilder.builder()).build();

        // Route stops + the holiday calendar.
        GroupBuilder.with(dm).name("/Shipment/Stops").repeat(100).build();
        FieldBuilder.with(dm).name("/Shipment/Stops/Location").ft(StringTypeBuilder.builder()).build();
        Field eta = FieldBuilder.with(dm).name("/Shipment/Stops/Eta").ft(DateTypeBuilder.builder()).build();
        GroupBuilder.with(dm).name("/Shipment/Holidays").repeat(100).build();
        FieldBuilder.with(dm).name("/Shipment/Holidays/HolidayDate").ft(DateTypeBuilder.builder()).build();

        // 1) parallel iteration by Warehouse: demand & capacity both present, or neither.
        RuleBuilder.with(dm).name("/Shipment/UnmatchedWarehouseRule").field(demandUnits).modify(rb -> rb
                .errorCondition("FieldsNotCollectivelyFilled(Demand/Units, Capacity/Units)")
                .errorCode("UNMATCHED_WAREHOUSE").severity(Severity.ERROR)
                .errorMessage(Demos.text("This warehouse has demand and capacity declared inconsistently.",
                                         "Für dieses Lager sind Bedarf und Kapazität inkonsistent erfasst."))).build();

        // 2) parallel iteration, value comparison across the join: demand must not exceed capacity.
        RuleBuilder.with(dm).name("/Shipment/OverCapacityRule").field(demandUnits).modify(rb -> rb
                .errorCondition("FieldFilled(Demand/Units) And FieldFilled(Capacity/Units) And [Demand/Units] > [Capacity/Units]")
                .errorCode("OVER_CAPACITY").severity(Severity.ERROR)
                .errorMessage(Demos.text("Demanded units exceed the warehouse capacity.",
                                         "Bedarf übersteigt die Lagerkapazität."))).build();

        // 3) row-order via CurrentRepetition: the max ETA among preceding stops must be < this stop's ETA.
        RuleBuilder.with(dm).name("/Shipment/Stops/OutOfOrderRule").field(eta).modify(rb -> rb
                .errorCondition("MaxValue(/Shipment/Stops*/Eta Having CurrentRepetition(/Shipment/Stops) < CurrentRepetition($/Shipment/Stops)) >= [/Shipment/Stops/Eta]")
                .errorCode("OUT_OF_ORDER").severity(Severity.ERROR)
                .errorMessage(Demos.text("Stop ETAs must be in chronological order.",
                                         "Stopp-Ankunftszeiten müssen chronologisch geordnet sein."))).build();

        // 4) correlated filter via $: a stop's ETA must not fall on a holiday.
        RuleBuilder.with(dm).name("/Shipment/Stops/HolidayClashRule").field(eta).modify(rb -> rb
                .errorCondition("FieldFilled(/Shipment/Stops/Eta) And NumberOfFilledFields(/Shipment/Holidays*/HolidayDate Having [/Shipment/Holidays/HolidayDate] == [$/Shipment/Stops/Eta]) > 0")
                .errorCode("DELIVERY_ON_HOLIDAY").severity(Severity.ERROR)
                .errorMessage(Demos.text("Delivery is scheduled on a holiday.",
                                         "Lieferung ist an einem Feiertag geplant."))).build();

        return dm;
    }
}
