(function () {
  // ===== 토스트 =====
  function ensureToastContainer() {
    var c = document.getElementById('toastContainer');
    if (!c) {
      c = document.createElement('div');
      c.id = 'toastContainer';
      c.className = 'toast-container';
      document.body.appendChild(c);
    }
    return c;
  }

  // type: 'success'(기본, 짧고 녹색) | 'error'(빨강, 오래) | 'info'(중립)
  window.showToast = function (message, type) {
    type = type || 'success';
    var c = ensureToastContainer();
    var t = document.createElement('div');
    t.className = 'toast toast-' + type;
    var msg = document.createElement('span');
    msg.className = 'toast-msg';
    msg.textContent = message;
    t.appendChild(msg);
    c.appendChild(t);

    requestAnimationFrame(function () { t.classList.add('show'); });

    var duration = type === 'error' ? 4000 : (type === 'info' ? 2500 : 1400);
    var timer = setTimeout(remove, duration);
    t.addEventListener('click', function () { clearTimeout(timer); remove(); });

    function remove() {
      t.classList.remove('show');
      setTimeout(function () { if (t.parentNode) t.parentNode.removeChild(t); }, 250);
    }
  };

  // ===== 확인 모달 (Promise<boolean>) =====
  function ensureConfirmModal() {
    var m = document.getElementById('confirmModal');
    if (!m) {
      m = document.createElement('div');
      m.id = 'confirmModal';
      m.className = 'cm-backdrop';
      m.style.display = 'none';
      m.innerHTML =
        '<div class="cm-box">' +
        '<p class="cm-msg"></p>' +
        '<div class="cm-actions">' +
        '<button type="button" class="cm-btn cm-cancel">취소</button>' +
        '<button type="button" class="cm-btn cm-ok">확인</button>' +
        '</div></div>';
      document.body.appendChild(m);
    }
    return m;
  }

  // confirmDialog(message, { okText, cancelText, danger }) → Promise<boolean>
  window.confirmDialog = function (message, opts) {
    opts = opts || {};
    var m = ensureConfirmModal();
    var msgEl = m.querySelector('.cm-msg');
    var okBtn = m.querySelector('.cm-ok');
    var cancelBtn = m.querySelector('.cm-cancel');

    msgEl.textContent = message;
    okBtn.textContent = opts.okText || '확인';
    cancelBtn.textContent = opts.cancelText || '취소';
    okBtn.className = 'cm-btn cm-ok' + (opts.danger ? ' cm-danger' : '');
    m.style.display = 'flex';

    return new Promise(function (resolve) {
      function cleanup(result) {
        m.style.display = 'none';
        okBtn.onclick = null;
        cancelBtn.onclick = null;
        m.onclick = null;
        document.removeEventListener('keydown', onKey, true);
        resolve(result);
      }
      okBtn.onclick = function () { cleanup(true); };
      cancelBtn.onclick = function () { cleanup(false); };
      m.onclick = function (e) { if (e.target === m) cleanup(false); };
      function onKey(e) {
        if (e.key === 'Escape') { e.stopImmediatePropagation(); cleanup(false); }
        else if (e.key === 'Enter') { e.stopImmediatePropagation(); cleanup(true); }
      }
      document.addEventListener('keydown', onKey, true); // capture: 다른 ESC 핸들러보다 먼저
      okBtn.focus();
    });
  };

  // ===== 요소를 잠깐 녹색으로 반짝 (성공 강조) =====
  window.flashSuccess = function (el) {
    if (!el) return;
    el.classList.remove('flash-success');
    void el.offsetWidth; // 리플로우로 애니메이션 재시작
    el.classList.add('flash-success');
    setTimeout(function () { el.classList.remove('flash-success'); }, 900);
  };

  // ===== 새로고침/이동 후에 표시할 토스트 (reload로 지워지지 않게) =====
  window.toastAfterReload = function (message, type) {
    try {
      sessionStorage.setItem('pendingToast', JSON.stringify({ m: message, t: type || 'success' }));
    } catch (e) {}
  };

  // 페이지 로드 시 대기 중인 토스트가 있으면 표시
  try {
    var pending = sessionStorage.getItem('pendingToast');
    if (pending) {
      sessionStorage.removeItem('pendingToast');
      var pd = JSON.parse(pending);
      var run = function () { window.showToast(pd.m, pd.t); };
      if (document.body) run();
      else document.addEventListener('DOMContentLoaded', run);
    }
  } catch (e) {}
})();
