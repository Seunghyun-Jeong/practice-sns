// 댓글 본문의 @아이디를 프로필 링크로 바꾼다.
//
// 해시태그와 달리 정규식만으로는 링크를 만들 수 없다. 프로필 주소가 아이디가 아니라
// id 기준이라, 서버가 data-mentions에 실어준 짝을 보고 주소를 만든다.
//
// 같은 요소를 해시태그 쪽에서도 건드리기 때문에, 요소 전체를 다시 만들지 않고
// 글자만 담긴 노드를 찾아 그 안에서만 바꾼다. 통째로 갈아끼우면 먼저 만들어진
// 해시태그 링크가 사라진다.
(function () {
  const MENTION_PATTERN = /(?<![A-Za-z0-9._])@([a-z0-9._]{4,10})/g;

  function knownMentions(el) {
    const known = {};
    try {
      (JSON.parse(el.dataset.mentions || '[]') || []).forEach(m => {
        known[m.username] = m.userId;
      });
    } catch (e) { /* 링크만 못 걸릴 뿐 글은 그대로 보인다 */ }
    return known;
  }

  /** 글자 노드 하나를 잘라 멘션만 링크로 바꾼 조각을 돌려준다 (바꿀 게 없으면 null) */
  function replaceInTextNode(node, known) {
    const text = node.nodeValue;
    if (!text || text.indexOf('@') === -1) return null;

    const frag = document.createDocumentFragment();
    let lastIndex = 0;
    let match;
    let changed = false;

    MENTION_PATTERN.lastIndex = 0;
    while ((match = MENTION_PATTERN.exec(text)) !== null) {
      const userId = known[match[1]];
      if (userId === undefined) continue;   // 없는 아이디는 글자 그대로 둔다

      if (match.index > lastIndex) {
        frag.appendChild(document.createTextNode(text.slice(lastIndex, match.index)));
      }

      const link = document.createElement('a');
      link.className = 'mention-link';
      link.href = '/profile/' + userId;
      link.textContent = match[0];
      link.addEventListener('click', (e) => e.stopPropagation());   // 카드 클릭과 겹치지 않게
      frag.appendChild(link);

      lastIndex = match.index + match[0].length;
      changed = true;
    }

    if (!changed) return null;

    if (lastIndex < text.length) {
      frag.appendChild(document.createTextNode(text.slice(lastIndex)));
    }
    return frag;
  }

  function linkifyElement(el) {
    if (!el || el.dataset.mentionLinked === 'true') return;
    el.dataset.mentionLinked = 'true';

    if (!el.textContent || el.textContent.indexOf('@') === -1) return;

    const known = knownMentions(el);
    if (Object.keys(known).length === 0) return;

    // 순회 중에 바꾸면 목록이 흔들리므로 먼저 모아둔다
    const textNodes = [];
    const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
    while (walker.nextNode()) {
      textNodes.push(walker.currentNode);
    }

    textNodes.forEach(node => {
      const frag = replaceInTextNode(node, known);
      if (frag) node.parentNode.replaceChild(frag, node);
    });
  }

  window.linkifyMentions = function (root) {
    (root || document).querySelectorAll('.comment-text').forEach(linkifyElement);
  };

  document.addEventListener('DOMContentLoaded', () => window.linkifyMentions(document));
})();
