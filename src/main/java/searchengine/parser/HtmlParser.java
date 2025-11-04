package searchengine.parser;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.Http;
import searchengine.exceptions.ReadingException;
import searchengine.exceptions.ThreadException;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.IndexingServiceImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.RecursiveTask;

@Slf4j
public class HtmlParser extends RecursiveTask<Set<PageEntity>> {

    private final String url;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final Set<String> visitedUrl;
    private final Http http;

    public HtmlParser(String url, PageRepository pageRepository, SiteRepository siteRepository, Set<String> visitedUrl, Http http) {
        this.url = url;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
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

        checkInterrupted();
        Set<PageEntity> pageEntities = new HashSet<>();

        while (!IndexingServiceImpl.stopRequested.get()) {
            String path = getPath(url);

            if (!visitedUrl.add(path)) {
                return pageEntities;
            }
            checkInterrupted();

            Response response = getResponse(url);
            checkInterrupted();

            String html = response.body();
            ArrayList<HtmlParser> taskList = new ArrayList<>();

            Elements elements;
            try {
                checkInterrupted();
                elements = response.parse().select("a[href]");
            } catch (IOException e) {
                throw new ReadingException("Не могу прочитать страницу");
            }

            for (Element link : elements) {

                checkInterrupted();
                String href = link.attr("href").trim();

                if (isRelativeLink(href)) {
                    if (visitedUrl.add(href)) {
                        PageEntity page = createPage(href, response, html);
                        pageEntities.add(page);
                    }

                    String absUrl = link.absUrl("href");
                    if (absUrl.isEmpty()) continue;

                    HtmlParser parser = new HtmlParser(absUrl, pageRepository, siteRepository, visitedUrl, http);
                    parser.fork();
                    taskList.add(parser);
                }
            }
            for (HtmlParser task : taskList) {
                checkInterrupted();
                pageEntities.addAll(task.join());
            }
            break;
        }
        return pageEntities;
    }

    public void checkInterrupted() {
        if (IndexingServiceImpl.stopRequested.get() || Thread.currentThread().isInterrupted()) {
           throw new ThreadException("Индексация прервана пользователем");
        }
    }

    private boolean isRelativeLink(String href) {
        if (href == null || href.isEmpty()) {
            return false;
        }
        return !href.startsWith("http://") &&
                !href.startsWith("https://") &&
                !href.startsWith("ftp://") &&
                !href.startsWith("//") &&
                !href.startsWith("mailto:") &&
                !href.startsWith("tel:") &&
                !href.startsWith("#") &&
                !href.matches("^[a-zA-Z]+:.*");
    }

    public String getPath(String href) {
        return href.equals(url) ? "/" : href;
    }

    public PageEntity createPage(String path, Response response, String html) {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(path);
        pageEntity.setCode(response.statusCode());
        pageEntity.setContent(html);
        SiteEntity site = siteRepository.findSiteByUrl(url).get();
        pageEntity.setSite(site);
        return pageEntity;
    }
}
