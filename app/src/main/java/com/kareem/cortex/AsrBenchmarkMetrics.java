package com.kareem.cortex;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Provider-neutral ASR benchmark scoring.
 *
 * This class intentionally contains no model names, prompt vocabulary, domain hints,
 * correction dictionaries or engine-specific logic. The reference transcript is the
 * only authority. This keeps benchmark scoring independent from candidate tuning.
 */
public final class AsrBenchmarkMetrics {
    public static final class Score {
        public final int referenceWords;
        public final int wordEdits;
        public final double wer;
        public final int referenceChars;
        public final int charEdits;
        public final double cer;
        public final int arabicReferenceWords;
        public final int arabicWordEdits;
        public final double arabicWer;
        public final int latinReferenceWords;
        public final int latinWordEdits;
        public final double latinWer;
        public final int referenceNumbers;
        public final int matchedNumbers;
        public final double numberRecall;
        public final int referenceScriptSwitches;
        public final int hypothesisScriptSwitches;
        public final int duplicateAdjacentWords;

        Score(int referenceWords, int wordEdits, double wer,
              int referenceChars, int charEdits, double cer,
              int arabicReferenceWords, int arabicWordEdits, double arabicWer,
              int latinReferenceWords, int latinWordEdits, double latinWer,
              int referenceNumbers, int matchedNumbers, double numberRecall,
              int referenceScriptSwitches, int hypothesisScriptSwitches,
              int duplicateAdjacentWords) {
            this.referenceWords = referenceWords;
            this.wordEdits = wordEdits;
            this.wer = wer;
            this.referenceChars = referenceChars;
            this.charEdits = charEdits;
            this.cer = cer;
            this.arabicReferenceWords = arabicReferenceWords;
            this.arabicWordEdits = arabicWordEdits;
            this.arabicWer = arabicWer;
            this.latinReferenceWords = latinReferenceWords;
            this.latinWordEdits = latinWordEdits;
            this.latinWer = latinWer;
            this.referenceNumbers = referenceNumbers;
            this.matchedNumbers = matchedNumbers;
            this.numberRecall = numberRecall;
            this.referenceScriptSwitches = referenceScriptSwitches;
            this.hypothesisScriptSwitches = hypothesisScriptSwitches;
            this.duplicateAdjacentWords = duplicateAdjacentWords;
        }
    }

    private enum Script { ARABIC, LATIN, NUMBER, OTHER }

    private AsrBenchmarkMetrics() {}

    public static Score score(String reference, String hypothesis) {
        String ref = normalize(reference);
        String hyp = normalize(hypothesis);
        List<String> refWords = tokens(ref);
        List<String> hypWords = tokens(hyp);
        int wordEdits = editDistance(refWords, hypWords);

        String refChars = compactChars(ref);
        String hypChars = compactChars(hyp);
        int charEdits = editDistanceChars(refChars, hypChars);

        List<String> refArabic = filterScript(refWords, Script.ARABIC);
        List<String> hypArabic = filterScript(hypWords, Script.ARABIC);
        int arabicEdits = editDistance(refArabic, hypArabic);

        List<String> refLatin = filterScript(refWords, Script.LATIN);
        List<String> hypLatin = filterScript(hypWords, Script.LATIN);
        int latinEdits = editDistance(refLatin, hypLatin);

        List<String> refNumbers = filterScript(refWords, Script.NUMBER);
        List<String> hypNumbers = filterScript(hypWords, Script.NUMBER);
        int matchedNumbers = multisetMatches(refNumbers, hypNumbers);

        return new Score(
                refWords.size(), wordEdits, ratio(wordEdits, refWords.size()),
                refChars.length(), charEdits, ratio(charEdits, refChars.length()),
                refArabic.size(), arabicEdits, ratio(arabicEdits, refArabic.size()),
                refLatin.size(), latinEdits, ratio(latinEdits, refLatin.size()),
                refNumbers.size(), matchedNumbers,
                refNumbers.isEmpty() ? 1.0 : (double) matchedNumbers / refNumbers.size(),
                scriptSwitches(refWords), scriptSwitches(hypWords),
                adjacentDuplicateCount(hypWords)
        );
    }

    static String normalize(String s) {
        if (s == null) return "";
        String out = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        out = out.replace('\u0640', ' '); // tatweel
        out = out.replaceAll("[\\p{Punct}\\p{S}]+", " ");
        out = out.replaceAll("\\s+", " ").trim();
        return out;
    }

    private static String compactChars(String s) {
        return s.replace(" ", "");
    }

    private static List<String> tokens(String s) {
        ArrayList<String> out = new ArrayList<>();
        if (s.isEmpty()) return out;
        for (String p : s.split(" ")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    private static List<String> filterScript(List<String> words, Script wanted) {
        ArrayList<String> out = new ArrayList<>();
        for (String w : words) if (scriptOf(w) == wanted) out.add(w);
        return out;
    }

    private static Script scriptOf(String token) {
        boolean arabic = false, latin = false, digit = false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (Character.isDigit(c)) digit = true;
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.ARABIC ||
                    block == Character.UnicodeBlock.ARABIC_SUPPLEMENT ||
                    block == Character.UnicodeBlock.ARABIC_EXTENDED_A) arabic = true;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) latin = true;
        }
        if (digit && !arabic && !latin) return Script.NUMBER;
        if (arabic && !latin) return Script.ARABIC;
        if (latin && !arabic) return Script.LATIN;
        return Script.OTHER;
    }

    private static int scriptSwitches(List<String> words) {
        Script prev = null;
        int switches = 0;
        for (String w : words) {
            Script s = scriptOf(w);
            if (s != Script.ARABIC && s != Script.LATIN) continue;
            if (prev != null && prev != s) switches++;
            prev = s;
        }
        return switches;
    }

    private static int adjacentDuplicateCount(List<String> words) {
        int n = 0;
        for (int i = 1; i < words.size(); i++) {
            if (words.get(i).equals(words.get(i - 1))) n++;
        }
        return n;
    }

    private static int multisetMatches(List<String> reference, List<String> hypothesis) {
        ArrayList<String> remaining = new ArrayList<>(hypothesis);
        int matches = 0;
        for (String r : reference) {
            int i = remaining.indexOf(r);
            if (i >= 0) {
                matches++;
                remaining.remove(i);
            }
        }
        return matches;
    }

    private static double ratio(int edits, int referenceSize) {
        if (referenceSize == 0) return edits == 0 ? 0.0 : 1.0;
        return (double) edits / referenceSize;
    }

    private static int editDistance(List<String> a, List<String> b) {
        int[] prev = new int[b.size() + 1];
        int[] cur = new int[b.size() + 1];
        for (int j = 0; j <= b.size(); j++) prev[j] = j;
        for (int i = 1; i <= a.size(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.size(); j++) {
                int cost = a.get(i - 1).equals(b.get(j - 1)) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev; prev = cur; cur = swap;
        }
        return prev[b.size()];
    }

    private static int editDistanceChars(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev; prev = cur; cur = swap;
        }
        return prev[b.length()];
    }
}
