package com.nhnacademy.workentry.attendance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 출결 기록 정보를 담는 엔티티입니다.
 */
@Getter
@Entity
@Table(name = "attendances")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "mb_no", nullable = false)
    private Long mbNo;

    @Column(name = "work_date")
    private LocalDate workDate;

    @Column(name = "in_time")
    private LocalDateTime inTime;

    @Column(name = "out_time")
    private LocalDateTime outTime;

    @Column(name = "work_minutes")
    private Integer workMinutes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status", nullable = false)
    private AttendanceStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private Attendance(
            Long mbNo,
            LocalDate workDate,
            LocalDateTime inTime,
            LocalDateTime outTime,
            Integer workMinutes,
            AttendanceStatus status) {
        this.mbNo = mbNo;
        this.workDate = workDate;
        this.inTime = inTime;
        this.outTime = outTime;
        this.workMinutes = workMinutes;
        this.status = status;
    }

    public static Attendance newAttendance(Long mbNo, LocalDate workDate, LocalDateTime inTime, LocalDateTime outTime, Integer workMinutes, AttendanceStatus status) {
        return new Attendance(mbNo, workDate, inTime, outTime, workMinutes, status);
    }

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void updateCheckOut(LocalDateTime outTime, Integer workMinutes, AttendanceStatus status) {
        this.outTime = outTime;
        this.workMinutes = workMinutes;
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }
}
