package com.kareem.cortex;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/**
 * Evidence-grounded V5 student adjudicator.
 *
 * The old adapter merely serialized Cortex's already-derived rows. This layer performs
 * a second-pass reconciliation over the SAME packet seen by the teacher: evidence binding,
 * duplicate/situation linking, lifecycle supersession, temporal sanity checks and action
 * synthesis. It is deliberately deterministic so Teacher/Student diffs remain reproducible.
 */
public final class CognitiveStudentReasoner {
    private CognitiveStudentReasoner(){}

    public static JSONObject decide(JSONObject packet){
        try{
            JSONObject out=new JSONObject().put("schema_version",CognitiveDecisionContract.VERSION);
            JSONArray decisions=new JSONArray();
            JSONObject state=packet==null?null:packet.optJSONObject("current_state");
            JSONArray evidence=packet==null?null:packet.optJSONArray("new_evidence");
            long now=packet==null?System.currentTimeMillis():packet.optLong("generated_at",System.currentTimeMillis());
            List<JSONObject> situations=collectSituations(state);
            List<JSONObject> ev=objects(evidence);

            // 1) Evidence-only high-salience events must not disappear just because no derived row exists.
            for(JSONObject e:ev){
                if(isSecurityApproval(e)){
                    JSONArray refs=new JSONArray().put(e.optString("ref"));
                    decisions.put(decision("ASK_USER","",refs,.92,
                            "A recent sign-in approval request is security-sensitive and requires immediate identity verification.",
                            new JSONObject().put("kind","SECURITY_APPROVAL").put("state","pending_verification"),
                            "Confirm whether you initiated this sign-in. Approve only if it was you; otherwise deny and investigate."));
                    break;
                }
            }

            // 2) Reconcile live situations into canonical groups instead of treating duplicate rows as separate obligations.
            boolean[] used=new boolean[situations.size()];
            for(int i=0;i<situations.size();i++){
                if(used[i])continue;
                JSONObject base=situations.get(i); List<JSONObject> group=new ArrayList<>(); group.add(base); used[i]=true;
                for(int j=i+1;j<situations.size();j++) if(!used[j]&&sameSituation(base,situations.get(j))){group.add(situations.get(j));used[j]=true;}
                adjudicateGroup(decisions,group,ev,now);
            }

            // 3) If there are no derived situations, retain grounded evidence rather than inventing attention.
            if(decisions.length()==0 && !ev.isEmpty()){
                JSONObject e=ev.get(0); JSONArray refs=new JSONArray().put(e.optString("ref"));
                decisions.put(decision("STORE","",refs,.45,
                        "Recent evidence exists but Cortex has not yet formed a reliable live situation from it.",
                        new JSONObject().put("state","unreconciled"),null));
            }

            out.put("summary",summary(decisions));
            out.put("decisions",decisions);
            return out;
        }catch(Exception e){throw new IllegalStateException("Cannot adjudicate Cortex cognitive packet",e);}
    }

    private static void adjudicateGroup(JSONArray out,List<JSONObject> group,List<JSONObject> evidence,long now)throws Exception{
        JSONObject rep=representative(group); String target=rep.optString("ref");
        List<JSONObject> bound=bindEvidence(group,evidence); JSONArray refs=new JSONArray(); for(JSONObject e:bound)refs.put(e.optString("ref"));
        JSONObject newest=newest(bound); String text=groupText(group)+" "+evidenceText(bound);
        String lower=norm(text);

        boolean resolved=newest!=null && isResolutionEvidence(newest);
        boolean financial=isFinancial(lower);
        boolean medical=isMedical(lower);
        boolean waiting=containsKind(group,"WAITING") || lower.contains("waiting");
        boolean decisionKind=containsKind(group,"DECISION");
        boolean duplicate=group.size()>1;
        long targetAt=TemporalResolver.resolveForAttention(text,rep.optLong("updated_at",now));
        boolean clearlyFuture=targetAt>now+48L*3600000L;
        boolean recentOrDue=targetAt<=0 || targetAt<=now+24L*3600000L;

        JSONObject proposed=new JSONObject()
                .put("kind",rep.optString("kind"))
                .put("state",resolved?"resolved_or_superseded":rep.optString("state"))
                .put("merged_refs",refsOfSituations(group))
                .put("evidence_bound",refs.length());

        if(resolved){
            out.put(decision("UPDATE_SITUATION",target,refs,.90,
                    "Newer evidence indicates this previously open situation has already progressed, completed, or been superseded.",
                    proposed,"Mark the old obligation resolved/superseded and keep the newer evidence as the current truth."));
            return;
        }

        if(duplicate){
            out.put(decision("LINK",target,refs,.86,
                    "Multiple live rows appear to describe one evolving situation; they should not compete as separate attention items.",
                    proposed,"Merge the duplicate rows into one canonical situation while preserving history."));
        }

        if(financial){
            out.put(decision("SURFACE_NOW",target,refs,.88,
                    "Recent payment/limit evidence indicates an unresolved financial state whose latest event changes the same ongoing situation.",
                    proposed.put("domain","financial"),
                    "Show one consolidated card/payment situation with the latest status and the next concrete remediation step."));
            return;
        }

        if(medical){
            if(clearlyFuture){
                out.put(decision("WATCH",target,refs,.79,
                        "This is medically relevant but its normalized time is still in the future, so it should remain visible without being promoted to immediate attention.",
                        proposed.put("domain","medical").put("target_at",targetAt),
                        "Keep the appointment/follow-up scheduled and surface it when its time window approaches."));
            }else if(recentOrDue){
                out.put(decision("SURFACE_NOW",target,refs,.84,
                        "This is a live medical follow-up and its time is due/near or cannot be safely treated as future-dated.",
                        proposed.put("domain","medical").put("target_at",targetAt),
                        "Verify whether the appointment/result follow-up is still outstanding; complete or explicitly resolve it so it cannot resurface indefinitely."));
            }
            return;
        }

        if(waiting){
            out.put(decision("WATCH",target,refs,.76,
                    "The situation depends on an external change; bound evidence should be checked before escalating it again.",
                    proposed,"Check whether the dependency changed, then follow up only if it remains unresolved."));
        }else if(decisionKind){
            out.put(decision("ASK_USER",target,refs,.74,
                    "A real unresolved decision remains after evidence reconciliation and needs either a choice or missing information.",
                    proposed,"Ask the smallest question needed to resolve the decision, then update its lifecycle state."));
        }else{
            out.put(decision("STORE",target,refs,.62,
                    refs.length()>0?"The situation is grounded in evidence but does not currently justify immediate interruption.":"The derived situation lacks direct evidence binding, so it should not be promoted solely from its existing classification.",
                    proposed,refs.length()>0?"Keep it in context and re-evaluate when new evidence changes the state.":"Re-bind this row to supporting evidence before surfacing it."));
        }
    }

    private static List<JSONObject> collectSituations(JSONObject state){
        ArrayList<JSONObject> out=new ArrayList<>(); if(state==null)return out; LinkedHashSet<String> seen=new LinkedHashSet<>();
        String[] names={"attention","waiting","decisions","goals","situations"};
        for(String name:names){JSONArray a=state.optJSONArray(name);if(a==null)continue;for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;String r=x.optString("ref");if(r.isEmpty()||!seen.add(r))continue;out.add(x);}}
        return out;
    }

    private static List<JSONObject> bindEvidence(List<JSONObject> group,List<JSONObject> evidence){
        ArrayList<Scored> scored=new ArrayList<>(); String gt=canonical(groupText(group)); Set<Long> threads=new HashSet<>(); Set<Long> anchors=new HashSet<>();
        for(JSONObject s:group){long t=s.optLong("thread_id",0);if(t>0)threads.add(t);long a=s.optLong("anchor_signal_id",0);if(a>0)anchors.add(a);}
        for(JSONObject e:evidence){double score=0;long t=e.optLong("thread_id",0);long id=e.optLong("local_id",0);if(t>0&&threads.contains(t))score+=1.2;if(id>0&&anchors.contains(id))score+=1.5;score+=tokenOverlap(gt,canonical(oneEvidenceText(e)));if(score>=.42)scored.add(new Scored(e,score));}
        scored.sort((a,b)->Double.compare(b.score,a.score));ArrayList<JSONObject> out=new ArrayList<>();for(Scored s:scored){out.add(s.o);if(out.size()>=8)break;}return out;
    }

    private static boolean sameSituation(JSONObject a,JSONObject b){
        long ta=a.optLong("thread_id",0),tb=b.optLong("thread_id",0); if(ta>0&&tb>0&&ta==tb)return true;
        String aa=canonical(a.optString("semantic_key")+" "+a.optString("title")+" "+a.optString("body"));
        String bb=canonical(b.optString("semantic_key")+" "+b.optString("title")+" "+b.optString("body"));
        return tokenOverlap(aa,bb)>=.58;
    }

    private static JSONObject representative(List<JSONObject> g){JSONObject best=g.get(0);for(JSONObject x:g){int bi=best.optInt("importance",0),xi=x.optInt("importance",0);long bu=best.optLong("updated_at",0),xu=x.optLong("updated_at",0);if(xi>bi||(xi==bi&&xu>bu))best=x;}return best;}
    private static JSONObject newest(List<JSONObject> xs){JSONObject best=null;long bt=Long.MIN_VALUE;for(JSONObject x:xs){long t=Math.max(x.optLong("occurred_at",0),x.optLong("updated_at",0));if(t>bt){bt=t;best=x;}}return best;}
    private static boolean containsKind(List<JSONObject> g,String k){for(JSONObject x:g)if(k.equalsIgnoreCase(x.optString("kind")))return true;return false;}
    private static JSONArray refsOfSituations(List<JSONObject> g){JSONArray a=new JSONArray();for(JSONObject x:g)a.put(x.optString("ref"));return a;}

    private static boolean isResolutionEvidence(JSONObject e){String z=norm(oneEvidenceText(e));return has(z,"you’re all set","you're all set","all set","setup complete","set up","completed","done","successfully","تم بنجاح","تم الاعداد","اتعمل","خلص","completed successfully")&&!isSecurityApproval(e);}
    private static boolean isSecurityApproval(JSONObject e){String z=norm(oneEvidenceText(e));return has(z,"approve sign-in","approve sign in","new sign-in request","new sign in request","authenticator","تسجيل دخول","طلب تسجيل دخول")&&has(z,"approve","request","موافقة","طلب");}
    private static boolean isFinancial(String z){return has(z,"card","credit","debit","over limit","over-limit","declined","transaction","payment","spotify","snapchat","بطاقة","كارت","حد الائتمان","مرفوض","عملية","دفع");}
    private static boolean isMedical(String z){return has(z,"doctor","hospital","appointment","scan","ct","mri","lab","result","medical","عيادة","دكتور","مستشفى","موعد","اشعة","أشعة","تحليل","تحاليل","نتيجة","صدر");}

    private static JSONObject decision(String type,String target,JSONArray refs,double confidence,String reason,JSONObject state,String next)throws Exception{
        return new JSONObject().put("type",type).put("target_ref",target==null?"":target).put("evidence_refs",refs==null?new JSONArray():refs).put("confidence",Math.max(0,Math.min(1,confidence))).put("reason",reason).put("proposed_state",state==null?new JSONObject():state).put("next_action",next==null?JSONObject.NULL:next);
    }
    private static String summary(JSONArray d){int now=0,watch=0,ask=0,update=0,link=0;for(int i=0;i<d.length();i++){String t=d.optJSONObject(i)==null?"":d.optJSONObject(i).optString("type");if("SURFACE_NOW".equals(t))now++;else if("WATCH".equals(t))watch++;else if("ASK_USER".equals(t))ask++;else if("UPDATE_SITUATION".equals(t))update++;else if("LINK".equals(t))link++;}return "Evidence-grounded reconciliation: "+now+" surface now, "+watch+" watch, "+ask+" ask, "+update+" lifecycle update, "+link+" link/merge.";}
    private static List<JSONObject> objects(JSONArray a){ArrayList<JSONObject> out=new ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null)out.add(x);}return out;}
    private static String groupText(List<JSONObject> g){StringBuilder b=new StringBuilder();for(JSONObject x:g)b.append(' ').append(x.optString("title")).append(' ').append(x.optString("body")).append(' ').append(x.optString("semantic_key")).append(' ').append(x.optString("metadata_json"));return b.toString();}
    private static String evidenceText(List<JSONObject> e){StringBuilder b=new StringBuilder();for(JSONObject x:e)b.append(' ').append(oneEvidenceText(x));return b.toString();}
    private static String oneEvidenceText(JSONObject e){return e.optString("title")+" "+e.optString("body")+" "+e.optString("raw_text")+" "+e.optString("extracted_text")+" "+e.optString("summary")+" "+e.optString("reason")+" "+e.optString("metadata_json");}
    private static String norm(String s){return LocalSemanticEmbedder.norm(s==null?"":s);}
    private static String canonical(String s){String x=norm(s);StringBuilder b=new StringBuilder();for(String w:x.split("[^\\p{L}\\p{Nd}]+")){if(w.length()<2||STOP.contains(w))continue;if(b.length()>0)b.append(' ');b.append(w);}return b.toString();}
    private static double tokenOverlap(String a,String b){if(a==null||b==null||a.isEmpty()||b.isEmpty())return 0;HashSet<String>x=new HashSet<>(Arrays.asList(a.split(" "))),y=new HashSet<>(Arrays.asList(b.split(" ")));x.remove("");y.remove("");if(x.isEmpty()||y.isEmpty())return 0;int inter=0;for(String w:x)if(y.contains(w))inter++;return inter/(double)Math.min(x.size(),y.size());}
    private static boolean has(String z,String...xs){for(String x:xs)if(z.contains(norm(x)))return true;return false;}
    private static final Set<String> STOP=new HashSet<>(Arrays.asList("the","a","an","to","of","and","or","for","in","on","at","is","are","be","my","your","this","that","من","في","على","الى","إلى","اللي","ده","دي","و","او","أو"));
    private static final class Scored{final JSONObject o;final double score;Scored(JSONObject o,double score){this.o=o;this.score=score;}}
}
