package com.nhnacademy.workentry.entry.realtime.service.impl;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.nhnacademy.workentry.entry.realtime.dto.EntryRealtimeDto;
import com.nhnacademy.workentry.entry.realtime.service.EntryRealtimeService;
import com.nhnacademy.workentry.log.realtime.LogWebSocketHandler;
import com.nhnacademy.workentry.notify.adapter.NotifyAdapter;
import com.nhnacademy.workentry.notify.dto.EmailRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * InfluxDB에서 최근 하루 데이터를 조회하여
 * 실시간 출입 통계를 반환하는 서비스 클래스입니다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EntryRealtimeServiceImpl implements EntryRealtimeService {

    private final InfluxDBClient influxDBClient;
    private final LogWebSocketHandler logWebSocketHandler;
    private final NotifyAdapter notifyAdapter;

    @Value("${admin.email}")
    private String adminEmail;

    /**
     * 최근 24시간 이내의 출입 데이터를 1분 간격으로 집계합니다.
     *
     * @return EntryRealtimeDto 객체 (가장 최근 시간의 데이터)
     */
    @Override
    public EntryRealtimeDto getLatestEntry() {
        List<FluxTable> tables = getFluxTables();

        for (FluxTable table : tables) {
            for (FluxRecord fRecord : table.getRecords()) {
                LocalDateTime entryTime = Objects.requireNonNull(fRecord.getTime()).atZone(ZoneId.of("Asia/Seoul")).toLocalDateTime();
                int count = ((Number) Objects.requireNonNull(fRecord.getValue())).intValue();

                EntryRealtimeDto dto = new EntryRealtimeDto(entryTime, count);

                logAndBroadcast(dto, entryTime);

                return dto; // 정상적인 FluxRecord가 존재할 경우 무조건 바로 반환

            }
        }

        return new EntryRealtimeDto(LocalDateTime.now(ZoneId.of("Asia/Seoul")), 0);
    }

    @NotNull
    private List<FluxTable> getFluxTables() {
        String flux = """
                        from(bucket: "coffee-mqtt")
                          |> range(start: -1d)
                          |> filter(fn: (r) => r["_measurement"] == "sensor")
                          |> filter(fn: (r) => r["_field"] == "value")
                          |> filter(fn: (r) => r["location"] == "입구")
                          |> filter(fn: (r) => r["type"] == "activity")
                          |> aggregateWindow(every: 1m, fn: count, createEmpty: true)
                          |> sort(columns: ["_time"], desc: true)
                          |> limit(n: 1)
                      """;

        QueryApi queryApi = influxDBClient.getQueryApi();
        return queryApi.query(flux);
    }

    /**
     * 출입이 허용되지 않는 '절대 통제 시간대'에 해당하는지 여부를 판단합니다.
     * <p>
     * 일반적인 야근 시간(예: 18시~23시)은 허용되지만,
     * 보안상 출입이 엄격히 금지된 시간대(예: 자정 00시~새벽 4시 59분)는 '이상 출입'으로 간주합니다.
     * <p>
     * 이 메서드는 다음의 조건을 만족할 경우 true를 반환합니다:
     * <ul>
     *     <li>자정 00시(00:00)부터 오전 4시 59분(04:59) 사이</li>
     * </ul>
     * 이 외의 시간대는 정상 출입으로 간주되며 false를 반환합니다.
     *
     * @param time 출입이 감지된 시간
     * @return 출입이 금지된 시간대일 경우 true, 그렇지 않으면 false
     */
    private boolean isInTargetTime(LocalDateTime time) {
        int hour = time.getHour();

        return hour < 5; // 00:00~04:59, 이상 출입으로 간주
    }

    /**
     * 실시간 출입 데이터에 대한 로그를 기록하고 WebSocket을 통해 브로드캐스트합니다.
     * <p>
     * - 심야 시간대(entryTime이 지정된 범위 내)에는 경고(ALERT) 레벨로 로그를 남기고,
     * - 일반 시간대에는 정보(INFO) 레벨로 로그를 남깁니다.
     * <p>
     * 또한, 데이터 직렬화 실패 시 에러 로그를 출력하고 에러 메시지를 WebSocket으로 전송합니다.
     *
     * @param dto       출입 정보를 담은 DTO 객체 (시간, 출입자 수)
     * @param entryTime 출입이 감지된 시간 (심야 여부 판단에 사용)
     */
    private void logAndBroadcast(EntryRealtimeDto dto, LocalDateTime entryTime) {

        boolean isNight = isInTargetTime(entryTime);
        boolean hasEntry = dto.getCount() > 0;

        String logTime = entryTime.format( DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")); // "2025-06-05 21:44"
        String message;

        if (isNight && hasEntry) {
            message = String.format("[%s]이상 출입자 발생. 관리자는 확인 부탁드립니다.", logTime);
        } else {
            message = String.format("[%s] 현재 출입자 수는 %d명 입니다.", logTime, dto.getCount());
            log.info("[{}] 현재 출입자 수는 {}명입니다. (Session: {})",
                    logTime, dto.getCount(), RequestContextHolder.currentRequestAttributes().getSessionId());
        }


        // 로그 출력
        if (isNight && hasEntry) {
            EmailRequest notifyEntry = new EmailRequest(
                    adminEmail,
                    "⚠️ 이상 출입 감지 알림",
                    dto.getTime()+"\n이상 출입자 발생.\n관리자 확인 바랍니다.",
                    "ROLE_ADMIN"
            );
            notifyAdapter.sendTextEmail(notifyEntry);

            log.error(message);
        } else {
            log.info(message);
        }

        // WebSocket 방송
        logWebSocketHandler.broadcast(message);

    }

}