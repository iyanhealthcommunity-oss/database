const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const validTs = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const COACH_UID = "coach_iyan";
const OTHER_COACH_UID = "coach_other";
const STANDARD_EMAIL = "sinta@ihc.test";
const BASIC_EMAIL = "budi@ihc.test";
const OTHER_EMAIL = "other@ihc.test";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

async function seedClients() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const ts = validTs();
    await db.collection("clients").doc("client_standard").set({
      coachUid: COACH_UID,
      fullName: "Sinta Standard",
      emailNormalized: STANDARD_EMAIL,
      packageTier: "STANDARD",
      status: "AKTIF",
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("clients").doc("client_basic").set({
      coachUid: COACH_UID,
      fullName: "Budi Basic",
      emailNormalized: BASIC_EMAIL,
      packageTier: "BASIC",
      status: "AKTIF",
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("clientInvites").doc(STANDARD_EMAIL).set({
      coachUid: COACH_UID,
      clientId: "client_standard",
      packageTier: "STANDARD",
      active: true,
      updatedAt: ts,
    });
    await db.collection("packageRules").doc("STANDARD").set({
      coachUid: COACH_UID,
      tier: "STANDARD",
      maxSessionsPerWeek: 3,
      canReschedule: true,
      updatedAt: ts,
    });
  });
}

test("Unauthenticated caller cannot read clients list or create client", async () => {
  await seedClients();
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("clients").get());
  await assertFails(unauth.collection("clients").doc("x").set({ fullName: "X" }));
});

test("Coach can create/query own clients and task schedules; another coach is blocked", async () => {
  const coachDb = testEnv.authenticatedContext(COACH_UID, { email: "iyanhealthcommunity@gmail.com" }).firestore();
  const ts = validTs();
  await assertSucceeds(
    coachDb.collection("clients").doc("c_1").set({
      coachUid: COACH_UID,
      fullName: "Andi Baru",
      emailNormalized: "andi@ihc.test",
      packageTier: "PREMIUM",
      status: "AKTIF",
      startDateMillis: 1700000000000,
      endDateMillis: 1702592000000,
      totalSessionsQuota: 24,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(coachDb.collection("clients").where("coachUid", "==", COACH_UID).get());
  await assertSucceeds(
    coachDb.collection("clientTaskSchedules").doc("c_1_FOTO_PROGRES").set({
      coachUid: COACH_UID,
      clientId: "c_1",
      taskType: "FOTO_PROGRES",
      enabled: true,
      frequencyDays: 7,
      notes: "",
      updatedAt: ts,
    })
  );

  const otherCoachDb = testEnv.authenticatedContext(OTHER_COACH_UID, { email: "othercoach@ihc.test" }).firestore();
  await assertFails(otherCoachDb.collection("clients").doc("c_1").get());
  await assertFails(otherCoachDb.collection("clientTaskSchedules").doc("c_1_FOTO_PROGRES").get());
});

test("Client can only read own client document and cannot read other clients", async () => {
  await seedClients();
  const sintaDb = testEnv.authenticatedContext("uid_sinta", { email: STANDARD_EMAIL }).firestore();
  await assertSucceeds(sintaDb.collection("clients").doc("client_standard").get());
  await assertSucceeds(sintaDb.collection("clients").where("emailNormalized", "==", STANDARD_EMAIL).get());
  await assertFails(sintaDb.collection("clients").doc("client_basic").get());
  await assertFails(sintaDb.collection("clients").where("emailNormalized", "==", BASIC_EMAIL).get());
});

test("Client cannot list clientInvites (prevents email enumeration) or list packageRules directly", async () => {
  await seedClients();
  const sintaDb = testEnv.authenticatedContext("uid_sinta", { email: STANDARD_EMAIL }).firestore();
  await assertFails(sintaDb.collection("clientInvites").get());
  await assertSucceeds(sintaDb.collection("clientInvites").doc(STANDARD_EMAIL).get());
  await assertSucceeds(sintaDb.collection("packageRules").doc("STANDARD").get());
  await assertFails(sintaDb.collection("packageRules").get());
});

test("Standard client CAN create rescheduleRequest; Basic client is BLOCKED by rules", async () => {
  await seedClients();
  const ts = validTs();
  const sintaDb = testEnv.authenticatedContext("uid_sinta", { email: STANDARD_EMAIL }).firestore();
  await assertSucceeds(
    sintaDb.collection("rescheduleRequests").doc("req_sinta").set({
      coachUid: COACH_UID,
      sessionId: "sess_1",
      clientId: "client_standard",
      clientEmail: STANDARD_EMAIL,
      clientName: "Sinta Standard",
      originalStartMillis: 1700000000000,
      requestedStartMillis: 1700086400000,
      requestedEndMillis: 1700090000000,
      reason: "Bentrok kerja",
      status: "MENUNGGU",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const budiDb = testEnv.authenticatedContext("uid_budi", { email: BASIC_EMAIL }).firestore();
  await assertFails(
    budiDb.collection("rescheduleRequests").doc("req_budi").set({
      coachUid: COACH_UID,
      sessionId: "sess_2",
      clientId: "client_basic",
      clientEmail: BASIC_EMAIL,
      clientName: "Budi Basic",
      originalStartMillis: 1700000000000,
      requestedStartMillis: 1700086400000,
      requestedEndMillis: 1700090000000,
      reason: "Mau pindah",
      status: "MENUNGGU",
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Client cannot spoof another client's workoutRequest or delete records", async () => {
  await seedClients();
  const ts = validTs();
  const otherDb = testEnv.authenticatedContext("uid_other", { email: OTHER_EMAIL }).firestore();
  await assertFails(
    otherDb.collection("workoutRequests").doc("wr_spoof").set({
      coachUid: COACH_UID,
      clientId: "client_standard",
      clientEmail: STANDARD_EMAIL,
      clientName: "Sinta",
      requestedExercise: "Squat",
      status: "MENUNGGU",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const coachDb = testEnv.authenticatedContext(COACH_UID, { email: "iyanhealthcommunity@gmail.com" }).firestore();
  await assertFails(coachDb.collection("clients").doc("client_standard").delete());
});
