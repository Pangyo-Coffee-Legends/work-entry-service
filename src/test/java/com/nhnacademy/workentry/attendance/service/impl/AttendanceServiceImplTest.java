package com.nhnacademy.workentry.attendance.service.impl;

import com.nhnacademy.workentry.attendance.constant.AttendanceStatusConstants;
import com.nhnacademy.workentry.attendance.dto.AttendanceDto;
import com.nhnacademy.workentry.attendance.dto.AttendanceRequest;
import com.nhnacademy.workentry.attendance.entity.Attendance;
import com.nhnacademy.workentry.attendance.entity.AttendanceStatus;
import com.nhnacademy.workentry.attendance.repository.AttendanceRepository;
import com.nhnacademy.workentry.attendance.repository.AttendanceStatusRepository;
import com.nhnacademy.workentry.common.exception.AttendanceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceImplTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private AttendanceStatusRepository attendanceStatusRepository;

    @InjectMocks
    private AttendanceServiceImpl attendanceService;

    @Test
    @DisplayName("특정 회원의 전체 출결 기록 조회 테스트")
    void testGetAttendanceByNo() {
        Long mbNo = 1L;
        Attendance attendance = mock(Attendance.class);
        when(attendanceRepository.findAllByMbNo(mbNo)).thenReturn(List.of(attendance));

        List<AttendanceDto> result = attendanceService.getAttendanceByNo(mbNo);

        assertThat(result).hasSize(1);
        verify(attendanceRepository).findAllByMbNo(mbNo);
    }

    @Test
    @DisplayName("특정 회원의 지정된 기간 내 출결 기록을 페이지 단위로 조회 테스트")
    void testGetAttendanceByNoAndDateRange() {
        Long mbNo = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        LocalDateTime start = LocalDate.now().minusDays(5).atStartOfDay();
        LocalDateTime end = LocalDate.now().atStartOfDay();

        AttendanceDto mockDto = new AttendanceDto(
                1L, mbNo,
                LocalDate.now(),
                start, end,
                "출근"
        );

        Page<AttendanceDto> expectedPage = new PageImpl<>(List.of(mockDto));

        when(attendanceRepository.getAttendanceByNoAndDateRange(mbNo, start.toLocalDate(), end.toLocalDate(), pageable))
                .thenReturn(expectedPage);

        Page<AttendanceDto> result = attendanceService.getAttendanceByNoAndDateRange(mbNo, start.toLocalDate(), end.toLocalDate(), pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getMbNo()).isEqualTo(mbNo);
        verify(attendanceRepository).getAttendanceByNoAndDateRange(mbNo, start.toLocalDate(), end.toLocalDate(), pageable);
    }

    @Test
    @DisplayName("출근 기록 생성 테스트")
    void testCreateAttendance() {
        AttendanceRequest request = new AttendanceRequest();
        request.setMbNo(1L);
        request.setWorkDate(LocalDate.now());
        request.setCheckIn(LocalDateTime.now());
        request.setCheckOut(LocalDateTime.now().plusHours(8));
        request.setWorkMinutes(480);
        request.setStatus(AttendanceStatusConstants.STATUS_PRESENT);

        AttendanceStatus mockStatus = new AttendanceStatus();
        when(attendanceStatusRepository.findByDescription(request.getStatus()))
                .thenReturn(Optional.of(mockStatus));

        attendanceService.createAttendance(request);

        verify(attendanceRepository, times(1)).save(any(Attendance.class));
    }

    @Test
    @DisplayName("출근 기록 생성 테스트")
    void testCheckOut_successful() {
        Long mbNo = 1L;
        LocalDate workDate = LocalDate.now();

        Attendance attendance = mock(Attendance.class);
        AttendanceStatus status = new AttendanceStatus();
        when(attendanceRepository.findByMbNoAndWorkDate(mbNo, workDate)).thenReturn(Optional.of(attendance));
        when(attendance.getStatus()).thenReturn(new AttendanceStatus(null, AttendanceStatusConstants.STATUS_PRESENT));
        when(attendanceStatusRepository.findByDescription(AttendanceStatusConstants.STATUS_PRESENT))
                .thenReturn(Optional.of(status));
        when(attendance.getInTime()).thenReturn(LocalDateTime.now().minusHours(9));

        attendanceService.checkOut(mbNo, workDate);

        verify(attendanceRepository).save(attendance);
    }

    @Test
    @DisplayName("존재하지 않는 출근 기록 에러 발생 테스트")
    void testCheckOut_attendanceNotFound() {
        Long mbNo = 999L;
        LocalDate workDate = LocalDate.now();

        when(attendanceRepository.findByMbNoAndWorkDate(mbNo, workDate)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attendanceService.checkOut(mbNo, workDate))
                .isInstanceOf(AttendanceNotFoundException.class);
    }

    @Test
    @DisplayName("존재하지 않는 근무 통계 조회 exception 발생 테스트")
    void testGetRecentWorkingHoursByMember_noRecords() {
        Long mbNo = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        Page<Attendance> emptyPage = Page.empty();

        when(attendanceRepository.findByMbNoAndWorkDateBetween(any(), any(), any(), eq(pageable)))
                .thenReturn(emptyPage);

        assertThatThrownBy(() -> attendanceService.getRecentWorkingHoursByMember(mbNo, pageable))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    @DisplayName("최근 일주일 출결 정보 조회 테스트")
    void testGetRecentAttendanceSummary() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Attendance> page = new PageImpl<>(List.of(mock(Attendance.class)));

        when(attendanceRepository.findByWorkDateBetween(any(), any(), eq(pageable))).thenReturn(page);

        Page<AttendanceDto> result = attendanceService.getRecentAttendanceSummary(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }
}