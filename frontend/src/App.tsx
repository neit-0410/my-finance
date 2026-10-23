import { useCallback, useEffect, useState } from "react";
import { readSession, SESSION_KEY, tokenExpiry } from "./api";
import type { Session } from "./api";
import Auth from "./Auth";
import Dashboard from "./Dashboard";
export default function App() {
  const [session, setSession] = useState<Session | null>(readSession),
    [notice, setNotice] = useState("");
  const logout = useCallback((message = "") => {
    try {
      sessionStorage.removeItem(SESSION_KEY);
    } catch {
      /* Storage can be disabled. */
    }
    setSession(null);
    setNotice(message);
  }, []);
  useEffect(() => {
    if (!session) return;
    const expire = () =>
      logout("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
    window.addEventListener("session-expired", expire);
    const timer = window.setTimeout(
      expire,
      Math.max(
        0,
        Math.min(2147483647, tokenExpiry(session.token) - Date.now()),
      ),
    );
    return () => {
      window.removeEventListener("session-expired", expire);
      clearTimeout(timer);
    };
  }, [session, logout]);
  function login(value: Session) {
    try {
      sessionStorage.setItem(SESSION_KEY, JSON.stringify(value));
    } catch {
      /* Keep session in memory. */
    }
    setNotice("");
    setSession(value);
  }
  return session ? (
    <Dashboard session={session} logout={logout} />
  ) : (
    <Auth onLogin={login} notice={notice} />
  );
}
