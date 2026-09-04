package com.example.sns.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    /**
     * 답글이면 원 댓글, 아니면 null.
     *
     * 답글의 답글은 새로 파지 않고 같은 부모에 붙인다. 누구에게 답한 것인지는
     * 본문의 멘션이 알려주기 때문에 깊이를 늘릴 이유가 없다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    /**
     * 이 댓글에 달린 답글.
     *
     * orphanRemoval을 걸지 않는다. 답글은 별개의 요청으로 저장되기 때문에 이 목록이
     * 비어 있는 채로 남아 있을 수 있는데, 그 상태에서 삭제를 컬렉션에 맡기면
     * 자바가 아는 것과 DB에 있는 것이 어긋난다. 지우는 것은 서비스에서 직접 한다.
     */
    @OneToMany(mappedBy = "parent", cascade = CascadeType.REMOVE)
    private List<Comment> replies = new ArrayList<>();

    public boolean isReply() {
        return parent != null;
    }

    @OneToMany(mappedBy = "comment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CommentLike> likes = new ArrayList<>();

    /** 이 댓글을 가리키는 알림 (댓글이 지워지면 알림도 함께 삭제) */
    @OneToMany(mappedBy = "comment", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Notification> notifications = new ArrayList<>();

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;
}
