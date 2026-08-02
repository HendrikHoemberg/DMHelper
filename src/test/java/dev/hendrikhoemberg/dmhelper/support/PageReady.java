package dev.hendrikhoemberg.dmhelper.support;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

/**
 * Navigation for the browser gates.
 *
 * <p>{@code NETWORKIDLE} is defined as half a second of network silence, so it bills 500ms to
 * every navigation whether or not the page has anything left to fetch. Nearly every page in
 * this application is composed by Thymeleaf before the response is written, and for those the
 * wait buys nothing — at 127 navigations it was the bulk of {@code ShellRenderGateTest}'s 66
 * second runtime. {@code LOAD} still waits for the document and its subresources, which is
 * what the layout and computed-style measurements need.
 *
 * <p>Two route families genuinely are not finished when the document is, and they keep the old
 * wait. {@code /library} leaves ten result panes empty and fills them from
 * {@code hx-trigger="load"} — a pane that swaps in a wide table is exactly what the overflow
 * assertions exist to catch. The cockpit composes its module geometry in JavaScript after
 * DOMContentLoaded, so a gate that measures module rectangles needs it settled first.
 */
public final class PageReady {

    private PageReady() {
    }

    /**
     * True when the server's response is not yet the final DOM for this route. The library's
     * per-category routes are {@code /library?tab=…}, so the query form has to match too —
     * matching only the bare path would hand the reference gate a page whose result panes are
     * still empty, and its card assertions would measure nothing.
     */
    public static boolean settlesAfterLoad(String path) {
        return path.equals("/library") || path.startsWith("/library?") || path.endsWith("/session");
    }

    /** Navigate to {@code base + path} and wait for whichever state that route actually needs. */
    public static void open(Page page, String base, String path) {
        page.navigate(base + path);
        page.waitForLoadState(settlesAfterLoad(path) ? LoadState.NETWORKIDLE : LoadState.LOAD);
    }
}
