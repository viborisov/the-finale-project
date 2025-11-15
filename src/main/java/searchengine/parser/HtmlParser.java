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
import searchengine.model.PageEntity;
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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.RecursiveTask;

@Slf4j
public class HtmlParser extends RecursiveTask<Set<PageEntity>> {

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
                      SiteRepository siteRepository,
                      IndexRepository indexRepository,
                      LemmaRepository lemmaRepository,
                      LemmaFinder lemmaFinder,
                      SitesList sitesList,
                      Set<String> visitedUrl,
                      Http http) {
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
                    .userAgent(http.getUserAgent())
                    .referrer(http.getReferrer())
                    .timeout(http.getTimeout())
                    .execute();
        } catch (IOException ex) {
            throw new ReadingException("Ошибка загрузки страницы: " + url);
        }
    }

    @Override
    protected Set<PageEntity> compute() {
        IndexingPageService indexingPageService = context.getBean(IndexingPageService.class);
        checkInterrupted();
        Set<PageEntity> pageEntities = new HashSet<>();

        while (!IndexingServiceImpl.stopRequested.get()) {
            if (!visitedUrl.add(url)) {
                return pageEntities;
            }
            checkInterrupted();

            Response response = getResponse(url);
            checkInterrupted();

            String html = response.body();
            int statusCode = response.statusCode();
            indexingPageService.indexPage(url, html, statusCode, site);
            ArrayList<HtmlParser> taskList = new ArrayList<>();

            Elements elements;
            try {
                checkInterrupted();
                elements = response.parse().select("a[href]");
            } catch (IOException e) {
                throw new ReadingException("Не могу прочитать страницу");
            }

            String currentDomain = getDomain(url);

            for (Element link : elements) {
                checkInterrupted();
                String href = link.attr("href").trim();

                String absUrl = link.absUrl("href");
                if (absUrl.isEmpty()) continue;

                if (!isInternalLink(absUrl, currentDomain)) continue;


                if (visitedUrl.add(absUrl)) {
                    PageEntity page = createPage(href, response, html);
                    pageEntities.add(page);
                }

                HtmlParser parser = new HtmlParser(context, absUrl, site, pageRepository, siteRepository, indexRepository,
                        lemmaRepository, lemmaFinder, sitesList, visitedUrl, http);
                parser.fork();
                taskList.add(parser);
            }
            for (HtmlParser task : taskList) {
                checkInterrupted();
                pageEntities.addAll(task.join());
            }
        }
        return pageEntities;
    }

    private String getDomain(String url) {
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


    public PageEntity createPage(String path, Response response, String html) {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(path);
        pageEntity.setCode(response.statusCode());
        pageEntity.setContent(html);
        pageEntity.setSite(site);
        return pageEntity;
    }
}
