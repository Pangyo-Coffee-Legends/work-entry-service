package com.nhnacademy.workentry.attendance.controller;

import com.nhnacademy.workentry.attendance.dto.AttendanceDto;
import com.nhnacademy.workentry.attendance.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 출결 관련 REST API 컨트롤러입니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/attendances")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping("/{no}")
    public List<AttendanceDto> getAttendanceByNo(
            @PathVariable Long no,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        log.info("🔍 회원 {}의 출결 조회 요청: {} ~ {}", no, start, end);
        List<AttendanceDto> result = attendanceService.getAttendanceByNoAndDateRange(no, start, end);
        log.debug("조회된 출결 수: {}", result.size());

        return result;
    }

    @GetMapping("/summary/recent")
    public List<AttendanceDto> getRecentAttendanceSummary() {
        log.info("📊 최근 7일 출결 요약 요청");
        List<AttendanceDto> result = attendanceService.getRecentAttendanceSummary();
        log.debug("요약된 출결 수: {}", result.size());

        return result;
    }
}
