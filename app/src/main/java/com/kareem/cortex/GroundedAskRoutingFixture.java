package com.kareem.cortex;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/**
 * Rollback-only adversarial verifier for Grounded Ask routing.
 *
 * This deliberately creates conflicting evidence so the test can catch routing bugs that ordinary
 * happy-path tests miss: losing an attached focal item, treating personal-memory questions as
 * generic external questions, letting an unrelated attachment suppress authoritative local state,
 * over-grounding generic first-person questions, or aliasing a missing citation source to M1.
 */
public final class GroundedAskRoutingFixture {
    private GroundedAskRoutingFixture(){}

    public static final class Report {
        public final ArrayList<String> pass=new ArrayList<>(),fail=new ArrayList<>();
        public boolean ok(){return fail.isEmpty();}
        public String text(){
            StringBuilder b=new StringBuilder();
            for(String x:pass)b.append("PASS · ").append(x).append('\n');
            for(String x:fail)b.append("FAIL · ").append(x).append('\n');
            b.append("Summary: ").append(pass.size()).append(" pass · ").append(fail.size()).append(" fail");
            return b.toString().trim();
        }
    }

    public static Report run(Context context,VaultDb db)throws Exception{
        Report r=new Report();SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try{
            exactFocalAuthority(context,db,sql,r);
            personalMemoryPolicy(r);
            operationalPrecedence(r);
            citationIdentity(db,r);
            return r;
        }finally{sql.endTransaction();}
    }

    public static String verify(Context context,VaultDb db)throws Exception{
        Report r=run(context,db);if(!r.ok())throw new AssertionError(r.text());return r.text();
    }

    private static void exactFocalAuthority(Context context,VaultDb db,SQLiteDatabase sql,Report r)throws Exception{
        String nonce=Long.toHexString(System.nanoTime());
        String focalToken="FOCAL_ROUTE_"+nonce,decoyToken="DECOY_ROUTE_"+nonce;
        String q="What is the exact routing token in this attached capture?";
        long focal=db.insert("TEXT","diagnostic_rollback","Attached routing fixture","The exact routing token in this attached capture is "+focalToken+".","Diagnostics","grounded,routing,focal","",Fingerprint.text(focalToken),"{\"synthetic\":true}");
        long decoy=db.insert("TEXT","diagnostic_rollback","Semantic decoy","What is the exact routing token in this attached capture? The routing token is "+decoyToken+".","Diagnostics","grounded,routing,decoy","",Fingerprint.text(decoyToken),"{\"synthetic\":true}");
        if(focal<=0||decoy<=0){r.fail.add("focal authority fixture could not create synthetic evidence");return;}
        sql.execSQL("UPDATE knowledge_items SET status='analyzed',extracted_text=raw_text WHERE id IN (?,?)",new Object[]{focal,decoy});
        SemanticIndex.indexItem(db,focal);SemanticIndex.indexItem(db,decoy);
        LocalAskRouter.Result out=BrainRouter.fast(context,db,q,"your_data",focal,null);
        boolean first=!out.grounded.sources.isEmpty()&&out.grounded.sources.get(0).item.id==focal;
        boolean exact=out.answer!=null&&out.answer.contains(focalToken)&&!out.answer.contains(decoyToken);
        if(first&&exact)r.pass.add("Your Data attached focal remains M1 and beats a stronger semantic decoy");
        else r.fail.add("attached focal lost authority · first_source="+(out.grounded.sources.isEmpty()?"none":out.grounded.sources.get(0).item.id)+" · answer="+clip(out.answer,180));
    }

    private static void personalMemoryPolicy(Report r){
        String[] personal={
            "Who was the doctor I saw last week?",
            "Which pharmacy did I use?",
            "اسم الدكتور اللي كشفت عنده كان إيه؟",
            "أنا اشتريت العربية منين؟"
        };
        String[] general={
            "What is the capital of France?",
            "Explain photosynthesis simply.",
            "I want to know the capital of France.",
            "أنا عايز أعرف عاصمة فرنسا."
        };
        ArrayList<String> misses=new ArrayList<>(),falsePositives=new ArrayList<>();
        for(String q:personal)if(!BrainRouter.needsBroadContext(q))misses.add(q);
        for(String q:general)if(BrainRouter.needsBroadContext(q))falsePositives.add(q);
        if(misses.isEmpty()&&falsePositives.isEmpty())r.pass.add("personal-memory questions require Cortex grounding while obvious general questions keep fast-general eligibility");
        else r.fail.add("grounding intent classifier mismatch · personal_missed="+misses+" · general_overgrounded="+falsePositives);
    }

    private static void operationalPrecedence(Report r){
        boolean unrelated=BrainRouter.operationalFastPathEligible("What am I waiting for?",991L);
        boolean explicitFocal=BrainRouter.operationalFastPathEligible("What needs my attention in this attached document?",991L);
        if(unrelated&&!explicitFocal)r.pass.add("unrelated attachment cannot suppress authoritative operational state; explicit focal wording can keep focal ownership");
        else r.fail.add("operational/focal precedence mismatch · unrelated="+unrelated+" · explicit_focal="+explicitFocal);
    }

    private static void citationIdentity(VaultDb db,Report r){
        String nonce=Long.toHexString(System.nanoTime());
        long a=db.insert("TEXT","diagnostic_rollback","Citation source A","Citation source A "+nonce,"Diagnostics","citation,source","",Fingerprint.text("cite-a-"+nonce),"{\"synthetic\":true}");
        long b=db.insert("TEXT","diagnostic_rollback","Citation source B","Citation source B "+nonce,"Diagnostics","citation,source","",Fingerprint.text("cite-b-"+nonce),"{\"synthetic\":true}");
        KnowledgeItem ka=db.getById(a);ArrayList<SemanticHit> hits=new ArrayList<>();if(ka!=null)hits.add(new SemanticHit(ka,.9,"source A"));
        int present=SecondBrainEngine.sourceNumber(hits,a),missing=SecondBrainEngine.sourceNumber(hits,b);
        if(present==1&&missing==0)r.pass.add("citation identity uses M1 only for the actual source and returns 0 for an absent source");
        else r.fail.add("citation alias detected · present="+present+" · absent_resolved_to="+missing);
    }

    private static String clip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()<=n?x:x.substring(0,n)+"…";}
}
