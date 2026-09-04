// 댓글 답글 달기.
//
// 답글 버튼을 누르면 입력창에 상대 아이디를 미리 채워준다. 답글은 한 단만 있어서
// 누구에게 답한 것인지가 위치로는 드러나지 않기 때문에, 그 역할을 멘션이 대신한다.
//
// 페이지마다 복붙하지 않고 여기 한 곳에 둔다. 예전에 헤더 동작을 페이지마다
// 복사해뒀다가 새로 만든 화면에서 통째로 빠진 적이 있다.
(function () {
  'use strict';

  function els() {
    return {
      input: document.getElementById('commentInput'),
      parentId: document.getElementById('replyParentId'),
      target: document.getElementById('replyTarget'),
      targetText: document.getElementById('replyTargetText')
    };
  }

  function startReply(commentId, author) {
    const { input, parentId, target, targetText } = els();
    if (!input || !parentId) return;

    parentId.value = commentId;
    if (targetText) targetText.textContent = author + '님에게 답글';
    if (target) target.style.visibility = 'visible';

    const mention = '@' + author + ' ';
    // 이미 채워둔 멘션이 있으면 바꿔치고, 사용자가 쓰던 글은 남긴다
    input.value = input.value.replace(/^@[a-z0-9._]{4,10}\s*/, '');
    input.value = mention + input.value;
    input.focus();
    input.setSelectionRange(input.value.length, input.value.length);
  }

  function cancelReply() {
    const { input, parentId, target } = els();
    if (parentId) parentId.value = '';
    if (target) target.style.visibility = 'hidden';
    if (input) {
      input.value = input.value.replace(/^@[a-z0-9._]{4,10}\s*/, '');
      input.focus();
    }
  }

  /**
   * 지금 쓰는 댓글이 답글이면 원 댓글 id, 아니면 null.
   * 멘션을 지웠더라도 답글이라는 사실은 그대로 둔다. 그래야 답을 받은 사람에게 알림이 간다.
   */
  window.getReplyParentId = function () {
    const el = document.getElementById('replyParentId');
    const value = el ? el.value : '';
    return value ? Number(value) : null;
  };

  document.addEventListener('click', function (e) {
    const replyBtn = e.target.closest && e.target.closest('.reply-comment-btn');
    if (replyBtn) {
      startReply(replyBtn.getAttribute('data-comment-id'), replyBtn.getAttribute('data-author'));
      return;
    }

    if (e.target && e.target.id === 'replyCancelBtn') {
      cancelReply();
    }
  });
})();
