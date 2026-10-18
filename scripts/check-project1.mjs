// check-project1.mjs
// Checks Project 1 Day 8 (roles, ticket visibility) and Day 9 (comments, auditing) end to end.
// Needs: Node 18+, the app running, and Docker on PATH (used to promote the first admin and inspect rows).
// Run:   node check-project1.mjs
// Optional env overrides: BASE_URL, DB_CONTAINER, DB_USER, DB_NAME
import { execFileSync } from 'node:child_process';

const BASE = process.env.BASE_URL ?? 'http://localhost:8080';
const DB = {
  container: process.env.DB_CONTAINER ?? 'helpdesk-db',
  user: process.env.DB_USER ?? 'helpdesk_user',
  name: process.env.DB_NAME ?? 'helpdesk',
};
const PASSWORD = 'password123';
const run = Date.now().toString(36);

let passed = 0;
const failed = [];

function check(name, actual, expected) {
  const ok = actual === expected;
  if (ok) passed += 1;
  else failed.push(name);
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${ok ? '' : `   (expected ${expected}, got ${actual})`}`);
}

async function api(method, path, token, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }
  return { status: res.status, data };
}

function sql(query) {
  return execFileSync(
    'docker',
    ['exec', DB.container, 'psql', '-U', DB.user, '-d', DB.name, '-t', '-A', '-c', query],
    { encoding: 'utf8' }
  ).trim();
}

async function register(label) {
  const email = `${label}-${run}@test.com`;
  const res = await api('POST', '/api/auth/register', null, { name: `${label} ${run}`, email, password: PASSWORD });
  if (res.status !== 201) {
    throw new Error(`Registering ${label} failed: ${res.status} ${JSON.stringify(res.data)}`);
  }
  return { label, email, id: res.data.user.id, token: res.data.accessToken };
}

// The role is baked into the token, so log in again after a role change.
async function relogin(account) {
  const res = await api('POST', '/api/auth/login', null, { email: account.email, password: PASSWORD });
  if (res.status !== 200) {
    throw new Error(`Logging in ${account.label} failed: ${res.status} ${JSON.stringify(res.data)}`);
  }
  account.token = res.data.accessToken;
}

// A PUT carries the full ticket, so build it from the current state plus the changes.
const asUpdate = (ticket, overrides = {}) => ({
  title: ticket.title,
  description: ticket.description,
  status: ticket.status,
  priority: ticket.priority,
  assignedToId: ticket.assignedToId,
  ...overrides,
});

const listIds = (res) => (res.data?.data ?? []).map((item) => item.id);

async function main() {
  console.log(`Target: ${BASE}   (run id ${run})\n`);

  const probe = await api('GET', '/api/tickets', null);
  check('server is up and demands a token', probe.status, 401);
  if (probe.status !== 401) throw new Error('Unexpected response from the server, stopping.');

  console.log('\n--- setup ---');
  const admin = await register('admin');
  const agent1 = await register('agent1');
  const agent2 = await register('agent2');
  const userA = await register('usera');
  const userB = await register('userb');

  sql(`UPDATE users SET role = 'ADMIN' WHERE id = ${Number(admin.id)}`);
  await relogin(admin);

  for (const agent of [agent1, agent2]) {
    const res = await api('PATCH', `/api/users/${agent.id}/role`, admin.token, { role: 'AGENT' });
    check(`admin promotes ${agent.label} to AGENT`, res.status, 200);
    await relogin(agent);
  }

  // ---------------------------------------------------------------- Day 8
  console.log('\n--- Day 8: authorization ---');

  const created = await api('POST', '/api/tickets', userA.token, {
    title: `Printer jam ${run}`,
    description: 'Jammed on floor 3',
    priority: 'HIGH',
  });
  check('userA creates a ticket', created.status, 201);
  check('creator comes from the token', created.data.createdById, userA.id);
  let T = created.data;

  const withAssignee = await api('POST', '/api/tickets', userA.token, { title: 'Nope', assignedToId: agent1.id });
  check('USER cannot assign on create', withAssignee.status, 403);

  const listA = await api('GET', '/api/tickets?size=100', userA.token);
  check('userA list contains only own tickets', (listA.data.data ?? []).every((t) => t.createdById === userA.id), true);
  check('userA list contains the new ticket', listIds(listA).includes(T.id), true);

  check('userB GET of userA ticket is 404', (await api('GET', `/api/tickets/${T.id}`, userB.token)).status, 404);
  check('userB PUT of userA ticket is 404', (await api('PUT', `/api/tickets/${T.id}`, userB.token, asUpdate(T))).status, 404);
  check('userB DELETE is 403 (route level)', (await api('DELETE', `/api/tickets/${T.id}`, userB.token)).status, 403);

  const retitle = await api('PUT', `/api/tickets/${T.id}`, userA.token, asUpdate(T, { title: `Printer jam (edited) ${run}` }));
  check('userA edits own open ticket title', retitle.status, 200);
  T = retitle.data;

  check('USER cannot change status', (await api('PUT', `/api/tickets/${T.id}`, userA.token, asUpdate(T, { status: 'IN_PROGRESS' }))).status, 403);

  const listAgent1 = await api('GET', '/api/tickets?size=100', agent1.token);
  const listAgent2 = await api('GET', '/api/tickets?size=100', agent2.token);
  check('agent1 sees the unassigned ticket', listIds(listAgent1).includes(T.id), true);
  check('agent2 sees the unassigned ticket', listIds(listAgent2).includes(T.id), true);

  const claim = await api('PUT', `/api/tickets/${T.id}`, agent1.token, asUpdate(T, { assignedToId: agent1.id, status: 'IN_PROGRESS' }));
  check('agent1 claims the ticket', claim.status, 200);
  T = claim.data;

  check('agent2 GET of agent1 ticket is 404', (await api('GET', `/api/tickets/${T.id}`, agent2.token)).status, 404);
  const listAgent2After = await api('GET', '/api/tickets?size=100', agent2.token);
  check('agent2 list no longer contains it', listIds(listAgent2After).includes(T.id), false);

  check('agent cannot assign to another agent', (await api('PUT', `/api/tickets/${T.id}`, agent1.token, asUpdate(T, { assignedToId: agent2.id }))).status, 403);
  check('admin cannot assign to a USER', (await api('PUT', `/api/tickets/${T.id}`, admin.token, asUpdate(T, { assignedToId: userB.id }))).status, 400);

  const reassign = await api('PUT', `/api/tickets/${T.id}`, admin.token, asUpdate(T, { assignedToId: agent2.id }));
  check('admin reassigns to agent2', reassign.status, 200);
  T = reassign.data;

  check('USER cannot edit a ticket once it is in progress', (await api('PUT', `/api/tickets/${T.id}`, userA.token, asUpdate(T, { title: 'too late' }))).status, 409);
  check('agent cannot delete', (await api('DELETE', `/api/tickets/${T.id}`, agent1.token)).status, 403);
  check('admin deletes the ticket', (await api('DELETE', `/api/tickets/${T.id}`, admin.token)).status, 204);
  check('deleted ticket is 404', (await api('GET', `/api/tickets/${T.id}`, admin.token)).status, 404);

  check('non-admin cannot change roles', (await api('PATCH', `/api/users/${userB.id}/role`, agent1.token, { role: 'AGENT' })).status, 403);
  check('admin cannot change own role', (await api('PATCH', `/api/users/${admin.id}/role`, admin.token, { role: 'AGENT' })).status, 403);
  check('role change for unknown user is 404', (await api('PATCH', '/api/users/999999999/role', admin.token, { role: 'AGENT' })).status, 404);
  check('invalid role value is 400', (await api('PATCH', `/api/users/${userB.id}/role`, admin.token, { role: 'SUPERUSER' })).status, 400);

  // ---------------------------------------------------------------- Day 9
  console.log('\n--- Day 9: comments and auditing ---');

  const t2res = await api('POST', '/api/tickets', userA.token, { title: `Laptop battery ${run}`, priority: 'MEDIUM' });
  check('userA creates a second ticket', t2res.status, 201);
  let T2 = t2res.data;
  const t3res = await api('POST', '/api/tickets', userA.token, { title: `Other ticket ${run}` });
  const T3 = t3res.data;

  const c1res = await api('POST', `/api/tickets/${T2.id}/comments`, userA.token, { content: 'Still draining fast' });
  check('userA comments on own ticket', c1res.status, 201);
  check('comment author comes from the token', c1res.data.authorId, userA.id);
  const c1 = c1res.data;

  check('userB cannot comment on it (404)', (await api('POST', `/api/tickets/${T2.id}/comments`, userB.token, { content: 'hi' })).status, 404);

  const c2res = await api('POST', `/api/tickets/${T2.id}/comments`, agent1.token, { content: 'Looking into it' });
  check('agent1 comments on the unassigned ticket', c2res.status, 201);
  const c2 = c2res.data;

  const comments = await api('GET', `/api/tickets/${T2.id}/comments`, userA.token);
  check('userA reads comments', comments.status, 200);
  check('two comments returned', comments.data.data.length, 2);
  check('oldest comment first', comments.data.data[0]?.authorId, userA.id);

  const page2 = await api('GET', `/api/tickets/${T2.id}/comments?size=1&page=2`, userA.token);
  check('page 2 holds the second comment', page2.data.data[0]?.id, c2.id);
  check('pagination reports 2 pages', page2.data.pagination?.totalPages, 2);

  check('userB cannot read comments (404)', (await api('GET', `/api/tickets/${T2.id}/comments`, userB.token)).status, 404);
  check('blank comment is 400', (await api('POST', `/api/tickets/${T2.id}/comments`, userA.token, { content: '   ' })).status, 400);
  check('empty body is 400', (await api('POST', `/api/tickets/${T2.id}/comments`, userA.token, {})).status, 400);
  check('comment on unknown ticket is 404', (await api('POST', '/api/tickets/999999999/comments', userA.token, { content: 'x' })).status, 404);
  check('comment without a token is 401', (await api('POST', `/api/tickets/${T2.id}/comments`, null, { content: 'x' })).status, 401);

  const claim2 = await api('PUT', `/api/tickets/${T2.id}`, agent1.token, asUpdate(T2, { assignedToId: agent1.id, status: 'IN_PROGRESS' }));
  check('agent1 claims the second ticket', claim2.status, 200);
  T2 = claim2.data;

  const audit = sql(`SELECT concat(created_by, ',', updated_by) FROM tickets WHERE id = ${Number(T2.id)}`);
  check('audit: created_by is userA, updated_by is the last editor (agent1)', audit, `${userA.id},${agent1.id}`);

  check('agent2 cannot comment on agent1 ticket (404)', (await api('POST', `/api/tickets/${T2.id}/comments`, agent2.token, { content: 'x' })).status, 404);
  check('comment id on the wrong ticket is 404', (await api('DELETE', `/api/tickets/${T3.id}/comments/${c1.id}`, admin.token)).status, 404);
  check('USER cannot delete another author comment', (await api('DELETE', `/api/tickets/${T2.id}/comments/${c2.id}`, userA.token)).status, 403);
  check('admin deletes any comment', (await api('DELETE', `/api/tickets/${T2.id}/comments/${c2.id}`, admin.token)).status, 204);
  check('author deletes own comment', (await api('DELETE', `/api/tickets/${T2.id}/comments/${c1.id}`, userA.token)).status, 204);

  check('admin comments (leaves one for the cascade test)', (await api('POST', `/api/tickets/${T2.id}/comments`, admin.token, { content: 'Admin note' })).status, 201);
  const close = await api('PUT', `/api/tickets/${T2.id}`, admin.token, asUpdate(T2, { status: 'CLOSED' }));
  check('admin closes the ticket', close.status, 200);
  check('closed ticket rejects comments (409)', (await api('POST', `/api/tickets/${T2.id}/comments`, userA.token, { content: 'late' })).status, 409);

  check('admin deletes the ticket that has comments', (await api('DELETE', `/api/tickets/${T2.id}`, admin.token)).status, 204);
  check('its comments were deleted with it', sql(`SELECT COUNT(*) FROM comments WHERE ticket_id = ${Number(T2.id)}`), '0');
  check('comments of a deleted ticket are 404', (await api('GET', `/api/tickets/${T2.id}/comments`, admin.token)).status, 404);
  check('cleanup of the third ticket', (await api('DELETE', `/api/tickets/${T3.id}`, admin.token)).status, 204);

  console.log(`\n${passed} passed, ${failed.length} failed`);
  if (failed.length > 0) {
    console.log('\nFailed checks:');
    failed.forEach((name) => console.log(`  - ${name}`));
    process.exitCode = 1;
  }
}

main().catch((err) => {
  console.error(`\nStopped early: ${err.message}`);
  if (err.message === 'fetch failed') console.error(`Could not reach ${BASE}. Is the app running?`);
  process.exitCode = 1;
});
