package com.kareem.cortex;

import java.util.*;

/** Canonical recipes for documents Cortex may create from grounded Work Vault evidence. */
public final class WorkDocumentRecipe {
    public static final String VERSION="work_document_recipe_001";
    private WorkDocumentRecipe(){}

    public enum Kind {
        ASSIGNMENT_ORDER_MANUFACTURING_ONLY,
        ASSIGNMENT_ORDER_MANUFACTURING_AND_SUPPLY,
        SUPPLY_ORDER_SUPPLY_ONLY,
        COMMERCIAL_COMPARISON,
        FOLLOW_UP_REPORT,
        PRICE_COMPARISON,
        PROJECT_STATUS_REPORT,
        OWNER_PRESENTATION
    }

    public static final class Recipe {
        public final Kind kind;
        public final String outputFormat;
        public final String family;
        public final String scope;
        public final List<String> requiredFields;
        public final List<String> preferredSections;
        public final boolean localCandidate;

        Recipe(Kind kind,String outputFormat,String family,String scope,
               List<String> requiredFields,List<String> preferredSections,boolean localCandidate){
            this.kind=kind;this.outputFormat=outputFormat;this.family=family;this.scope=scope;
            this.requiredFields=Collections.unmodifiableList(new ArrayList<>(requiredFields));
            this.preferredSections=Collections.unmodifiableList(new ArrayList<>(preferredSections));
            this.localCandidate=localCandidate;
        }
    }

    public static Recipe forKind(Kind kind){
        if(kind==null)kind=Kind.PROJECT_STATUS_REPORT;
        switch(kind){
            case ASSIGNMENT_ORDER_MANUFACTURING_ONLY:
                return order(kind,"DOCX","ASSIGNMENT_ORDER","MANUFACTURING_ONLY");
            case ASSIGNMENT_ORDER_MANUFACTURING_AND_SUPPLY:
                return order(kind,"DOCX","ASSIGNMENT_ORDER","MANUFACTURING_AND_SUPPLY");
            case SUPPLY_ORDER_SUPPLY_ONLY:
                return order(kind,"DOCX","SUPPLY_ORDER","SUPPLY_ONLY");
            case COMMERCIAL_COMPARISON:
                return new Recipe(kind,"XLSX","COMPARISON","COMMERCIAL",
                        Arrays.asList("project","items","vendors","currency"),
                        Arrays.asList("project_summary","vendor_columns","item_rows","totals","commercial_terms","recommendation"),true);
            case FOLLOW_UP_REPORT:
                return new Recipe(kind,"XLSX","FOLLOW_UP","PROCUREMENT",
                        Arrays.asList("project","procurement_references"),
                        Arrays.asList("pr","status","supplier","po","pending_action","responsible","expected_date","value","notes"),true);
            case PRICE_COMPARISON:
                return new Recipe(kind,"XLSX","PRICE_COMPARISON","ANALYTICS",
                        Arrays.asList("items","price_records"),
                        Arrays.asList("item","vendor","project","unit","currency","previous_price","current_price","delta","percent","sources"),true);
            case OWNER_PRESENTATION:
                return new Recipe(kind,"PPTX","PRESENTATION","OWNER",
                        Arrays.asList("project","source_evidence"),
                        Arrays.asList("cover","executive_summary","decisions","comparisons","approvals","open_items","source_notes"),false);
            case PROJECT_STATUS_REPORT:
            default:
                return new Recipe(kind,"DOCX","REPORT","PROJECT_STATUS",
                        Arrays.asList("project","source_evidence"),
                        Arrays.asList("executive_summary","procurement_status","open_items","prices","approvals","risks","source_register"),false);
        }
    }

    private static Recipe order(Kind kind,String format,String family,String scope){
        return new Recipe(kind,format,family,scope,
                Arrays.asList("project","vendor","items","source_evidence"),
                Arrays.asList("header","order_information","project_vendor_details","scope_items","commercial_terms","totals","terms_conditions","signatures","source_register"),false);
    }

    public static String displayName(Kind kind){
        switch(kind){
            case ASSIGNMENT_ORDER_MANUFACTURING_ONLY:return "أمر إسناد — مصنعات فقط";
            case ASSIGNMENT_ORDER_MANUFACTURING_AND_SUPPLY:return "أمر إسناد — مصنعات + توريد";
            case SUPPLY_ORDER_SUPPLY_ONLY:return "أمر توريد — توريد فقط";
            case COMMERCIAL_COMPARISON:return "Commercial Comparison";
            case FOLLOW_UP_REPORT:return "Follow-up Report";
            case PRICE_COMPARISON:return "Price Comparison";
            case OWNER_PRESENTATION:return "Owner Presentation";
            case PROJECT_STATUS_REPORT:
            default:return "Project Status Report";
        }
    }
}