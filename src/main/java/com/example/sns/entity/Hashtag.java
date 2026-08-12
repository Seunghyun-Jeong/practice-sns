package com.example.sns.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 해시태그.
 * 게시글 본문에서 뽑아낸 이름을 저장한다. (# 은 저장하지 않는다)
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "hashtag")
public class Hashtag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 태그 이름 (영문은 소문자로 통일해서 저장) */
    @Column(unique = true, nullable = false, length = 50)
    private String name;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Hashtag(String name) {
        this.name = name;
    }
}
