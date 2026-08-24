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
            <span class="chat-room-preview ${r.unreadCount > 0 ? 'unread' : ''}">${r.lastMessage ? escapeHtml(r.lastMessage) : '대화를 시작해보세요'}</span>
            ${r.unreadCount > 0 ? `<span class="chat-room-unread">${r.unreadCount > 99 ? '99+' : r.unreadCount}</span>` : ''}
          </div>
        </div>
      </a>
    `).join('');
  }

  if (roomList) {
    loadRooms();
    handleChatMessage = () => loadRooms();   // 새 메시지가 오면 목록을 다시 그린다
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

  function messageHtml(m) {
    const mine = m.senderId === myId;
    const readMark = mine
      ? `<span class="chat-read-check${m.read ? ' on' : ''}" aria-label="읽음">
           <svg viewBox="0 0 24 24" width="9" height="9" fill="none" stroke="#fff" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
         </span>`
      : '';
    return `
      <div class="chat-msg ${mine ? 'mine' : 'theirs'}" data-id="${m.id}">
        <div class="chat-bubble">${escapeHtml(m.content)}</div>
        ${readMark}
        <span class="chat-msg-time">${timeShort(m.createdAt)}</span>
      </div>`;
  }

  function scrollToBottom() {
    messagesEl.scrollTop = messagesEl.scrollHeight;
  }

  function appendMessages(list) {
    if (list.length === 0) return;
    const nearBottom = messagesEl.scrollHeight - messagesEl.scrollTop - messagesEl.clientHeight < 80;
    messagesEl.insertAdjacentHTML('beforeend', list.map(messageHtml).join(''));
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
      hasPrev = data.hasNext;
      loadPrevBtn.style.display = hasPrev ? 'block' : 'none';
      // 붙인 만큼 스크롤을 보정해서 보던 위치를 유지한다
      messagesEl.scrollTop = messagesEl.scrollHeight - prevHeight;
    } catch (e) { /* 실패 시 버튼을 다시 누르면 된다 */ }
  });

  // 폴링 보완: 소켓이 끊겨 있는 동안만 최신 페이지를 다시 받아 빠진 메시지를 붙인다
  async function pollNew() {
    if (socketOpen()) return;
    try {
      const data = await fetchPage(0);
      const asc = data.messages.slice().reverse();
      const fresh = asc.filter(m => m.id > lastMessageId);
      if (fresh.length === 0) return;
      appendMessages(fresh);
      if (fresh.some(m => m.senderId !== myId)) markRead();
    } catch (e) { /* 다음 폴링에서 다시 시도한다 */ }
  }

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
