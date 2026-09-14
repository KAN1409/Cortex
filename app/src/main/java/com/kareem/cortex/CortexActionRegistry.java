package com.kareem.cortex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Stable semantic identity for every meaningful Cortex user action. */
public final class CortexActionRegistry {
    public static final String DOCK_SURFACE = "CORTEX_DOCK";

    public enum Category { NAVIGATION, INTERACTION, CAPTURE, WORK, MEMORY, LOCAL_MUTATION, EXTERNAL_HANDOFF, SYSTEM, DIAGNOSTIC, SETTINGS }
    public enum VerificationPolicy { NONE, DESTINATION_VISIBLE, LOCAL_READ_BACK, HANDOFF_ONLY, EFFECT_OBSERVED, INTERNAL_DIAGNOSTIC }
    public enum EvidencePolicy { NONE, SOURCE_OPTIONAL, SOURCE_REQUIRED, RECEIPT_REQUIRED, HANDOFF_RECEIPT }
    public enum UndoPolicy { NONE, LOCAL_ROLLBACK, PROVIDER_OWNED }
    public enum IdempotencyPolicy { IDEMPOTENT, AT_MOST_ONCE, RETRYABLE, USER_CONFIRM_REQUIRED_ON_RETRY }

    public static final class Action {
        public final String actionId;
        public final Category category;
        public final String title;
        public final List<String> requiredInputs;
        public final String primarySurface;
        public final List<String> allowedSecondarySurfaces;
        public final String executor;
        public final VerificationPolicy verificationPolicy;
        public final EvidencePolicy evidencePolicy;
        public final UndoPolicy undoPolicy;
        public final IdempotencyPolicy idempotencyPolicy;

        Action(String actionId, Category category, String title, List<String> requiredInputs,
               String primarySurface, List<String> allowedSecondarySurfaces, String executor,
               VerificationPolicy verificationPolicy, EvidencePolicy evidencePolicy,
               UndoPolicy undoPolicy, IdempotencyPolicy idempotencyPolicy) {
            this.actionId = clean(actionId).toLowerCase(Locale.ROOT);
            this.category = category;
            this.title = clean(title);
            this.requiredInputs = immutable(requiredInputs);
            this.primarySurface = clean(primarySurface).toUpperCase(Locale.ROOT);
            this.allowedSecondarySurfaces = immutableUpper(allowedSecondarySurfaces);
            this.executor = clean(executor);
            this.verificationPolicy = verificationPolicy;
            this.evidencePolicy = evidencePolicy;
            this.undoPolicy = undoPolicy;
            this.idempotencyPolicy = idempotencyPolicy;
        }
    }

    private static final List<Action> ALL = Collections.unmodifiableList(Arrays.asList(
            nav("destination.now.open", "Open Now", CortexDestinationRegistry.NOW),
            nav("destination.work.open", "Open Work", CortexDestinationRegistry.WORK),
            nav("destination.memory.open", "Open Memory", CortexDestinationRegistry.MEMORY),
            nav("destination.capture.open", "Open Capture", CortexDestinationRegistry.CAPTURE),
            nav("settings.open", "Open Settings", CortexDestinationRegistry.SETTINGS),
            a("dock.open", Category.INTERACTION, "Open Cortex Dock", noInputs(), CortexDestinationRegistry.NOW, list(CortexDestinationRegistry.WORK, CortexDestinationRegistry.MEMORY, CortexDestinationRegistry.CAPTURE), "CortexDock", VerificationPolicy.DESTINATION_VISIBLE, EvidencePolicy.NONE, UndoPolicy.NONE, IdempotencyPolicy.IDEMPOTENT),
            a("cortex.ask", Category.INTERACTION, "Ask Cortex", list("prompt"), DOCK_SURFACE, list(CortexDestinationRegistry.NOW, CortexDestinationRegistry.WORK, CortexDestinationRegistry.MEMORY), "CortexAsk", VerificationPolicy.NONE, EvidencePolicy.SOURCE_OPTIONAL, UndoPolicy.NONE, IdempotencyPolicy.IDEMPOTENT),

            a("capture.voice", Category.CAPTURE, "Capture voice", noInputs(), CortexDestinationRegistry.CAPTURE, list(DOCK_SURFACE), "CaptureVoice", VerificationPolicy.LOCAL_READ_BACK, EvidencePolicy.RECEIPT_REQUIRED, UndoPolicy.LOCAL_ROLLBACK, IdempotencyPolicy.AT_MOST_ONCE),
            a("capture.text", Category.CAPTURE, "Capture text", list("text"), CortexDestinationRegistry.CAPTURE, list(DOCK_SURFACE), "CaptureText", VerificationPolicy.LOCAL_READ_BACK, EvidencePolicy.RECEIPT_REQUIRED, UndoPolicy.LOCAL_ROLLBACK, IdempotencyPolicy.IDEMPOTENT),
            a("capture.image", Category.CAPTURE, "Capture image", list("image_source"), CortexDestinationRegistry.CAPTURE, list(DOCK_SURFACE), "CaptureImage", VerificationPolicy.LOCAL_READ_BACK, EvidencePolicy.RECEIPT_REQUIRED, UndoPolicy.LOCAL_ROLLBACK, IdempotencyPolicy.IDEMPOTENT),
            a("capture.file", Category.CAPTURE, "Capture file", list("file_uri"), CortexDestinationRegistry.CAPTURE, list(DOCK_SURFACE), "CaptureFile", VerificationPolicy.LOCAL_READ_BACK, EvidencePolicy.RECEIPT_REQUIRED, UndoPolicy.LOCAL_ROLLBACK, IdempotencyPolicy.IDEMPOTENT),
            a("capture.share_import", Category.CAPTURE, "Import shared item", list("shared_payload"), CortexDestinationRegistry.CAPTURE, list(), "ShareImporter", VerificationPolicy.LOCAL_READ_BACK, EvidencePolicy.RECEIPT_REQUIRED, UndoPolicy.LOCAL_ROLLBACK, IdempotencyPolicy.IDEMPOTENT),
            a("capture.screen.setup", Category.CAPTURE, "Configure screen understanding", noInputs(), CortexDestinationRegistry.CAPTURE, list(CortexDestinationRegistry.SETTINGS), "AndroidAccessibilitySettings", VerificationPolicy.EFFECT_OBSERVED, EvidencePolicy.RECEIPT_REQUIRED, UndoPolicy.PROVIDER_OWNED, IdempotencyPolicy.IDEMPOTENT),
            navLike("capture.pipeline.open", "Open capture pipeline", CortexDestinationRegistry.SETTINGS, CortexDestinationRegistry.CAPTURE),
            navLike("capture.evidence.open", "Open captured evidence", CortexDestinationRegistry.CAPTURE, CortexDestinationRegistry.MEMORY),
            navLike("capture.library.voice.open", "Open voice library", CortexDestinationRegistry.CAPTURE),
            navLike("capture.library.visual.open", "Open visual memory", CortexDestinationRegistry.MEMORY, CortexDestinationRegistry.CAPTURE),
            navLike("capture.library.files.open", "Open files library", CortexDestinationRegistry.MEMORY, CortexDestinationRegistry.CAPTURE),

            navLike("work.archive.open", "Open work archive", CortexDestinationRegistry.WORK),
            a("work.ask", Category.WORK, "Ask work archive", list("prompt"), CortexDestinationRegistry.WORK, list(DOCK_SURFACE), "WorkVaultAsk", VerificationPolicy.NONE, EvidencePolicy.SOURCE_REQUIRED, UndoPolicy.NONE, IdempotencyPolicy.IDEMPOTENT),
            navLike("work.projects.open", "Open work projects", CortexDestinationRegistry.WORK),
            navLike("work.followup.open", "Open work follow-up", CortexDestinationRegistry.WORK),
            navLike("work.prices.open", "Open price intelligence", CortexDestinationRegistry.WORK),
            navLike("work.files.open", "Open work files", CortexDestinationRegistry.WORK),
            navLike("work.build_chatgpt", "Build with ChatGPT", CortexDestinationRegistry.WORK),

            a("memory.search", Category.MEMORY, "Search memory", list("query"), CortexDestinationRegistry.MEMORY, list(DOCK_SURFACE), "MemorySearch", VerificationPolicy.NONE, EvidencePolicy.SOURCE_OPTIONAL, UndoPolicy.NONE, IdempotencyPolicy.IDEMPOTENT),
            a("memory.sync", Category.MEMORY, "Sync visual memory", noInputs(), CortexDestinationRegistry.MEMORY, list(), "VisualMemorySync", VerificationPolicy.EFFECT_OBSERVED, EvidencePolicy.RECEIPT_REQUIRED, UndoPolicy.NONE, IdempotencyPolicy.RETRYABLE),
            a("memory.ocr.retry", Category.MEMORY, "Retry OCR", noInputs(), CortexDestinationRegistry.MEMORY, list(), "OcrWorker", VerificationPolicy.EFFECT_OBSERVED, EvidencePolicy.RECEIPT_REQUIRED, UndoPolicy.NONE, IdempotencyPolicy.RETRYABLE),
            a("memory.semantic.enable_or_repair", Category.MEMORY, "Enable or repair semantic memory", noInputs(), CortexDestinationRegistry.MEMORY, list(), "SemanticMemory", VerificationPolicy.EFFECT_OBSERVED, EvidencePolicy.RECEIPT_REQUIRED, UndoPolicy.NONE, IdempotencyPolicy.RETRYABLE),
            a("memory.refresh", Category.MEMORY, "Refresh memory", noInputs(), CortexDestinationRegistry.MEMORY, list(), "MemoryRefresh", VerificationPolicy.NONE, EvidencePolicy.NONE, UndoPolicy.NONE, IdempotencyPolicy.IDEMPOTENT),
            navLike("memory.knowledge.open", "Open knowledge", CortexDestinationRegistry.MEMORY),

            local("task.create", "Create task", CortexDestinationRegistry.WORK, "CognitiveStore.addDerived"),
            local("follow_up.create", "Create follow-up", CortexDestinationRegistry.WORK, "CognitiveStore.addDerived"),
            local("wait_for.create", "Create waiting item", CortexDestinationRegistry.NOW, "CognitiveStore.addDerived"),
            local("knowledge_note.create", "Create knowledge note", CortexDestinationRegistry.MEMORY, "CognitiveStore.addDerived"),
            external("calendar.prepare", "Prepare calendar event", list("title", "start_time"), "CortexActionExecutor.calendarDraft"),
            external("call.prepare", "Prepare call", list("phone_number"), "ACTION_DIAL"),
            external("message.prepare", "Prepare message", list("recipient", "body"), "CortexActionExecutor.messageDraft"),
            external("email.prepare", "Prepare email", list("to", "subject", "body"), "CortexActionExecutor.emailDraft"),
            a("project.link", Category.LOCAL_MUTATION, "Link item to project", list("source_item_id", "project_id"), CortexDestinationRegistry.WORK, list(CortexDestinationRegistry.MEMORY), "CognitiveStore.linkChecked", VerificationPolicy.LOCAL_READ_BACK, EvidencePolicy.RECEIPT_REQUIRED, UndoPolicy.LOCAL_ROLLBACK, IdempotencyPolicy.IDEMPOTENT),
            external("web.search", "Open web search", list("query"), "CortexActionExecutor.searchWeb"),
            external("app.open", "Open app", list("package"), "PackageManager.launchIntent"),
            externalOn("evidence.open_original", "Open original evidence", list("evidence_uri"), "CortexActionExecutor.openEvidence", CortexDestinationRegistry.MEMORY),
            a("feedback.record", Category.LOCAL_MUTATION, "Record feedback", list("source_item_id", "event"), CortexDestinationRegistry.MEMORY, list(CortexDestinationRegistry.NOW, CortexDestinationRegistry.WORK), "CognitiveStore.feedback", VerificationPolicy.LOCAL_READ_BACK, EvidencePolicy.RECEIPT_REQUIRED, UndoPolicy.NONE, IdempotencyPolicy.IDEMPOTENT),

            navLike("system_health.open", "Open System Health", CortexDestinationRegistry.SYSTEM_HEALTH, CortexDestinationRegistry.SETTINGS),
            diagnostic("diagnostics.crash.export", "Export crash report"),
            diagnostic("diagnostics.process_exit.export", "Export process exit"),
            diagnostic("diagnostics.attention_trace.export", "Export attention trace"),
            diagnostic("diagnostics.e2e.run", "Run embedded end-to-end test"),
            a("permissions.accessibility.open", Category.SETTINGS, "Open accessibility settings", noInputs(), CortexDestinationRegistry.SETTINGS, list(CortexDestinationRegistry.CAPTURE), "AndroidAccessibilitySettings", VerificationPolicy.EFFECT_OBSERVED, EvidencePolicy.RECEIPT_REQUIRED, UndoPolicy.PROVIDER_OWNED, IdempotencyPolicy.IDEMPOTENT),
            navLike("settings.asr.open", "Open voice settings", CortexDestinationRegistry.SETTINGS),
            navLike("settings.reasoning_model.open", "Open reasoning model settings", CortexDestinationRegistry.SETTINGS),
            navLike("settings.gemini.open", "Open Gemini settings", CortexDestinationRegistry.SETTINGS),
            navLike("settings.teacher.open", "Open teacher settings", CortexDestinationRegistry.SETTINGS),
            navLike("settings.learning.open", "Open learning settings", CortexDestinationRegistry.SETTINGS),
            navLike("settings.privacy_integrations.open", "Open data, privacy and integrations", CortexDestinationRegistry.SETTINGS)
    ));

    private CortexActionRegistry() {}

    public static List<Action> all() { return ALL; }

    public static Action require(String actionId) {
        String wanted = clean(actionId).toLowerCase(Locale.ROOT);
        for (Action a : ALL) if (a.actionId.equals(wanted)) return a;
        throw new IllegalArgumentException("Unknown Cortex action: " + actionId);
    }

    public static void validateOrThrow() {
        Set<String> ids = new HashSet<>();
        for (Action action : ALL) {
            if (action.actionId.isEmpty() || action.title.isEmpty() || action.category == null || action.executor.isEmpty()
                    || action.verificationPolicy == null || action.evidencePolicy == null || action.undoPolicy == null || action.idempotencyPolicy == null) {
                throw new IllegalStateException("Incomplete action contract: " + action.actionId);
            }
            if (!ids.add(action.actionId)) throw new IllegalStateException("Duplicate actionId: " + action.actionId);
            validateSurface(action.primarySurface, action.actionId);
            for (String secondary : action.allowedSecondarySurfaces) validateSurface(secondary, action.actionId);
            if (action.category == Category.EXTERNAL_HANDOFF && action.verificationPolicy != VerificationPolicy.HANDOFF_ONLY && action.verificationPolicy != VerificationPolicy.EFFECT_OBSERVED) {
                throw new IllegalStateException("External action cannot equate dispatch with verified success: " + action.actionId);
            }
            if (action.category == Category.LOCAL_MUTATION && action.verificationPolicy != VerificationPolicy.LOCAL_READ_BACK) {
                throw new IllegalStateException("Local mutation must use read-back verification: " + action.actionId);
            }
            if ((action.verificationPolicy == VerificationPolicy.LOCAL_READ_BACK || action.verificationPolicy == VerificationPolicy.HANDOFF_ONLY || action.verificationPolicy == VerificationPolicy.EFFECT_OBSERVED)
                    && action.evidencePolicy != EvidencePolicy.RECEIPT_REQUIRED && action.evidencePolicy != EvidencePolicy.HANDOFF_RECEIPT) {
                throw new IllegalStateException("Verified/observed action requires receipt evidence: " + action.actionId);
            }
        }
    }

    private static void validateSurface(String surface, String actionId) {
        if (DOCK_SURFACE.equals(surface)) return;
        if (!CortexDestinationRegistry.contains(surface)) throw new IllegalStateException("Unknown action surface " + surface + " for " + actionId);
    }

    private static Action nav(String id, String title, String destination) {
        return a(id, Category.NAVIGATION, title, noInputs(), destination, list(), "CortexNavigation", VerificationPolicy.DESTINATION_VISIBLE, EvidencePolicy.NONE, UndoPolicy.NONE, IdempotencyPolicy.IDEMPOTENT);
    }

    private static Action navLike(String id, String title, String primary, String... secondary) {
        return a(id, Category.NAVIGATION, title, noInputs(), primary, Arrays.asList(secondary), "CortexNavigation", VerificationPolicy.DESTINATION_VISIBLE, EvidencePolicy.NONE, UndoPolicy.NONE, IdempotencyPolicy.IDEMPOTENT);
    }

    private static Action local(String id, String title, String surface, String executor) {
        return a(id, Category.LOCAL_MUTATION, title, noInputs(), surface, list(DOCK_SURFACE), executor, VerificationPolicy.LOCAL_READ_BACK, EvidencePolicy.RECEIPT_REQUIRED, UndoPolicy.LOCAL_ROLLBACK, IdempotencyPolicy.IDEMPOTENT);
    }

    private static Action external(String id, String title, List<String> inputs, String executor) {
        return externalOn(id, title, inputs, executor, DOCK_SURFACE);
    }

    private static Action externalOn(String id, String title, List<String> inputs, String executor, String surface) {
        return a(id, Category.EXTERNAL_HANDOFF, title, inputs, surface, list(), executor, VerificationPolicy.HANDOFF_ONLY, EvidencePolicy.HANDOFF_RECEIPT, UndoPolicy.PROVIDER_OWNED, IdempotencyPolicy.USER_CONFIRM_REQUIRED_ON_RETRY);
    }

    private static Action diagnostic(String id, String title) {
        return a(id, Category.DIAGNOSTIC, title, noInputs(), CortexDestinationRegistry.SYSTEM_HEALTH, list(), "InternalDiagnostics", VerificationPolicy.INTERNAL_DIAGNOSTIC, EvidencePolicy.SOURCE_OPTIONAL, UndoPolicy.NONE, IdempotencyPolicy.IDEMPOTENT);
    }

    private static Action a(String id, Category category, String title, List<String> inputs,
                            String primarySurface, List<String> secondary, String executor,
                            VerificationPolicy verification, EvidencePolicy evidence,
                            UndoPolicy undo, IdempotencyPolicy idempotency) {
        return new Action(id, category, title, inputs, primarySurface, secondary, executor, verification, evidence, undo, idempotency);
    }

    private static List<String> noInputs() { return Collections.emptyList(); }
    private static List<String> list(String... values) { return Arrays.asList(values); }
    private static List<String> immutable(List<String> values) { return Collections.unmodifiableList(new ArrayList<>(values == null ? Collections.emptyList() : values)); }
    private static List<String> immutableUpper(List<String> values) {
        ArrayList<String> out = new ArrayList<>();
        if (values != null) for (String value : values) out.add(clean(value).toUpperCase(Locale.ROOT));
        return Collections.unmodifiableList(out);
    }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
