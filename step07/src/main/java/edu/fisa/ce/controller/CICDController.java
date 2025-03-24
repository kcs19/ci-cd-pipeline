package edu.fisa.ce.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CICDController {
	
	@GetMapping("fisa")
	public String reqRes() {
		System.out.println("reqRes()");
		return "요청 응답 성공";
	}

}
