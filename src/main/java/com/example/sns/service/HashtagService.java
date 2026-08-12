package com.example.sns.service;

import com.example.sns.entity.Hashtag;
import com.example.sns.entity.Post;
import com.example.sns.entity.PostHashtag;
import com.example.sns.repository.HashtagRepository;
import com.example.sns.repository.PostHashtagRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HashtagService {
    private final HashtagRepository hashtagRepository;
    private final PostHashtagRepository postHashtagRepository;

    /** #뒤에 오는 한글·영문·숫자·밑줄을 태그로 본다 */
    private static final Pattern TAG_PATTERN =
            Pattern.compile("#([0-9A-Za-z가-힣_]+)");

    /** 한 게시글에 붙일 수 있는 최대 태그 수 */
    private static final int MAX_TAGS = 30;

    /** 태그 이름 최대 길이 (컬럼 길이와 맞춘다) */
    private static final int MAX_NAME_LENGTH = 50;

    /**
     * 본문에서 태그를 뽑아 게시글에 연결한다.
     * 수정 시에도 호출되며, 기존 연결을 지우고 다시 만든다.
     */
    @Transactional
    public void syncTags(Post post) {
        Set<String> names = extractTagNames(post.getContent());

        // 기존 연결 제거 후 다시 계산 (본문에서 사라진 태그를 떼어내기 위해)
        List<PostHashtag> existing = postHashtagRepository.findByPost(post);
        if (!existing.isEmpty()) {
            postHashtagRepository.deleteAll(existing);
            postHashtagRepository.flush();
        }

        for (String name : names) {
            Hashtag hashtag = hashtagRepository.findByName(name)
                    .orElseGet(() -> hashtagRepository.save(new Hashtag(name)));
            postHashtagRepository.save(new PostHashtag(post, hashtag));
        }
    }

    /**
     * 본문에서 태그 이름을 뽑는다.
     * 영문은 소문자로 통일하고, 중복은 제거한다. (#Cat 과 #cat 은 같은 태그)
     */
    public Set<String> extractTagNames(String content) {
        Set<String> names = new LinkedHashSet<>();
        if (content == null || content.isBlank()) {
            return names;
        }

        Matcher matcher = TAG_PATTERN.matcher(content);
        while (matcher.find() && names.size() < MAX_TAGS) {
            String name = matcher.group(1).toLowerCase();
            if (name.length() <= MAX_NAME_LENGTH) {
                names.add(name);
            }
        }
        return names;
    }
}
