package com.example.sns.service;

import com.example.sns.entity.User;
import com.example.sns.repository.UserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 댓글 본문의 @아이디를 찾아 실제 사용자로 바꿔준다.
 *
 * 해시태그와 달리 별도 테이블을 두지 않는다. 해시태그는 "#고양이가 달린 게시글 전부"처럼
 * 거꾸로 찾아가는 화면이 있어서 연결 테이블이 필요했지만, 멘션은 알림을 보내고 나면
 * 거꾸로 찾을 일이 없다. 저장해두면 본문과 어긋나지 않게 관리할 상태만 늘어난다.
 */
@Service
@RequiredArgsConstructor
public class MentionService {

    /**
     * 아이디 규칙(소문자, 숫자, 마침표, 밑줄)에 맞춰 잡는다.
     * 이메일이 걸리지 않도록 앞에 글자가 붙어 있으면 멘션으로 보지 않는다.
     */
    private static final Pattern MENTION_PATTERN =
            Pattern.compile("(?<![A-Za-z0-9._])@([a-z0-9._]{4,10})");

    private final UserRepository userRepository;

    /** 본문에 적힌 아이디들. 쓴 순서를 지키고 같은 사람은 한 번만 담는다 */
    public Set<String> extractUsernames(String content) {
        Set<String> names = new LinkedHashSet<>();
        if (content == null || content.isBlank()) {
            return names;
        }

        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * 본문의 멘션 중 실제로 존재하는 사용자만 돌려준다.
     * 없는 아이디는 링크도 알림도 없이 그냥 글자로 남는다.
     */
    public List<User> findMentionedUsers(String content) {
        Set<String> names = extractUsernames(content);
        return findByUsernames(names);
    }

    /** 여러 댓글의 멘션을 한 번에 조회할 때 쓴다 */
    public List<User> findByUsernames(Set<String> names) {
        if (names.isEmpty()) {
            return List.of();
        }
        return userRepository.findByUsernameIn(names);
    }
}
