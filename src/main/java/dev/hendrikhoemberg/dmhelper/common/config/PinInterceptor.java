package dev.hendrikhoemberg.dmhelper.common.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

public class PinInterceptor implements HandlerInterceptor {

    public static final String PIN_COOKIE = "dm_pin";

    private final PinManager pinManager;

    public PinInterceptor(PinManager pinManager) {
        this.pinManager = pinManager;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String pin = extractPin(request);

        if (!pinManager.isValid(pin)) {
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
