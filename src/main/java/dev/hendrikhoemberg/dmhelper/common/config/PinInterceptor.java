package dev.hendrikhoemberg.dmhelper.common.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

record RateLimitEntry(int failures, long firstFailureTime, long blockedUntil) {
}

public class PinInterceptor implements HandlerInterceptor {

    public static final String PIN_COOKIE = "dm_pin";

    private final PinManager pinManager;
    private final Map<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();

    private static final int FAILURE_THRESHOLD_SLOWDOWN = 5;
    private static final int FAILURE_THRESHOLD_BLOCK = 10;
    private static final long BLOCK_DURATION_MS = 30_000;
    private static final long SLOWDOWN_DELAY_MS = 2_000;

    public PinInterceptor(PinManager pinManager) {
        this.pinManager = pinManager;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String pin = extractPin(request);
        String ip = request.getRemoteAddr();

        RateLimitEntry entry = rateLimitMap.get(ip);
        if (entry != null && entry.blockedUntil() > System.currentTimeMillis()) {
            response.setStatus(429);
            response.setContentType("text/plain");
            response.getWriter().write("Too many PIN attempts");
            response.getWriter().flush();
            return false;
        }
        if (entry != null && entry.blockedUntil() > 0) {
            rateLimitMap.remove(ip);
        }

        if (!pinManager.isValid(pin)) {
            RateLimitEntry current = rateLimitMap.merge(ip,
                    new RateLimitEntry(1, System.currentTimeMillis(), 0),
                    (old, val) -> new RateLimitEntry(old.failures() + 1, old.firstFailureTime(), 0));

            if (current.failures() >= FAILURE_THRESHOLD_BLOCK) {
                rateLimitMap.put(ip, new RateLimitEntry(current.failures(), current.firstFailureTime(),
                        System.currentTimeMillis() + BLOCK_DURATION_MS));
                response.setStatus(429);
                response.setContentType("text/plain");
                response.getWriter().write("Too many PIN attempts");
                response.getWriter().flush();
                return false;
            }

            if (current.failures() >= FAILURE_THRESHOLD_SLOWDOWN) {
                Thread.sleep(SLOWDOWN_DELAY_MS);
            }

            response.setStatus(403);
            response.setContentType("text/html");
            response.getWriter().write("""
                    <!DOCTYPE html>
                    <html lang="en" data-theme="dark">
                    <head><style>
                        :root{--color-bg:#1a1a2e;--color-text:#e0e0e0;--color-accent:#7b68ee;--color-text-muted:#8888a0;--color-surface:#16213e;--color-border:#2a2a4a;--radius:8px}
                        body{background:var(--color-bg);color:var(--color-text);font-family:'Segoe UI',system-ui,sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0}
                        .pin-box{background:var(--color-surface);border:1px solid var(--color-border);border-radius:var(--radius);padding:32px;text-align:center;max-width:400px}
                        h1{color:var(--color-accent);margin-bottom:8px}h3{color:var(--color-text-muted);margin-bottom:24px}
                        input{padding:10px 16px;font-size:18px;text-align:center;letter-spacing:4px;background:var(--color-bg);color:var(--color-text);border:1px solid var(--color-border);border-radius:var(--radius);width:100%;box-sizing:border-box}
                        input:focus{outline:none;border-color:var(--color-accent)}
                        .error{color:#e74c3c;margin-top:8px;font-size:.875rem;display:none}
                        button{margin-top:16px;padding:8px 24px;background:var(--color-accent);color:#fff;border:none;border-radius:var(--radius);font-size:1rem;cursor:pointer}
                        button:hover{opacity:.9}
                    </style></head>
                    <body><div class="pin-box">
                        <h1>DMHelper</h1>
                        <h3>Enter the session PIN to continue</h3>
                        <form method="post" action="/dm/authenticate" onsubmit="event.preventDefault();submitPin()">
                            <input type="text" name="pin" id="pinInput" placeholder="XXXXXX" maxlength="6" autofocus autocomplete="off">
                            <div class="error" id="pinError">Invalid PIN</div>
                            <button type="submit">Unlock</button>
                        </form>
                        <script>
                            function submitPin(){
                                var pin=document.getElementById('pinInput').value.toUpperCase();
                                var days=365;
                                document.cookie='dm_pin='+pin+';path=/;max-age='+(days*24*60*60)+';SameSite=Lax';
                                location.reload();
                            }
                        </script>
                    </div></body></html>
                    """);
            response.getWriter().flush();
            return false;
        }

        rateLimitMap.remove(ip);
        return true;
    }

    private String extractPin(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                    .filter(c -> PIN_COOKIE.equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse("");
        }
        return "";
    }
}
