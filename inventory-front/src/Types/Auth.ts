import { UserRole } from "@/constants/Strings";

export type ApiResponse<T> = {
  data: T;
  success: boolean;
};

export interface LoginResponse {
  accessToken: string;
  user: UserData;
}

export interface UserData {
  id: number;
  firstName: string;
  otherName: string;
  lastName: string;
  email: string;
  telephonePrimary: string;
  telephoneSecondary: string;
  profilePicture: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface registerPartA {
  firstName: string;
  otherName: string;
  lastName: string;
  email: string;
  password: string;
  businessName: string;
  businessEmail: string;
  businessContact: string;
  businessSecondaryContact: string;
}

export interface registerPartB {
  businessLogo: File | null;
}

export interface RegisterPayload {
  data: registerPartA;
  businessLogo: registerPartB;
}

export type AuthContextType = {
  userData: UserData | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (data: LoginPayload) => Promise<void>;
  logout: () => Promise<void>;
  register: (data: RegisterPayload) => Promise<void>;
};
