package edu.fisa.ce.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class CICDController {
	
	@GetMapping("/canary")
	public String reqRes() {
		System.out.println("version1");
		log.info("**요청 & 응답**");
		return "요청 응답 성공";
	}
	
	@GetMapping("/fisa1")
	public String reqRes1() {
		System.out.println("reqRes()");
		log.info("**요청 & 응답**");
		
		for(int i=1; i<=10; i++) {
			System.out.println("data 값"+ i);
		}
		
		return "요청 응답 성공";
	}
}
