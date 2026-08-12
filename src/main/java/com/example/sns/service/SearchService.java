package com.example.sns.service;

import com.example.sns.dto.SearchResultDto;
import com.example.sns.repository.HashtagRepository;
import com.example.sns.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final UserRepository userRepository;
    private final HashtagRepository hashtagRepository;

    /** 각 영역에서 보여줄 최대 개수 */
    private static final int LIMIT = 8;

    public SearchResultDto search(String keyword) {
        String q = keyword == null ? "" : keyword.trim();

        // 검색어 앞의 # 은 태그를 찾겠다는 뜻이므로 떼고 검색한다
        if (q.startsWith("#")) {
            q = q.substring(1);
        }

        if (q.isBlank()) {
            return new SearchResultDto(List.of(), List.of());
        }

        List<SearchResultDto.UserResult> users =
                userRepository.searchByUsername(q, LocalDateTime.now(), PageRequest.of(0, LIMIT)).stream()
                        .map(u -> new SearchResultDto.UserResult(u.getId(), u.getUsername(), u.getProfileImageUrl()))
                        .collect(Collectors.toList());

        List<SearchResultDto.HashtagResult> hashtags =
                hashtagRepository.searchByKeyword(q, PageRequest.of(0, LIMIT)).stream()
                        .map(row -> new SearchResultDto.HashtagResult((String) row[0], (Long) row[1]))
                        .collect(Collectors.toList());

        return new SearchResultDto(users, hashtags);
    }
}
