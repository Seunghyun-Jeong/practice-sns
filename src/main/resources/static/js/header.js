// 헤더의 동작(햄버거 메뉴, 로그아웃, 회원탈퇴).
// 헤더는 공용 fragment 인데 동작이 페이지마다 복사되어 있어서
// 새로 만든 페이지에서 빠지는 일이 있었다. 헤더가 스스로 동작을 챙기도록 여기에 모은다.
(function () {
  'use strict';

  document.addEventListener('DOMContentLoaded', function () {
    const hamburgerBtn = document.getElementById('hamburgerBtn');
    const dropdownMenu = document.getElementById('dropdownMenu');
    const logoutBtn = document.getElementById('logoutBtn');
    const deleteAccountBtn = document.getElementById('deleteAccountBtn');

    // 햄버거 메뉴 열고 닫기
    if (hamburgerBtn && dropdownMenu) {
      hamburgerBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        dropdownMenu.style.display = dropdownMenu.style.display === 'block' ? 'none' : 'block';
      });

      // 메뉴 바깥을 누르면 닫는다
      document.addEventListener('click', (e) => {
        if (dropdownMenu.style.display !== 'block') return;
        if (!dropdownMenu.contains(e.target) && !hamburgerBtn.contains(e.target)) {
          dropdownMenu.style.display = 'none';
        }
      });
    }

    // 로그아웃: 쿠키를 만료시키고 메인으로 보낸다
    if (logoutBtn) {
      logoutBtn.addEventListener('click', () => {
        fetch('/api/users/logout', { method: 'POST', credentials: 'include' })
          .then(() => { window.location.href = '/'; })
          .catch(() => { window.location.href = '/'; });
      });
    }

    // 회원탈퇴
    if (deleteAccountBtn) {
      deleteAccountBtn.addEventListener('click', async () => {
        const ok = await confirmDialog('정말로 회원 탈퇴하시겠습니까?', { okText: '탈퇴', danger: true });
        if (!ok) return;

        try {
          const res = await fetch('/api/users/me', { method: 'DELETE', credentials: 'include' });
          if (res.ok) {
            toastAfterReload('회원 탈퇴가 완료되었습니다.');
            window.location.href = '/login';
          } else {
            const data = await res.json();
            showToast(data.message || '회원 탈퇴에 실패했습니다.', 'error');
          }
        } catch (err) {
          showToast('에러가 발생했습니다.', 'error');
        }
      });
    }
  });
})();
