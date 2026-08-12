(function () {
  const btn = document.getElementById('notiBtn');
  const dropdown = document.getElementById('notiDropdown');
  const badge = document.getElementById('notiBadge');
  const list = document.getElementById('notiList');
  if (!btn || !dropdown || !list) return;   // 비로그인 상태에는 알림 영역이 없다

  function timeAgo(iso) {
    if (!iso) return '';
    const then = new Date(iso);
    if (isNaN(then.getTime())) return '';
    const diff = (Date.now() - then.getTime()) / 1000;
    if (diff < 60) return '방금 전';
    if (diff < 3600) return Math.floor(diff / 60) + '분 전';
    if (diff < 86400) return Math.floor(diff / 3600) + '시간 전';
    if (diff < 604800) return Math.floor(diff / 86400) + '일 전';
    if (diff < 2592000) return Math.floor(diff / 604800) + '주 전';
    return then.getFullYear() + '.' + String(then.getMonth() + 1).padStart(2, '0') + '.' + String(then.getDate()).padStart(2, '0');
  }

  async function loadUnreadCount() {
    try {
      const res = await fetch('/api/notifications/unread-count', { credentials: 'include' });
      if (!res.ok) return;
      const data = await res.json();
      setBadge(data.count || 0);
    } catch (e) { /* 배지는 실패해도 조용히 넘어간다 */ }
  }

  function setBadge(count) {
    if (count > 0) {
      badge.textContent = count > 99 ? '99+' : count;
      badge.style.display = 'flex';
    } else {
      badge.style.display = 'none';
    }
  }

  async function loadNotifications() {
    list.innerHTML = '<div class="noti-empty">불러오는 중...</div>';
    try {
      const res = await fetch('/api/notifications', { credentials: 'include' });
      const data = await res.json();

      if (!res.ok) {
        list.innerHTML = '<div class="noti-empty">알림을 불러오지 못했습니다.</div>';
        return;
      }

      const items = data.notifications || [];
      if (items.length === 0) {
        list.innerHTML = '<div class="noti-empty">아직 받은 알림이 없습니다.</div>';
        return;
      }

      list.innerHTML = '';
      items.forEach(n => list.appendChild(buildRow(n)));
    } catch (e) {
      console.error(e);
      list.innerHTML = '<div class="noti-empty">알림을 불러오지 못했습니다.</div>';
    }
  }

  function buildRow(n) {
    const row = document.createElement('div');
    row.className = 'noti-row' + (n.read ? '' : ' unread');

    const avatar = document.createElement('img');
    avatar.className = 'noti-avatar';
    avatar.src = n.actorProfileImageUrl || '/images/default-profile.svg';
    avatar.alt = '프로필';

    const body = document.createElement('div');
    body.className = 'noti-body';

    const text = document.createElement('div');
    text.className = 'noti-text';
    const who = document.createElement('b');
    who.textContent = n.actorUsername;
    text.appendChild(who);
    text.appendChild(document.createTextNode(' ' + n.message));

    body.appendChild(text);

    // 댓글 알림이면 어떤 댓글인지 미리 보여준다
    if (n.commentContent) {
      const preview = document.createElement('div');
      preview.className = 'noti-preview';
      preview.textContent = n.commentContent;
      body.appendChild(preview);
    }

    const time = document.createElement('div');
    time.className = 'noti-time';
    time.textContent = timeAgo(n.createdAt);
    body.appendChild(time);

    row.appendChild(avatar);
    row.appendChild(body);

    // 게시글 알림이면 썸네일을 함께 보여준다
    if (n.postImageUrl) {
      const thumb = document.createElement('img');
      thumb.className = 'noti-thumb';
      thumb.src = n.postImageUrl;
      thumb.alt = '게시글';
      row.appendChild(thumb);
    }

    row.addEventListener('click', () => {
      dropdown.style.display = 'none';
      if (n.type === 'FOLLOW') {
        window.location.href = '/profile/' + n.actorId;
      } else if (n.postId) {
        window.location.href = '/#post-' + n.postId;   // 메인에서 해당 게시글 모달이 열린다
      }
    });

    return row;
  }

  btn.addEventListener('click', async (e) => {
    e.stopPropagation();
    const opening = dropdown.style.display !== 'block';
    dropdown.style.display = opening ? 'block' : 'none';
    if (!opening) return;

    await loadNotifications();

    // 열어서 확인했으므로 읽음 처리하고 배지를 지운다
    try {
      await fetch('/api/notifications/read', { method: 'POST', credentials: 'include' });
      setBadge(0);
    } catch (err) { /* 무시 */ }
  });

  document.addEventListener('click', (e) => {
    if (dropdown.style.display === 'block' && !e.target.closest('.noti-menu')) {
      dropdown.style.display = 'none';
    }
  });

  loadUnreadCount();
})();
