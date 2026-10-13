"use client";
import { useContext, createContext, useState, useEffect } from "react";
import {
  AuthContextType,
  LoginPayload,
  LoginResponse,
  RegisterPayload,
  UserData,
} from "@/Types/Auth";
import { apiRequest } from "@/utils/functions";
import { defaultBaseUrl, urls } from "@/constants/Strings";
import { useRouter } from "next/navigation";

export const AuthContext = createContext<AuthContextType | null>(null);

export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
  const router = useRouter();
  const [userData, setUserData] = useState<UserData | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [message, setMessage] = useState<string>("");
  const [isLoading, setLoading] = useState<boolean>(true);
  const [isAuthenticated, setAuthenticated] = useState<boolean>(false);

  useEffect(() => {
    refreshToken();
  }, [accessToken]);

  const refreshToken = async () => {
    try {
      const responseRefresh = await apiRequest<LoginResponse>(
        `${defaultBaseUrl}/${urls.refresh.url}`,
        `${urls.refresh.meth}`,
      );

      if (!responseRefresh.success) {
        console.log("REFRESH ERROR");
        return;
      }

      setAccessToken(responseRefresh.data.accessToken);
      setUserData(responseRefresh.data.user);
    } catch (error: any) {
      setAccessToken(null);
      setUserData(null);
      console.log("fetching user details  failed:", error.message);
    }
  };

  const login = async (data: LoginPayload) => {
    try {
      const responseLogin = await apiRequest<LoginResponse>(
        `${defaultBaseUrl}/${urls.login.url}`,
        `${urls.login.meth}`,
        data,
      );

      if (!responseLogin.success) {
        //return to the login page and send an alert
        setMessage("Check credentials");
        router.push("/pages");
        return;
      }

      setAccessToken(responseLogin.data.accessToken);
      setUserData(responseLogin.data.user);
      router.push("/pages");
    } catch (error: any) {
      setAccessToken(null);
      setUserData(null);
      console.log("Login failed:", error.message);
      //toast.error("Login failed")
    }
  };

  const register = async (data: RegisterPayload) => {
    try {
      const responseRegister = await apiRequest<string>(
        `${defaultBaseUrl}/${urls.register.url}`,
        `${urls.register.meth}`,
        data,
      );

      if (!responseRegister.success) {
        setMessage("Check credentials");
      }

      setMessage(responseRegister.data);
    } catch (error: any) {
      setAccessToken(null);
      setUserData(null);
      console.log("registration failed:", error.message);
      //toast.error("Login failed");
    }
  };

  const logout = async () => {
    try {
      const responseLogout = await apiRequest<string>(
        `${defaultBaseUrl}/${urls.logout.url}`,
        `${urls.logout.meth}`,
      );

      if (!responseLogout.success) {
        console.log("REFRESH ERROR");
        return;
      }

      setAccessToken(null);
      setUserData(null);
      setMessage(responseLogout.data);
    } catch (error: any) {
      setAccessToken(null);
      setUserData(null);
      console.log("fetching user details  failed:", error.message);
    }
  };

  return (
    <AuthContext.Provider
      value={{ userData, isLoading, isAuthenticated, login, register, logout }}
    >
      {children}
    </AuthContext.Provider>
  );
};
