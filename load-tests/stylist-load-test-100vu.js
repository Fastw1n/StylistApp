import http from "k6/http";
import { check, sleep } from "k6";
import { Rate } from "k6/metrics";

export const errorRate = new Rate("errors");

export const options = {
  scenarios: {
    baseline_user_flow: {
      executor: "ramping-vus",
      stages: [
  { duration: "30s", target: 50 },
  { duration: "1m", target: 50 },
  { duration: "30s", target: 100 },
  { duration: "1m", target: 100 },
  { duration: "30s", target: 0 },
],
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<1200"],
    errors: ["rate<0.05"],
  },
};

const BASE_URL = __ENV.BASE_URL || "http://localhost:8000";
const EMAIL = __ENV.EMAIL || "load.user@example.com";
const PASSWORD = __ENV.PASSWORD || "secret123";
const ITEM_IDS = (__ENV.ITEM_IDS || "")
  .split(",")
  .map((item) => item.trim())
  .filter(Boolean);

function request(method, path, body = null, token = null) {
  const params = {
    headers: {
      "Content-Type": "application/json",
    },
  };

  if (token) {
    params.headers.Authorization = `Bearer ${token}`;
  }

  const payload = body ? JSON.stringify(body) : null;
  return http.request(method, `${BASE_URL}${path}`, payload, params);
}

function getToken() {
  const login = request("POST", "/v1/auth/login", {
    email: EMAIL,
    password: PASSWORD,
  });

  if (login.status === 200) {
    return login.json("token");
  }

  const register = request("POST", "/v1/auth/register", {
    email: EMAIL,
    password: PASSWORD,
    name: "Load User",
  });

  return register.status === 200 ? register.json("token") : null;
}

export default function () {
  const token = getToken();
  const hasToken = check(token, {
    "auth token received": (value) => Boolean(value),
  });

  if (!hasToken) {
    errorRate.add(1);
    sleep(1);
    return;
  }

  const items = request("GET", "/v1/items", null, token);
  const outfits = request("GET", "/v1/outfits", null, token);
  const profile = request(
    "PUT",
    "/v1/profile/body",
    {
      height_cm: 171,
      weight_kg: 62,
      chest_cm: 88,
      waist_cm: 70,
    },
    token
  );

  const checksPassed = check(null, {
    "items listed": () => items.status === 200,
    "outfits listed": () => outfits.status === 200,
    "profile saved": () => profile.status === 200,
  });

  errorRate.add(!checksPassed);

  if (ITEM_IDS.length > 0) {
    const outfit = request(
      "POST",
      "/v1/outfits",
      {
        name: `Load outfit ${__VU}-${__ITER}`,
        item_ids: ITEM_IDS,
        style: "casual",
        colors: ["white", "blue"],
        season: "summer",
      },
      token
    );

    const outfitCreated = check(outfit, {
      "outfit created": (response) => response.status === 200,
    });
    errorRate.add(!outfitCreated);
  }

  sleep(1);
}
