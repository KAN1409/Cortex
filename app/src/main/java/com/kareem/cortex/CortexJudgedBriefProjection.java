package com.kareem.cortex;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** User-facing projection. FINAL JUDGMENT remains the only attention owner. */
public final class CortexJudgedBriefProjection {
    public static final String VERSION="cortex_judged_brief_projection_003";
    private CortexJudgedBriefProjection(){}

    public static PrimeBriefStore.Snapshot load(Context context,VaultDb db){
        PrimeBriefStore.Snapshot fallback=PrimeBriefStore.load(db);if(context==null||db==null)return fallback;
        CanonicalAttentionMaterializer.Result materialized;
        try{materialized=CanonicalAttentionMaterializer.run(context.getApplicationContext(),db);}catch(Throwable ignored){return fallback;}
        PrimeBriefStore.Snapshot base=PrimeBriefStore.load(db);
        if(materialized.candidates<=0)return new PrimeBriefStore.Snapshot(base.recent,new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),base.changes,base.worthKnowing,base.reviews);
        Set<Long> selected=materialized.selectedSituations;
        return new PrimeBriefStore.Snapshot(base.recent,filterCanonicalAttention(base.actions,selected),filterCanonicalAttention(base.waiting,selected),filterCanonicalAttention(base.decisions,selected),base.changes,base.worthKnowing,base.reviews);
    }

    private static ArrayList<PrimeBriefStore.Item> filterCanonicalAttention(List<PrimeBriefStore.Item> items,Set<Long> selected){ArrayList<PrimeBriefStore.Item> out=new ArrayList<>();if(items==null)return out;for(PrimeBriefStore.Item item:items){if(item==null)continue;boolean canonical=item.id>=UniversalEventStore.ATTENTION_COMPAT_OFFSET;if(canonical&&item.threadId>0&&selected.contains(item.threadId))out.add(item);}return out;}
}
