/* 接口封装：角色通过 X-Role 请求头区分；同源访问由 Java 服务托管，也支持独立静态服务器（CORS 已开启）。 */
(function (global) {
  const BASE = '/api';

  async function request(method, path, role, body) {
    const opt = {
      method,
      headers: { 'X-Role': role || 'student' },
    };
    if (body !== undefined) {
      opt.headers['Content-Type'] = 'application/json; charset=utf-8';
      opt.body = JSON.stringify(body);
    }
    let res;
    try {
      res = await fetch(BASE + path, opt);
    } catch (networkErr) {
      throw new Error('无法连接后端服务，请确认 Java 服务已启动（默认 8080 端口）');
    }

    const contentType = res.headers.get('Content-Type') || '';
    if (contentType.includes('application/json')) {
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || ('请求失败 (' + res.status + ')'));
      return data;
    }
    if (!res.ok) throw new Error('请求失败 (' + res.status + ')');
    return res; // 导出等非 JSON 响应
  }

  global.Api = {
    listCases: (role) => request('GET', '/cases', role),
    getCase: (role, id) => request('GET', '/cases/' + encodeURIComponent(id), role),
    createCase: (data) => request('POST', '/cases', 'teacher', data),
    saveCase: (id, data) => request('PUT', '/cases/' + encodeURIComponent(id), 'teacher', data),
    deleteCase: (id) => request('DELETE', '/cases/' + encodeURIComponent(id), 'teacher'),
    setStatus: (id, status) =>
      request('POST', '/cases/' + encodeURIComponent(id) + '/status', 'teacher', { status }),
    exportUrl: (id) => BASE + '/cases/' + encodeURIComponent(id) + '/export',
  };
})(window);
