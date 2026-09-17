package com.example.sns.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportCreateRequest {
    /** Report.Reason 의 이름 */
    private String reason;
    private String detail;
}
