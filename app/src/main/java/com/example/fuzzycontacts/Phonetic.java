package com.example.fuzzycontacts;

import java.text.Normalizer;

/**
 * Fuzzy / phonetic name matching.
 *
 * Strategy (combined for best results):
 *   1. normalize()  -> lowercase, strip accents, keep letters, collapse repeats
 *                      (so "Anirbaan" -> "anirban", "Tareek" -> "tarek")
 *   2. exact / prefix / substring match  (fast, high score)
 *   3. Levenshtein edit distance         (catches Tareq/Tariq/Tarek and
 *                                          Anirban/Onirban even though their
 *                                          first letters differ)
 *   4. Soundex                            (extra phonetic safety net)
 */
public final class Phonetic {

    private Phonetic() {}

    /** lowercase, remove diacritics, keep a-z, collapse consecutive duplicate letters. */
    public static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        n = n.replaceAll("\\p{M}+", "");   // drop combining accent marks
        n = n.toLowerCase();
        StringBuilder sb = new StringBuilder();
        char prev = 0;
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (c >= 'a' && c <= 'z') {
                if (c != prev) sb.append(c);  // collapse repeated letters
                prev = c;
            } else {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') sb.append(' ');
                prev = 0;
            }
        }
        return sb.toString().trim();
    }

    /** Classic Soundex code (4 chars) for a single normalized token. */
    public static String soundex(String token) {
        if (token == null || token.isEmpty()) return "";
        token = token.toUpperCase();
        StringBuilder code = new StringBuilder();
        char first = token.charAt(0);
        code.append(first);
        char prevDigit = digit(first);
        for (int i = 1; i < token.length() && code.length() < 4; i++) {
            char d = digit(token.charAt(i));
            if (d != '0' && d != prevDigit) {
                code.append(d);
            }
            // vowels (digit '0') reset the "previous", h/w do not
            char c = token.charAt(i);
            if (c != 'H' && c != 'W') {
                prevDigit = d;
            }
        }
        while (code.length() < 4) code.append('0');
        return code.toString();
    }

    private static char digit(char c) {
        switch (c) {
            case 'B': case 'F': case 'P': case 'V': return '1';
            case 'C': case 'G': case 'J': case 'K':
            case 'Q': case 'S': case 'X': case 'Z': return '2';
            case 'D': case 'T': return '3';
            case 'L': return '4';
            case 'M': case 'N': return '5';
            case 'R': return '6';
            default:  return '0'; // vowels, h, w, y
        }
    }

    /** Levenshtein edit distance. */
    public static int levenshtein(String a, String b) {
        int la = a.length(), lb = b.length();
        if (la == 0) return lb;
        if (lb == 0) return la;
        int[] prev = new int[lb + 1];
        int[] cur = new int[lb + 1];
        for (int j = 0; j <= lb; j++) prev[j] = j;
        for (int i = 1; i <= la; i++) {
            cur[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= lb; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                cur[j] = Math.min(Math.min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[lb];
    }

    /**
     * Score how well a contact name matches a query. 0 means "no match".
     * Higher is better; used for filtering + ranking.
     */
    public static int matchScore(String name, String query) {
        String nq = normalize(query);
        if (nq.isEmpty()) return 0;
        String nn = normalize(name);
        if (nn.isEmpty()) return 0;
        return matchScorePre(nn, nn.split(" "), nq, nq.split(" "));
    }

    /**
     * Same scoring as {@link #matchScore}, but takes the contact's name already
     * normalized + tokenized (computed once at load time) and the query already
     * normalized (computed once per search). This avoids re-normalizing every
     * contact on every keystroke — the main source of typing lag.
     */
    public static int matchScorePre(String nn, String[] nTokens, String nq, String[] qTokens) {
        if (nq.isEmpty() || nn.isEmpty()) return 0;

        int best = 0;
        if (nn.contains(nq)) best = 95;          // whole-name substring

        for (String qt : qTokens) {
            if (qt.isEmpty()) continue;
            for (String nt : nTokens) {
                if (nt.isEmpty()) continue;
                int s = tokenScore(nt, qt);
                if (s > best) best = s;
            }
        }
        return best;
    }

    private static int tokenScore(String nt, String qt) {
        if (nt.equals(qt)) return 100;
        if (nt.startsWith(qt) || nt.contains(qt)) return 90;

        int d = levenshtein(nt, qt);
        int allow = (qt.length() <= 4) ? 1 : 2;   // longer words tolerate more typos
        if (d <= allow) return 85 - d * 5;

        if (soundex(nt).equals(soundex(qt))) return 60;
        return 0;
    }
}
