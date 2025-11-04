package searchengine.parser;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;

import org.springframework.stereotype.Component;
import searchengine.exceptions.ReadingException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

@Setter
@Getter
@Slf4j
@RequiredArgsConstructor
@Component
public class LemmaFinder {

    private final LuceneMorphology luceneMorphology;
    private final String WORD_TYPE_REGEX = "\\W\\w&&[^а-яА-Я\\s]";
    private final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};

    public HashMap<String, Float> collectLemmas(String url) {
        String html = clearText(getResponse(url).body());
        String[] words = arrayContainsRussianWords(html);
        HashMap<String, Float> lemmas = new HashMap<>();

        for (String word : words) {

            if (word.isBlank()) {
                continue;
            }

            List<String> normalForm = luceneMorphology.getNormalForms(word);
            if (normalForm.isEmpty()) {
                continue;
            }

            List<String> wordBaseForm = luceneMorphology.getMorphInfo(word);
            if (anyWordBaseBelongToParticle(wordBaseForm)) {
                continue;
            }

            String normalWord = normalForm.get(0);
            if (lemmas.containsKey(normalWord)) {
                lemmas.put(normalWord, lemmas.get(normalWord) + 1);
            } else {
                lemmas.put(normalWord, 1f);
            }
        }
        return lemmas;
    }

//    public Set<String> getLemmaSet (String url) {
//        String[] textArray = arrayContainsRussianWords(url);
//        Set<String> lemmaSet = new HashSet<>();
//
//        for (String word : textArray) {
//            if (!word.isEmpty() && isCorrectWordForm(word)) {
//                List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
//                if (anyWordBaseBelongToParticle(wordBaseForms)) {
//                    continue;
//                }
//                lemmaSet.addAll(luceneMorphology.getNormalForms(word));
//            }
//        }
//        return lemmaSet;
//    }

    private String[] arrayContainsRussianWords(String text) {
        return text.toLowerCase()
                .replaceAll("[^а-я\\s]", "")
                .trim()
                .split("\\s+");
    }

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForm) {
        return wordBaseForm.stream().anyMatch(this::hasParticleProperty);
    }

    private boolean hasParticleProperty (String wordBase) {
        for (String property : particlesNames) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

//    private boolean isCorrectWordForm(String word) {
//        List<String> wordInfo = luceneMorphology.getMorphInfo(word);
//        for (String morphInfo : wordInfo) {
//            if (morphInfo.matches(WORD_TYPE_REGEX)) {
//                return false;
//            }
//        }
//        return true;
//    }

    private String clearText(String text) {
        if (text.isEmpty()) {
            return "";
        }
        return Jsoup.parse(text).text();
    }

    public Response getResponse(String url) {
        try {
            return  Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .referrer("https://www.google.com")
                    .execute();
        } catch (IOException e) {
            throw new ReadingException("Не удалось прочитать страницу");
        }
    }
}
