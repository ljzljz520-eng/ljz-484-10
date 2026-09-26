/* 页面主逻辑：角色切换、学生阅读、教师编辑（草稿/发布/导出/删除）。 */
(function () {
  'use strict';

  const state = {
    role: 'student',
    studentCases: [],
    teacherCases: [],
    selectedStudentId: null,
    selectedTeacherId: null,
  };

  // ---------- 工具 ----------

  function $(id) { return document.getElementById(id); }

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function emptyToDash(s) {
    s = String(s || '').trim();
    return s ? esc(s) : '—';
  }

  let toastTimer = null;
  function toast(msg, isError) {
    const el = $('toast');
    el.textContent = msg;
    el.className = 'toast' + (isError ? ' error' : '');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.className = 'toast hidden'; }, 2600);
  }

  function statusBadge(status) {
    return status === 'published'
      ? '<span class="badge badge-published">已发布</span>'
      : '<span class="badge badge-draft">草稿</span>';
  }

  // ---------- 角色切换 ----------

  document.querySelectorAll('#roleSwitch button').forEach((btn) => {
    btn.addEventListener('click', () => {
      const role = btn.dataset.role;
      if (role === state.role) return;
      state.role = role;
      document.querySelectorAll('#roleSwitch button').forEach((b) =>
        b.classList.toggle('active', b === btn));
      $('studentView').classList.toggle('hidden', role !== 'student');
      $('teacherView').classList.toggle('hidden', role !== 'teacher');
      load();
    });
  });

  async function load() {
    if (state.role === 'student') {
      await loadStudent();
    } else {
      await loadTeacher();
    }
  }

  // ---------- 学生端 ----------

  async function loadStudent() {
    try {
      state.studentCases = await Api.listCases('student');
    } catch (e) {
      toast(e.message, true);
      state.studentCases = [];
    }
    renderStudentList();
    if (state.selectedStudentId && state.studentCases.some((c) => c.id === state.selectedStudentId)) {
      renderStudentReader(state.studentCases.find((c) => c.id === state.selectedStudentId));
    } else {
      state.selectedStudentId = null;
      $('studentReader').innerHTML = '<div class="empty-tip">请从左侧选择一个已发布案例进行阅读</div>';
    }
  }

  function renderStudentList() {
    $('studentCount').textContent = state.studentCases.length + ' 篇';
    const ul = $('studentList');
    if (!state.studentCases.length) {
      ul.innerHTML = '<li style="cursor:default;color:#8a8275;">暂无已发布案例</li>';
      return;
    }
    ul.innerHTML = state.studentCases.map((c) => `
      <li data-id="${esc(c.id)}" class="${c.id === state.selectedStudentId ? 'active' : ''}">
        <div class="case-item-title">${esc(c.title) || '（无标题）'}</div>
        <div class="case-item-meta">
          <span>${emptyToDash(c.court)}</span>
        </div>
      </li>`).join('');
    ul.querySelectorAll('li[data-id]').forEach((li) => {
      li.addEventListener('click', () => {
        state.selectedStudentId = li.dataset.id;
        renderStudentList();
        renderStudentReader(state.studentCases.find((c) => c.id === li.dataset.id));
      });
    });
  }

  function renderStudentReader(c) {
    if (!c) return;
    const chapters = c.chapters || [];
    $('studentReader').innerHTML = `
      <article class="doc">
        <h2 class="doc-title">${esc(c.title) || '（无标题）'}</h2>
        <div class="doc-meta">
          <span>案号：${emptyToDash(c.caseNumber)}</span>
          <span>审理法院：${emptyToDash(c.court)}</span>
          ${statusBadge(c.status)}
        </div>
        <div class="doc-updated">最后更新：${esc(c.updatedAt)}</div>

        <h3 class="sec">案情概要</h3>
        <p class="body-text">${esc(c.summary) || '（暂无内容）'}</p>

        <h3 class="sec">基本事实</h3>
        <p class="body-text">${esc(c.facts) || '（暂无内容）'}</p>

        <h3 class="sec">讲解章节</h3>
        ${chapters.length ? chapters.map((ch, i) => `
          <div class="chapter-card">
            <h4><span class="order-tag">${i + 1}</span>${esc(ch.heading) || '未命名章节'}</h4>
            <p class="body-text">${esc(ch.body) || '（暂无内容）'}</p>
          </div>`).join('') : '<p class="body-text">（暂未编写讲解章节）</p>'}
      </article>`;
  }

  // ---------- 教师端 ----------

  async function loadTeacher() {
    try {
      state.teacherCases = await Api.listCases('teacher');
    } catch (e) {
      toast(e.message, true);
      state.teacherCases = [];
    }
    renderTeacherList();
    if (state.selectedTeacherId && state.teacherCases.some((c) => c.id === state.selectedTeacherId)) {
      renderTeacherEditor(state.teacherCases.find((c) => c.id === state.selectedTeacherId));
    } else {
      state.selectedTeacherId = null;
      $('teacherEditor').innerHTML =
        '<div class="empty-tip">从左侧选择案例编辑，或点击“新建案例”开始备课</div>';
    }
  }

  function renderTeacherList() {
    const ul = $('teacherList');
    ul.innerHTML = state.teacherCases.map((c) => `
      <li data-id="${esc(c.id)}" class="${c.id === state.selectedTeacherId ? 'active' : ''}">
        <div class="case-item-title">${esc(c.title) || '（无标题）'}</div>
        <div class="case-item-meta">
          ${statusBadge(c.status)}
          <span>${esc(c.updatedAt) || ''}</span>
        </div>
      </li>`).join('');
    ul.querySelectorAll('li[data-id]').forEach((li) => {
      li.addEventListener('click', () => {
        state.selectedTeacherId = li.dataset.id;
        renderTeacherList();
        renderTeacherEditor(state.teacherCases.find((c) => c.id === li.dataset.id));
      });
    });
  }

  function renderTeacherEditor(c) {
    if (!c) return;
    const isPublished = c.status === 'published';
    const chapters = c.chapters || [];
    const notes = c.notes || [];

    $('teacherEditor').innerHTML = `
      <div class="editor-toolbar">
        <button class="btn btn-primary" id="tSave">💾 保存草稿/修改</button>
        ${isPublished
          ? '<button class="btn btn-warn" id="tUnpublish">↩ 撤回为草稿</button>'
          : '<button class="btn btn-success" id="tPublish">🚀 发布给学生</button>'}
        <button class="btn" id="tExport">📄 导出讲义(Markdown)</button>
        <button class="btn btn-danger" id="tDelete">🗑 删除</button>
        <span class="status-now">当前状态：${statusBadge(c.status)}
          <span style="color:#8a8275;">（ID: ${esc(c.id)}，更新于 ${esc(c.updatedAt)}）</span>
        </span>
      </div>

      <div class="field-row">
        <div class="field">
          <label>案例标题</label>
          <input type="text" id="fTitle" value="${esc(c.title)}" placeholder="例如：张某诉某公司劳动争议案" />
        </div>
        <div class="field">
          <label>案号</label>
          <input type="text" id="fCaseNumber" value="${esc(c.caseNumber)}" placeholder="（2024）京0105民初XXXX号" />
        </div>
      </div>
      <div class="field">
        <label>审理法院</label>
        <input type="text" id="fCourt" value="${esc(c.court)}" placeholder="例如：北京市朝阳区人民法院" />
      </div>
      <div class="field">
        <label>案情概要</label>
        <textarea id="fSummary" rows="3">${esc(c.summary)}</textarea>
      </div>
      <div class="field">
        <label>基本事实</label>
        <textarea id="fFacts" rows="5">${esc(c.facts)}</textarea>
      </div>

      <div class="block-section">
        <div class="sec-title">讲解章节 <span class="sec-note">发布后学生可见</span></div>
        <div id="chapterBox"></div>
        <button class="btn btn-small" id="btnAddChapter">＋ 添加章节</button>
      </div>

      <div class="block-section">
        <div class="sec-title">课堂备注 <span class="sec-note">仅教师可见，不向学生展示；会一并导出到讲义</span></div>
        <div id="noteBox"></div>
        <button class="btn btn-small" id="btnAddNote">＋ 添加备注</button>
      </div>

      <div style="padding-bottom:30px;">
        <button class="btn btn-primary" id="tSaveBottom">保存</button>
      </div>`;

    const chapterBox = $('chapterBox');
    chapters.forEach((ch) => chapterBox.appendChild(chapterRow(ch)));
    const noteBox = $('noteBox');
    notes.forEach((n) => noteBox.appendChild(noteRow(n)));

    $('btnAddChapter').addEventListener('click', () =>
      chapterBox.appendChild(chapterRow({ heading: '', body: '' })));
    $('btnAddNote').addEventListener('click', () =>
      noteBox.appendChild(noteRow({ content: '', createdAt: '' })));

    $('tSave').addEventListener('click', () => saveCase(c));
    $('tSaveBottom').addEventListener('click', () => saveCase(c));
    $('tExport').addEventListener('click', () => exportCase(c));
    $('tDelete').addEventListener('click', () => deleteCase(c));
    if (isPublished) {
      $('tUnpublish').addEventListener('click', () => changeStatus(c, 'draft'));
    } else {
      $('tPublish').addEventListener('click', () => publish(c));
    }
  }

  function chapterRow(ch) {
    const div = document.createElement('div');
    div.className = 'sub-item';
    div.innerHTML = `
      <div class="sub-head">
        <input type="text" class="ch-heading" placeholder="章节标题，如：争议焦点梳理" />
        <button class="btn-remove" title="删除章节">✕</button>
      </div>
      <textarea class="ch-body" rows="4" placeholder="章节讲解内容……"></textarea>`;
    div.querySelector('.ch-heading').value = ch.heading || '';
    div.querySelector('.ch-body').value = ch.body || '';
    div.querySelector('.btn-remove').addEventListener('click', () => div.remove());
    return div;
  }

  function noteRow(n) {
    const div = document.createElement('div');
    div.className = 'sub-item';
    div.innerHTML = `
      <div class="sub-head">
        <input type="text" class="n-content" placeholder="课堂备注：提问设计、时间安排、延伸法条等（学生不可见）" />
        <button class="btn-remove" title="删除备注">✕</button>
      </div>
      <div class="hint">${n.createdAt ? '记录于 ' + esc(n.createdAt) : '尚未保存'}</div>`;
    div.querySelector('.n-content').value = n.content || '';
    div.querySelector('.btn-remove').addEventListener('click', () => div.remove());
    return div;
  }

  function collectForm() {
    const chapters = Array.from(document.querySelectorAll('#chapterBox .sub-item')).map((row) => ({
      heading: row.querySelector('.ch-heading').value,
      body: row.querySelector('.ch-body').value,
    }));
    const notes = Array.from(document.querySelectorAll('#noteBox .sub-item')).map((row) => ({
      content: row.querySelector('.n-content').value,
    })).filter((n) => n.content.trim());
    return {
      title: $('fTitle').value,
      caseNumber: $('fCaseNumber').value,
      court: $('fCourt').value,
      summary: $('fSummary').value,
      facts: $('fFacts').value,
      chapters,
      notes,
    };
  }

  async function saveCase(c) {
    try {
      const updated = await Api.saveCase(c.id, collectForm());
      toast('已保存' + (updated.status === 'published' ? '（案例仍为已发布，学生端内容已更新）' : '（草稿）'));
      await loadTeacher();
    } catch (e) {
      toast(e.message, true);
    }
  }

  async function publish(c) {
    // 发布前先保存当前编辑内容，避免“发布的是旧内容”
    try {
      await Api.saveCase(c.id, collectForm());
      await Api.setStatus(c.id, 'published');
      toast('已保存并发布，学生端现在可以阅读');
      await loadTeacher();
    } catch (e) {
      toast(e.message, true);
    }
  }

  async function changeStatus(c, status) {
    try {
      await Api.setStatus(c.id, status);
      toast(status === 'draft' ? '已撤回为草稿，学生端不再可见' : '状态已更新');
      await loadTeacher();
    } catch (e) {
      toast(e.message, true);
    }
  }

  function exportCase(c) {
    // 借助隐藏 a 标签携带 X-Role 无法通过 fetch 下载，故用 fetch 取 blob 触发保存
    fetch(Api.exportUrl(c.id), { headers: { 'X-Role': 'teacher' } })
      .then(async (res) => {
        if (!res.ok) {
          const err = await res.json().catch(() => ({}));
          throw new Error(err.error || '导出失败 (' + res.status + ')');
        }
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = '讲义-' + (c.title || c.id) + '.md';
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
        toast('讲义已导出');
      })
      .catch((e) => toast(e.message, true));
  }

  async function deleteCase(c) {
    if (!confirm('确定删除《' + (c.title || c.id) + '》吗？此操作不可恢复。')) return;
    try {
      await Api.deleteCase(c.id);
      state.selectedTeacherId = null;
      toast('已删除');
      await loadTeacher();
    } catch (e) {
      toast(e.message, true);
    }
  }

  $('btnCreate').addEventListener('click', async () => {
    const title = prompt('请输入新案例标题（创建后默认为草稿，学生不可见）：', '');
    if (title === null) return;
    try {
      const created = await Api.createCase({ title: title.trim() });
      state.selectedTeacherId = created.id;
      toast('已新建草稿，请继续补充内容');
      await loadTeacher();
    } catch (e) {
      toast(e.message, true);
    }
  });

  // ---------- 启动 ----------
  load();
})();
