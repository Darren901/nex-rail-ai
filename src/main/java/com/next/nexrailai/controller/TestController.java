package com.next.nexrailai.controller;

import com.next.nexrailai.dto.ThsrTimetableDTO;
import com.next.nexrailai.service.ThsrTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    private final ThsrTicketService thstTicketService;

    @GetMapping("/search")
    public List<ThsrTimetableDTO> testSearch(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam String date,
            @RequestParam(required = false) String time) {
        return thstTicketService.searchTickets(from, to, date, time);
    }
}
