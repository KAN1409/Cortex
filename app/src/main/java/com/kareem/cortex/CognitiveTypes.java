package com.kareem.cortex;

/** Stable vocabulary for the unified cognitive graph. */
public final class CognitiveTypes {
    private CognitiveTypes(){}

    public static final class ObjectType {
        public static final String RAW_SIGNAL="raw_signal";
        public static final String MEMORY="memory";
        public static final String DERIVED="derived";
        public static final String ENTITY="entity";
        public static final String THREAD="thread";
        public static final String PROJECT="project";
        public static final String GOAL="goal";
        public static final String AI_JOB="ai_job";
        private ObjectType(){}
    }

    public static final class DerivedKind {
        public static final String REVIEW="REVIEW";
        public static final String ACTION="ACTION";
        public static final String WAITING="WAITING";
        public static final String DECISION="DECISION";
        public static final String EVENT="EVENT";
        public static final String IDEA="IDEA";
        public static final String OPPORTUNITY="OPPORTUNITY";
        public static final String INSIGHT="INSIGHT";
        public static final String HYPOTHESIS="HYPOTHESIS";
        public static final String PROJECT_CANDIDATE="PROJECT_CANDIDATE";
        public static final String GOAL_SIGNAL="GOAL_SIGNAL";
        public static final String ADMIN_LIFECYCLE="ADMIN_LIFECYCLE";
        public static final String DEVICE_INCIDENT="DEVICE_INCIDENT";
        private DerivedKind(){}
    }

    public static final class EntityKind {
        public static final String PERSON="PERSON";
        public static final String ORGANIZATION="ORGANIZATION";
        public static final String TOPIC="TOPIC";
        public static final String PLACE="PLACE";
        public static final String PRODUCT="PRODUCT";
        public static final String DOCUMENT="DOCUMENT";
        public static final String UNKNOWN="UNKNOWN";
        private EntityKind(){}
    }

    public static final class Relation {
        public static final String PROMOTED_TO="promoted_to";
        public static final String SUPPORTS="supports";
        public static final String GROUNDED_BY="grounded_by";
        public static final String MENTIONS="mentions";
        public static final String PART_OF_THREAD="part_of_thread";
        public static final String RELATED_TO="related_to";
        public static final String ABOUT="about";
        public static final String INVOLVES="involves";
        public static final String SERVES_GOAL="serves_goal";
        public static final String BLOCKS="blocks";
        public static final String DEPENDS_ON="depends_on";
        public static final String EVOLVED_FROM="evolved_from";
        public static final String CONTRADICTS="contradicts";
        public static final String CONFIRMS="confirms";
        private Relation(){}
    }
}
