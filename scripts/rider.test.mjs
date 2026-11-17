import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';

const base = process.env.RIDER_TEST_BASE;
const database = process.env.RIDER_TEST_DB;

function sql(statement) {
  assert.match(database ?? '', /^smartorder_rider_test_[a-zA-Z0-9_]+$/);
  const result = spawnSync(process.env.MYSQL_BIN ?? '.tools/mysql-8.0.40-winx64/bin/mysql.exe', [
    '-h', '127.0.0.1', '-P', process.env.DB_PORT ?? '3307', '-u', process.env.DB_USER ?? 'root',
    '--default-character-set=utf8mb4', '-N', '-B', database, '-e', statement,
  ], { encoding: 'utf8', env: { ...process.env, MYSQL_PWD: process.env.DB_PASS ?? '123456' } });
  assert.equal(result.status, 0, result.stderr);
  return result.stdout.trim();
}

async function api(path, token, method = 'GET', body, code = 0, extraHeaders = {}) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...extraHeaders },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  const result = await response.json();
  assert.equal(result.code, code, `${method} ${path}: ${result.message}`);
  return result.data;
}

test('配送履约真实接口与 MySQL 回归', { skip: !base || !database }, async (t) => {
  assert.match(base, /^http:\/\/127\.0\.0\.1:18080\/api\/v1$/);
  sql("UPDATE d_rider SET status = 1 WHERE user_id = 1; UPDATE d_rider SET status = 0 WHERE user_id = 2;");
  sql("INSERT INTO m_date_override (merchant_id, biz_date, override_type, open_time, close_time, reason) VALUES (1, CURDATE(), 'HOURS', '00:00:00', '23:59:59', '配送自动测试') ON DUPLICATE KEY UPDATE override_type = 'HOURS', open_time = '00:00:00', close_time = '23:59:59';");
  let rider;
  let student;
  let merchant;
  let task;
  let orderNo;
  let startingProfile;

  await t.test('未登录、密码错误、无资格与商家身份均被拒绝', async () => {
    await api('/rider/profile', null, 'GET', undefined, 20000);
    await api('/auth/rider/login', null, 'POST', { studentNo: '2022010101', password: 'wrong-password' }, 20008);
    await api('/auth/rider/login', null, 'POST', { studentNo: '2022010102', password: '010102' }, 20003);
    student = await api('/auth/password/login', null, 'POST', { studentNo: '2022010102', password: '010102' });
    await api('/rider/tasks', student.accessToken, 'GET', undefined, 20003);
    merchant = await api('/auth/console/login', null, 'POST', { username: 'chuanxiang', password: 'demo123456' });
    await api('/rider/profile', merchant.accessToken, 'GET', undefined, 20003);
    rider = await api('/auth/rider/login', null, 'POST', { studentNo: '2022010101', password: '010101' });
    assert.equal(rider.rider.status, 1);
    startingProfile = rider.rider;
    const again = await api('/auth/rider/login', null, 'POST', { studentNo: '2022010101', password: '010101' });
    assert.notEqual(rider.refreshToken, again.refreshToken);
    const pair = await api('/auth/refresh', null, 'POST', { refreshToken: rider.refreshToken });
    rider.accessToken = pair.accessToken;
    rider.refreshToken = pair.refreshToken;
    assert.equal((await api('/rider/profile', rider.accessToken)).riderId, startingProfile.riderId);
  });

  await t.test('配送必须提交自己的有效地址，并保存下单时快照', async () => {
    const addressInput = { contactName: '配送测试同学', contactPhone: '13800000002', campusArea: '中心校区', building: '紫荆公寓 5 栋', roomNo: '301', tag: '', remark: '', isDefault: true };
    const addresses = await api('/addresses', student.accessToken, 'POST', addressInput);
    const address = addresses.find((row) => row.isDefault);
    const draft = { merchantId: '1', fulfillmentType: 'DELIVERY', items: [{ dishId: '5', entries: [], quantity: 5 }], addressId: address.addressId };
    await api('/orders/preflight', rider.accessToken, 'POST', draft, 10002);
    const missingAddress = { ...draft };
    delete missingAddress.addressId;
    const response = await fetch(`${base}/orders/preflight`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${student.accessToken}` }, body: JSON.stringify(missingAddress) });
    assert.notEqual((await response.json()).code, 0);
    const preflight = await api('/orders/preflight', student.accessToken, 'POST', draft);
    assert.equal(preflight.deliveryFee, 300);
    const created = await api('/orders', student.accessToken, 'POST', draft, 0, { 'X-Client-Order-No': randomUUID().replaceAll('-', '') });
    orderNo = created.orderNo;
    assert.match(orderNo, /^\d{16,20}$/);
    await api(`/addresses/${address.addressId}`, student.accessToken, 'PUT', { ...addressInput, roomNo: '999' });
    assert.match(sql(`SELECT dorm_address FROM o_order WHERE order_no = '${orderNo}'`), /301/);
    const payment = await api('/payments', student.accessToken, 'POST', { orderNo, channel: 'MOCK' });
    await api(`/payments/${payment.paymentNo}/confirm`, student.accessToken, 'POST');
    await api(`/merchant/orders/${orderNo}/accept`, merchant.accessToken, 'POST');
    await api(`/merchant/orders/${orderNo}/ready`, merchant.accessToken, 'POST');
    const pool = await api('/rider/tasks?scope=POOL', rider.accessToken);
    task = pool.list.find((row) => row.orderNo === orderNo);
    assert.ok(task);
    assert.equal(task.contactPhone, '');
    assert.equal(task.dormAddress, '');
    assert.ok(!task.pickupCode);
    assert.equal(task.remark, '');
    assert.deepEqual(task.items, []);
  });

  await t.test('休息禁止接新单，抢单只能成功一次且不能越权操作', async () => {
    await api('/rider/status', rider.accessToken, 'PATCH', { status: 2 });
    await api(`/rider/tasks/${task.deliveryId}/accept`, rider.accessToken, 'POST', undefined, 10001);
    await api('/rider/status', rider.accessToken, 'PATCH', { status: 1 });
    sql("INSERT INTO d_rider (user_id, real_name, phone, campus_area, status) VALUES (2, '测试配送员二', '13800000002', '中心校区', 1) ON DUPLICATE KEY UPDATE status = 1;");
    const second = await api('/auth/rider/login', null, 'POST', { studentNo: '2022010102', password: '010102' });
    const results = await Promise.all([rider.accessToken, second.accessToken].map(async (token) => {
      const response = await fetch(`${base}/rider/tasks/${task.deliveryId}/accept`, { method: 'POST', headers: { Authorization: `Bearer ${token}` } });
      return { result: await response.json(), token };
    }));
    assert.deepEqual(results.map((row) => row.result.code).sort(), [0, 30103]);
    const winner = results.find((row) => row.result.code === 0);
    const loser = results.find((row) => row.result.code !== 0);
    rider.accessToken = winner.token;
    startingProfile = await api('/rider/profile', rider.accessToken);
    task = winner.result.data;
    assert.match(task.dormAddress, /301/);
    assert.equal(task.contactPhone, '13800000002');
    await api(`/rider/tasks/${task.deliveryId}/pick`, loser.token, 'POST', undefined, 20003);
    await api(`/rider/tasks/${task.deliveryId}/deliver`, rider.accessToken, 'POST', undefined, 30102);
    await api(`/merchant/orders/${orderNo}/verify`, merchant.accessToken, 'POST', { pickupCode: task.pickupCode }, 30102);
    const active = await api('/rider/tasks?scope=ACTIVE', rider.accessToken);
    assert.ok(active.list.some((row) => row.deliveryId === task.deliveryId));
  });

  await t.test('取餐不结束订单，重复送达只完成一次并计入一次收入', async () => {
    await api(`/rider/tasks/${task.deliveryId}/pick`, rider.accessToken, 'POST');
    await api(`/rider/tasks/${task.deliveryId}/pick`, rider.accessToken, 'POST');
    assert.equal(sql(`SELECT status FROM o_order WHERE order_no = '${orderNo}'`), 'READY');
    assert.equal(sql(`SELECT taken_at IS NOT NULL FROM o_order WHERE order_no = '${orderNo}'`), '1');
    const results = await Promise.all([1, 2].map(() => api(`/rider/tasks/${task.deliveryId}/deliver`, rider.accessToken, 'POST')));
    assert.ok(results.every((row) => row.status === 'DELIVERED'));
    assert.equal(sql(`SELECT status FROM o_order WHERE order_no = '${orderNo}'`), 'COMPLETED');
    const profile = await api('/rider/profile', rider.accessToken);
    assert.equal(profile.deliveryCount, startingProfile.deliveryCount + 1);
    assert.equal(profile.incomeAmount, startingProfile.incomeAmount + task.riderIncome);
    const history = await api('/rider/tasks?scope=HISTORY', rider.accessToken);
    assert.ok(history.list.some((row) => row.deliveryId === task.deliveryId));
    const completed = await api(`/orders/${orderNo}`, student.accessToken);
    assert.equal(completed.status, 'COMPLETED');
    assert.ok(completed.timeline.some((row) => row.note.includes('配送')));
    const archived = history.list.find((row) => row.deliveryId === task.deliveryId);
    assert.equal(archived.contactPhone, '');
    assert.equal(archived.dormAddress, '');
  });

  await t.test('已有登录凭证不能绕过配送资格停用', async () => {
    await api('/rider/status', rider.accessToken, 'PATCH', { status: 3 }, 10001);
    assert.match(startingProfile.riderId, /^\d+$/);
    sql(`UPDATE d_rider SET status = 3 WHERE id = ${startingProfile.riderId}`);
    try {
      await api('/rider/profile', rider.accessToken, 'GET', undefined, 20003);
      await api('/rider/tasks?scope=HISTORY', rider.accessToken, 'GET', undefined, 20003);
      await api('/rider/status', rider.accessToken, 'PATCH', { status: 1 }, 20003);
    } finally {
      sql(`UPDATE d_rider SET status = 1 WHERE id = ${startingProfile.riderId}`);
    }
  });
});
