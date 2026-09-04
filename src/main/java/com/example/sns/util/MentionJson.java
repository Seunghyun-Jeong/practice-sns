package com.example.sns.util;

import com.example.sns.dto.MentionDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 화면에서 멘션을 링크로 만들 때 쓸 자료를 data 속성에 담아준다.
 *
 * 본문에는 아이디만 있어서 브라우저 쪽에서 프로필 주소를 만들 수가 없다.
 * 프로필 주소가 아이디가 아니라 id 기준이기 때문인데, 아이디로 되돌리면
 * 예전에 겪었던 문제(아이디를 바꾸면 기존 주소가 404)가 그대로 돌아온다.
 * 그래서 서버가 아이디와 id의 짝을 같이 내려준다.
 */
@Component
@RequiredArgsConstructor
public class MentionJson {
    private final ObjectMapper objectMapper;

    public String toJson(List<MentionDto> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(mentions);
        } catch (JsonProcessingException e) {
            // 링크가 안 걸릴 뿐 글은 그대로 보이면 되므로 조용히 넘어간다
            return "";
        }
    }
}
