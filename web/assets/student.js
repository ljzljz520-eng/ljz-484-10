/* 学生端：只读浏览已发布案例 */
(function () {
  const listView = document.getElementById('listView');
  const detailView = document.getElementById('detailView');
  const caseList = document.getElementById('caseList');
  const caseDetail = document.getElementById('caseDetail');
  const emptyTip = document.getElementById('emptyTip');
  const loadError = document.getElementById('loadError');
  const searchInput = document.getElementById('searchInput');
  let allCases = [];

  async function loadList() {
    loadError.hidden = true;
    try {
      const data = await Api.listPublished();
      allCases = data.cases || [];
      renderList(allCases);
    } catch (e) {
      loadError.hidden = false;
      caseList.innerHTML = '';
    }
  }

  function renderList(cases) {
    caseList.innerHTML = '';
    emptyTip.hidden = cases.length > 0;
    cases.forEach(c => {
      const div = document.createElement('div');
      div.className = 'card';
      div.innerHTML =
        '<h3>' + esc(c.title) + '</h3>' +
        '<div class="meta">' +
          (c.court ? '<span>🏛 ' + esc(c.court) + '</span>' : '') +
          (c.date ? '<span>📅 ' + esc(c.date) + '</span>' : '') +
          (c.category ? '<span>🏷 ' + esc(c.category) + '</span>' : '') +
        '</div>' +
        (c.summary ? '<div class="summary">' + esc(c.summary) + '</div>' : '') +
        '<div class="foot">案号：' + esc(c.caseNo || '—') +
          '　·　已发布讲解 ' + (c.publishedChapters || 0) + ' 节</div>';
      div.onclick = () => location.hash = '#case/' + c.id;
      caseList.appendChild(div);
    });
  }

  async function openCase(id) {
    loadError.hidden = true;
    try {
      const data = await Api.getPublished(id);
      const c = data.case;
      const chapters = (c.chapters || []).map((ch, i) =>
        '<div class="chapter">' +
          '<h4>' + esc(ch.title) + '<span class="chapter-meta">第 ' + (i + 1) + ' 节</span></h4>' +
          '<div class="prose">' + paragraphs(ch.content) + '</div>' +
        '</div>'
      ).join('');

      caseDetail.innerHTML =
        '<div class="detail-head">' +
          '<span class="badge published">已发布</span>' +
          '<h2>' + esc(c.title) + '</h2>' +
          '<div class="detail-meta">' +
            (c.caseNo ? '<span>案号：' + esc(c.caseNo) + '</span>' : '') +
            (c.court ? '<span>审理法院：' + esc(c.court) + '</span>' : '') +
            (c.date ? '<span>日期：' + esc(c.date) + '</span>' : '') +
            (c.category ? '<span>类别：' + esc(c.category) + '</span>' : '') +
          '</div>' +
        '</div>' +
        (c.summary ? '<div class="section"><h3>案情摘要</h3><div class="prose">' + paragraphs(c.summary) + '</div></div>' : '') +
        (c.facts ? '<div class="section"><h3>案件事实</h3><div class="prose">' + paragraphs(c.facts) + '</div></div>' : '') +
        '<div class="section"><h3>讲解章节（' + (c.chapters || []).length + '）</h3>' +
          (chapters || '<p class="empty">老师还没有发布讲解章节。</p>') +
        '</div>';
      listView.hidden = true;
      detailView.hidden = false;
      window.scrollTo(0, 0);
    } catch (e) {
      toast('案例加载失败：' + e.message, true);
      location.hash = '';
    }
  }

  function showList() {
    detailView.hidden = true;
    listView.hidden = false;
  }

  function route() {
    const m = location.hash.match(/^#case\/(.+)$/);
    if (m) openCase(m[1]); else showList();
  }

  searchInput.addEventListener('input', () => {
    const q = searchInput.value.trim().toLowerCase();
    if (!q) return renderList(allCases);
    renderList(allCases.filter(c =>
      [c.title, c.court, c.category, c.caseNo].some(v =>
        String(v || '').toLowerCase().includes(q))
    ));
  });
  document.getElementById('reloadBtn').onclick = loadList;
  document.getElementById('backBtn').onclick = () => { location.hash = ''; };
  document.getElementById('printBtn').onclick = () => window.print();
  window.addEventListener('hashchange', route);

  loadList();
  route();
})();
