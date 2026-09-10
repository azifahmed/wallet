package com.paytm.wallet.web;

import com.paytm.wallet.observability.LiveLogBuffer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequiredArgsConstructor
public class LiveLogsController {

    private static final long SSE_TIMEOUT_MS = 30L * 60L * 1000L;

    private final LiveLogBuffer liveLogBuffer;

    @GetMapping(path = "/logs", produces = MediaType.TEXT_HTML_VALUE)
    public String logsPage() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1"/>
              <title>Wallet Transfer — Live Logs</title>
              <style>
                :root { color-scheme: dark; }
                body {
                  margin: 0; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
                  background: #0b1020; color: #d7e0ff;
                }
                header {
                  position: sticky; top: 0; display: flex; gap: 1rem; align-items: baseline;
                  flex-wrap: wrap; padding: 0.75rem 1rem; background: #121933; border-bottom: 1px solid #243056;
                }
                h1 { margin: 0; font-size: 1rem; font-weight: 600; }
                #status { font-size: 0.85rem; opacity: 0.85; }
                #status.ok { color: #6dffb0; }
                #status.bad { color: #ff8f8f; }
                pre {
                  margin: 0; padding: 1rem; white-space: pre-wrap; word-break: break-word;
                  font-size: 0.8rem; line-height: 1.35;
                }
                .line { border-bottom: 1px solid #1a2340; padding: 0.15rem 0; }
              </style>
            </head>
            <body>
              <header>
                <h1>Live domain logs</h1>
                <span id="status">connecting…</span>
                <span style="opacity:0.7;font-size:0.8rem">Open this while running burst.sh</span>
              </header>
              <pre id="out"></pre>
              <script>
                const out = document.getElementById('out');
                const status = document.getElementById('status');
                const maxLines = 500;
                const es = new EventSource('/logs/stream');
                es.onopen = () => {
                  status.textContent = 'live';
                  status.className = 'ok';
                };
                es.onerror = () => {
                  status.textContent = 'disconnected — retrying…';
                  status.className = 'bad';
                };
                es.onmessage = (ev) => {
                  const div = document.createElement('div');
                  div.className = 'line';
                  div.textContent = ev.data;
                  out.appendChild(div);
                  while (out.childElementCount > maxLines) {
                    out.removeChild(out.firstChild);
                  }
                  window.scrollTo(0, document.body.scrollHeight);
                };
              </script>
            </body>
            </html>
            """;
    }

    @GetMapping(path = "/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs() throws IOException {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicReference<AutoCloseable> subscriptionRef = new AtomicReference<>();

        AutoCloseable subscription = liveLogBuffer.subscribe(line -> {
            if (closed.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().data(line));
            } catch (IOException | IllegalStateException ex) {
                closeQuietly(closed, subscriptionRef.get(), emitter);
            }
        });
        subscriptionRef.set(subscription);

        try {
            for (String line : liveLogBuffer.snapshot()) {
                emitter.send(SseEmitter.event().data(line));
            }
        } catch (IOException | IllegalStateException ex) {
            closeQuietly(closed, subscription, emitter);
            throw ex;
        }

        Runnable cleanup = () -> closeQuietly(closed, subscription, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        return emitter;
    }

    private static void closeQuietly(AtomicBoolean closed,
                                     AutoCloseable subscription,
                                     SseEmitter emitter) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (subscription != null) {
            try {
                subscription.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // ignore
        }
    }
}
