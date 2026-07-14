package com.example.sns.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SuspendedUserDto {
    private Long id;
    private String username;
    private String suspendedUntil;
}
