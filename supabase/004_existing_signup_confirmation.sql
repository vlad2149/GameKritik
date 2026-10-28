-- One-time operation approved for the educational, no-confirmation signup mode.
-- Already applied on 2026-10-07. Does not change passwords.
update auth.users set email_confirmed_at=now(),confirmation_token=''
where email is not null and email_confirmed_at is null;
