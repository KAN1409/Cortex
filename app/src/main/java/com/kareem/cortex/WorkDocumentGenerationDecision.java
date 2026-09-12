package com.kareem.cortex;

import org.json.JSONArray;
import org.json.JSONObject;

/** Deterministic router: local generation only when a safe concrete generator exists and evidence is sufficient. */
public final class WorkDocumentGenerationDecision {
    public static final String VERSION="work_document_generation_decision_002";
    private WorkDocumentGenerationDecision(){}

    public enum Route { LOCAL_GENERATION, CHATGPT_BUILD }

    public static final class Decision {
        public final Route route;
        public final String reason;
        public final int evidenceRows;
        Decision(Route route,String reason,int rows){this.route=route;this.reason=reason;this.evidenceRows=rows;}
    }

    public static Decision decide(WorkDocumentRecipe.Kind kind,JSONObject payload){
        JSONArray prices=payload==null?null:payload.optJSONArray("priceRecords");
        int priceRows=prices==null?0:prices.length();
        return decide(kind,priceRows);
    }

    /** Pure-Java decision path for deterministic tests and callers that already know grounded row count. */
    public static Decision decide(WorkDocumentRecipe.Kind kind,int priceRows){
        if(kind==null)return new Decision(Route.CHATGPT_BUILD,"Unknown document kind",0);
        int rows=Math.max(0,priceRows);
        switch(kind){
            case COMMERCIAL_COMPARISON:
                if(rows>0)return new Decision(Route.LOCAL_GENERATION,"Structured price rows are sufficient for a deterministic XLSX comparison",rows);
                return new Decision(Route.CHATGPT_BUILD,"No structured price rows are available for a safe local comparison",0);
            case PRICE_COMPARISON:
                if(rows>0)return new Decision(Route.LOCAL_GENERATION,"Structured price history is sufficient for a deterministic XLSX comparison",rows);
                return new Decision(Route.CHATGPT_BUILD,"No structured price history is available for a safe local comparison",0);
            case FOLLOW_UP_REPORT:
                return new Decision(Route.CHATGPT_BUILD,"Follow-up output needs stronger status-column mapping before local generation is safe",0);
            case ASSIGNMENT_ORDER_MANUFACTURING_ONLY:
            case ASSIGNMENT_ORDER_MANUFACTURING_AND_SUPPLY:
            case SUPPLY_ORDER_SUPPLY_ONLY:
                return new Decision(Route.CHATGPT_BUILD,"Procurement orders require professional layout, wording and review-sensitive fields",0);
            case PROJECT_STATUS_REPORT:
                return new Decision(Route.CHATGPT_BUILD,"Project reports require narrative synthesis and professional document layout",0);
            case OWNER_PRESENTATION:
            default:
                return new Decision(Route.CHATGPT_BUILD,"Presentation layout and visual hierarchy require the document builder",0);
        }
    }
}
