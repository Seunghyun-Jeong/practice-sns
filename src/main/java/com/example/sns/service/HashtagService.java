package com.example.sns.service;

import com.example.sns.entity.Hashtag;
import com.example.sns.entity.Post;
import com.example.sns.entity.PostHashtag;
import com.example.sns.repository.HashtagRepository;
import com.example.sns.repository.PostHashtagRepository;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
     * 본문에서 태그를 뽑아 게시글에 연결한다. 글을 쓸 때와 고칠 때 모두 부른다.
     *
     * 예전에는 기존 연결을 전부 지우고 다시 만들어서, 태그를 그대로 두고 본문만 고쳐도 태그 수만큼
     * 지우고 다시 넣었다. 이제 지금 연결과 비교해 본문에서 사라진 태그만 떼고 새로 생긴 태그만 붙인다.
     * 떼는 태그와 붙이는 태그는 겹치지 않으므로, 같은 연결을 지우기 전에 다시 넣어 유니크 제약에 걸릴 일도 없다.
     * 새로 붙일 태그는 태그마다 찾지 않고 이름 목록으로 한 번에 찾고, 없는 것만 만든다.
     */
    @Transactional
    public void syncTags(Post post) {
        Set<String> names = extractTagNames(post.getContent());
        List<PostHashtag> current = postHashtagRepository.findByPostWithHashtag(post);

        List<PostHashtag> removed = current.stream()
                .filter(link -> !names.contains(link.getHashtag().getName()))
                .toList();
        if (!removed.isEmpty()) {
            postHashtagRepository.deleteAll(removed);
        }

        Set<String> added = new LinkedHashSet<>(names);
        current.forEach(link -> added.remove(link.getHashtag().getName()));
        if (added.isEmpty()) {
            return;
        }

        Map<String, Hashtag> tagsByName = new HashMap<>();
        hashtagRepository.findByNameIn(added).forEach(tag -> tagsByName.put(tag.getName(), tag));
        List<Hashtag> missing = added.stream()
                .filter(name -> !tagsByName.containsKey(name))
                .map(Hashtag::new)
                .toList();
        hashtagRepository.saveAll(missing).forEach(tag -> tagsByName.put(tag.getName(), tag));

        postHashtagRepository.saveAll(added.stream()
                .map(name -> new PostHashtag(post, tagsByName.get(name)))
                .toList());
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
