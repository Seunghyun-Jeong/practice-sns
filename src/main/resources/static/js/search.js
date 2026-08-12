(function () {
  const input = document.getElementById('searchInput');
  const dropdown = document.getElementById('searchDropdown');
  if (!input || !dropdown) return;

  let timer = null;
  let lastQuery = '';

  function close() {
    dropdown.style.display = 'none';
  }

  function renderEmpty(message) {
    dropdown.innerHTML = '';
    const empty = document.createElement('div');
    empty.className = 'search-empty';
    empty.textContent = message;
    dropdown.appendChild(empty);
    dropdown.style.display = 'block';
  }

  function buildUserRow(u) {
    const row = document.createElement('div');
    row.className = 'search-row';

    const avatar = document.createElement('img');
    avatar.className = 'search-avatar';
    avatar.src = u.profileImageUrl || '/images/default-profile.svg';
    avatar.alt = '프로필';

    const name = document.createElement('div');
    name.className = 'search-name';
    name.textContent = u.username;

    row.appendChild(avatar);
    row.appendChild(name);
    row.addEventListener('mousedown', () => {
      window.location.href = '/profile/' + u.id;
    });
    return row;
  }

  function buildTagRow(t) {
    const row = document.createElement('div');
    row.className = 'search-row';

    const icon = document.createElement('div');
    icon.className = 'search-tag-icon';
    icon.textContent = '#';

    const body = document.createElement('div');
    body.className = 'search-tag-body';

    const name = document.createElement('div');
    name.className = 'search-name';
    name.textContent = '#' + t.name;

    const count = document.createElement('div');
    count.className = 'search-sub';
    count.textContent = '게시물 ' + t.postCount + '개';

    body.appendChild(name);
    body.appendChild(count);

    row.appendChild(icon);
    row.appendChild(body);
    row.addEventListener('mousedown', () => {
      window.location.href = '/?tag=' + encodeURIComponent(t.name);
    });
    return row;
  }

  function render(data) {
    const users = data.users || [];
    const tags = data.hashtags || [];

    if (users.length === 0 && tags.length === 0) {
      renderEmpty('검색 결과가 없습니다.');
      return;
    }

    dropdown.innerHTML = '';

    if (users.length > 0) {
      const head = document.createElement('div');
      head.className = 'search-section';
      head.textContent = '계정';
      dropdown.appendChild(head);
      users.forEach(u => dropdown.appendChild(buildUserRow(u)));
    }

    if (tags.length > 0) {
      const head = document.createElement('div');
      head.className = 'search-section';
      head.textContent = '해시태그';
      dropdown.appendChild(head);
      tags.forEach(t => dropdown.appendChild(buildTagRow(t)));
    }

    dropdown.style.display = 'block';
  }

  async function doSearch(q) {
    try {
      const res = await fetch('/api/search?q=' + encodeURIComponent(q), { credentials: 'include' });
      if (!res.ok) { close(); return; }
      const data = await res.json();
      if (input.value.trim() !== q) return;   // 그새 입력이 바뀌었으면 버린다
      render(data);
    } catch (e) {
      console.error(e);
      close();
    }
  }

  input.addEventListener('input', () => {
    const q = input.value.trim();
    clearTimeout(timer);

    if (q === '') { close(); lastQuery = ''; return; }
    if (q === lastQuery) return;
    lastQuery = q;

    // 타이핑이 멈춘 뒤에 한 번만 요청한다
    timer = setTimeout(() => doSearch(q), 250);
  });

  input.addEventListener('focus', () => {
    if (input.value.trim() !== '' && dropdown.innerHTML !== '') {
      dropdown.style.display = 'block';
    }
  });

  // 엔터를 치면 첫 번째 결과로 이동
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') { close(); input.blur(); return; }
    if (e.key !== 'Enter') return;
    const first = dropdown.querySelector('.search-row');
    if (first) first.dispatchEvent(new MouseEvent('mousedown'));
  });

  document.addEventListener('click', (e) => {
    if (!e.target.closest('.search-box')) close();
  });
})();
