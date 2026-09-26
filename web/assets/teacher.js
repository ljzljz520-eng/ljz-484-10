/* 教师端：案例 / 章节 / 备注的维护、发布与导出 */
(function () {
  const loginView = document.getElementById('loginView');
  const appView = document.getElementById('appView');
  const loginErr = document.getElementById('loginErr');
  const tokenInput = document.getElementById('tokenInput');
  const caseItems = document.getElementById('caseItems');
  const editorPlaceholder = document.getElementById('editorPlaceholder');
  const editorBody = document.getElementById('editorBody');

  let cases = [];           // 列表摘要
  let current = null;       // 当前案例详情（完整对象）

  // ---------- 登录 ----------

  async function init() {
    if (Api.token()) {
      try {
        await refreshList();
        enterApp();
        return;
      } catch (e) {
        Api.clearToken();
      }
    }
    enterLogin();
  }

  function enterLogin() {
    appView.hidden = true;
    loginView.hidden = false;
    tokenInput.focus();
  }

  function enterApp() {
    loginView.hidden = true;
    appView.hidden = false;
    renderSidebar();
  }

  document.getElementById('loginBtn').onclick = async () => {
    const t = tokenInput.value.trim();
    if (!t) { loginErr.textContent = '请输入口令'; return; }
    Api.setToken(t);
    try {
      await refreshList();
      loginErr.textContent = '';
      enterApp();
    } catch (e) {
      loginErr.textContent = '登录失败：' + e.message;
      Api.clearToken();
    }
  };
  tokenInput.addEventListener('keydown', e => { if (e.key === 'Enter') document.getElementById('loginBtn').click(); });
  document.getElementById('logoutBtn').onclick = () => {
    Api.clearToken();
    current = null;
    cases = [];
    enterLogin();
  };

  // ---------- 侧边栏 ----------

  async function refreshList(selectId) {
    const data = await Api.listCases();
    cases = data.cases || [];
    renderSidebar();
    if (selectId) await selectCase(selectId);
  }

  function renderSidebar() {
    caseItems.innerHTML = '';
    cases.forEach(c => {
      const div = document.createElement('div');
      div.className = 'case-item' + (current && current.id === c.id ? ' active' : '');
      const badge = c.status === 'published'
        ? '<span class="badge published">已发布</span>'
        : '<span class="badge draft">草稿</span>';
      div.innerHTML =
        '<div class="t"><span>' + esc(c.title) + '</span>' + badge + '</div>' +
        '<div class="s">章节 ' + (c.chapterCount || 0) + '（已发 ' + (c.publishedChapters || 0) +
          '）· 备注 ' + (c.noteCount || 0) + '</div>';
      div.onclick = () => selectCase(c.id);
      caseItems.appendChild(div);
    });
  }

  document.getElementById('newCaseBtn').onclick = async () => {
    if (current && !confirm('切换前请确认当前案例已保存，确定新建？')) return;
    try {
      const data = await Api.createCase({
        title: '未命名案例 ' + new Date().toLocaleDateString('zh-CN')
      });
      await refreshList(data.case.id);
      document.getElementById('f_title').focus();
    } catch (e) { toast('新建失败：' + e.message, true); }
  };

  // ---------- 编辑器 ----------

  async function selectCase(id) {
    try {
      const data = await Api.getCase(id);
      current = data.case;
      renderSidebar();
      renderEditor();
    } catch (e) { toast('加载失败：' + e.message, true); }
  }

  function renderEditor() {
    editorPlaceholder.hidden = true;
    editorBody.hidden = false;
    document.getElementById('f_title').value = current.title || '';
    document.getElementById('f_caseNo').value = current.caseNo || '';
    document.getElementById('f_court').value = current.court || '';
    document.getElementById('f_date').value = current.date || '';
    document.getElementById('f_category').value = current.category || '';
    document.getElementById('f_summary').value = current.summary || '';
    document.getElementById('f_rows').value = current.facts || '';
    renderStatus();
    renderChapters();
    renderNotes();
  }

  function renderStatus() {
    document.getElementById('caseStatus').innerHTML = current.status === 'published'
      ? '<span class="badge published">已发布</span>'
      : '<span class="badge draft">草稿（学生不可见）</span>';
  }

  function collectFields() {
    return {
      title: document.getElementById('f_title').value.trim(),
      caseNo: document.getElementById('f_caseNo').value.trim(),
      court: document.getElementById('f_court').value.trim(),
      date: document.getElementById('f_date').value.trim(),
      category: document.getElementById('f_category').value.trim(),
      summary: document.getElementById('f_summary').value,
      facts: document.getElementById('f_rows').value
    };
  }

  document.getElementById('saveBtn').onclick = async () => {
    if (!current) return;
    try {
      const data = await Api.updateCase(current.id, collectFields());
      current = data.case;
      renderStatus();
      await refreshListKeepCurrent();
      toast('已保存');
    } catch (e) { toast('保存失败：' + e.message, true); }
  };

  document.getElementById('publishBtn').onclick = async () => {
    if (!current) return;
    try {
      const data = await Api.updateCase({ ...collectFields(), status: 'published' });
      current = data.case;
      renderStatus();
      await refreshListKeepCurrent();
      toast('案例已发布，学生端可见');
    } catch (e) { toast('发布失败：' + e.message, true); }
  };

  document.getElementById('unpublishBtn').onclick = async () => {
    if (!current) return;
    if (!confirm('下架后学生端将无法看到该案例，确定？')) return;
    try {
      const data = await Api.unpublishCase(current.id);
      current = data.case;
      renderStatus();
      await refreshListKeepCurrent();
      toast('已下架为草稿');
    } catch (e) { toast('操作失败：' + e.message, true); }
  };

  document.getElementById('deleteBtn').onclick = async () => {
    if (!current) return;
    if (!confirm('确定删除案例「' + current.title + '」及其全部章节与备注？此操作不可恢复。')) return;
    try {
      await Api.deleteCase(current.id);
      current = null;
      editorBody.hidden = true;
      editorPlaceholder.hidden = false;
      await refreshList();
      toast('已删除');
    } catch (e) { toast('删除失败：' + e.message, true); }
  };

  document.getElementById('exportBtn').onclick = async () => {
    if (!current) return;
    // 先保存当前编辑内容，避免导出旧数据
    try {
      const data = await Api.updateCase(current.id, collectFields());
      current = data.case;
    } catch (e) { toast('导出前保存失败：' + e.message, true); return; }
    try {
      const includeNotes = document.getElementById('includeNotes').checked;
      const blob = await Api.exportHandout(current.id, includeNotes);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = '讲义-' + current.title.replace(/[\\/:*?"<>|\s]+/g, '_') + '.md';
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      toast('讲义已导出');
    } catch (e) { toast('导出失败：' + e.message, true); }
  };

  async function refreshListKeepCurrent() {
    const id = current ? current.id : null;
    const data = await Api.listCases();
    cases = data.cases || [];
    renderSidebar();
    if (id) {
      const detail = await Api.getCase(id);
      current = detail.case;
      renderStatus();
    }
  }

  // ---------- 章节 ----------

  function renderChapters() {
    const list = document.getElementById('chapterList');
    list.innerHTML = '';
    const chs = (current.chapters || []).slice()
      .sort((a, b) => (a.order - b.order) || String(a.id).localeCompare(String(b.id)));
    chs.forEach(ch => list.appendChild(chapterBlock(ch)));
  }

  function chapterBlock(ch) {
    const wrap = document.createElement('div');
    wrap.className = 'sub-block';
    wrap.dataset.id = ch.id;
    wrap.innerHTML =
      '<div class="row">' +
        '<input class="order" type="number" value="' + esc(ch.order) + '" title="显示顺序">' +
        '<input class="title-input" type="text" value="' + esc(ch.title) + '">' +
        '<span class="ch-status"></span>' +
        '<span class="btns">' +
          '<button class="secondary small btn-save">保存</button>' +
          '<button class="secondary small btn-toggle"></button>' +
          '<button class="danger small btn-del">删除</button>' +
        '</span>' +
      '</div>' +
      '<textarea class="ch-content" rows="5" placeholder="章节讲解内容（可使用空行分段）">' + esc(ch.content) + '</textarea>';
    const statusEl = wrap.querySelector('.ch-status');
    const toggleBtn = wrap.querySelector('.btn-toggle');

    function paint() {
      if (ch.status === 'published') {
        statusEl.innerHTML = '<span class="badge published">已发布</span>';
        toggleBtn.textContent = '下架';
      } else {
        statusEl.innerHTML = '<span class="badge draft">草稿</span>';
        toggleBtn.textContent = '发布';
      }
    }
    paint();

    wrap.querySelector('.btn-save').onclick = async () => {
      try {
        const payload = {
          title: wrap.querySelector('.title-input').value.trim(),
          content: wrap.querySelector('.ch-content').value,
          order: parseInt(wrap.querySelector('.order').value, 10) || 1
        };
        if (!payload.title) { toast('章节标题不能为空', true); return; }
        const data = await Api.updateChapter(ch.id, payload);
        Object.assign(ch, data.chapter);
        await refreshListKeepCurrent();
        renderChapters();
        toast('章节已保存');
      } catch (e) { toast('保存失败：' + e.message, true); }
    };

    toggleBtn.onclick = async () => {
      try {
        if (ch.status === 'published') {
          const data = await Api.unpublishChapter(ch.id);
          Object.assign(ch, data.chapter);
        } else {
          // 发布前自动保存标题/内容/顺序
          const payload = {
            title: wrap.querySelector('.title-input').value.trim(),
            content: wrap.querySelector('.ch-content').value,
            order: parseInt(wrap.querySelector('.order').value, 10) || 1,
            status: 'published'
          };
          if (!payload.title) { toast('章节标题不能为空', true); return; }
          const data = await Api.updateChapter(ch.id, payload);
          Object.assign(ch, data.chapter);
          if (current.status !== 'published') {
            toast('章节已发布；案例本身仍为草稿，发布案例后学生才可见');
          } else {
            toast('章节已发布');
          }
        }
        await refreshListKeepCurrent();
        renderChapters();
      } catch (e) { toast('操作失败：' + e.message, true); }
    };

    wrap.querySelector('.btn-del').onclick = async () => {
      if (!confirm('删除该章节？')) return;
      try {
        await Api.deleteChapter(ch.id);
        current.chapters = current.chapters.filter(x => x.id !== ch.id);
        await refreshListKeepCurrent();
        renderChapters();
        toast('章节已删除');
      } catch (e) { toast('删除失败：' + e.message, true); }
    };

    return wrap;
  }

  document.getElementById('addChapterBtn').onclick = async () => {
    if (!current) return;
    try {
      const nextOrder = (current.chapters || []).reduce((m, x) => Math.max(m, x.order || 0), 0) + 1;
      const data = await Api.addChapter(current.id, {
        title: '新章节（' + nextOrder + '）', content: '', order: nextOrder
      });
      current.chapters = current.chapters || [];
      current.chapters.push(data.chapter);
      renderChapters();
      const blocks = document.querySelectorAll('#chapterList .sub-block');
      const last = blocks[blocks.length - 1];
      if (last) last.querySelector('.title-input').focus();
      toast('已新增草稿章节');
    } catch (e) { toast('新增失败：' + e.message, true); }
  };

  // ---------- 备注 ----------

  function renderNotes() {
    const list = document.getElementById('noteList');
    list.innerHTML = '';
    (current.notes || []).forEach(n => {
      const wrap = document.createElement('div');
      wrap.className = 'note-item';
      wrap.innerHTML =
        '<div style="flex:1;">' +
          '<textarea rows="2">' + esc(n.content) + '</textarea>' +
          '<div class="note-meta">更新于 ' + fmtTime(n.updatedAt) + '</div>' +
        '</div>' +
        '<span style="display:flex;flex-direction:column;gap:6px;">' +
          '<button class="secondary small btn-note-save">保存</button>' +
          '<button class="danger small btn-note-del">删除</button>' +
        '</span>';
      const ta = wrap.querySelector('textarea');
      wrap.querySelector('.btn-note-save').onclick = async () => {
        const content = ta.value.trim();
        if (!content) { toast('备注内容不能为空', true); return; }
        try {
          const data = await Api.updateNote(n.id, content);
          Object.assign(n, data.note);
          await refreshListKeepCurrent();
          renderNotes();
          toast('备注已保存');
        } catch (e) { toast('保存失败：' + e.message, true); }
      };
      wrap.querySelector('.btn-note-del').onclick = async () => {
        if (!confirm('删除该备注？')) return;
        try {
          await Api.deleteNote(n.id);
          current.notes = current.notes.filter(x => x.id !== n.id);
          await refreshListKeepCurrent();
          renderNotes();
        } catch (e) { toast('删除失败：' + e.message, true); }
      };
      list.appendChild(wrap);
    });
  }

  document.getElementById('addNoteBtn').onclick = async () => {
    if (!current) return;
    const ta = document.getElementById('newNote');
    const content = ta.value.trim();
    if (!content) { toast('请输入备注内容', true); return; }
    try {
      const data = await Api.addNote(current.id, content);
      current.notes = current.notes || [];
      current.notes.push(data.note);
      ta.value = '';
      await refreshListKeepCurrent();
      renderNotes();
    } catch (e) { toast('添加失败：' + e.message, true); }
  };

  function fmtTime(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    return isNaN(d) ? iso : d.toLocaleString('zh-CN', { hour12: false });
  }

  init();
})();
