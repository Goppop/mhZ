package com.policyradar;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 政府网站选择器调试工具
 *
 * 使用方法：
 *   修改 TARGET_URL 和下方的 selectors，直接 main() 运行，
 *   观察控制台输出，调整选择器直到结果正确。
 *
 * 不需要启动 Spring，纯 Java main 方法，秒级反馈。
 */
public class SelectorDebugTest {

    // ============================================================
    // ★ 改这里：目标网站
    // ============================================================
    static final String TARGET_URL = "https://www.ndrc.gov.cn/xxgk/zcfb/tz/";

    // ── 列表页选择器 ──
    static final String LIST_ITEM_SELECTOR = "ul.u-list li";     // 每一行
    static final String ITEM_LINK_SELECTOR = "a[href]";          // 行内链接
//    static final String ITEM_DATE_SELECTOR = "span.date, span.time"; // 行内日期
    static final String ITEM_DATE_SELECTOR = "li > span:last-child, li span"; // 行内日期


    // ── 详情页选择器（可选：如果想顺便测详情页） ──
    static final boolean TEST_DETAIL = false;  // 改成 true 会额外抓第一条详情页
    static final String DETAIL_TITLE_SEL   = "h1.title, h1";
    static final String DETAIL_CONTENT_SEL = "div.TRS_Editor, div.article-content";
    static final String DETAIL_DATE_SEL    = "div.article-time span, span.time";
    static final String DETAIL_AGENCY_SEL  = "div.article-source, span.source";

    // ============================================================

    static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    static final Pattern DATE_PATTERN =
            Pattern.compile("(\\d{4})[年/\\-](\\d{1,2})[月/\\-](\\d{1,2})日?");

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(60));
        System.out.println("目标: " + TARGET_URL);
        System.out.println("=".repeat(60));

        Document doc = Jsoup.connect(TARGET_URL)
                .userAgent(UA)
                .timeout(15_000)
                .get();

        System.out.println("页面标题: " + doc.title());
        System.out.println();

        // ── 测试列表选择器 ──
        Elements items = doc.select(LIST_ITEM_SELECTOR);
        System.out.printf("列表行 [%s] → 共 %d 条%n", LIST_ITEM_SELECTOR, items.size());
        System.out.println("-".repeat(60));

        int printed = 0;
        String firstDetailUrl = null;

        for (Element item : items) {
            if (printed >= 5) {
                System.out.printf("  ... 还有 %d 条（只显示前5）%n", items.size() - 5);
                break;
            }

            Element link = item.selectFirst(ITEM_LINK_SELECTOR);
            String href  = link != null ? link.absUrl("href") : "【未找到链接】";
            String title = link != null ? link.text().trim()  : "【未找到标题】";

            Element dateEl = item.selectFirst(ITEM_DATE_SELECTOR);
            String dateStr = dateEl != null ? dateEl.text().trim() : "【未找到日期】";
            LocalDate date = parseDate(dateStr);

            System.out.printf("  标题: %s%n", title.isEmpty() ? "【空】" : title);
            System.out.printf("  链接: %s%n", href);
            System.out.printf("  日期: %s → 解析: %s%n", dateStr, date);
            System.out.println();

            if (firstDetailUrl == null && !href.startsWith("【")) {
                firstDetailUrl = href;
            }
            printed++;
        }

        if (items.isEmpty()) {
            System.out.println("  ⚠ 没有找到任何列表行！请检查选择器：" + LIST_ITEM_SELECTOR);
            System.out.println("  提示：在浏览器 Console 里执行：");
            System.out.println("    document.querySelectorAll('" + LIST_ITEM_SELECTOR + "').length");
        }

        // ── 测试详情页（可选） ──
        if (TEST_DETAIL && firstDetailUrl != null) {
            System.out.println("=".repeat(60));
            System.out.println("详情页: " + firstDetailUrl);
            System.out.println("=".repeat(60));

            Document detail = Jsoup.connect(firstDetailUrl)
                    .userAgent(UA)
                    .timeout(15_000)
                    .get();

            System.out.println("标题 [" + DETAIL_TITLE_SEL + "]: "
                    + textOrMissing(detail, DETAIL_TITLE_SEL));
            System.out.println("日期 [" + DETAIL_DATE_SEL + "]: "
                    + textOrMissing(detail, DETAIL_DATE_SEL));
            System.out.println("机构 [" + DETAIL_AGENCY_SEL + "]: "
                    + textOrMissing(detail, DETAIL_AGENCY_SEL));

            String content = textOrMissing(detail, DETAIL_CONTENT_SEL);
            System.out.println("正文 [" + DETAIL_CONTENT_SEL + "]: "
                    + (content.startsWith("【") ? content : content.substring(0, Math.min(200, content.length())) + "…"));
        }

        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("调试完成。选择器正确后，按下方 SQL 注册为数据源：");
        printSqlTemplate();
    }

    static String textOrMissing(Document doc, String selector) {
        for (String s : selector.split(",")) {
            Elements el = doc.select(s.trim());
            if (!el.isEmpty() && !el.first().text().isBlank()) {
                return el.first().text().trim();
            }
        }
        return "【未找到，选择器: " + selector + "】";
    }

    static LocalDate parseDate(String text) {
        if (text == null) return null;
        Matcher m = DATE_PATTERN.matcher(text);
        if (m.find()) {
            try {
                return LocalDate.of(
                        Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)));
            } catch (Exception ignored) {}
        }
        return null;
    }

    static void printSqlTemplate() {
        System.out.println();
        System.out.println("-- 直接爬列表页（type=HTML）");
        System.out.println("INSERT INTO policy_data_source (name, type, config, enabled, cron_expr) VALUES (");
        System.out.println("  '发改委通知公告',");
        System.out.println("  'HTML',");
        System.out.printf("  '{\"url\":\"%s\",%n", TARGET_URL);
        System.out.printf("    \"list_selector\":\"%s\",%n", LIST_ITEM_SELECTOR + " " + ITEM_LINK_SELECTOR);
        System.out.println("    \"detail_selectors\":{");
        System.out.printf("      \"title\":\"%s\",%n", DETAIL_TITLE_SEL);
        System.out.printf("      \"publishDate\":\"%s\",%n", ITEM_DATE_SELECTOR);
        System.out.printf("      \"content\":\"%s\"%n", DETAIL_CONTENT_SEL);
        System.out.println("    }");
        System.out.println("  }',");
        System.out.println("  1, '0 0 8 * * ?'");
        System.out.println(");");
    }
}
