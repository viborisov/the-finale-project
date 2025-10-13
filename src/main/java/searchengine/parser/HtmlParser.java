package searchengine.parser;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.exceptions.ReadingException;
import searchengine.exceptions.ThreadException;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class HtmlParser extends RecursiveTask<Set<PageEntity>> {

    private final String url;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final Set<String> visitedUrl;
    private final AtomicBoolean stopRequested;
    public HtmlParser(String url, PageRepository pageRepository, SiteRepository siteRepository, Set<String> visitedUrl, AtomicBoolean stopRequested) {

        this.url = url;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.visitedUrl = visitedUrl;
        this.stopRequested = stopRequested;
    }

    public Response getHtml(String url) {
        checkInterrupted();
        try {
            Thread.sleep(1500);
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .referrer("https://www.google.com")
                    .timeout(1500)
                    .execute();
        } catch (IOException ex) {
            throw new ReadingException("Ошибка загрузки страницы: " + url);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ThreadException("Индексация прервана пользователем");
        }
    }

    @Override
    protected Set<PageEntity> compute() {
        checkInterrupted();
        Set<PageEntity> pageEntities = new HashSet<>();
        String path = getPath(url);

        if (!visitedUrl.add(path)) {
            return pageEntities;
        }

        Elements elements;
        Response response = getHtml(url);
        checkInterrupted();
        String html = response.body();
        ArrayList<HtmlParser> taskList = new ArrayList<>();

        try {
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
                checkInterrupted();
                String absUrl = link.absUrl("href");
                HtmlParser parser = new HtmlParser(absUrl, pageRepository, siteRepository, visitedUrl, stopRequested);
                parser.fork();
                taskList.add(parser);
            }
        }
        for (HtmlParser task : taskList) {
            checkInterrupted();
            pageEntities.addAll(task.join());
        }
        return pageEntities;
    }

    public void checkInterrupted() {
        if (stopRequested.get() || Thread.currentThread().isInterrupted()) {
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
        SiteEntity site = siteRepository.findSiteByUrl(url);
        pageEntity.setSite(site);
        return pageEntity;
    }
}
