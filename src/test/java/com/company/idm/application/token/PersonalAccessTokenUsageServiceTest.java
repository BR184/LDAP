package com.company.idm.application.token;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.company.idm.domain.token.PersonalAccessToken;
import com.company.idm.domain.token.PersonalAccessTokenRepository;
import com.company.idm.infrastructure.config.PersonalAccessTokenProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PersonalAccessTokenUsageServiceTest {

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-08-03T10:00:00Z"),
        ZoneId.of("Asia/Shanghai")
    );

    private final PersonalAccessTokenRepository repository = mock(PersonalAccessTokenRepository.class);
    private final PersonalAccessTokenProperties properties = new PersonalAccessTokenProperties();
    private final PersonalAccessTokenUsageService service = new PersonalAccessTokenUsageService(
        repository,
        properties,
        CLOCK
    );

    @Test
    void throttlesRecentUsageWrites() {
        PersonalAccessToken token = PersonalAccessToken.builder()
            .id(11L)
            .lastUsedAt(now().minusMinutes(1))
            .build();

        service.recordSuccessfulUse(token, "127.0.0.1");

        verify(repository, never()).updateLastUsedIfBefore(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void writesUsageAfterTheConfiguredWindow() {
        PersonalAccessToken token = PersonalAccessToken.builder()
            .id(11L)
            .lastUsedAt(now().minusMinutes(6))
            .build();

        service.recordSuccessfulUse(token, "127.0.0.1");

        verify(repository).updateLastUsedIfBefore(
            11L,
            now(),
            "127.0.0.1",
            now().minusMinutes(5)
        );
    }

    @Test
    void coalescesConcurrentUsageWritesForTheSameToken() throws Exception {
        PersonalAccessToken token = PersonalAccessToken.builder()
            .id(11L)
            .lastUsedAt(now().minusMinutes(6))
            .build();
        int concurrency = 10;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        List<Future<?>> futures = new ArrayList<>(concurrency);

        try {
            for (int index = 0; index < concurrency; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    service.recordSuccessfulUse(token, "127.0.0.1");
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        verify(repository, times(1)).updateLastUsedIfBefore(
            11L,
            now(),
            "127.0.0.1",
            now().minusMinutes(5)
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.now(CLOCK);
    }
}
