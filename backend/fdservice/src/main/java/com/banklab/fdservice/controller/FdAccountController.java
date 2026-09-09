package com.banklab.fdservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.banklab.fdservice.dto.CreateFdAccountRequest;
import com.banklab.fdservice.service.FdAccountService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/fd-accounts")
@RequiredArgsConstructor
public class FdAccountController {

    private final FdAccountService fdAccountService;

    @PostMapping
    public ResponseEntity<com.banklab.fdservice.dto.FdAccount> createAccount(
            @Valid @RequestBody CreateFdAccountRequest request) {
        com.banklab.fdservice.dto.FdAccount created = fdAccountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
