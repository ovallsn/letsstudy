import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { after, before, beforeEach, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  doc,
  getDoc,
  setDoc,
  Timestamp,
  writeBatch,
} from 'firebase/firestore';

let environment;

before(async () => {
  environment = await initializeTestEnvironment({
    projectId: 'demo-letsstudy',
    firestore: {
      rules: await readFile(new URL('../firestore.rules', import.meta.url), 'utf8'),
    },
  });
});

beforeEach(async () => {
  await environment.clearFirestore();
});

after(async () => {
  await environment?.cleanup();
});

test('private study data is available only to its signed-in owner', async () => {
  await environment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), 'users/alice/studyData/session'), { title: 'Private' });
  });

  const owner = environment.authenticatedContext('alice');
  const otherUser = environment.authenticatedContext('bob');
  const forgedAdmin = environment.authenticatedContext('bob', { admin: true });
  const anonymous = environment.unauthenticatedContext();

  await assertSucceeds(getDoc(doc(owner.firestore(), 'users/alice/studyData/session')));
  await assertFails(getDoc(doc(otherUser.firestore(), 'users/alice/studyData/session')));
  await assertFails(getDoc(doc(forgedAdmin.firestore(), 'users/alice/studyData/session')));
  await assertFails(getDoc(doc(anonymous.firestore(), 'users/alice/studyData/session')));
});

test('a signed-in learner can save private study data only under their own UID', async () => {
  const alice = environment.authenticatedContext('alice');
  const bob = environment.authenticatedContext('bob');

  await assertSucceeds(setDoc(doc(alice.firestore(), 'users/alice/studyData/session'), { title: 'Private' }));
  await assertFails(setDoc(doc(bob.firestore(), 'users/alice/studyData/session'), { title: 'Private' }));
});

test('a safe username can be reserved with its account profile', async () => {
  const learner = environment.authenticatedContext('alice');
  const db = learner.firestore();
  const batch = writeBatch(db);
  const username = 'riley_reader';
  const updatedAt = Timestamp.fromDate(new Date('2026-01-01T00:00:00.000Z'));

  batch.set(doc(db, 'users/alice/profile/account'), {
    displayName: 'Alex Morgan',
    username,
    updatedAt,
  });
  batch.set(doc(db, `usernameReservations/${username}`), { username });

  await assertSucceeds(batch.commit());
});

test('a disguised restricted username cannot be reserved directly', async () => {
  const learner = environment.authenticatedContext('alice');
  const db = learner.firestore();
  const batch = writeBatch(db);
  const username = 's_h1t';
  const updatedAt = Timestamp.fromDate(new Date('2026-01-01T00:00:00.000Z'));

  batch.set(doc(db, 'users/alice/profile/account'), {
    displayName: 'Learner',
    username,
    updatedAt,
  });
  batch.set(doc(db, `usernameReservations/${username}`), { username });

  await assertFails(batch.commit());
});

async function createLeaderboardEntry(currentStreakDays) {
  const learner = environment.authenticatedContext('alice');
  const db = learner.firestore();
  const entryId = '12345678-1234-4234-8234-123456789012';
  const displayName = 'riley_reader';
  const weekKey = '2026-W41';
  const batch = writeBatch(db);

  batch.set(doc(db, `leaderboardOwners/${entryId}`), {
    ownerUid: 'alice',
    entryId,
    displayName,
    weekKey,
  });
  batch.set(doc(db, 'users/alice/leaderboard/settings'), {
    enabled: true,
    entryId,
    displayName,
    updatedAt: Timestamp.now(),
    sharePhoto: false,
  });
  batch.set(doc(db, `publicLeaderboard/${entryId}`), {
    displayName,
    weeklyPoints: 120,
    weekKey,
    ...(currentStreakDays === undefined ? {} : { currentStreakDays }),
  });

  return batch.commit();
}

test('an opted-in learner can publish a bounded current streak on the public board', async () => {
  await assertSucceeds(createLeaderboardEntry(6));
});

test('a learner cannot publish a negative or unbounded current streak', async () => {
  await assertFails(createLeaderboardEntry(-1));
  await assertFails(createLeaderboardEntry(36501));
});

test('legacy public board entries can omit the current streak field', async () => {
  await assertSucceeds(createLeaderboardEntry(undefined));
});
