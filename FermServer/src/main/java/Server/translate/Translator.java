package Server.translate;

import Server.extract.Item;

import java.util.List;

/**
 * 추출된 글자 → 번역·요약·개념·시험 문제·용어 ({@link Server.service.DefaultAnalysisService} 의 3단계).
 * 실제 구현은 {@link LlmTranslator}, 테스트에서는 가짜로 바꿔 끼운다.
 *
 * <p>LLM 을 부르므로 몇 분 걸릴 수 있다. 만들지 못한 부분은 null 로 두고,
 * 가능하면 예외 대신 {@link Translation#empty()} 를 돌려준다.
 */
@FunctionalInterface
public interface Translator {
    Translation translate(String fileName, List<Item> items);
}
