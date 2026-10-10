import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router";
import { Banner } from "../components/Banner";
import { api } from "../data/client";
import { useAction } from "../data/hooks";
import { DEMO_LOGINS, DEMO_PASSWORD } from "../data/seed";
import { useSession } from "../data/session";

export function LoginScreen() {
  const { login, refresh } = useSession();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const { busy, error, run } = useAction();

  async function signIn(e: string, p: string) {
    const session = await run(() => login(e, p));
    if (!session) return;
    navigate(session.restaurants.length === 1 ? `/r/${session.restaurants[0].id}/order` : "/restaurants", { replace: true });
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    void signIn(email, password);
  }

  return (
    <main className="auth">
      <form className="auth-card" onSubmit={submit}>
        <h1>Sign in</h1>
        <label>
          Email
          <input type="email" autoComplete="username" autoCapitalize="none" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label>
          Password
          <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {error && <Banner kind="error">{error}</Banner>}
        <button className="primary" disabled={busy}>
          {busy ? "Signing in…" : "Sign in"}
        </button>

        <section className="demo">
          <h2>Demo logins</h2>
          <p className="muted">This build runs on sample data. Tap a role to sign in (password “{DEMO_PASSWORD}”).</p>
          <div className="demo-buttons">
            {DEMO_LOGINS.map((d) => (
              <button key={d.email} type="button" className="secondary" disabled={busy} onClick={() => signIn(d.email, DEMO_PASSWORD)}>
                {d.label}
                <small>
                  {d.email} · PIN {d.pin}
                </small>
              </button>
            ))}
          </div>
          <button
            type="button"
            className="link"
            onClick={async () => {
              if (!confirm("Start the demo over? Every change you made will be lost.")) return;
              api.resetDemo();
              await refresh();
            }}
          >
            Reset demo data
          </button>
        </section>
      </form>
    </main>
  );
}
