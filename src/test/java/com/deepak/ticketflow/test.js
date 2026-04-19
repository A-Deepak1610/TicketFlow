const axios = require("axios");

const BASE_URL = "http://localhost:8080";

const TOTAL_USERS = 2000;
const CONCURRENCY = 100;

const stats = {
  REGISTERED: 0,
  LOGGED_IN: 0,
  DIRECT: 0,
  SOFT_QUEUE: 0,
  HARD_QUEUE: 0,
  FAILED: 0
};

function updateStats(mode) {
  if (mode === "DIRECT") stats.DIRECT++;
  else if (mode === "SOFT_QUEUE") stats.SOFT_QUEUE++;
  else if (mode === "HARD_QUEUE") stats.HARD_QUEUE++;
  else stats.FAILED++;
}

/**
 * REGISTER
 */
async function registerUser(i) {
  try {
    await axios.post(`${BASE_URL}/register`, {
      userName: `user${i}`,
      password: "password123",
      email: `user${i}@test.com`
    });

    stats.REGISTERED++;
    console.log(`✅ [REGISTER] user${i}`);

  } catch (err) {
    if (err.response && err.response.status === 400) {
      console.log(`⚠️ [REGISTER] user${i} exists`);
    } else {
      stats.FAILED++;
      console.log(`❌ [REGISTER] user${i} failed`);
    }
  }
}

/**
 * LOGIN
 */
async function loginUser(i) {
  try {
    const res = await axios.post(`${BASE_URL}/login`, {
      userName: `user${i}`,
      password: "password123"
    });

    stats.LOGGED_IN++;
    console.log(`🔐 [LOGIN] user${i} success`);

    return res.data.accessToken;

  } catch (err) {
    stats.FAILED++;
    console.log(`❌ [LOGIN] user${i} failed`);
    return null;
  }
}

/**
 * JOIN QUEUE
 */
async function joinQueue(token, i) {
  try {
    const res = await axios.post(
      `${BASE_URL}/api/queue/join`,
      {
        eventId: 1,
        userType: "NORMAL"
      },
      {
        headers: { Authorization: `Bearer ${token}` }
      }
    );

    const mode = res.data.mode;
    updateStats(mode);

    if (mode === "DIRECT") {
      console.log(`🟢 [NO QUEUE] user${i}`);
    } else if (mode === "SOFT_QUEUE") {
      console.log(`🟡 [SOFT QUEUE] user${i} → pos ${res.data.position}`);
    } else if (mode === "HARD_QUEUE") {
      console.log(`🔴 [HARD QUEUE] user${i} → pos ${res.data.position}`);
    }

  } catch (err) {
    stats.FAILED++;
    console.log(`❌ [QUEUE] user${i} failed`);
  }
}

/**
 * FULL FLOW
 */
async function simulateUser(i) {
  await registerUser(i);

  const token = await loginUser(i);
  if (!token) return;

  await joinQueue(token, i);
}

/**
 * WORKER POOL
 */
async function runWithConcurrency() {
  let current = 1;

  async function worker() {
    while (true) {
      let i;

      if (current > TOTAL_USERS) break;
      i = current++;

      await simulateUser(i);

      // optional delay for realism
      // await new Promise(r => setTimeout(r, 5));
    }
  }

  const workers = [];
  for (let i = 0; i < CONCURRENCY; i++) {
    workers.push(worker());
  }

  await Promise.all(workers);
}

/**
 * RUN
 */
async function run() {
  console.log(`🚀 Starting ${TOTAL_USERS} users test with concurrency ${CONCURRENCY}`);
  console.time("⏱️ Total Time");

  await runWithConcurrency();

  console.timeEnd("⏱️ Total Time");

  console.log("\n📊 FINAL STATS:");
  console.log("👤 REGISTERED:", stats.REGISTERED);
  console.log("🔐 LOGGED IN:", stats.LOGGED_IN);
  console.log("🟢 NO QUEUE (DIRECT):", stats.DIRECT);
  console.log("🟡 SOFT QUEUE:", stats.SOFT_QUEUE);
  console.log("🔴 HARD QUEUE:", stats.HARD_QUEUE);
  console.log("❌ FAILED:", stats.FAILED);

  console.log("\n🚀 TEST COMPLETED");
}

run();