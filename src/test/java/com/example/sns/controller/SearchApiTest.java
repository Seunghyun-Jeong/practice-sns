package com.example.sns.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.entity.Hashtag;
import com.example.sns.entity.Post;
import com.example.sns.entity.PostHashtag;
import com.example.sns.entity.User;
import com.example.sns.repository.HashtagRepository;
import com.example.sns.repository.PostHashtagRepository;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SearchApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private HashtagRepository hashtagRepository;

    @Autowired
    private PostHashtagRepository postHashtagRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        User author = createUser("catlover");
        createUser("dogperson");

        Post post = new Post();
        post.setContent("우리집 고양이 #고양이 #일상");
        post.setImageUrl("/uploads/test.png");
        post.setAuthor(author);
        postRepository.save(post);

        linkTag(post, "고양이");
        linkTag(post, "일상");
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("Test1234!"));
        user.setRole(User.Role.USER);
        return userRepository.save(user);
    }

    private void linkTag(Post post, String name) {
        Hashtag hashtag = hashtagRepository.save(new Hashtag(name));
        postHashtagRepository.save(new PostHashtag(post, hashtag));
    }

    @Test
    @DisplayName("아이디 일부로 유저를 찾는다")
    void 유저를_검색한다() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "cat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].username").value("catlover"));
    }

    @Test
    @DisplayName("해시태그를 게시글 수와 함께 찾는다")
    void 해시태그를_검색한다() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "고양"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hashtags[0].name").value("고양이"))
                .andExpect(jsonPath("$.hashtags[0].postCount").value(1));
    }

    @Test
    @DisplayName("검색어 앞에 #을 붙여도 태그를 찾는다")
    void 샵을_붙여도_찾는다() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "#일상"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hashtags[0].name").value("일상"));
    }

    @Test
    @DisplayName("검색어가 비어 있으면 빈 결과를 준다")
    void 빈_검색어() throws Exception {
        mockMvc.perform(get("/api/search").param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isEmpty())
                .andExpect(jsonPath("$.hashtags").isEmpty());
    }

    @Test
    @DisplayName("정지된 유저는 검색 결과에 나오지 않는다")
    void 정지된_유저는_제외된다() throws Exception {
        User suspended = userRepository.findByUsername("dogperson").orElseThrow();
        suspended.setSuspendedUntil(LocalDateTime.now().plusDays(1));
        userRepository.save(suspended);

        mockMvc.perform(get("/api/search").param("q", "dog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isEmpty());
    }
}
