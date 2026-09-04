package com.example.sns.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sns.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 본문에서 @아이디를 뽑아내는 규칙 테스트.
 *
 * 아이디 규칙(소문자, 숫자, 마침표, 밑줄, 4~10자)에 맞춰 잡아야 하고,
 * 이메일처럼 멘션이 아닌 것을 잘못 잡으면 엉뚱한 사람에게 알림이 간다.
 */
@ExtendWith(MockitoExtension.class)
class MentionServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MentionService mentionService;

    @Test
    @DisplayName("본문에 적힌 아이디를 뽑아낸다")
    void 멘션을_뽑아낸다() {
        assertThat(mentionService.extractUsernames("@tester 안녕하세요"))
                .containsExactly("tester");
    }

    @Test
    @DisplayName("여러 명을 적으면 쓴 순서대로 모두 뽑는다")
    void 여러_명을_뽑는다() {
        assertThat(mentionService.extractUsernames("@first 님과 @second 님 보세요"))
                .containsExactly("first", "second");
    }

    @Test
    @DisplayName("같은 사람을 여러 번 적어도 한 번만 센다")
    void 중복은_한_번만() {
        assertThat(mentionService.extractUsernames("@tester @tester @tester"))
                .containsExactly("tester");
    }

    @Test
    @DisplayName("이메일은 멘션으로 보지 않는다")
    void 이메일은_멘션이_아니다() {
        // 앞에 글자가 붙어 있으면 멘션이 아니다. 이걸 잡으면 엉뚱한 사람에게 알림이 간다.
        assertThat(mentionService.extractUsernames("연락처는 hong@tester 입니다")).isEmpty();
    }

    @Test
    @DisplayName("아이디 규칙에 맞지 않으면 뽑지 않는다")
    void 규칙에_어긋나면_안_뽑는다() {
        assertThat(mentionService.extractUsernames("@ab")).isEmpty();              // 4자 미만
        assertThat(mentionService.extractUsernames("@Tester")).isEmpty();          // 대문자
        assertThat(mentionService.extractUsernames("@한글아이디")).isEmpty();        // 한글
    }

    @Test
    @DisplayName("문장 부호가 붙어 있어도 아이디만 잘라낸다")
    void 문장부호는_아이디에서_뺀다() {
        assertThat(mentionService.extractUsernames("@tester님 확인해주세요"))
                .containsExactly("tester");
        assertThat(mentionService.extractUsernames("(@tester)"))
                .containsExactly("tester");
    }

    @Test
    @DisplayName("멘션이 없으면 빈 값을 준다")
    void 멘션이_없으면_비어_있다() {
        assertThat(mentionService.extractUsernames("그냥 댓글입니다")).isEmpty();
        assertThat(mentionService.extractUsernames(null)).isEmpty();
        assertThat(mentionService.extractUsernames("")).isEmpty();
    }
}
