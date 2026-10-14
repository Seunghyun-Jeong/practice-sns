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

      decorate(document.getElementById('modalContent'));

      initDeleteButton();
      initEditButton();
      initLikeButtons();
      initAuthorProfileLink();
      initPostMenu();
      watchCommentEnd();

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
      const updatedContent = contentInput.value.trim();
      if (!updatedContent) { showToast('내용을 입력해주세요.', 'error'); return; }

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
    if (commentObserver) commentObserver.disconnect();
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

          const { id } = await response.json();
          showToast('댓글이 등록되었습니다.');
          document.getElementById('commentInput').value = '';
          if (window.clearReplyTarget) window.clearReplyTarget();
          await appendNewComment(postId, id);
        } catch (error) {
          console.error(error);
          showToast('댓글 등록 중 오류가 발생했습니다.', 'error');
        }
      }
    });
  });

  // 댓글 "수정" 버튼 → 수정 모달 열기 (목록은 건드리지 않음 → 레이아웃 유지)
  let ceTargetTextEl = null, cePostId = null, ceCommentId = null;

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

  // 댓글은 원 댓글 20개씩 끊어 받고, 답글은 원 댓글마다 접어 두었다가 펼칠 때 받는다.
  // 붙이는 줄은 모두 서버의 같은 조각(commentThreads.html)에서 오므로, 여기서는 받아서 꾸미고 끼워 넣기만 한다.

  // 상대 시간, #태그와 @아이디 링크, 답글 버튼 글자를 채운다. 새로 받은 조각에도 같은 것을 해야 한다
  function decorate(root) {
    root.querySelectorAll('.post-time').forEach(el => {
      el.textContent = timeAgo(el.getAttribute('data-time'));
    });
    if (window.linkifyHashtags) window.linkifyHashtags(root);
    if (window.linkifyMentions) window.linkifyMentions(root);
    root.querySelectorAll('.comment-thread').forEach(renderReplyToggle);
  }

  async function fetchFragment(url) {
    const response = await fetch(url, { credentials: 'include' });
    if (!response.ok) throw new Error('failed to load ' + url);
    const temp = document.createElement('div');
    temp.innerHTML = await response.text();
    decorate(temp);
    return temp;
  }

  function hasNextIn(temp) {
    const meta = temp.querySelector('.comment-page-meta');
    return meta ? meta.getAttribute('data-has-next') === 'true' : false;
  }

  // 이미 화면에 있는 댓글은 다시 붙이지 않는다 (방금 쓴 답글이 다음 묶음에 다시 들어오는 경우)
  function isShown(container, commentId) {
    return !!container.querySelector('[data-comment-id="' + commentId + '"]');
  }

  // 다음 묶음의 기준: 이미 보여준 마지막 댓글의 (작성 시각, id). prefix는 before(원 댓글) 또는 after(답글)
  function cursorQuery(prefix, commentEl) {
    return `${prefix}At=${encodeURIComponent(commentEl.dataset.createdAt)}&${prefix}Id=${commentEl.dataset.commentId}`;
  }

  function changeCommentCount(delta) {
    const el = document.querySelector('#modalContent .comment-count');
    if (!el) return;
    const count = parseInt(el.textContent.trim()) || 0;
    el.textContent = Math.max(0, count + delta);
  }

  // 원 댓글 다음 묶음. 목록 끝의 표시(sentinel)가 보이면 불러온다
  let commentObserver = null;
  let commentsHasNext = false;
  let commentsLoading = false;

  function watchCommentEnd() {
    if (commentObserver) commentObserver.disconnect();
    const list = document.querySelector('#modalContent .comment-list');
    const sentinel = document.querySelector('#modalContent .comment-sentinel');
    if (!list || !sentinel) return;

    commentsHasNext = hasNextIn(list);
    commentsLoading = false;
    commentObserver = new IntersectionObserver(entries => {
      if (entries[0].isIntersecting) loadMoreComments(list, sentinel);
    }, { root: document.querySelector('#modalContent .pd-body'), rootMargin: '200px' });
    commentObserver.observe(sentinel);
  }

  async function loadMoreComments(list, sentinel) {
    if (commentsLoading || !commentsHasNext) return;
    const threads = list.querySelectorAll(':scope > .comment-thread');
    const last = threads[threads.length - 1];
    if (!last) return;

    commentsLoading = true;
    try {
      const lastRow = last.querySelector(':scope > .comment');
      const temp = await fetchFragment(`/posts/${list.dataset.postId}/comments?${cursorQuery('before', lastRow)}`);
      if (!list.isConnected) return;   // 기다리는 사이 다른 게시글이 열렸다

      temp.querySelectorAll(':scope > .comment-thread').forEach(thread => {
        if (!isShown(list, thread.dataset.commentId)) list.insertBefore(thread, list.querySelector(':scope > .comment-page-meta'));
      });
      commentsHasNext = hasNextIn(temp);
    } catch (err) {
      console.error(err);
      commentsHasNext = false;
    } finally {
      commentsLoading = false;
    }

    // 새로 붙인 묶음이 짧아 끝이 여전히 보이면 옵저버가 다시 알려주지 않으므로 다시 걸어 둔다
    if (commentsHasNext && commentObserver && list.isConnected) {
      commentObserver.unobserve(sentinel);
      commentObserver.observe(sentinel);
    }
  }

  // 답글 버튼: 접혀 있으면 "답글 n개 보기", 펼쳤는데 덜 받았으면 "답글 n개 더 보기", 다 받았으면 "답글 숨기기"
  function renderReplyToggle(thread) {
    const btn = thread.querySelector(':scope > .reply-toggle-btn');
    const replies = thread.querySelector(':scope > .comment-replies');
    if (!btn || !replies) return;

    const total = Number(thread.dataset.replyCount) || 0;
    const shown = replies.querySelectorAll(':scope > .comment').length;
    const expanded = thread.dataset.expanded === 'true';

    replies.hidden = !expanded;
    btn.hidden = total === 0;
    if (!expanded) btn.textContent = `답글 ${total}개 보기`;
    else if (total > shown) btn.textContent = `답글 ${total - shown}개 더 보기`;
    else btn.textContent = '답글 숨기기';
  }

  // 답글 다음 묶음. 방금 쓴 답글(data-local)은 받은 답글보다 늦게 쓴 것이므로 그 앞에 끼운다
  async function loadReplies(thread) {
    const list = thread.closest('.comment-list');
    const replies = thread.querySelector(':scope > .comment-replies');
    const loaded = replies.querySelectorAll(':scope > .comment:not([data-local])');
    const last = loaded[loaded.length - 1];
    const query = last ? '?' + cursorQuery('after', last) : '';

    const temp = await fetchFragment(`/posts/${list.dataset.postId}/comments/${thread.dataset.commentId}/replies${query}`);
    const firstLocal = replies.querySelector(':scope > .comment[data-local]');
    temp.querySelectorAll(':scope > .comment').forEach(row => {
      if (!isShown(replies, row.dataset.commentId)) replies.insertBefore(row, firstLocal);
    });

    // 그사이 지워진 답글이 있으면 수가 실제보다 크게 남는다. 더 받을 게 없으면 화면에 있는 수로 맞춘다
    if (!hasNextIn(temp)) thread.dataset.replyCount = replies.querySelectorAll(':scope > .comment').length;
  }

  async function onReplyToggle(thread) {
    const replies = thread.querySelector(':scope > .comment-replies');
    const total = Number(thread.dataset.replyCount) || 0;
    const shown = replies.querySelectorAll(':scope > .comment').length;
    const expanded = thread.dataset.expanded === 'true';

    try {
      if (expanded && total <= shown) {
        thread.dataset.expanded = 'false';
      } else {
        thread.dataset.expanded = 'true';
        if (shown < total && (expanded || shown === 0)) await loadReplies(thread);
      }
    } catch (err) {
      console.error(err);
      showToast('답글을 불러오지 못했습니다.', 'error');
    }
    renderReplyToggle(thread);
  }

  // 방금 쓴 댓글을 모달을 다시 열지 않고 붙인다. 원 댓글은 최신순이라 맨 위에, 답글은 그 원 댓글 아래에
  async function appendNewComment(postId, commentId) {
    const list = document.querySelector('#modalContent .comment-list');
    let temp;
    try {
      temp = await fetchFragment(`/posts/${postId}/comments/${commentId}`);
    } catch (err) {
      console.error(err);
      openPostModal(postId);
      return;
    }
    const el = temp.firstElementChild;
    if (!list || !el) return;
    changeCommentCount(1);

    if (el.classList.contains('comment-thread')) {
      list.prepend(el);
      return;
    }

    const thread = list.querySelector(`.comment-thread[data-comment-id="${el.dataset.parentId}"]`);
    if (!thread) return;
    const replies = thread.querySelector(':scope > .comment-replies');
    const shownBefore = replies.querySelectorAll(':scope > .comment').length;

    el.dataset.local = 'true';
    replies.appendChild(el);
    thread.dataset.replyCount = (Number(thread.dataset.replyCount) || 0) + 1;

    // 접혀 있던 답글을 펼치면서, 아직 받지 않은 답글이 있으면 첫 묶음도 함께 받는다
    if (thread.dataset.expanded !== 'true') {
      thread.dataset.expanded = 'true';
      if (shownBefore === 0 && Number(thread.dataset.replyCount) > 1) {
        try { await loadReplies(thread); } catch (err) { console.error(err); }
      }
    }
    renderReplyToggle(thread);
  }

  async function deleteComment(btn) {
    if (!(await confirmDialog("댓글을 삭제하시겠습니까?", { okText: '삭제', danger: true }))) return;

    const commentEl = btn.closest('.comment');
    const thread = commentEl.closest('.comment-thread');
    try {
      const response = await fetch(`/api/posts/${btn.dataset.postId}/comments/${btn.dataset.commentId}`, {
        method: 'DELETE'
      });
      if (!response.ok) {
        await alertError(response, '댓글 삭제에 실패했습니다.');
        return;
      }
      showToast('댓글이 삭제되었습니다.');

      // 원 댓글을 지우면 서버가 답글도 함께 지우므로 답글 수만큼 더 줄인다
      if (commentEl.dataset.parentId) {
        commentEl.remove();
        thread.dataset.replyCount = Math.max(0, (Number(thread.dataset.replyCount) || 0) - 1);
        renderReplyToggle(thread);
        changeCommentCount(-1);
      } else {
        changeCommentCount(-1 - (Number(thread.dataset.replyCount) || 0));
        thread.remove();
      }
    } catch (error) {
      console.error(error);
      showToast("댓글 삭제 중 오류가 발생했습니다.", 'error');
    }
  }

  async function toggleCommentLike(btn) {
    const countEl = btn.parentElement.querySelector('.comment-like-count');
    try {
      const response = await fetch(`/api/posts/${btn.dataset.postId}/comments/${btn.dataset.commentId}/like`, {
        method: 'POST',
        credentials: 'include'
      });
      if (!response.ok) {
        await alertError(response, '댓글 좋아요 처리에 실패했습니다.');
        return;
      }
      const result = await response.json();
      const count = parseInt(countEl.textContent) || 0;
      countEl.textContent = result.liked ? count + 1 : Math.max(0, count - 1);
      btn.classList.toggle('liked', result.liked);
    } catch (err) {
      console.error(err);
      showToast('댓글 좋아요 처리 중 오류 발생', 'error');
    }
  }

  // 댓글 줄은 스크롤과 답글 펼치기로 나중에 붙기도 하므로, 버튼마다 걸지 않고 모달에서 한 번에 받는다
  document.getElementById('modalContent').addEventListener('click', function (e) {
    const toggle = e.target.closest('.reply-toggle-btn');
    if (toggle) { onReplyToggle(toggle.closest('.comment-thread')); return; }

    const editBtn = e.target.closest('.edit-comment-btn');
    if (editBtn) {
      const textEl = editBtn.closest('.comment').querySelector('.comment-text');
      openCommentEditModal(editBtn.dataset.postId, editBtn.dataset.commentId, textEl);
      return;
    }

    const deleteBtn = e.target.closest('.delete-comment-btn');
    if (deleteBtn) { deleteComment(deleteBtn); return; }

    const likeBtn = e.target.closest('.comment-like-btn');
    if (likeBtn) { e.stopPropagation(); toggleCommentLike(likeBtn); }
  });

  window.openPostModal = openPostModal;
})();
