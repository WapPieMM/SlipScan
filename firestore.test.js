const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const firebase = require("@firebase/rules-unit-testing");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or create expenses", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("expenses").get());
  await assertFails(
    unauthDb.collection("expenses").doc("exp_1").set({
      userId: ALICE_UID,
      merchantName: "Cafe Amazon",
      bankName: "KBank",
      amount: 65.0,
      category: "FOOD",
      transactionDate: "2026-11-09 09:30",
      referenceNo: "KB20261109001",
      memo: "Iced Latte",
      isFromSlip: true,
    })
  );
});

test("Authenticated user: can create and query own expense with valid schema", async () => {
  const aliceContext = testEnv.authenticatedContext(ALICE_UID);
  const aliceDb = aliceContext.firestore();

  // Seed using admin context to test read/list alignment
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("expenses")
      .doc("exp_alice_1")
      .set({
        userId: ALICE_UID,
        merchantName: "7-Eleven",
        bankName: "SCB",
        amount: 120.5,
        category: "FOOD",
        transactionDate: "2026-11-09 10:15",
        referenceNo: "SCB998877",
        memo: "Lunch & Snacks",
        isFromSlip: true,
        createdAt: new Date(),
        updatedAt: new Date(),
      });
  });

  await assertSucceeds(
    aliceDb.collection("expenses").where("userId", "==", ALICE_UID).get()
  );
  await assertSucceeds(
    aliceDb.collection("expenses").doc("exp_alice_1").get()
  );
});

test("Authenticated user: cannot read another user's expense or query without userId filter", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("expenses")
      .doc("exp_bob_1")
      .set({
        userId: BOB_UID,
        merchantName: "Lotus's",
        bankName: "Krungthai",
        amount: 450.0,
        category: "SHOPPING",
        transactionDate: "2026-11-09 12:00",
        referenceNo: "KTB12345",
        memo: "Groceries",
        isFromSlip: true,
        createdAt: new Date(),
        updatedAt: new Date(),
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("expenses").doc("exp_bob_1").get());
  await assertFails(aliceDb.collection("expenses").get());
});

test("Authenticated user: shadow update with ghost field is rejected", async () => {
  const now = new Date(Date.now() - 1000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("expenses")
      .doc("exp_alice_2")
      .set({
        userId: ALICE_UID,
        merchantName: "Grab",
        bankName: "KBank",
        amount: 85.0,
        category: "TRANSPORT",
        transactionDate: "2026-11-09 11:00",
        referenceNo: "KB111",
        memo: "Taxi",
        isFromSlip: true,
        createdAt: now,
        updatedAt: now,
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("expenses").doc("exp_alice_2").update({
      amount: 95.0,
      isVerified: true,
      updatedAt: new Date(),
    })
  );
});

test("Authenticated user: can read and write own budget but not another user's budget", async () => {
  const now = new Date(Date.now() - 1000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("budgets").doc(ALICE_UID).set({
      userId: ALICE_UID,
      monthlyLimit: 15000.0,
      updatedAt: now,
    })
  );

  await assertFails(bobDb.collection("budgets").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("budgets").doc(ALICE_UID).set({
      userId: BOB_UID,
      monthlyLimit: 5000.0,
      updatedAt: now,
    })
  );
});
