import { useState, type FormEvent } from "react";
import { Banner } from "../components/Banner";
import { Sheet } from "../components/Sheet";
import { api } from "../data/client";
import { useAction, useLoad } from "../data/hooks";
import { useRestaurant } from "../data/session";
import { assignableRoles, canManage, ROLE_LABEL } from "../domain/permissions";
import type { Role, StaffMember } from "../domain/types";

/** Who works here, their role, and the PIN they use to take over the shared tablet. */
export function StaffScreen() {
  const { rid, me } = useRestaurant();
  const { data: staff, error, reload } = useLoad(() => api.staff(rid), [rid]);
  const [inviting, setInviting] = useState(false);
  const [pinFor, setPinFor] = useState<StaffMember | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const action = useAction();
  const roles = assignableRoles(me.role);

  if (error) return <div className="splash">{error.message}</div>;
  if (!staff) return <div className="splash">Loading…</div>;

  async function change(member: StaffMember, changes: Parameters<typeof api.saveStaff>[2], message: string) {
    if (await action.run(() => api.saveStaff(rid, member.userId, changes))) {
      setNotice(message);
      await reload();
    }
  }

  return (
    <main className="page">
      <div className="page-head">
        <h1>Staff</h1>
        <button className="secondary" onClick={() => setInviting(true)}>
          + Add staff
        </button>
      </div>
      <p className="muted">
        Owners manage everyone and the restaurant settings. Managers edit the menu and manage cashiers. Cashiers take orders and mark dishes sold out.
      </p>
      {notice && (
        <Banner kind="ok" onClose={() => setNotice(null)}>
          {notice}
        </Banner>
      )}
      {action.error && <Banner kind="error">{action.error}</Banner>}

      <div className="staff-table" role="table">
        {staff.map((s) => {
          const manageable = canManage(me.role, s.role);
          return (
            <div key={s.userId} role="row" className={s.active ? "staff-row" : "staff-row inactive"}>
              <span role="cell" className="staff-name">
                <strong>{s.name}</strong>
                {s.userId === me.userId && <span className="tag">You</span>}
                <span className="muted">{s.email}</span>
              </span>
              <span role="cell">
                {manageable && roles.length > 1 ? (
                  <select value={s.role} disabled={action.busy} onChange={(e) => change(s, { role: e.target.value as Role }, `${s.name} is now ${ROLE_LABEL[e.target.value as Role].toLowerCase()}.`)}>
                    {roles.map((r) => (
                      <option key={r} value={r}>
                        {ROLE_LABEL[r]}
                      </option>
                    ))}
                  </select>
                ) : (
                  ROLE_LABEL[s.role]
                )}
              </span>
              <span role="cell" className="muted">
                {!s.active ? "Turned off" : s.invited ? "Invited" : "Active"} · {s.hasPin ? "PIN set" : "No PIN"}
              </span>
              <span role="cell" className="staff-actions">
                {manageable && s.active && (
                  <button className="secondary small" onClick={() => setPinFor(s)}>
                    {s.hasPin ? "Change PIN" : "Set PIN"}
                  </button>
                )}
                {manageable && s.userId !== me.userId && (
                  <button
                    className={s.active ? "link danger-link" : "link"}
                    disabled={action.busy}
                    onClick={() =>
                      change(s, { active: !s.active }, s.active ? `${s.name} can no longer sign in here.` : `${s.name} can sign in again.`)
                    }
                  >
                    {s.active ? "Turn off" : "Turn on"}
                  </button>
                )}
              </span>
            </div>
          );
        })}
      </div>

      {inviting && (
        <InviteSheet
          rid={rid}
          roles={roles}
          onClose={() => setInviting(false)}
          onDone={(m) => {
            setInviting(false);
            setNotice(`${m.name} was added. In the real app they get an email; in this demo they sign in with ${m.email} and password “demo”.`);
            void reload();
          }}
        />
      )}
      {pinFor && (
        <PinSheet
          member={pinFor}
          onClose={() => setPinFor(null)}
          onSave={async (pin) => {
            setPinFor(null);
            await change(pinFor, { pin }, pin ? `${pinFor.name}'s PIN is set.` : `${pinFor.name}'s PIN was removed.`);
          }}
        />
      )}
    </main>
  );
}

function InviteSheet({ rid, roles, onClose, onDone }: { rid: string; roles: Role[]; onClose(): void; onDone(m: StaffMember): void }) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<Role>("cashier");
  const { busy, error, run } = useAction();

  async function submit(e: FormEvent) {
    e.preventDefault();
    const member = await run(() => api.inviteStaff(rid, { name, email, role }));
    if (member) onDone(member);
  }

  return (
    <Sheet title="Add staff" onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        <label className="stacked">
          Name
          <input value={name} onChange={(e) => setName(e.target.value)} required />
        </label>
        <label className="stacked">
          Email
          <input type="email" autoCapitalize="none" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label className="stacked">
          Role
          <select value={role} onChange={(e) => setRole(e.target.value as Role)}>
            {roles.map((r) => (
              <option key={r} value={r}>
                {ROLE_LABEL[r]}
              </option>
            ))}
          </select>
        </label>
        {error && <Banner kind="error">{error}</Banner>}
        <button className="primary" disabled={busy}>
          {busy ? "Adding…" : "Add and send invite"}
        </button>
      </form>
    </Sheet>
  );
}

function PinSheet({ member, onClose, onSave }: { member: StaffMember; onClose(): void; onSave(pin: string | null): void }) {
  const [pin, setPin] = useState("");
  return (
    <Sheet title={`PIN for ${member.name}`} onClose={onClose}>
      <form
        className="stack"
        onSubmit={(e) => {
          e.preventDefault();
          if (/^\d{4}$/.test(pin)) onSave(pin);
        }}
      >
        <p className="muted">Four digits. They enter it on the shared tablet to take orders under their name.</p>
        <input
          className="pin-input"
          inputMode="numeric"
          autoComplete="off"
          maxLength={4}
          value={pin}
          onChange={(e) => setPin(e.target.value.replace(/\D/g, ""))}
          aria-label="New PIN"
          autoFocus
        />
        <button className="primary" disabled={!/^\d{4}$/.test(pin)}>
          Save PIN
        </button>
        {member.hasPin && (
          <button type="button" className="link danger-link" onClick={() => onSave(null)}>
            Remove PIN
          </button>
        )}
      </form>
    </Sheet>
  );
}
