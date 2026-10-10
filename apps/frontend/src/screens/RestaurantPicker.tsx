import { useNavigate } from "react-router";
import { useSession } from "../data/session";
import { ROLE_LABEL } from "../domain/permissions";

export function RestaurantPicker() {
  const { session, logout } = useSession();
  const navigate = useNavigate();
  if (!session) return null;

  return (
    <main className="auth">
      <div className="auth-card">
        <h1>Choose a restaurant</h1>
        <p className="muted">Signed in as {session.user.name}</p>
        <div className="picker">
          {session.restaurants.map((r) => (
            <button key={r.id} className="picker-item" onClick={() => navigate(`/r/${r.id}/order`)}>
              <strong>{r.name}</strong>
              <span className="muted">{ROLE_LABEL[r.role]}</span>
            </button>
          ))}
        </div>
        <button
          className="link"
          onClick={async () => {
            await logout();
            navigate("/login");
          }}
        >
          Sign out
        </button>
      </div>
    </main>
  );
}
