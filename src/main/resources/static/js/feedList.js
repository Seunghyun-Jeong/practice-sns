// 게시글 카드 목록을 /feed 조각으로 채운다.
//
// 메인 피드와 프로필의 게시글이 같이 쓴다. 다음 묶음 받기, 첫 묶음으로 갈아 끼우기,
// 늦게 온 응답 버리기를 화면마다 따로 두면 한쪽만 고쳐지므로 여기 한 곳에만 둔다.
// 탭 전환이나 새 글 확인처럼 메인 피드에만 있는 일은 메인 피드가 이 목록을 써서 한다.
(function () {
  'use strict';

  /**
   * container: 카드가 들어가는 상자. 첫 묶음은 서버가 그려 두고, data-has-next로 다음 묶음이 있는지 알려 준다
   * sentinel:  목록 바로 아래 빈 칸. 보이면 다음 묶음을 받는다
   * loading:   불러오는 동안 보여줄 요소 (없어도 된다)
   * params:    지금 보는 피드의 조건을 쿼리 문자열로 돌려주는 함수 (tab, tag, 사용자 등)
   */
  window.createFeedList = function ({ container, sentinel, loading, params }) {
    let hasNext = container.getAttribute('data-has-next') === 'true';
    let isLoading = false;

    // 첫 묶음으로 갈아 끼울 때마다 올라간다. 요청을 보낼 때 값을 잡아두고 응답이 왔을 때 달라져 있으면 버린다.
    // 이게 없으면 이전 피드의 다음 묶음이나 먼저 요청한 피드의 응답이 늦게 도착해 새 피드에 섞인다.
    let seq = 0;

    function showLoading(on) {
      if (loading) loading.style.display = on ? 'block' : 'none';
    }

    function hasNextIn(root) {
      const meta = root.querySelector('.feed-page-meta');
      return meta ? meta.getAttribute('data-has-next') === 'true' : false;
    }

    // 카드 하나의 자리 (작성 시각, id). prefix는 before(다음 묶음) 또는 after(새 글 확인)
    function cardCursor(prefix, card) {
      return card
        ? `&${prefix}At=${encodeURIComponent(card.dataset.createdAt)}&${prefix}Id=${card.dataset.postId}`
        : '';
    }

    // lastCard가 없으면 첫 묶음, 있으면 그 카드의 (작성 시각, id)보다 먼저 쓴 글부터 받는다.
    // 페이지 번호로 받으면 보는 사이 새 글이 올라왔을 때 밀려서 본 글이 다시 나온다.
    async function fetchCards(lastCard) {
      const response = await fetch(`/feed?${params()}${cardCursor('before', lastCard)}`, { credentials: 'include' });
      if (!response.ok) throw new Error('failed to load feed');
      const temp = document.createElement('div');
      temp.innerHTML = await response.text();
      return temp;
    }

    async function loadNext() {
      if (isLoading || !hasNext) return;
      isLoading = true;
      showLoading(true);
      const mySeq = seq;

      try {
        const shown = container.querySelectorAll('.post-summary');
        const temp = await fetchCards(shown[shown.length - 1]);
        if (mySeq !== seq) return;

        const cards = [...temp.querySelectorAll('.post-summary')];
        if (cards.length === 0) {
          hasNext = false;
          return;
        }

        // 이벤트는 새 카드에만 연결한다 (붙이기 전에 연결해도 이동 후 그대로 유지됨)
        initPostCards(temp);
        cards.forEach(card => container.appendChild(card));
        hasNext = hasNextIn(temp);
      } catch (err) {
        console.error(err);
        if (mySeq === seq) hasNext = false;
      } finally {
        if (mySeq === seq) {
          isLoading = false;
          showLoading(false);
          end.check();   // 붙인 묶음이 짧아 끝이 여전히 보이면 이어서 채운다
        }
      }
    }

    // 상자만 첫 묶음으로 갈아 끼운다. 페이지 전체를 다시 불러오지 않는다.
    async function reload() {
      seq += 1;
      const mySeq = seq;
      hasNext = false;
      isLoading = true;
      showLoading(true);

      window.scrollTo(0, 0);
      // 스크롤을 코드로 되돌린 것이라 배경 별은 따라오지 않게 한다
      if (window.starfieldRebase) window.starfieldRebase();

      try {
        const temp = await fetchCards(null);
        if (mySeq !== seq) return;

        initPostCards(temp);
        container.replaceChildren(...temp.childNodes);
        hasNext = hasNextIn(container);
      } catch (err) {
        console.error(err);
        if (mySeq === seq) showToast('피드를 불러오지 못했습니다.', 'error');
      } finally {
        if (mySeq === seq) {
          isLoading = false;
          showLoading(false);
          end.check();   // 새 첫 묶음이 짧으면 바로 다음 묶음을 채운다
        }
      }
    }

    const end = watchListEnd(sentinel, loadNext);
    end.check();   // 첫 화면이 짧아 스크롤이 없을 때도 채운다

    return {
      reload,
      cardCursor,
      firstCard: () => container.querySelector('.post-summary'),
      isLoading: () => isLoading,
      // 응답을 기다리는 사이 피드가 바뀌었는지 확인할 때 쓴다
      seq: () => seq
    };
  };
})();
