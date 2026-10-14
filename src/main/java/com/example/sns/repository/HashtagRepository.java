package com.example.sns.repository;

import com.example.sns.entity.Hashtag;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HashtagRepository extends JpaRepository<Hashtag, Long> {
    /** 여러 이름의 태그를 한 번에 찾는다. 없는 이름은 결과에 없다 */
    List<Hashtag> findByNameIn(Collection<String> names);

    /**
     * 검색어가 포함된 태그를 게시글이 많은 순으로 찾는다.
     * 연결된 게시글이 하나도 없는 태그(글이 지워진 경우)는 제외한다.
     */
    @Query("SELECT h.name, COUNT(ph) FROM Hashtag h "
            + "JOIN PostHashtag ph ON ph.hashtag = h "
            + "WHERE LOWER(h.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "GROUP BY h.name "
            + "ORDER BY COUNT(ph) DESC, h.name ASC")
    List<Object[]> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
