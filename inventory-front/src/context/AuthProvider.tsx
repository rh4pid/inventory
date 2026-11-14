"use client";
import { createContext, useContext, useState, useEffect, useCallback } from "react";
import { auth, setToken } from "@/lib/api";
import type { User, LoginPayload, SignUpPayload, LoginResponse } from "@/Types";

interface AuthContextType {
  user: User | null;
  accessToken: string | null;
  loading: boolean;
  login: (data: LoginPayload) => Promise<User>;
  /** Create an account and sign straight in. */
  signUp: (data: SignUpPayload) => Promise<User>;
  /** Replace the signed-in user (e.g. after changing password). */
  setUser: (u: User) => void;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    auth.refresh()
      .then(({ accessToken: t, user: u }) => {
        setToken(t);
        setAccessToken(t);
        setUser(u);
      })
      // Expired, revoked or suspended: clear the stale cookie so /login doesn't bounce back here.
      .catch(() => auth.logout().catch(() => {}))
      .finally(() => setLoading(false));
  }, []);

  const startSession = useCallback(({ accessToken: t, user: u }: LoginResponse) => {
    setToken(t);
    setAccessToken(t);
    setUser(u);
    return u;
  }, []);

  const login = useCallback(async (data: LoginPayload) => startSession(await auth.login(data)), [startSession]);
  const signUp = useCallback(async (data: SignUpPayload) => startSession(await auth.signUp(data)), [startSession]);

  const logout = useCallback(async () => {
    await auth.logout().catch(() => {});
    setToken(null);
    setAccessToken(null);
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{ user, accessToken, loading, login, signUp, logout, setUser }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
