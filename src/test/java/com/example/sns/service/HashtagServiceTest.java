package com.example.sns.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sns.repository.HashtagRepository;
import com.example.sns.repository.PostHashtagRepository;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 본문에서 해시태그를 뽑아내는 규칙 테스트.
 * DB가 필요 없는 순수 로직이라 Mockito 로 의존성만 채우고 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class HashtagServiceTest {

    @Mock
    private HashtagRepository hashtagRepository;

    @Mock
    private PostHashtagRepository postHashtagRepository;

    @InjectMocks
    private HashtagService hashtagService;

    @Test
    @DisplayName("본문에서 한글·영문 태그를 뽑아낸다")
    void 태그를_뽑아낸다() {
        Set<String> names = hashtagService.extractTagNames("오늘의 산책 #고양이 #daily 즐거웠다");

        assertThat(names).containsExactly("고양이", "daily");
    }

    @Test
    @DisplayName("영문 대소문자가 달라도 같은 태그로 본다")
    void 대소문자를_통일한다() {
        Set<String> names = hashtagService.extractTagNames("#Daily #DAILY #daily");

        assertThat(names).containsExactly("daily");
    }

    @Test
    @DisplayName("태그가 없으면 빈 결과를 반환한다")
    void 태그가_없으면_비어있다() {
        assertThat(hashtagService.extractTagNames("태그 없는 평범한 글")).isEmpty();
        assertThat(hashtagService.extractTagNames("")).isEmpty();
        assertThat(hashtagService.extractTagNames(null)).isEmpty();
    }

    @Test
    @DisplayName("# 만 있고 이름이 없으면 태그로 보지 않는다")
    void 이름이_없으면_태그가_아니다() {
        assertThat(hashtagService.extractTagNames("# 그냥 샵 #")).isEmpty();
    }

    @Test
    @DisplayName("같은 태그를 여러 번 써도 한 번만 저장한다")
    void 중복_태그는_한_번만() {
        Set<String> names = hashtagService.extractTagNames("#일상 #일상 #일상 #카페");

        assertThat(names).containsExactly("일상", "카페");
    }

    @Test
    @DisplayName("한 게시글의 태그는 30개까지만 인정한다")
    void 태그는_30개까지() {
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 40; i++) {
            content.append(" #tag").append(i);
        }

        assertThat(hashtagService.extractTagNames(content.toString())).hasSize(30);
    }
}
