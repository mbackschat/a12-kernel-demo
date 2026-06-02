package com.example.a12demo;

import java.math.BigDecimal;
import java.util.Locale;

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
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.BooleanTypeBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.NumberTypeBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.StringTypeBuilder;
import com.mgmtp.a12.kernel.md.rt.api.DocumentProcessingConfig;
import com.mgmtp.a12.kernel.md.rt.api.IDocumentRtService;

/**
 * Showcase: <b>structural rules over a repeating group</b> — the biggest step up from a flat form, and the
 * area where the kernel is most clearly differentiated from code-first validators (which drop to imperative
 * loops for any of this).
 *
 * <p>Domain: a music <b>Playlist</b> with a repeating list of <b>Tracks</b>. Three rules iterate/aggregate
 * over the rows:
 * <ol>
 *   <li><b>{@code RepetitionNotUnique}</b> — no two tracks may share a title; the error points at the
 *       <em>specific offending rows</em> (per-row feedback the kernel derives for you).</li>
 *   <li><b>{@code Sum}</b> — total running time must stay under one hour.</li>
 *   <li><b>{@code Sum(... Having ...)}</b> — a <em>filtered</em> aggregate: the running time of tracks flagged
 *       explicit must stay modest (a declarative aggregate over a subset of the rows).</li>
 * </ol>
 */
public final class IterationDemo {

    static final String DM_ID = "playlist";
    /** Output/examples folder for this demo (named by demo, not by model id). */
    static final String FOLDER = "iteration";

    public static void main(String[] args) {
        DocumentModel built = build();
        Demos.writeModel(built, FOLDER, DM_ID);
        IDocumentModel model = Demos.toPublicModel(built);
        IDocumentRtService rt = Demos.dynamicRtService(Demos.resolver(model));
        IDocumentV2Serializer ser = Demos.docSerializer(model);
        DocumentProcessingConfig cfg = DocumentProcessingConfig.builder(Locale.US).build();

        Demos.check(rt, ser, cfg, FOLDER, "balanced playlist", "balanced.json", DocumentV2.empty(DM_ID)
                .withFieldValue("Playlist[1]/Name", "Morning Focus")
                .withFieldValue("Playlist[1]/Tracks[1]/Title", "Aurora").withFieldValue("Playlist[1]/Tracks[1]/Seconds", BigDecimal.valueOf(200)).withFieldValue("Playlist[1]/Tracks[1]/Explicit", Boolean.FALSE)
                .withFieldValue("Playlist[1]/Tracks[2]/Title", "Tides").withFieldValue("Playlist[1]/Tracks[2]/Seconds", BigDecimal.valueOf(240)).withFieldValue("Playlist[1]/Tracks[2]/Explicit", Boolean.FALSE));

        Demos.check(rt, ser, cfg, FOLDER, "repeated track title", "repeated-title.json", DocumentV2.empty(DM_ID)
                .withFieldValue("Playlist[1]/Name", "Road Trip")
                .withFieldValue("Playlist[1]/Tracks[1]/Title", "Echo").withFieldValue("Playlist[1]/Tracks[1]/Seconds", BigDecimal.valueOf(180)).withFieldValue("Playlist[1]/Tracks[1]/Explicit", Boolean.FALSE)
                .withFieldValue("Playlist[1]/Tracks[2]/Title", "Echo").withFieldValue("Playlist[1]/Tracks[2]/Seconds", BigDecimal.valueOf(205)).withFieldValue("Playlist[1]/Tracks[2]/Explicit", Boolean.FALSE));

        Demos.check(rt, ser, cfg, FOLDER, "too long + explicit-heavy", "too-long.json", DocumentV2.empty(DM_ID)
                .withFieldValue("Playlist[1]/Name", "Marathon")
                .withFieldValue("Playlist[1]/Tracks[1]/Title", "Epic I").withFieldValue("Playlist[1]/Tracks[1]/Seconds", BigDecimal.valueOf(2000)).withFieldValue("Playlist[1]/Tracks[1]/Explicit", Boolean.TRUE)
                .withFieldValue("Playlist[1]/Tracks[2]/Title", "Epic II").withFieldValue("Playlist[1]/Tracks[2]/Seconds", BigDecimal.valueOf(2000)).withFieldValue("Playlist[1]/Tracks[2]/Explicit", Boolean.TRUE));
    }

    static DocumentModel build() {
        DocumentModel dm = DocumentModelBuilder.builderWithDefaultsForTests().id(DM_ID).build();

        // Playlist header field — also the anchor for playlist-level aggregate errors.
        Field name = FieldBuilder.with(dm).name("/Playlist/Name").ft(StringTypeBuilder.builder()).build();

        // Repeating track list (build the group explicitly so it is repeating; fields go inside it).
        GroupBuilder.with(dm).name("/Playlist/Tracks").repeat(200).build();
        Field title = FieldBuilder.with(dm).name("/Playlist/Tracks/Title").ft(StringTypeBuilder.builder()).build();
        FieldBuilder.with(dm).name("/Playlist/Tracks/Seconds")
                .ft(NumberTypeBuilder.builder().modify(b -> b.minValue(BigDecimal.ONE).positivesOnly(true))).build();
        FieldBuilder.with(dm).name("/Playlist/Tracks/Explicit").ft(BooleanTypeBuilder.builder()).build();

        // 1) per-row uniqueness: no duplicate track titles; the error attaches to the duplicated row(s).
        RuleBuilder.with(dm).name("/Playlist/Tracks/UniqueTitleRule").field(title).modify(rb -> rb
                .errorCondition("RepetitionNotUnique(Title @From RuleGroup)")
                .errorCode("DUPLICATE_TITLE").severity(Severity.ERROR)
                .errorMessage(Demos.text("The track '$Title.value$' appears more than once.",
                                         "Der Titel '$Title.value$' kommt mehrfach vor."))).build();

        // 2) aggregate: total running time must be under one hour (3600 s).
        RuleBuilder.with(dm).name("/Playlist/TooLongRule").field(name).modify(rb -> rb
                .errorCondition("FieldFilled(Name) And Sum(Tracks*/Seconds) > 3600")
                .errorCode("PLAYLIST_TOO_LONG").severity(Severity.ERROR)
                .errorMessage(Demos.text("The playlist exceeds one hour of total running time.",
                                         "Die Wiedergabeliste überschreitet eine Stunde Gesamtspielzeit."))).build();

        // 3) filtered aggregate: explicit tracks must not dominate (their running time stays <= 600 s).
        RuleBuilder.with(dm).name("/Playlist/ExplicitHeavyRule").field(name).modify(rb -> rb
                .errorCondition("FieldFilled(Name) And Sum(Tracks*/Seconds Having [Tracks/Explicit]==True) > 600")
                .errorCode("EXPLICIT_HEAVY").severity(Severity.WARNING)
                .errorMessage(Demos.text("Over 10 minutes of explicit tracks — consider a clean version.",
                                         "Über 10 Minuten explizite Titel — ggf. eine saubere Version anbieten."))).build();

        return dm;
    }
}
