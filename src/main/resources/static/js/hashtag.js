(function () {
  const TAG_PATTERN = /#([0-9A-Za-z가-힣_]+)/g;

  /**
   * 글 내용 안의 #태그를 링크로 바꾼다.
   * 서버가 만든 HTML을 다시 조립하지 않고, 텍스트를 쪼개 안전하게 노드로 만든다.
   */
  function linkifyElement(el) {
    if (!el || el.dataset.tagLinked === 'true') return;

    const text = el.textContent;
    if (!text || text.indexOf('#') === -1) {
      el.dataset.tagLinked = 'true';
      return;
    }

    const frag = document.createDocumentFragment();
    let lastIndex = 0;
    let match;

    TAG_PATTERN.lastIndex = 0;
    while ((match = TAG_PATTERN.exec(text)) !== null) {
      if (match.index > lastIndex) {
        frag.appendChild(document.createTextNode(text.slice(lastIndex, match.index)));
      }

      const link = document.createElement('a');
      link.className = 'hashtag-link';
      link.href = '/?tag=' + encodeURIComponent(match[1].toLowerCase());
      link.textContent = match[0];
      link.addEventListener('click', (e) => e.stopPropagation());   // 카드 클릭(모달 열기)과 겹치지 않게
      frag.appendChild(link);

      lastIndex = match.index + match[0].length;
    }

    if (lastIndex < text.length) {
      frag.appendChild(document.createTextNode(text.slice(lastIndex)));
    }

    el.textContent = '';
    el.appendChild(frag);
    el.dataset.tagLinked = 'true';
  }

  /** 전달한 범위 안의 글 내용에 태그 링크를 적용한다 */
  window.linkifyHashtags = function (root) {
    (root || document)
      .querySelectorAll('.post-caption-text, .post-content, .comment-text')
      .forEach(linkifyElement);
  };

  document.addEventListener('DOMContentLoaded', () => window.linkifyHashtags(document));
})();
