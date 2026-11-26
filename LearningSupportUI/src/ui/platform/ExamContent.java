package ui.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 서버가 보내는 시험 대비 글(exam_questions)을 퀴즈·체크리스트로 나눈다.
 *
 * <p>서버(LlmTranslator.exam)가 만드는 모양
 * <pre>
 * Q1. 문제
 *    A. 보기
 *    B. 보기 ...
 *
 * Self-check
 * [ ] 항목
 *
 * Answers
 * Q1. B. 보기 — 해설
 * </pre>
 * 정답이 보기와 똑같지 않으면 서버는 글자 그대로("Q1. 정답 — 해설") 적는다. 그때는 보기 글자로 찾아 본다.
 * 모양이 달라 아무것도 못 읽으면 {@link #raw()} 로 원래 글을 그대로 보여 준다.
 */
record ExamContent(List<Quiz> quizzes, List<String> checklist, String raw) {

    static final String CHECKLIST_HEADER = "Self-check";
    static final String ANSWERS_HEADER = "Answers";
    /** 서버가 정답과 해설 사이에 넣는 구분 */
    static final String EXPLANATION_SEPARATOR = " — ";

    private static final Pattern QUESTION = Pattern.compile("^Q(\\d+)\\.\\s*(.*)$");
    private static final Pattern OPTION = Pattern.compile("^\\s+([A-Z])\\.\\s?(.*)$");
    private static final Pattern CHECK_ITEM = Pattern.compile("^\\[[ xX]?\\]\\s*(.*)$");
    private static final Pattern LABELED_ANSWER = Pattern.compile("^([A-Z])\\.\\s?(.*)$");

    /**
     * 문제 하나.
     *
     * @param answerIndex 정답 보기 번호 (0부터). 모르면 -1
     * @param answerText  정답 글 (보기에서 찾지 못했을 때 보여 줄 글). 없으면 null
     * @param explanation 해설. 없으면 null
     */
    record Quiz(String question, List<String> options, int answerIndex, String answerText, String explanation) {

        boolean gradable() {
            return answerIndex >= 0 && answerIndex < options.size();
        }

        /** "B. Character Stream" 처럼. 정답을 모르면 null. */
        String answerLabel() {
            if (gradable()) {
                return (char) ('A' + answerIndex) + ". " + options.get(answerIndex);
            }
            return answerText;
        }
    }

    static final ExamContent EMPTY = new ExamContent(List.of(), List.of(), null);

    boolean isEmpty() {
        return quizzes.isEmpty() && checklist.isEmpty() && (raw == null || raw.isBlank());
    }

    /** 나눠 읽지 못해 원래 글만 있는 경우 */
    boolean unparsed() {
        return quizzes.isEmpty() && checklist.isEmpty() && raw != null && !raw.isBlank();
    }

    static ExamContent parse(String text) {
        if (text == null || text.isBlank()) {
            return EMPTY;
        }
        List<QuizBuilder> quizzes = new ArrayList<>();
        List<String> checklist = new ArrayList<>();
        List<String> answerLines = new ArrayList<>();
        String section = "questions";
        QuizBuilder q = null;

        for (String line : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            String t = line.strip();
            if (t.equals(CHECKLIST_HEADER)) {
                section = "checklist";
                continue;
            }
            if (t.equals(ANSWERS_HEADER)) {
                section = "answers";
                continue;
            }
            if (t.isEmpty()) {
                continue;
            }
            switch (section) {
                case "questions" -> {
                    Matcher qm = QUESTION.matcher(t);
                    Matcher om = OPTION.matcher(line);
                    if (qm.matches()) {
                        q = new QuizBuilder(qm.group(2).strip());
                        quizzes.add(q);
                    } else if (q != null && om.matches()) {
                        q.options.add(om.group(2).strip());
                    } else if (q != null) {
                        q.continueWith(t);       // 여러 줄 문제나 보기
                    }
                }
                case "checklist" -> {
                    Matcher cm = CHECK_ITEM.matcher(t);
                    if (cm.matches()) {
                        if (!cm.group(1).isBlank()) {
                            checklist.add(cm.group(1).strip());
                        }
                    } else if (!checklist.isEmpty()) {
                        int last = checklist.size() - 1;
                        checklist.set(last, checklist.get(last) + " " + t);
                    }
                }
                default -> {
                    if (QUESTION.matcher(t).matches() || answerLines.isEmpty()) {
                        answerLines.add(t);
                    } else {
                        int last = answerLines.size() - 1;
                        answerLines.set(last, answerLines.get(last) + " " + t);
                    }
                }
            }
        }

        for (String a : answerLines) {
            Matcher m = QUESTION.matcher(a);
            if (!m.matches()) {
                continue;
            }
            int n = Integer.parseInt(m.group(1));
            if (n >= 1 && n <= quizzes.size()) {
                quizzes.get(n - 1).applyAnswer(m.group(2).strip());
            }
        }

        List<Quiz> built = quizzes.stream()
                .filter(b -> !b.question.isBlank())
                .map(QuizBuilder::build).toList();
        return new ExamContent(built, List.copyOf(checklist), text.strip());
    }

    private static final class QuizBuilder {
        private String question;
        private final List<String> options = new ArrayList<>();
        private int answerIndex = -1;
        private String answerText;
        private String explanation;

        QuizBuilder(String question) {
            this.question = question;
        }

        void continueWith(String t) {
            if (options.isEmpty()) {
                question = (question + " " + t).strip();
            } else {
                int last = options.size() - 1;
                options.set(last, options.get(last) + " " + t);
            }
        }

        /** "B. 보기 — 해설" 또는 "보기 — 해설" */
        void applyAnswer(String body) {
            Matcher lm = LABELED_ANSWER.matcher(body);
            if (lm.matches()) {
                int index = lm.group(1).charAt(0) - 'A';
                if (index < options.size() && lm.group(2).startsWith(options.get(index))) {
                    answerIndex = index;
                    explanation = afterSeparator(lm.group(2).substring(options.get(index).length()));
                    return;
                }
            }
            // 보기 글자로 찾기 (긴 보기부터: 한 보기가 다른 보기의 앞부분일 수 있음)
            int best = -1;
            for (int k = 0; k < options.size(); k++) {
                String o = options.get(k);
                if (!o.isEmpty() && (body.equals(o) || body.startsWith(o + EXPLANATION_SEPARATOR))
                        && (best < 0 || o.length() > options.get(best).length())) {
                    best = k;
                }
            }
            if (best >= 0) {
                answerIndex = best;
                explanation = afterSeparator(body.substring(options.get(best).length()));
                return;
            }
            int cut = body.indexOf(EXPLANATION_SEPARATOR);
            answerText = blankToNull(cut < 0 ? body : body.substring(0, cut));
            explanation = cut < 0 ? null : blankToNull(body.substring(cut + EXPLANATION_SEPARATOR.length()));
        }

        private static String afterSeparator(String rest) {
            String s = rest.strip();
            if (s.startsWith(EXPLANATION_SEPARATOR.strip())) {
                s = s.substring(EXPLANATION_SEPARATOR.strip().length());
            }
            return blankToNull(s);
        }

        Quiz build() {
            return new Quiz(question, List.copyOf(options), answerIndex, answerText, explanation);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
