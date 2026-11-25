import { getToken } from "./firebase.js";

const API = "/api";

export async function api(path, options = {}) {
  const token = await getToken();

  const headers = {
    ...(options.headers || {}),
    Authorization: `Bearer ${token}`
  };

  if (options.body && !(options.body instanceof FormData)) {
    headers["Content-Type"] =
      headers["Content-Type"] || "application/json";
  }

  const response = await fetch(API + path, {
    ...options,
    headers
  });

  const text = await response.text();
  let data = {};

  try {
    data = text ? JSON.parse(text) : {};
  } catch {
    throw new Error("Invalid server response.");
  }

  if (!response.ok) {
    throw new Error(data.error || "Request failed.");
  }

  return data;
}

export async function getLabs() {
  return api("/labs");
}

export async function submitReport(report) {
  return api("/report", {
    method: "POST",
    body: JSON.stringify(report)
  });
}

export async function getTickets() {
  return api("/tickets");
}

export async function updateTicket(id, status) {
  return api("/tickets", {
    method: "POST",
    body: JSON.stringify({ id, status })
  });
}

export async function assignTicket(ticketId, workerId) {
  return api("/assignments", {
    method: "POST",
    body: JSON.stringify({
      ticket_id: ticketId,
      worker_id: workerId
    })
  });
}