package com.nhnacademy.workentry.member.controller;



import com.nhnacademy.workentry.member.dto.MemberInfoResponse;
import com.nhnacademy.workentry.member.dto.MemberResponse;
import com.nhnacademy.workentry.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 회원(Member)에 대한 HTTP 요청을 처리하는 컨트롤러 클래스입니다.
 * <p>
 * 회원 등록, 조회, 수정, 탈퇴 기능을 RESTful API 형태로 제공합니다.
 * URI 및 HTTP 메서드 규약에 따라 설계되어 있으며, 클라이언트-서버 간 명확한 역할 구분을 지원합니다.
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    /**
     * 이메일로 회원 정보를 조회합니다.
     *
     * @param mbEmail 조회할 회원 이메일
     * @return 해당 회원의 정보 (HTTP 200 OK)
     */
    @GetMapping("/email/{mbEmail}")
    public ResponseEntity<MemberResponse> getMemberByEmail(@PathVariable String mbEmail) {
        MemberResponse response = memberService.getMbEmail(mbEmail);
        return ResponseEntity.ok(response);
    }

    /**
     * 전체 회원의 요약 정보를 조회합니다.
     *
     * @return 회원 요약 정보 리스트 (HTTP 200 OK)
     */
    @GetMapping("/info-list")
    public ResponseEntity<List<MemberInfoResponse>> getMemberInfoList() {
        List<MemberInfoResponse> memberInfoList = memberService.getMemberInfoList();
        return ResponseEntity.ok(memberInfoList);
    }
}
