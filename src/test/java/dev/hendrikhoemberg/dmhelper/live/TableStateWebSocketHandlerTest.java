package dev.hendrikhoemberg.dmhelper.live;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class TableStateWebSocketHandlerTest {

    @Mock private ObjectMapper objectMapper;
    @Mock private TablePresentationService presentationService;
    @Mock private WebSocketSession session;

    @Test
    void serializesInitialStateAndBroadcastWritesForEachSession() throws Exception {
        AtomicReference<Runnable> stateChange = new AtomicReference<>();
        doAnswer(invocation -> {
            stateChange.set(invocation.getArgument(0));
            return null;
        }).when(presentationService).setOnStateChange(any());

        AtomicInteger stateReads = new AtomicInteger();
        CountDownLatch broadcastReadState = new CountDownLatch(1);
        when(presentationService.getCurrentState()).thenAnswer(invocation -> {
            if (stateReads.incrementAndGet() == 2) {
                broadcastReadState.countDown();
            }
            return LiveTableState.curtain();
        });
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(session.getId()).thenReturn("player-1");
        when(session.isOpen()).thenReturn(true);

        AtomicInteger sendsInProgress = new AtomicInteger();
        CountDownLatch initialSendEntered = new CountDownLatch(1);
        CountDownLatch releaseInitialSend = new CountDownLatch(1);
        CountDownLatch overlappingSendEntered = new CountDownLatch(1);
        doAnswer(invocation -> {
            int concurrentSends = sendsInProgress.incrementAndGet();
            try {
                if (concurrentSends == 1) {
                    initialSendEntered.countDown();
                    if (!releaseInitialSend.await(2, TimeUnit.SECONDS)) {
                        throw new AssertionError("initial send was not released");
                    }
                } else {
                    overlappingSendEntered.countDown();
                }
                return null;
            } finally {
                sendsInProgress.decrementAndGet();
            }
        }).when(session).sendMessage(any(TextMessage.class));

        TableStateWebSocketHandler handler =
                new TableStateWebSocketHandler(objectMapper, presentationService);
        AtomicReference<Throwable> threadFailure = new AtomicReference<>();
        Thread connection = thread("connection", threadFailure,
                () -> handler.afterConnectionEstablished(session));
        connection.start();
        assertThat(initialSendEntered.await(2, TimeUnit.SECONDS)).isTrue();

        Thread broadcast = thread("broadcast", threadFailure, stateChange.get());
        broadcast.start();
        assertThat(broadcastReadState.await(2, TimeUnit.SECONDS)).isTrue();
        boolean writesOverlapped = overlappingSendEntered.await(500, TimeUnit.MILLISECONDS);

        releaseInitialSend.countDown();
        connection.join(2_000);
        broadcast.join(2_000);

        assertThat(connection.isAlive()).isFalse();
        assertThat(broadcast.isAlive()).isFalse();
        assertThat(threadFailure.get()).isNull();
        assertThat(writesOverlapped).as("a WebSocketSession must have at most one writer").isFalse();
        verify(session, times(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    void initialMessageNeverContainsAudioData() throws Exception {
        AtomicReference<Runnable> stateChange = new AtomicReference<>();
        doAnswer(invocation -> {
            stateChange.set(invocation.getArgument(0));
            return null;
        }).when(presentationService).setOnStateChange(any());

        when(presentationService.getCurrentState()).thenReturn(LiveTableState.curtain());
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"type\":\"TABLE_STATE\",\"mode\":\"CURTAIN\"}");
        when(session.getId()).thenReturn("player-safety");
        when(session.isOpen()).thenReturn(true);

        TableStateWebSocketHandler handler =
                new TableStateWebSocketHandler(objectMapper, presentationService);

        var captured = ArgumentCaptor.forClass(TextMessage.class);
        handler.afterConnectionEstablished(session);
        verify(session).sendMessage(captured.capture());
        String payload = captured.getValue().getPayload();
        assertThat(payload)
                .doesNotContain("audio")
                .doesNotContain("cue")
                .doesNotContain("provider")
                .doesNotContain("YOUTUBE");
    }

    private Thread thread(String name, AtomicReference<Throwable> failure, Runnable action) {
        return new Thread(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
        }, name);
    }
}
