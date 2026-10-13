import { ApiResponse } from "@/Types/Auth";

export async function apiRequest<T>(
  url: string,
  method: string,
  data?: any,
  authToken?: string,
): Promise<ApiResponse<T>> {
  try {
    const headers = new Headers();

    let body: BodyInit | undefined;

    if (data instanceof FormData) {
      body = data;
    } else if (data !== undefined && data !== null) {
      headers.set("Content-Type", "application/json");
      body = JSON.stringify(data);
    }

    if (authToken) {
      headers.set("Authorization", "Bearer " + authToken);
    }
    const requestOptions: RequestInit = {
      method: method.toUpperCase(),
      headers,
      body,
      credentials: "include",
    };
    console.log(url);

    const response = await fetch(url, requestOptions);
    // if (!response.ok) {
    //   let message = response.statusText;

    //   try {
    //     const errorBody = await response.json();
    //     message = errorBody?.message || message;
    //   } catch {}

    //   console.log("api error call " + message);
    //   throw new Error(message);
    // }

    return {
      success: response.ok,
      data: (await response.json()) as T,
    };
  } catch (error: any) {
    console.log("API error:", error);
    throw error;
  }
}
