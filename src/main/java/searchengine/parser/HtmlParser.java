package searchengine.parser;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.context.ApplicationContext;
import searchengine.config.Http;
import searchengine.config.SitesList;
import searchengine.exceptions.ReadingException;
import searchengine.exceptions.ThreadException;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.IndexingPageService;
import searchengine.services.IndexingServiceImpl;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.RecursiveAction;

@Slf4j
public class HtmlParser extends RecursiveAction {
    private final ApplicationContext context;
    private final String url;
    private final SiteEntity site;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
    private final LemmaFinder lemmaFinder;
    private final SitesList sitesList;
    private final Set<String> visitedUrl;
    private final Http http;
    public HtmlParser(ApplicationContext context, String url,
                      SiteEntity site, PageRepository pageRepository,
                      SiteRepository siteRepository, IndexRepository indexRepository,
                      LemmaRepository lemmaRepository, LemmaFinder lemmaFinder,
                      SitesList sitesList,
                      Set<String> visitedUrl, Http http) {
        this.context = context;
        this.url = url;
        this.site = site;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.indexRepository = indexRepository;
        this.lemmaRepository = lemmaRepository;
        this.lemmaFinder = lemmaFinder;
        this.sitesList = sitesList;
        this.visitedUrl = visitedUrl;
        this.http = http;
    }
    public Response getResponse(String url) {
        checkInterrupted();
        try {
            if (IndexingServiceImpl.stopRequested.get() || Thread.currentThread().isInterrupted()) {
                throw new ThreadException("Индексация прервана пользователем");
            }
            return Jsoup.connect(url)
                    .userAgent(http.getUserAgent()) .referrer(http.getReferrer())
                    .timeout(http.getTimeout())
                    .execute();
        } catch (IOException ex) {
            throw new ReadingException("Ошибка загрузки страницы: " + url);
        }

    }
    @Override
    protected void compute() {
        IndexingPageService indexingPageService = context.getBean(IndexingPageService.class);
        checkInterrupted();
        System.out.println("Парсим url " + url);

        while (!IndexingServiceImpl.stopRequested.get()) {

            System.out.println(visitedUrl.contains(url));
            if (!visitedUrl.add(url)) {
                break;
            }
            checkInterrupted();

            Response response = getResponse(url);
            String html = response.body();
            int statusCode = response.statusCode();

            checkInterrupted();
            indexingPageService.indexPage(url, html, statusCode, site);
            Elements elements;

            try {
                checkInterrupted();
                elements = response.parse().select("a[href]");
            } catch (IOException e) {
                throw new ReadingException("Не могу прочитать страницу");
            }

            ArrayList<HtmlParser> taskList = new ArrayList<>();
            String currentDomain = getHost(url);

            for (Element link : elements) {
                checkInterrupted();

                String absUrl = link.absUrl("href").trim();

                if (absUrl.isEmpty() || !isInternalLink(absUrl, currentDomain)) continue;

                HtmlParser child = new HtmlParser(context, absUrl, site,
                        pageRepository, siteRepository, indexRepository,
                        lemmaRepository, lemmaFinder, sitesList,
                        visitedUrl, http);
                child.fork();
                taskList.add(child);
            }

            for (HtmlParser task : taskList) {
                checkInterrupted();
                task.join();
            }
        }
    }

    private String getHost(String url) {
        try {
            return new URL(url).getHost();
        } catch (MalformedURLException e) {
            throw new ReadingException("Не валидный URL: " + url);
        }
    }

    private boolean isInternalLink(String absUrl, String currentDomain) {
        try {
            URL linkUrl = new URL(absUrl);
            String host = linkUrl.getHost();
            return host.equalsIgnoreCase(currentDomain);

        } catch (MalformedURLException e) {
            return false;
        }
    }

    public void checkInterrupted() {
        if (IndexingServiceImpl.stopRequested.get() || Thread.currentThread().isInterrupted()) {
            throw new ThreadException("Индексация прервана пользователем");
        }
    }
}
