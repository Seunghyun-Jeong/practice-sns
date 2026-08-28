// 채팅: 헤더 배지 + 목록 페이지 + 대화방 페이지
// 새 메시지 수신은 WebSocket 푸시가 기본이고, 소켓이 끊긴 동안에만 폴링으로 보완한다.
(function () {
  'use strict';

  function timeAgo(iso) {
    if (!iso) return '';
    const then = new Date(iso);
    if (isNaN(then.getTime())) return '';
    const diff = (Date.now() - then.getTime()) / 1000;
    if (diff < 60) return '방금 전';
    if (diff < 3600) return Math.floor(diff / 60) + '분 전';
    if (diff < 86400) return Math.floor(diff / 3600) + '시간 전';
    if (diff < 604800) return Math.floor(diff / 86400) + '일 전';
    return then.getFullYear() + '.' + String(then.getMonth() + 1).padStart(2, '0') + '.' + String(then.getDate()).padStart(2, '0');
  }

  function timeShort(iso) {
    const d = new Date(iso);
    if (isNaN(d.getTime())) return '';
    const h = d.getHours();
    const ampm = h < 12 ? '오전' : '오후';
    const h12 = h % 12 === 0 ? 12 : h % 12;
    return ampm + ' ' + h12 + ':' + String(d.getMinutes()).padStart(2, '0');
  }

  function escapeHtml(s) {
    const div = document.createElement('div');
    div.textContent = s;
    return div.innerHTML;
  }

  // ---------- 헤더 배지 ----------
  const badge = document.getElementById('chatBadge');

  function setBadgeCount(count) {
    if (!badge) return;
    if (count > 0) {
      badge.textContent = count > 99 ? '99+' : count;
      badge.style.display = 'flex';
    } else {
      badge.style.display = 'none';
    }
  }

  async function loadChatBadge() {
    if (!badge) return;
    try {
      const res = await fetch('/api/chats/unread-count', { credentials: 'include' });
      if (!res.ok) return;
      const data = await res.json();
      setBadgeCount(data.count || 0);
    } catch (e) { /* 배지는 실패해도 조용히 넘어간다 */ }
  }

  // ---------- WebSocket ----------
  // 배지 요소가 있다 = 로그인 상태다. 어느 페이지에 있든 연결해서
  // 배지와 (채팅 페이지라면) 화면을 실시간으로 갱신한다.
  let socket = null;
  let handleChatMessage = null;   // 페이지별 새 메시지 처리 (목록/대화방에서 등록)
  let handleChatRead = null;      // 상대가 읽었을 때 처리 (대화방에서 등록)
  let handleChatMessageUpdated = null;   // 상대가 메시지를 수정·삭제했을 때 (대화방에서 등록)
  let handleResync = null;        // 소켓이 다시 열렸을 때 화면을 서버 상태로 맞추는 처리 (페이지별 등록)
  let hasConnectedBefore = false; // 최초 연결과 재연결을 구분한다

  function socketOpen() {
    return socket && socket.readyState === WebSocket.OPEN;
  }

  function connectSocket() {
    if (!badge) return;
    const protocol = location.protocol === 'https:' ? 'wss://' : 'ws://';
    try {
      socket = new WebSocket(protocol + location.host + '/ws');
    } catch (e) {
      return;
    }

    socket.onopen = () => {
      // 최초 연결 때는 페이지 로드가 이미 최신 데이터를 가져왔으므로 건너뛴다.
      // 재연결이라면 끊겨 있던 동안의 변경(수정·삭제·읽음)을 놓쳤을 수 있어 따라잡는다.
      if (hasConnectedBefore) {
        loadChatBadge();
        if (handleResync) handleResync();
      }
      hasConnectedBefore = true;
    };

    socket.onmessage = (event) => {
      let data;
      try {
        data = JSON.parse(event.data);
      } catch (e) {
        return;
      }
      if (data.type === 'chat-badge') {
        setBadgeCount(data.count || 0);
      } else if (data.type === 'chat-message' && handleChatMessage) {
        handleChatMessage(data.message);
      } else if (data.type === 'chat-read' && handleChatRead) {
        handleChatRead(data.roomId);
      } else if (data.type === 'chat-message-updated' && handleChatMessageUpdated) {
        handleChatMessageUpdated(data.message);
      } else if (data.type === 'noti-badge') {
        // 알림 배지는 notification.js 담당이라 문서 이벤트로 넘긴다
        document.dispatchEvent(new CustomEvent('noti-badge', { detail: { count: data.count || 0 } }));
      }
    };

    // 끊기면 잠시 후 다시 연결한다 (그 동안은 폴링이 메꾼다)
    socket.onclose = () => {
      socket = null;
      setTimeout(connectSocket, 3000);
    };
  }

  if (badge) {
    loadChatBadge();
    connectSocket();
  }

  // 이 스크립트는 헤더에서 로드되므로, 본문에 있는 요소들은 DOM이 다 그려진 뒤에 찾는다
  document.addEventListener('DOMContentLoaded', initPage);

  function initPage() {

  // ---------- 목록 페이지 ----------
  const roomList = document.getElementById('chatRoomList');

  async function loadRooms() {
    try {
      const res = await fetch('/api/chats', { credentials: 'include' });
      if (!res.ok) throw new Error();
      const data = await res.json();
      renderRooms(data.rooms || []);
    } catch (e) {
      roomList.innerHTML = '<div class="chat-empty">목록을 불러오지 못했습니다.</div>';
    }
  }

  /**
   * 목록 미리보기 문구.
   * 삭제된 메시지는 내용이 비어 있어 "대화 없음"과 구분되지 않으므로 플래그로 판단한다.
   */
  function previewText(r) {
    if (r.lastMessageDeleted) return '삭제된 메시지입니다.';
    if (r.lastMessage) return escapeHtml(r.lastMessage);
    return '대화를 시작해보세요';
  }

  function renderRooms(rooms) {
    if (rooms.length === 0) {
      roomList.innerHTML = '<div class="chat-empty">아직 대화가 없습니다.<br>프로필에서 메시지 버튼을 눌러 대화를 시작해보세요.</div>';
      return;
    }
    roomList.innerHTML = rooms.map(r => `
      <a class="chat-room-item" href="/chat/${r.roomId}">
        <img class="chat-avatar" src="${r.partnerProfileImageUrl || '/images/default-profile.svg'}" alt="프로필" />
        <div class="chat-room-info">
          <div class="chat-room-top">
            <span class="chat-room-name">${escapeHtml(r.partnerUsername)}</span>
            <span class="chat-room-time">${timeAgo(r.lastMessageAt)}</span>
          </div>
          <div class="chat-room-bottom">
            <span class="chat-room-preview ${r.unreadCount > 0 ? 'unread' : ''}">${previewText(r)}</span>
            ${r.unreadCount > 0 ? `<span class="chat-room-unread">${r.unreadCount > 99 ? '99+' : r.unreadCount}</span>` : ''}
          </div>
        </div>
      </a>
    `).join('');
  }

  if (roomList) {
    loadRooms();
    handleChatMessage = () => loadRooms();   // 새 메시지가 오면 목록을 다시 그린다
    handleResync = () => loadRooms();        // 재연결 시에도 목록을 다시 맞춘다
  }

  // ---------- 대화방 페이지 ----------
  const messagesEl = document.getElementById('chatMessages');
  if (!messagesEl) return;

  const roomId = document.getElementById('chatRoomId').value;
  const myId = Number(document.getElementById('chatMyId').value);
  const loadPrevBtn = document.getElementById('chatLoadPrev');
  const form = document.getElementById('chatSendForm');
  const input = document.getElementById('chatInput');
  const sendBtn = document.getElementById('chatSendBtn');

  let oldestPage = 0;      // 지금까지 불러온 가장 오래된 페이지
  let hasPrev = false;     // 이전 페이지가 더 있는지
  let lastMessageId = 0;   // 폴링 보완용: 마지막으로 그린 메시지 id

  // 화면에 그려둔 메시지의 상태 스냅샷 (id -> 내용과 플래그).
  // 폴링으로 받아온 것과 비교해 "실제로 바뀐 것만" 다시 그리기 위해 둔다.
  const rendered = new Map();

  function remember(m) {
    rendered.set(m.id, { content: m.content, read: m.read, edited: m.edited, deleted: m.deleted });
  }

  function isChanged(prev, m) {
    return prev.content !== m.content || prev.read !== m.read
        || prev.edited !== m.edited || prev.deleted !== m.deleted;
  }

  function messageHtml(m) {
    const mine = m.senderId === myId;
    // 삭제된 메시지는 읽음 체크를 보여줄 이유가 없다
    const readMark = (mine && !m.deleted)
      ? `<span class="chat-read-check${m.read ? ' on' : ''}" aria-label="읽음">
           <svg viewBox="0 0 24 24" width="9" height="9" fill="none" stroke="#fff" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
         </span>`
      : '';
    const bubble = m.deleted
      ? `<div class="chat-bubble deleted">삭제된 메시지입니다.</div>`
      : `<div class="chat-bubble">${escapeHtml(m.content)}</div>`;
    // 수정 표시는 말풍선 안이 아니라 시간 뒤에 붙인다 (예: 오후 12:21 (수정됨))
    const editedMark = (m.edited && !m.deleted) ? ' (수정됨)' : '';
    return `
      <div class="chat-msg ${mine ? 'mine' : 'theirs'}" data-id="${m.id}"
           data-read="${m.read}" data-deleted="${m.deleted}">
        ${bubble}
        ${readMark}
        <span class="chat-msg-time">${timeShort(m.createdAt)}${editedMark}</span>
      </div>`;
  }

  /** 이미 그려진 메시지를 새 내용으로 갈아끼운다 (수정·삭제 반영) */
  function replaceMessage(m) {
    const el = messagesEl.querySelector(`.chat-msg[data-id="${m.id}"]`);
    if (!el) return;
    el.outerHTML = messageHtml(m);
    remember(m);
  }

  function scrollToBottom() {
    messagesEl.scrollTop = messagesEl.scrollHeight;
  }

  function appendMessages(list) {
    if (list.length === 0) return;
    const nearBottom = messagesEl.scrollHeight - messagesEl.scrollTop - messagesEl.clientHeight < 80;
    messagesEl.insertAdjacentHTML('beforeend', list.map(messageHtml).join(''));
    list.forEach(remember);
    lastMessageId = Math.max(lastMessageId, list[list.length - 1].id);
    if (nearBottom) scrollToBottom();
  }

  async function fetchPage(page) {
    const res = await fetch(`/api/chats/${roomId}/messages?page=${page}`, { credentials: 'include' });
    if (!res.ok) throw new Error();
    return res.json();
  }

  async function markRead() {
    try {
      await fetch(`/api/chats/${roomId}/read`, { method: 'POST', credentials: 'include' });
      loadChatBadge();
    } catch (e) { /* 읽음 처리는 실패해도 조용히 넘어간다 */ }
  }

  // 상대가 읽으면 내 메시지의 읽음 체크를 켠다
  handleChatRead = (rid) => {
    if (String(rid) !== String(roomId)) return;
    messagesEl.querySelectorAll('.chat-msg.mine .chat-read-check').forEach(el => el.classList.add('on'));
    // 읽힌 뒤에는 수정·삭제가 불가능하므로 메뉴 판단용 상태도 같이 갱신한다
    messagesEl.querySelectorAll('.chat-msg.mine').forEach(el => el.dataset.read = 'true');
    closeMenu();
  };

  // 상대가 자기 메시지를 수정하거나 삭제하면 내 화면도 갱신한다
  handleChatMessageUpdated = (m) => {
    if (!m || String(m.roomId) !== String(roomId)) return;
    replaceMessage(m);
  };

  // WebSocket 푸시: 이 방의 상대 메시지면 바로 붙이고 읽음 처리한다
  handleChatMessage = (m) => {
    if (!m || String(m.roomId) !== String(roomId)) return;
    if (m.senderId === myId) return;   // 내 메시지는 전송 응답에서 이미 그렸다
    appendMessages([m]);
    markRead();
  };

  // 첫 로드: 최신 페이지를 시간순으로 그린다 (API는 최신순이라 뒤집는다)
  async function initMessages() {
    try {
      const data = await fetchPage(0);
      const asc = data.messages.slice().reverse();
      loadPrevBtn.insertAdjacentHTML('afterend', asc.map(messageHtml).join(''));
      asc.forEach(remember);
      hasPrev = data.hasNext;
      loadPrevBtn.style.display = hasPrev ? 'block' : 'none';
      if (asc.length > 0) lastMessageId = asc[asc.length - 1].id;
      scrollToBottom();
      markRead();
    } catch (e) {
      messagesEl.innerHTML = '<div class="chat-empty">메시지를 불러오지 못했습니다.</div>';
    }
  }

  // 위의 "이전 메시지 보기": 다음 페이지를 위쪽에 붙인다
  loadPrevBtn.addEventListener('click', async () => {
    try {
      const data = await fetchPage(oldestPage + 1);
      oldestPage += 1;
      const asc = data.messages.slice().reverse();
      const prevHeight = messagesEl.scrollHeight;
      loadPrevBtn.insertAdjacentHTML('afterend', asc.map(messageHtml).join(''));
      asc.forEach(remember);
      hasPrev = data.hasNext;
      loadPrevBtn.style.display = hasPrev ? 'block' : 'none';
      // 붙인 만큼 스크롤을 보정해서 보던 위치를 유지한다
      messagesEl.scrollTop = messagesEl.scrollHeight - prevHeight;
    } catch (e) { /* 실패 시 버튼을 다시 누르면 된다 */ }
  });

  /**
   * 서버 상태와 화면을 맞춘다.
   * 새 메시지는 붙이고, 이미 그려둔 메시지는 내용·플래그가 바뀐 것만 다시 그린다.
   * 수정·삭제·읽음은 id가 바뀌지 않으므로 "새 id만 붙이는" 방식으로는 잡히지 않는다.
   */
  async function resyncMessages() {
    try {
      const data = await fetchPage(0);
      const asc = data.messages.slice().reverse();

      const fresh = [];
      for (const m of asc) {
        const prev = rendered.get(m.id);
        if (!prev) {
          fresh.push(m);
        } else if (isChanged(prev, m)) {
          replaceMessage(m);
        }
      }

      if (fresh.length > 0) {
        appendMessages(fresh);
        if (fresh.some(m => m.senderId !== myId)) markRead();
      }
    } catch (e) { /* 다음 기회에 다시 맞춘다 */ }
  }

  // 소켓이 끊겨 있는 동안에만 폴링으로 대신 맞춘다
  async function pollNew() {
    if (socketOpen()) return;
    await resyncMessages();
  }

  // 재연결 시에도 끊긴 동안의 변경을 따라잡는다
  handleResync = resyncMessages;

  // ---------- 우클릭 메뉴 (수정·삭제) ----------
  const menu = document.getElementById('chatMsgMenu');
  const editModal = document.getElementById('chatEditModal');
  const editInput = document.getElementById('chatEditInput');
  let targetId = null;   // 메뉴가 열린 대상 메시지 id

  function closeMenu() {
    menu.style.display = 'none';
    targetId = null;
  }

  /** 상대가 읽기 전인 내 메시지에서만 메뉴를 연다 */
  messagesEl.addEventListener('contextmenu', (e) => {
    const el = e.target.closest('.chat-msg.mine');
    if (!el) return;                                   // 상대 메시지는 브라우저 기본 메뉴를 둔다
    if (el.dataset.read === 'true' || el.dataset.deleted === 'true') return;

    e.preventDefault();
    targetId = el.dataset.id;
    menu.style.display = 'block';

    // 메뉴는 .chat-room-container 기준으로 배치되므로(offsetParent),
    // 좌표 변환과 경계 보정 모두 같은 기준을 써야 한다.
    const box = menu.offsetParent.getBoundingClientRect();
    const x = Math.min(e.clientX - box.left, box.width - menu.offsetWidth - 8);
    const y = Math.min(e.clientY - box.top, box.height - menu.offsetHeight - 8);
    menu.style.left = Math.max(8, x) + 'px';
    menu.style.top = Math.max(8, y) + 'px';
  });

  document.addEventListener('click', (e) => {
    if (!menu.contains(e.target)) closeMenu();
  });
  messagesEl.addEventListener('scroll', closeMenu);

  document.getElementById('chatEditBtn').addEventListener('click', () => {
    const el = messagesEl.querySelector(`.chat-msg[data-id="${targetId}"] .chat-bubble`);
    if (!el) return;
    editInput.value = el.textContent;   // 수정 표시가 말풍선 밖으로 빠져서 그대로 쓰면 된다
    editModal.dataset.messageId = targetId;
    editModal.style.display = 'flex';
    closeMenu();
    editInput.focus();
  });

  document.getElementById('chatDeleteBtn').addEventListener('click', async () => {
    const id = targetId;
    closeMenu();
    if (!id) return;
    if (!(await confirmDialog('이 메시지를 삭제할까요?'))) return;
    try {
      const res = await fetch(`/api/chats/messages/${id}`, { method: 'DELETE', credentials: 'include' });
      const data = await res.json();
      if (!res.ok) { showToast(data.message || '삭제하지 못했습니다.', 'error'); return; }
      replaceMessage(data);
    } catch (e) {
      showToast('삭제하지 못했습니다.', 'error');
    }
  });

  document.getElementById('chatEditCancel').addEventListener('click', () => {
    editModal.style.display = 'none';
  });

  document.getElementById('chatEditSave').addEventListener('click', async () => {
    const id = editModal.dataset.messageId;
    const content = editInput.value.trim();
    if (!content) { showToast('메시지 내용을 입력해주세요.', 'error'); return; }
    try {
      const res = await fetch(`/api/chats/messages/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ content })
      });
      const data = await res.json();
      if (!res.ok) { showToast(data.message || '수정하지 못했습니다.', 'error'); return; }
      replaceMessage(data);
      editModal.style.display = 'none';
    } catch (e) {
      showToast('수정하지 못했습니다.', 'error');
    }
  });

  // 전송
  input.addEventListener('input', () => {
    sendBtn.disabled = input.value.trim().length === 0;
  });

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const content = input.value.trim();
    if (!content) return;
    sendBtn.disabled = true;
    try {
      const res = await fetch(`/api/chats/${roomId}/messages`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ content })
      });
      const data = await res.json();
      if (!res.ok) {
        showToast(data.message || '메시지를 보내지 못했습니다.', 'error');
        return;
      }
      messagesEl.insertAdjacentHTML('beforeend', messageHtml(data));
      remember(data);
      lastMessageId = Math.max(lastMessageId, data.id);
      input.value = '';
      scrollToBottom();
    } catch (err) {
      showToast('메시지를 보내지 못했습니다.', 'error');
    } finally {
      sendBtn.disabled = input.value.trim().length === 0;
      input.focus();
    }
  });

  initMessages();
  setInterval(pollNew, 3000);
  }
})();
