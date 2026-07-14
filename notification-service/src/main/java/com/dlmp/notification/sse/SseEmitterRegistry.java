package com.dlmp.notification.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds one SseEmitter per connected browser tab, keyed by userId (a user can
 * have several tabs open). Works with a single notification-service instance
 * (Render's free tier runs exactly one) — no cross-instance fan-out needed.
 */
@Component
@Slf4j
public class SseEmitterRegistry {

    // Just under typical proxy/load-balancer idle-connection timeouts; the
    // browser's native EventSource auto-reconnects on close, so this is a
    // deliberate, harmless disconnect-and-resume rather than a failure.
    private static final long TIMEOUT_MS = 55_000;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public SseEmitter register(String userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emittersByUser.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(ex -> remove(userId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("status", "connected")));
        } catch (IOException e) {
            remove(userId, emitter);
        }
        return emitter;
    }

    public void push(String userId, Object payload) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null || emitters.isEmpty()) return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(payload));
            } catch (IOException | IllegalStateException e) {
                remove(userId, emitter);
            }
        }
    }

    /** Keeps idle connections alive through proxies that would otherwise close them. */
    @Scheduled(fixedRate = 20_000)
    public void heartbeat() {
        emittersByUser.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    remove(userId, emitter);
                }
            }
        });
    }

    private void remove(String userId, SseEmitter emitter) {
        List<SseEmitter> list = emittersByUser.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emittersByUser.remove(userId);
        }
    }
}
