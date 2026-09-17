// 헤더의 동작(햄버거 메뉴, 로그아웃, 회원탈퇴).
// 헤더는 공용 fragment 인데 동작이 페이지마다 복사되어 있어서
// 새로 만든 페이지에서 빠지는 일이 있었다. 헤더가 스스로 동작을 챙기도록 여기에 모은다.
(function () {
  'use strict';

  /*
   * 배경 별.
   * 순수 CSS(다중 box-shadow)로 만들면 마크업이 필요 없지만 한 레이어의 별이
   * 전부 같이 반짝인다. opacity가 요소 단위이기 때문이다.
   * 별마다 다른 시점에 반짝이게 하려면 요소가 따로 있어야 해서 여기서 만든다.
   * 템플릿 10개를 고치는 대신, 이미 모든 페이지에 실리는 이 파일에 둔다.
   */
  const STAR_COUNT = 70;
  const TWINKLE_RATIO = 0.25;

  /*
   * 이스터에그. 페이지를 열 때마다 하나를 골라 배경 어딘가에 놓는다.
   * 이름표도 연결선도 그리지 않는다. 선을 그으면 별자리가 아니라 도형이 되고,
   * 배경을 보다가 알아채는 순간이 이 기능의 전부다.
   * 좌표는 0~1로 정규화한 근사값이다. 천문 데이터가 아니라 모양을 알아보게 하는 것이 목적.
   */
  const CONSTELLATIONS = [
    { name: '북두칠성', ratio: 0.55, stars: [
      [0.00, 0.20], [0.17, 0.12], [0.33, 0.10], [0.48, 0.20],
      [0.52, 0.45], [0.80, 0.52], [0.78, 0.22]
    ]},
    { name: '오리온자리', ratio: 1.05, stars: [
      [0.22, 0.10], [0.72, 0.06],
      [0.36, 0.48], [0.48, 0.47], [0.60, 0.45],
      [0.30, 0.92], [0.78, 0.88]
    ]},
    { name: '카시오페이아자리', ratio: 0.5, stars: [
      [0.00, 0.10], [0.24, 0.62], [0.50, 0.18], [0.74, 0.66], [1.00, 0.05]
    ]},
    { name: '백조자리', ratio: 1.0, stars: [
      [0.50, 0.00], [0.50, 0.45], [0.10, 0.55], [0.90, 0.38], [0.50, 1.00]
    ]},
    { name: '전갈자리', ratio: 0.95, stars: [
      [0.10, 0.02], [0.22, 0.10], [0.16, 0.18],
      [0.30, 0.30], [0.38, 0.48], [0.44, 0.66],
      [0.56, 0.80], [0.72, 0.86], [0.86, 0.76], [0.92, 0.62]
    ]}
  ];

  function rand(min, max) {
    return min + Math.random() * (max - min);
  }

  function pick(list) {
    return list[Math.floor(Math.random() * list.length)];
  }

  /*
   * 별자리는 묶음 단위로 떠다닌다.
   * 별마다 따로 움직이면 시간이 지날수록 모양이 일그러져 알아볼 수 없게 된다.
   * 래퍼가 통째로 움직이고 개별 별은 반짝이기만 한다.
   */
  function buildConstellation() {
    const data = pick(CONSTELLATIONS);
    const width = rand(150, 260);
    const height = width * data.ratio;

    const box = document.createElement('div');
    box.className = 'cst';
    box.dataset.name = data.name;

    const vw = window.innerWidth;
    const vh = window.innerHeight;

    // 콘텐츠 열(최대 600px + 좌우 여백)을 피해 양옆에 둔다.
    // 좁은 화면은 피할 자리가 없으므로 그냥 아무 데나 둔다.
    const margin = (vw - 640) / 2;
    let x;
    if (margin > width + 40) {
      x = Math.random() < 0.5 ? rand(10, margin - width - 10) : rand(vw - margin + 10, vw - width - 10);
    } else {
      x = rand(10, Math.max(20, vw - width - 10));
    }
    const y = rand(60, Math.max(80, vh - height - 40));

    box.style.setProperty('--x', Math.round(x) + 'px');
    box.style.setProperty('--y', Math.round(y) + 'px');
    box.style.setProperty('--w', Math.round(width) + 'px');
    box.style.setProperty('--h', Math.round(height) + 'px');
    box.style.setProperty('--mx', rand(-12, 12).toFixed(1) + 'px');
    box.style.setProperty('--my', rand(-16, 16).toFixed(1) + 'px');
    box.style.setProperty('--dt', rand(70, 130).toFixed(1) + 's');
    box.style.setProperty('--dd', rand(-50, 0).toFixed(1) + 's');

    // 몇 개만 반짝이게 고른다
    const twinkling = new Set();
    while (twinkling.size < Math.min(3, data.stars.length)) {
      twinkling.add(Math.floor(Math.random() * data.stars.length));
    }

    data.stars.forEach(([sx, sy], i) => {
      const star = document.createElement('i');
      // 일반 별보다 크고 밝아야 하나의 덩어리로 읽혀 모양을 알아볼 수 있다
      star.style.setProperty('--x', (sx * 100).toFixed(2) + '%');
      star.style.setProperty('--y', (sy * 100).toFixed(2) + '%');
      star.style.setProperty('--s', rand(1.8, 3).toFixed(2) + 'px');
      star.style.setProperty('--o', rand(0.5, 0.85).toFixed(2));

      if (twinkling.has(i)) {
        star.classList.add('twinkle');
        star.style.setProperty('--tt', rand(3.5, 7).toFixed(1) + 's');
        star.style.setProperty('--td', rand(0, 10).toFixed(1) + 's');
      }

      box.appendChild(star);
    });

    return box;
  }

  function buildStarfield() {
    if (document.querySelector('.starfield')) return;

    const layer = document.createElement('div');
    layer.className = 'starfield';
    layer.setAttribute('aria-hidden', 'true');

    const stars = document.createDocumentFragment();

    for (let i = 0; i < STAR_COUNT; i++) {
      const star = document.createElement('i');
      // 대부분 작고 흐리게. 글을 읽는 데 방해가 되면 안 된다.
      const size = Math.random() < 0.82 ? rand(1, 1.6) : rand(1.8, 2.6);

      star.style.setProperty('--x', rand(0, 100).toFixed(2) + '%');
      star.style.setProperty('--y', rand(0, 100).toFixed(2) + '%');
      star.style.setProperty('--s', size.toFixed(2) + 'px');
      star.style.setProperty('--o', rand(0.12, 0.55).toFixed(2));

      // 떠다님: 아주 느리게, 방향은 별마다 다르게
      star.style.setProperty('--mx', rand(-18, 18).toFixed(1) + 'px');
      star.style.setProperty('--my', rand(-30, 30).toFixed(1) + 'px');
      star.style.setProperty('--dt', rand(40, 110).toFixed(1) + 's');
      star.style.setProperty('--dd', rand(-40, 0).toFixed(1) + 's');

      // 일부만 반짝인다. 지연을 흩어 놓아야 한꺼번에 깜빡이지 않는다.
      if (Math.random() < TWINKLE_RATIO) {
        star.classList.add('twinkle');
        star.style.setProperty('--tt', rand(3, 7).toFixed(1) + 's');
        star.style.setProperty('--td', rand(0, 14).toFixed(1) + 's');
      }

      stars.appendChild(star);
    }

    layer.appendChild(stars);
    layer.appendChild(buildConstellation());
    document.body.insertBefore(layer, document.body.firstChild);
  }

  document.addEventListener('DOMContentLoaded', function () {
    buildStarfield();

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
