(function () {
  // 피드와 프로필이 같은 게시글 상세 모달을 쓴다. 두 화면에 같은 코드를 두고 한쪽만
  // 고치는 일이 생겨서, 모달과 댓글과 좋아요를 다루는 부분은 여기로 모았다.
  // 카드를 클릭할 때 부르는 openPostModal만 밖에서 쓰므로 window에 올린다.

  async function openPostModal(postId) {
    try {
      const response = await fetch('/posts/' + postId + '/modal', {
        credentials: 'include'
      });
      if (!response.ok) throw new Error('Failed to fetch post detail');

      const html = await response.text();
      document.getElementById('modalContent').innerHTML = html;

      document.getElementById('postDetailModal').style.display = 'flex';
      document.body.style.overflow = 'hidden';

      // 모달 내부의 작성 시간 / 댓글 시간을 상대 시간으로 변환
      document.querySelectorAll('#modalContent .post-time').forEach(el => {
        el.textContent = timeAgo(el.getAttribute('data-time'));
      });

      // 모달 안의 #태그도 링크로
      if (window.linkifyHashtags) window.linkifyHashtags(document.getElementById('modalContent'));
      if (window.linkifyMentions) window.linkifyMentions(document.getElementById('modalContent'));

      initDeleteButton();
      initEditButton();
      initEditCommentButtons();
      initDeleteCommentButtons();
      initLikeButtons();
      initCommentLikeButtons();
      initAuthorProfileLink();
      initPostMenu();

      // 현재 URL에 열린 게시글을 기록 → 프로필 다녀온 뒤 뒤로가기 시 모달 복원
      if (location.hash !== '#post-' + postId) {
        history.replaceState({ modalPost: postId }, '', '#post-' + postId);
      }

    } catch (error) {
      showToast('게시글 상세 정보를 불러오는데 실패했습니다.', 'error');
      console.error(error);
    }
  }

  // 페이지 진입/뒤로가기 복원 시 URL 해시에 게시글이 있으면 모달 재오픈
  window.addEventListener('pageshow', function () {
    const m = location.hash.match(/^#post-(\d+)$/);
    if (m && document.getElementById('postDetailModal').style.display !== 'flex') {
      openPostModal(m[1]);
    }
  });

  // 이미 이 페이지에 있는 상태에서 해시만 바뀐 경우 (알림에서 게시글로 이동 등)
  window.addEventListener('hashchange', function () {
    const m = location.hash.match(/^#post-(\d+)$/);
    if (m && document.getElementById('postDetailModal').style.display !== 'flex') {
      openPostModal(m[1]);
    }
  });

  function initAuthorProfileLink() {
    document.querySelectorAll('#modalContent .author-name, #modalContent .comment-author').forEach(authorEl => {
      authorEl.addEventListener('click', () => {
        const authorId = authorEl.getAttribute('data-author-id');
        if (authorId) {
          window.location.href = '/profile/' + authorId;
        }
      });
    });
  }

  // ...(더보기) 메뉴 토글 + 댓글 아이콘 → 입력창 포커스
  function initPostMenu() {
    const menuBtn = document.querySelector('#modalContent .pd-menu-btn');
    const dropdown = document.querySelector('#modalContent .pd-menu-dropdown');
    if (menuBtn && dropdown) {
      menuBtn.onclick = (e) => {
        e.stopPropagation();
        dropdown.style.display = dropdown.style.display === 'block' ? 'none' : 'block';
      };
      dropdown.querySelectorAll('.btn').forEach(b =>
        b.addEventListener('click', () => { dropdown.style.display = 'none'; }));
    }
    const commentIc = document.getElementById('pdCommentFocus');
    if (commentIc) {
      commentIc.onclick = () => document.getElementById('commentInput')?.focus();
    }
  }

  // ...메뉴 바깥 클릭 시 닫기 (전역 1회)
  document.addEventListener('click', (e) => {
    const dd = document.querySelector('#modalContent .pd-menu-dropdown');
    if (dd && dd.style.display === 'block' && !e.target.closest('.pd-menu')) {
      dd.style.display = 'none';
    }
  });

  function initDeleteButton() {
    const deleteBtn = document.querySelector('#modalContent .delete-post-btn');
    if (deleteBtn) {
      deleteBtn.onclick = async function () {
        const postId = this.getAttribute('data-post-id');
        console.log("삭제 버튼 클릭됨, postId:", postId);

        if (!(await confirmDialog('정말로 삭제하시겠습니까?', { okText: '삭제', danger: true }))) return;

        try {
          const response = await fetch('/api/posts/' + postId, {
            method: 'DELETE',
            credentials: 'include'
          });

          if (response.ok) {
            toastAfterReload('게시글이 삭제되었습니다.');
            closePostModal();
            location.reload();
          } else {
            await alertError(response, '게시글 삭제에 실패했습니다.');
          }
        } catch (e) {
          console.error(e);
          showToast('삭제 중 오류 발생', 'error');
        }
      };
    }
  }

  function initEditButton() {
    const editBtn = document.querySelector('#modalContent .edit-post-btn');
    const saveBtn = document.querySelector('#modalContent .save-post-btn');
    const contentEl = document.querySelector('#modalContent .post-content');
    const contentInput = document.querySelector('#modalContent .edit-content-input');

    if (!editBtn || !saveBtn || !contentEl || !contentInput) return;

    editBtn.onclick = function () {
      contentEl.style.display = 'none';
      contentInput.style.display = 'block';
      editBtn.style.display = 'none';
      saveBtn.style.display = 'inline-block';
    };

    saveBtn.onclick = async function () {
      const postId = this.getAttribute('data-post-id');
      const updatedContent = contentInput.value;

      try {
        const response = await fetch('/api/posts/' + postId, {
          method: 'PUT',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ content: updatedContent })
        });

        if (response.ok) {
          showToast('게시글이 수정되었습니다.');

          contentEl.textContent = updatedContent;

          contentEl.style.display = 'inline';
          contentInput.style.display = 'none';
          editBtn.style.display = 'inline-block';
          saveBtn.style.display = 'none';
        } else {
          await alertError(response, '게시글 수정에 실패했습니다.');
        }
      } catch (e) {
        console.error(e);
        showToast('수정 중 오류 발생', 'error');
      }
    };
  }

  async function alertError(response, fallback) {
    let message = fallback;
    try {
      const data = await response.json();
      if (data && data.message) message = data.message;
    } catch (e) { /* JSON 아님 → fallback 사용 */ }
    showToast(message, 'error');
  }

  function closePostModal() {
    const modal = document.getElementById('postDetailModal');
    if (modal) modal.style.display = 'none';
    document.body.style.overflow = 'auto';
    // URL 해시 정리 (닫은 뒤엔 뒤로가기로 모달이 다시 뜨지 않도록)
    if (location.hash.indexOf('#post-') === 0) {
      history.replaceState(null, '', location.pathname + location.search);
    }
  }

  // 모달 바깥(어두운 백드롭) 클릭 시 닫기
  document.getElementById('postDetailModal').addEventListener('click', function (event) {
    if (event.target === this) closePostModal();
  });

  // ESC 키로도 닫기 (수정 모달이 열려 있으면 그것부터 닫음)
  document.addEventListener('keydown', function (event) {
    if (event.key !== 'Escape') return;
    const ce = document.getElementById('commentEditModal');
    if (ce && ce.style.display === 'flex') { closeCommentEditModal(); return; }
    closePostModal();
  });

  document.addEventListener('DOMContentLoaded', function () {
    const modalContent = document.getElementById('modalContent');

    modalContent.addEventListener('click', async function (e) {
      if (e.target && e.target.id === 'submitCommentBtn') {
        const postId = e.target.getAttribute('data-post-id');
        const content = document.getElementById('commentInput').value.trim();

        if (!content) {
          showToast('댓글을 입력해주세요.', 'error');
          return;
        }

        try {
          const response = await fetch(`/api/posts/${postId}/comments`, {
            method: 'POST',
            credentials: 'include',
            headers: {
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({ content, parentId: window.getReplyParentId ? window.getReplyParentId() : null })
          });

          if (!response.ok) {
            await alertError(response, '댓글 등록에 실패했습니다.');
            return;
          }

          showToast('댓글이 등록되었습니다.');
          openPostModal(postId);
        } catch (error) {
          console.error(error);
          showToast('댓글 등록 중 오류가 발생했습니다.', 'error');
        }
      }
    });
  });

  // 댓글 "수정" 버튼 → 수정 모달 열기 (목록은 건드리지 않음 → 레이아웃 유지)
  let ceTargetTextEl = null, cePostId = null, ceCommentId = null;

  function initEditCommentButtons() {
    document.querySelectorAll("#modalContent .edit-comment-btn").forEach(btn => {
      btn.onclick = () => {
        const commentEl = btn.closest(".comment");
        const textEl = commentEl.querySelector(".comment-text");
        openCommentEditModal(btn.getAttribute("data-post-id"), btn.getAttribute("data-comment-id"), textEl);
      };
    });
  }

  function openCommentEditModal(postId, commentId, textEl) {
    cePostId = postId; ceCommentId = commentId; ceTargetTextEl = textEl;
    const input = document.getElementById("commentEditInput");
    input.value = textEl.textContent.trim();
    document.getElementById("commentEditModal").style.display = "flex";
    input.focus();
  }

  function closeCommentEditModal() {
    document.getElementById("commentEditModal").style.display = "none";
    ceTargetTextEl = null; cePostId = null; ceCommentId = null;
  }

  document.getElementById("commentEditSave").addEventListener("click", async () => {
    const newContent = document.getElementById("commentEditInput").value.trim();
    if (!newContent) { showToast("댓글 내용을 입력하세요.", 'error'); return; }
    try {
      const response = await fetch(`/api/posts/${cePostId}/comments/${ceCommentId}`, {
        method: "PUT",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ content: newContent })
      });
      if (response.ok) {
        const flashTarget = ceTargetTextEl ? ceTargetTextEl.closest('.comment') : null;
        if (ceTargetTextEl) ceTargetTextEl.textContent = newContent;
        closeCommentEditModal();
        showToast('댓글이 수정되었습니다.');
        flashSuccess(flashTarget);
      } else {
        await alertError(response, "댓글 수정에 실패했습니다.");
      }
    } catch (e) {
      console.error(e);
      showToast("댓글 수정 중 오류가 발생했습니다.", 'error');
    }
  });

  document.getElementById("commentEditCancel").addEventListener("click", closeCommentEditModal);
  document.getElementById("commentEditModal").addEventListener("click", function (e) {
    if (e.target === this) closeCommentEditModal();
  });

  function initDeleteCommentButtons() {
    document.querySelectorAll('.comment').forEach(commentEl => {
      const deleteBtn = commentEl.querySelector('.delete-comment-btn');
      if (!deleteBtn) return;

      deleteBtn.addEventListener('click', async (e) => {
        if (!(await confirmDialog("댓글을 삭제하시겠습니까?", { okText: '삭제', danger: true }))) return;

        const commentId = commentEl.dataset.commentId;
        const postId = e.target.getAttribute("data-post-id");

        if (!postId) {
          showToast("postId 정보를 찾을 수 없습니다.", 'error');
          return;
        }

        try {
          const response = await fetch(`/api/posts/${postId}/comments/${commentId}`, {
            method: 'DELETE'
          });

          if (response.ok) {
            commentEl.remove();
            showToast('댓글이 삭제되었습니다.');

            const commentCountSpan = document.querySelector('#modalContent .comment-count');
            if (commentCountSpan) {
              const currentCount = parseInt(commentCountSpan.textContent.trim());
              if (!isNaN(currentCount) && currentCount > 0) {
                commentCountSpan.textContent = currentCount - 1;
              }
            }

          } else {
            await alertError(response, '댓글 삭제에 실패했습니다.');
          }
        } catch (error) {
          console.error(error);
          showToast("댓글 삭제 중 오류가 발생했습니다.", 'error');
        }
      });
    });
  }

  // 모달 ↔ 피드 좋아요 상태 동기화
  function syncFeedLike(postId, liked) {
    const feedBtn = document.querySelector('.feed-like-btn[data-post-id="' + postId + '"]');
    if (!feedBtn) return;
    const feedCountEl = feedBtn.parentElement.querySelector('.feed-like-count');
    const alreadyLiked = feedBtn.classList.contains('liked');
    if (liked === alreadyLiked) return; // 상태 동일하면 카운트 중복 변경 방지
    let count = feedCountEl ? (parseInt(feedCountEl.textContent) || 0) : 0;
    if (liked) {
      feedBtn.classList.add('liked');
      if (feedCountEl) feedCountEl.textContent = count + 1;
    } else {
      feedBtn.classList.remove('liked');
      if (feedCountEl) feedCountEl.textContent = Math.max(0, count - 1);
    }
  }

  function initLikeButtons() {
    document.querySelectorAll('.like-btn').forEach(btn => {
      btn.onclick = async (e) => {
        e.stopPropagation();

        const postId = btn.getAttribute('data-post-id');
        const countEl = document.querySelector('#modalContent .like-count');

        try {
          const response = await fetch(`/api/posts/${postId}/like`, {
            method: 'POST',
            credentials: 'include'
          });

          if (response.ok) {
            const result = await response.json();

            if (countEl) {
              let count = parseInt(countEl.textContent) || 0;

              if (result.liked) {
                countEl.textContent = count + 1;
                btn.classList.add('liked');
              } else {
                countEl.textContent = count - 1 >= 0 ? count - 1 : 0;
                btn.classList.remove('liked');
              }
            }

            // 모달에서의 좋아요/취소를 피드 카드에도 실시간 반영
            syncFeedLike(postId, result.liked);
          } else {
            await alertError(response, '좋아요 처리에 실패했습니다.');
          }
        } catch (error) {
          console.error(error);
          showToast('좋아요 처리 중 오류 발생', 'error');
        }
      };
    });
  }

  function initCommentLikeButtons() {
    document.querySelectorAll('.comment-like-btn').forEach(btn => {
      btn.onclick = async (e) => {
        e.stopPropagation();

        const commentId = btn.getAttribute('data-comment-id');
        const postId = btn.getAttribute('data-post-id');
        const countEl = btn.parentElement.querySelector('.comment-like-count');

        try {
          const response = await fetch(`/api/posts/${postId}/comments/${commentId}/like`, {
            method: 'POST',
            credentials: 'include'
          });

          if (response.ok) {
            const result = await response.json();
            let count = parseInt(countEl.textContent) || 0;

            if (result.liked) {
              count += 1;
              btn.classList.add('liked');
            } else {
              count = Math.max(0, count - 1);
              btn.classList.remove('liked');
            }

            countEl.textContent = count;
          } else {
            await alertError(response, '댓글 좋아요 처리에 실패했습니다.');
          }
        } catch (err) {
          console.error(err);
          showToast('댓글 좋아요 처리 중 오류 발생', 'error');
        }
      };
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    initEditCommentButtons();
    initDeleteCommentButtons();
    initLikeButtons();
    initCommentLikeButtons();
  });

  window.openPostModal = openPostModal;
})();
