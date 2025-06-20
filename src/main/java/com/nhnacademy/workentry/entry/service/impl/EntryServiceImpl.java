package com.nhnacademy.workentry.entry.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.nhnacademy.workentry.entry.dto.EntryCountDto;
import com.nhnacademy.workentry.entry.service.EntryService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * InfluxDB에서 출입 데이터(센서 데이터)를 조회하여
 * 일별 출입 횟수를 반환하는 서비스 클래스입니다.
 *
 * <p>이 서비스는 InfluxDBClient를 통해 Flux 쿼리를 실행하고,
 * 응답 결과를 List 형태로 가공하여 반환합니다.</p>
 */
@Slf4j
@Service
@AllArgsConstructor
public class EntryServiceImpl implements EntryService {
    private final InfluxDBClient influxDBClient;
    private final ObjectMapper objectMapper;

    /**
     * InfluxDB에 저장된 센서 데이터를 조회하여
     * 최근 7일간의 일별 출입 횟수를 가져옵니다.
     *
     * <p>Flux 쿼리를 통해 '입구' 위치의 'activity(인원이 지나간 횟수)' 타입 센서 값을 집계하여,
     * 날짜별 출입 횟수를 계산합니다.</p>
     * 쿼리문 속 range는 해당 데이터 기간설정
     * aggregateWindow 속 every 뒤엔 받아올 시간 및 월별 단위 작성
     * @return 날짜와 해당 날짜의 출입 횟수를 담은 EntryCountDto 객체 리스트
     */
    @Override
    public List<EntryCountDto> getWeeklyEntryCounts() {
        List<FluxTable> tables = getFluxTables();
        log.info("FluxTable 수: {}", tables.size());
        List<EntryCountDto> result = new ArrayList<>();

        for (FluxTable table : tables) {
            log.info("Record 수: {}", table.getRecords().size());
            for (FluxRecord fRecord : table.getRecords()) {
                log.info("Record: time={}, value={}", fRecord.getTime(), fRecord.getValue());

                String date = Objects.requireNonNull(fRecord.getTime()).toString().substring(0, 10);
                int count = ((Number) Objects.requireNonNull(fRecord.getValue())).intValue();
                EntryCountDto dto = new EntryCountDto(date, count);

                try {
                    String json = objectMapper.writeValueAsString(dto);
                    String fallbackMessage = "[Influx Entry] " + json;
                    log.info(fallbackMessage);

                } catch (JsonProcessingException e) {
                    log.error("[Influx Entry] JSON 직렬화 실패", e);
                }

                result.add(dto);
            }
        }

        return result;
    }

    @NotNull
    private List<FluxTable> getFluxTables() {
        // 현재 날짜 기준으로 이번 주 월요일 00:00 (한국 시간, Asia/Seoul) 계산
        // InfluxDB는 UTC 기반이므로, 이를 UTC로 변환한 ISO 8601 형식으로 사용함
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        ZonedDateTime startOfWeek = monday.atStartOfDay(ZoneId.of("Asia/Seoul")).withZoneSameInstant(ZoneOffset.UTC);
        String startTime = startOfWeek.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);


        String flux = String.format("""
            import "date"
            from(bucket: "coffee-mqtt")
              |> range(start: %s)
              |> filter(fn: (r) => r["_measurement"] == "sensor")
              |> filter(fn: (r) => r["_field"] == "value")
              |> filter(fn: (r) => r["location"] == "입구")
              |> filter(fn: (r) => r["type"] == "activity")
              |> aggregateWindow(every: 1d, fn: count, createEmpty: false)
              |> map(fn: (r) => ({ r with _time: date.truncate(t: r._time, unit: 1d) }))  // _time 자정으로 통일
              |> group(columns: ["_time"])
              |> sum(column: "_value")
              |> keep(columns: ["_time", "_value"])
              |> yield(name: "daily_count")
        """, startTime);

        QueryApi queryApi = influxDBClient.getQueryApi();
        return queryApi.query(flux);
    }
}
