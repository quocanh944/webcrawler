package com.udacity.webcrawler;

import com.udacity.webcrawler.main.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Pattern;

/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on
 * a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
  private final Clock clock;
  private final PageParserFactory parserFactory;
  private final Duration timeout;
  private final int popularWordCount;
  private final ForkJoinPool pool;
  private final int maxDepth;
  private final List<Pattern> ignoredUrls;

  @Inject
  ParallelWebCrawler(
      Clock clock,
      PageParserFactory parserFactory,
      @Timeout Duration timeout,
      @PopularWordCount int popularWordCount,
      @TargetParallelism int threadCount,
      @MaxDepth int maxDepth,
      @IgnoredUrls List<Pattern> ignoredUrls) {
    this.clock = clock;
    this.timeout = timeout;
    this.parserFactory = parserFactory;
    this.ignoredUrls = ignoredUrls;
    this.maxDepth = maxDepth;
    this.popularWordCount = popularWordCount;
    this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
  }

  @Override
  public CrawlResult crawl(List<String> startingUrls) {
    Instant deadline = clock.instant().plus(timeout);
    // Using Collections.synchronizedMap for the thread-safe data types
    // Avoid counts can be access at the same time
    // The same with Collections.synchronizedSet for visited urls
    // Using set because I don't want duplicate element
    Map<String, Integer> syncCounts = Collections.synchronizedMap(new HashMap<String, Integer>());
    Set<String> syncVisitedUrls = Collections.synchronizedSet(new HashSet<String>());

    for (String url : startingUrls) {
      pool.submit(new crawlInternalTask(url, deadline, maxDepth, syncCounts, syncVisitedUrls)).invoke();
    }

    if (syncCounts.isEmpty()) {
      return new CrawlResult.Builder()
        .setWordCounts(syncCounts)
        .setUrlsVisited(syncVisitedUrls.size())
        .build();
    }

    return new CrawlResult.Builder()
        .setWordCounts(WordCounts.sort(syncCounts, popularWordCount))
        .setUrlsVisited(syncVisitedUrls.size())
        .build();
  }

  @Override
  public int getMaxParallelism() {
    return Runtime.getRuntime().availableProcessors();
  }

  private final class crawlInternalTask extends RecursiveAction {
    private String url;
    private final Instant deadline;
    private int maxDepth;
    private final Map<String, Integer> counts;
    private Set<String> visitedUrls;

    public crawlInternalTask(String url, Instant deadline, int maxDepth, Map<String, Integer> counts,
        Set<String> visitedUrls) {
      this.url = url;
      this.deadline = deadline;
      this.maxDepth = maxDepth;
      this.counts = counts;
      this.visitedUrls = visitedUrls;
    }
    // Copy instance constructor
    public crawlInternalTask(crawlInternalTask duplicate) {
      this.url = duplicate.url;
      this.deadline = duplicate.deadline;
      this.maxDepth = duplicate.maxDepth;
      this.counts = duplicate.counts;
      this.visitedUrls = duplicate.visitedUrls;
    }
    // The ideal of Builder design pattern but in my style of coding
    private crawlInternalTask setMaxDepth(int maxDepth) {
      this.maxDepth = maxDepth;
      return this;
    }

    private crawlInternalTask setVisitedUrls(Set<String> visitedUrls) {
      this.visitedUrls = visitedUrls;
      return this;
    }

    private crawlInternalTask setUrl(String url) {
      this.url = url;
      return this;
    }

    @Override
    protected void compute() {
      // Using algorithm from SequentialWebCrawler
      if (maxDepth == 0 || clock.instant().isAfter(deadline)) {
        return;
      }
      for (Pattern pattern : ignoredUrls) {
        if (pattern.matcher(url).matches()) {
          return;
        }
      }
      if (visitedUrls.contains(url)) {
        return;
      }
      visitedUrls.add(url);
      PageParser.Result result = parserFactory.get(url).parse();
      for (Map.Entry<String, Integer> e : result.getWordCounts().entrySet()) {
        if (counts.containsKey(e.getKey())) {
          counts.put(e.getKey(), e.getValue() + counts.get(e.getKey()));
        } else {
          counts.put(e.getKey(), e.getValue());
        }
      }

      for (String link: result.getLinks()) {
        invokeAll(new crawlInternalTask(
                this.setMaxDepth(maxDepth - 1) // decrease maxDepth by 1 after 1 times recursive
                        .setVisitedUrls(visitedUrls) // update visitedUrls for next recursive
                        .setUrl(link) // set the start url
                )
        );
      }

    }
  }

}
