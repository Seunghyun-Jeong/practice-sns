(function () {
  // 피드와 프로필의 게시글 카드에 상대 시간, 좋아요, 닉네임 클릭을 연결한다.
  // 무한 스크롤로 새로 불러온 카드에도 그대로 다시 적용할 수 있게 범위를 받는다.

  function initPostCards(root) {
    root.querySelectorAll('.post-time').forEach(el => {
      el.textContent = timeAgo(el.getAttribute('data-time'));
    });

    // 피드 하트 클릭 → 좋아요 토글 (모달 안 열림)
    root.querySelectorAll('.feed-like-btn').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const postId = btn.getAttribute('data-post-id');
        try {
          const response = await fetch('/api/posts/' + postId + '/like', { method: 'POST', credentials: 'include' });
          if (!response.ok) {
            showToast(response.status === 401 || response.status === 403 ? '로그인이 필요합니다.' : '좋아요 처리에 실패했습니다.', 'error');
            return;
          }
          const data = await response.json();
          const countEl = btn.parentElement.querySelector('.feed-like-count');
          let count = parseInt(countEl.textContent) || 0;
          if (data.liked) { btn.classList.add('liked'); countEl.textContent = count + 1; }
          else { btn.classList.remove('liked'); countEl.textContent = Math.max(0, count - 1); }
        } catch (err) { console.error(err); showToast('좋아요 처리 중 오류가 발생했습니다.', 'error'); }
      });
    });

    // 피드 닉네임 클릭 → 프로필 (모달 안 열림)
    root.querySelectorAll('.post-summary .post-author-name').forEach(el => {
      el.addEventListener('click', (e) => {
        e.stopPropagation();
        const authorId = el.getAttribute('data-author-id');
        if (authorId) window.location.href = '/profile/' + authorId;
      });
    });

    // 글 내용의 #태그를 링크로
    if (window.linkifyHashtags) window.linkifyHashtags(root);
    if (window.linkifyMentions) window.linkifyMentions(root);
  }

  window.initPostCards = initPostCards;
})();
