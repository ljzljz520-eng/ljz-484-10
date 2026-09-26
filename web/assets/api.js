/**
 * 前端接口封装。
 * 默认与页面同源（由 Java 服务托管 web/ 目录）。
 * 若前端用独立端口开发（如 Live Server / python -m http.server），
 * 把 BASE 改成 'http://localhost:8080' 即可，后端已开启 CORS。
 */
const Api = {
  BASE: '',
  TOKEN_KEY: 'lawcase_teacher_token',

  token() { return localStorage.getItem(this.TOKEN_KEY) || ''; },
  setToken(t) { localStorage.setItem(this.TOKEN_KEY, t); },
  clearToken() { localStorage.removeItem(this.TOKEN_KEY); },

  async req(path, { method = 'GET', body = null, auth = false, raw = false } = {}) {
    const headers = {};
    if (body !== null) headers['Content-Type'] = 'application/json';
    if (auth) {
      const t = this.token();
      if (!t) throw new Error('未输入教师口令');
      headers['X-Teacher-Token'] = t;
    }
    const res = await fetch(this.BASE + path, {
      method,
      headers,
      body: body !== null ? JSON.stringify(body) : undefined
    });
    if (res.status === 401 && auth) {
      this.clearToken();
    }
    if (!res.ok) {
      let msg = '请求失败 (' + res.status + ')';
      try {
        const j = await res.json();
        if (j.error) msg = j.error;
      } catch (e) { /* 忽略 */ }
      throw new Error(msg);
    }
    return raw ? res : res.json();
  },

  // ---- 学生端 ----
  listPublished() { return this.req('/api/published/cases'); },
  getPublished(id) { return this.req('/api/published/cases/' + id); },

  // ---- 教师端 ----
  listCases() { return this.req('/api/teacher/cases', { auth: true }); },
  getCase(id) { return this.req('/api/teacher/cases/' + id, { auth: true }); },
  createCase(data) { return this.req('/api/teacher/cases', { method: 'POST', body: data, auth: true }); },
  updateCase(id, data) { return this.req('/api/teacher/cases/' + id, { method: 'PUT', body: data, auth: true }); },
  deleteCase(id) { return this.req('/api/teacher/cases/' + id, { method: 'DELETE', auth: true }); },
  publishCase(id) { return this.req('/api/teacher/cases/' + id + '/publish', { method: 'POST', auth: true }); },
  unpublishCase(id) { return this.req('/api/teacher/cases/' + id + '/unpublish', { method: 'POST', auth: true }); },

  addChapter(caseId, data) { return this.req('/api/teacher/cases/' + caseId + '/chapters', { method: 'POST', body: data, auth: true }); },
  updateChapter(chId, data) { return this.req('/api/teacher/chapters/' + chId, { method: 'PUT', body: data, auth: true }); },
  deleteChapter(chId) { return this.req('/api/teacher/chapters/' + chId, { method: 'DELETE', auth: true }); },
  publishChapter(chId) { return this.req('/api/teacher/chapters/' + chId + '/publish', { method: 'POST', auth: true }); },
  unpublishChapter(chId) { return this.req('/api/teacher/chapters/' + chId + '/unpublish', { method: 'POST', auth: true }); },

  addNote(caseId, content) { return this.req('/api/teacher/cases/' + caseId + '/notes', { method: 'POST', body: { content }, auth: true }); },
  updateNote(noteId, content) { return this.req('/api/teacher/notes/' + noteId, { method: 'PUT', body: { content }, auth: true }); },
  deleteNote(noteId) { return this.req('/api/teacher/notes/' + noteId, { method: 'DELETE', auth: true }); },

  async exportHandout(id, includeNotes) {
    const res = await this.req('/api/teacher/cases/' + id + '/export?includeNotes=' + (includeNotes ? 1 : 0),
      { auth: true, raw: true });
    return res.blob();
  }
};

/** HTML 转义，防止内容注入。 */
function esc(s) {
  return String(s == null ? '' : s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

/** 多行文本渲染为段落。 */
function paragraphs(s) {
  return esc(s).split(/\n{2,}/).map(p =>
    '<p>' + p.replace(/\n/g, '<br>') + '</p>'
  ).join('');
}

let toastTimer = null;
function toast(msg, isError = false) {
  let el = document.getElementById('toast');
  if (!el) {
    el = document.createElement('div');
    el.id = 'toast';
    document.body.appendChild(el);
  }
  el.textContent = msg;
  el.className = 'show' + (isError ? ' error' : '');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.className = ''; }, 2600);
}
