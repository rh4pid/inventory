export const defaultBaseUrl = "http://localhost:5000";

export type UserRole =
  | "owner"
  | "admin"
  | "warehouse_manager"
  | "store_manager"
  | "receptionist"
  | "driver";

export const urls = {
  login: { meth: "GET", url: "auth" },
  register: { meth: "POST", url: "auth" },
  refresh: { meth: "GET", url: "auth/refresh" },
  logout: { meth: "POST", url: "auth/logout" },
  auhMe: (accessToken: string) => ({
    meth: "GET",
    url: `/auth/me/${accessToken}`,
  }),
};
