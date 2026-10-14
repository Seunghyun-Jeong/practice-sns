// 목록 끝에 닿으면 다음 묶음을 부른다.
//
// 피드, 게시글 댓글, 팔로워 목록이 같이 쓴다. 화면마다 따로 두었다가
// 한쪽만 고쳐지는 일이 없도록 "끝에 닿았는지 알아채는 일"은 여기 한 곳에서만 한다.
// 다음 묶음이 있는지, 지금 불러오는 중인지는 각 화면이 알고 있으므로 onReach 안에서 판단한다.
(function () {
  'use strict';

  // 끝 표시가 보이기 이만큼 전에 미리 부른다
  const MARGIN = 200;

  /**
   * sentinel: 목록 바로 아래에 둔 빈 칸
   * onReach:  끝에 닿았을 때 부를 함수
   * options.root: 스크롤되는 상자. 없으면 창 전체
   *
   * 돌려주는 check()는 묶음을 붙인 뒤에 부른다. 붙인 묶음이 짧아 끝이 여전히 보이면
   * 옵저버는 "보이기 시작하는 순간"에만 알려주기 때문에 다시 알려주지 않는다.
   * stop()은 목록을 닫을 때 부른다.
   */
  window.watchListEnd = function (sentinel, onReach, options) {
    const root = (options && options.root) || null;

    function nearEnd() {
      // 감춰진 목록(닫힌 모달)은 위치가 전부 0으로 나와 끝에 닿은 것처럼 보인다
      if (!sentinel.isConnected || sentinel.offsetParent === null) return false;
      const bottom = root ? root.getBoundingClientRect().bottom : window.innerHeight;
      return sentinel.getBoundingClientRect().top - bottom < MARGIN;
    }

    function check() {
      if (nearEnd()) onReach();
    }

    const observer = new IntersectionObserver(entries => {
      if (entries[0].isIntersecting) onReach();
    }, { root, rootMargin: MARGIN + 'px' });
    observer.observe(sentinel);

    // 옵저버 알림이 늦거나 오지 않는 환경을 대비해 스크롤로도 확인한다
    const scroller = root || window;
    scroller.addEventListener('scroll', check, { passive: true });
    window.addEventListener('resize', check);

    return {
      check,
      stop() {
        observer.disconnect();
        scroller.removeEventListener('scroll', check);
        window.removeEventListener('resize', check);
      }
    };
  };
})();
