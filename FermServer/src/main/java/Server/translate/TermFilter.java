package Server.translate;

import Server.db.AnalysisResultDao;
import Server.db.TermDao;
import com.termtranslator.core.Models.BundleOutput;
import com.termtranslator.core.Models.TermEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * LLM 이 찾아 준 새 용어 후보 중 전공용어만 남긴다.
 * LLM 에게도 기준을 주지만 틀릴 수 있어서, 여기서 한 번 더 확인한다.
 *
 * <p>남기는 조건 (모두 맞아야 함)
 * <ul>
 *   <li>한국어 용어에 한글이 있고, 2~30자, 네 단어 이하</li>
 *   <li>한글·영문·숫자·공백·하이픈·가운뎃점만 있음 (코드나 문장 부호가 섞이면 뺌)</li>
 *   <li>문장처럼 "~한다/~이다" 로 끝나지 않음</li>
 *   <li>일반 단어 목록(파일, 예제, 실습 ...)에 없고, 여러 단어면 마지막 단어도 일반 단어가 아님</li>
 *   <li><b>원문에 실제로 나옴</b> (LLM 이 지어낸 용어를 막음, 띄어쓰기 차이는 무시)</li>
 *   <li>영어가 있고 100자 이하, 한글이 없고, 코드 기호가 없음</li>
 *   <li>사전에 이미 있는 한국어 용어가 아니고, 같은 용어는 한 번만</li>
 * </ul>
 * 남은 용어는 저장할 때 "확인 전(verified=false)" 으로 사전에 들어간다.
 */
public final class TermFilter {

    /** 문서 하나에서 새로 넣는 용어 최대 개수 */
    static final int MAX_PER_DOCUMENT = 40;
    static final int MIN_KOREAN = 2;
    static final int MAX_KOREAN = 30;
    static final int MAX_WORDS = 4;
    static final int MAX_EXPLANATION = 300;

    private static final Pattern HANGUL = Pattern.compile("[가-힣]");
    private static final Pattern KOREAN_ALLOWED = Pattern.compile("[가-힣A-Za-z0-9 ·\\-]+");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");
    private static final Pattern ENGLISH_CODE = Pattern.compile("[_(){};=<>\"`]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    /** 문장 끝 (용어가 아니라 문장을 돌려준 경우) */
    private static final List<String> SENTENCE_ENDINGS = List.of("한다", "된다", "하다", "이다", "니다", "있다", "없다");

    /** 강의 자료에 흔하지만 전공용어가 아닌 단어 (띄어쓰기 없이 비교) */
    static final Set<String> GENERAL_WORDS = Set.of(
            "파일", "방법", "예제", "실습", "내용", "프로그램", "코드", "데이터", "정보", "기능", "문제", "과제",
            "설명", "목차", "학습", "강의", "수업", "목표", "학습목표", "단원", "페이지", "그림", "예시", "연습",
            "참고", "소개", "요약", "이해", "결과", "사용", "사용법", "개념", "정리", "시간", "이름", "문장",
            "화면", "버튼", "사용자", "질문", "정답", "시험", "출처", "교재", "부분", "종류", "특징", "장점",
            "단점", "차이", "차이점", "과정", "단계", "목적", "작성", "변경", "추가", "삭제", "확인", "예",
            "실습문제", "연습문제", "실행결과", "실행 결과", "다음", "이전", "경우", "형태", "방식", "기본");

    private TermFilter() {
    }

    /**
     * 묶음 응답들의 newTerms 를 걸러 저장할 용어 목록으로 만든다 (처음 나온 순서).
     *
     * @param bundles    파이프라인의 묶음 결과
     * @param sourceText 문서 원문 전체 (용어가 실제로 나오는지 확인용)
     * @param known      사전에 이미 있는 한국어 용어 (다시 넣지 않음)
     */
    public static List<AnalysisResultDao.Term> pick(List<BundleOutput> bundles, String sourceText,
                                                    Collection<String> known) {
        String source = squash(sourceText == null ? "" : sourceText);
        Set<String> seen = new HashSet<>();
        for (String k : known == null ? List.<String>of() : known) {
            if (k != null) {
                seen.add(squash(k));
            }
        }
        List<AnalysisResultDao.Term> out = new ArrayList<>();
        for (BundleOutput b : bundles == null ? List.<BundleOutput>of() : bundles) {
            if (b == null || b.result() == null || b.result().newTerms() == null) {
                continue;
            }
            for (TermEntry t : b.result().newTerms()) {
                if (out.size() >= MAX_PER_DOCUMENT) {
                    return out;
                }
                AnalysisResultDao.Term term = accept(t, source);
                if (term != null && seen.add(squash(term.korean()))) {
                    out.add(term);
                }
            }
        }
        return out;
    }

    /** 후보 하나를 검사한다. 전공용어로 볼 수 없으면 null. source 는 squash 된 원문. */
    static AnalysisResultDao.Term accept(TermEntry t, String source) {
        if (t == null) {
            return null;
        }
        String ko = normalize(t.ko());
        String en = normalize(t.en());
        if (!isKoreanTerm(ko) || !isEnglishTerm(en) || !source.contains(squash(ko))) {
            return null;
        }
        String ex = t.easyExplanation() == null ? "" : t.easyExplanation().strip();
        if (ex.length() > MAX_EXPLANATION) {
            ex = ex.substring(0, MAX_EXPLANATION - 1).strip() + "…";
        }
        return new AnalysisResultDao.Term(ko, en, ex.isEmpty() ? null : ex);
    }

    static boolean isKoreanTerm(String ko) {
        if (ko.length() < MIN_KOREAN || ko.length() > MAX_KOREAN || !HANGUL.matcher(ko).find()) {
            return false;
        }
        if (!KOREAN_ALLOWED.matcher(ko).matches() || ko.split(" ").length > MAX_WORDS) {
            return false;
        }
        for (String ending : SENTENCE_ENDINGS) {
            if (ko.endsWith(ending)) {
                return false;
            }
        }
        if (GENERAL_WORDS.contains(ko) || GENERAL_WORDS.contains(squash(ko))) {
            return false;
        }
        // "추상적인 개념", "간단한 방법" 처럼 꾸미는 말 + 일반 단어로 끝나는 구도 뺀다
        String[] words = ko.split(" ");
        return words.length == 1 || !GENERAL_WORDS.contains(words[words.length - 1]);
    }

    static boolean isEnglishTerm(String en) {
        return !en.isEmpty() && en.length() <= TermDao.MAX_LENGTH && LATIN.matcher(en).find()
                && !HANGUL.matcher(en).find() && !ENGLISH_CODE.matcher(en).find();
    }

    /** 앞뒤 공백을 지우고 여러 칸 공백을 한 칸으로 */
    private static String normalize(String s) {
        return s == null ? "" : SPACES.matcher(s.strip()).replaceAll(" ");
    }

    /** 공백을 모두 지운다 (원문의 줄바꿈·띄어쓰기 차이를 무시하고 비교) */
    private static String squash(String s) {
        return SPACES.matcher(s).replaceAll("");
    }
}
